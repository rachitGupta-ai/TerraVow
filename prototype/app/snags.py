"""Machine instances, snag submission, the Reporter/Resolver workflow, guidance and health views
(design E2, E7.1, E8; Req 22-25).

ROLE HANDLING. There is NO authentication or authorisation in this prototype. The `role` and
`handle` sent with every write are self-declared; the role check below is WORKFLOW GUIDANCE that
keeps the demo flow honest (a Reporter cannot accept, resolve or close), NOT access control
(Req 25.2, 25.11). Handles are pseudonyms; no names, emails or phone numbers are collected.
"""
from __future__ import annotations
import re
import uuid
from datetime import date
from typing import Dict, List, Optional, Tuple

from . import catalogue, engine, industries, templates
from .settings import ANSWER_MAX_CHARS, HISTORY_RESPONSE_MAX, INSTANCES_MAX, SNAGS_PER_MACHINE_MAX

HISTORY = "snag_history"
SNAGS = "snags"
REPORTER, RESOLVER = "REPORTER", "RESOLVER"
ROLES = (REPORTER, RESOLVER)
HANDLE_RE = re.compile(r"^[A-Za-z0-9_-]{3,32}$")
TAG_RE = re.compile(r"^[A-Za-z0-9 _.-]+$")
GUIDANCE_LABEL = "Template guidance (AI-generated or generic), for qualified-engineer review"
NO_GUIDANCE = "no template guidance available"
ESTIMATE_LABELS = ["estimate", "illustrative", "not-an-official-certification"]

# action -> (role, allowed from statuses, to status)            (E8 permitted-actions table)
TRANSITIONS = {
    "accept": (RESOLVER, ("OPEN",), "ASSIGNED"),
    "resolve": (RESOLVER, ("ASSIGNED", "OPEN"), "RESOLVED"),
    "close": (RESOLVER, ("RESOLVED",), "CLOSED"),
}


class Forbidden(Exception):
    """403: the role may not perform this action."""


class Conflict(Exception):
    """409: the action is not permitted from the current status."""


class TooMany(Exception):
    """429: a configured cap is reached."""


def handle_ok(h: object) -> bool:
    return isinstance(h, str) and HANDLE_RE.fullmatch(h) is not None


# ---------------------------------------------------------------- redaction (E8, Req 22.10)
_DATE = re.compile(r"\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2}(:\d{2})?)?")
_EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
_PHONE = re.compile(r"(?<![\w:.-])\+?\d(?:[ ().-]?\d){9,14}(?![\w:])")


def redact(text: str) -> str:
    """Deterministic, idempotent: protect ISO dates, then remove emails and 10-15 digit phones."""
    if not text:
        return text
    protected: List[str] = []

    def _keep(m):
        protected.append(m.group(0))
        return f"\x00{len(protected) - 1}\x00"
    s = _DATE.sub(_keep, text)
    s = _EMAIL.sub("[email removed]", s)
    s = _PHONE.sub("[phone removed]", s)
    return re.sub(r"\x00(\d+)\x00", lambda m: protected[int(m.group(1))], s)


# ---------------------------------------------------------------- template / form schema
def template_for(store, model_key: str, industry: str) -> Tuple[dict, str, str]:
    """(template, template_source, schema_version): stored AI template, else the industry Base."""
    doc = templates.get_stored(store, model_key)
    if doc is not None:
        return doc["template"], "ai", doc["schema_version"]
    base = templates.BASES[industry]
    return base["template"], "base", base["base_template_id"]


def validate_answers(form_schema: List[dict], payload: dict) -> List[dict]:
    """Every error is collected before rejecting (Req 22.5). Returns [{field, reason}]."""
    errs: List[dict] = []
    by_id = {f["id"]: f for f in form_schema}
    sev = payload.get("severity")
    if isinstance(sev, bool) or not isinstance(sev, int) or not 1 <= sev <= 5:
        errs.append({"field": "severity", "reason": "required integer from 1 to 5"})
    st = payload.get("snag_type_id")
    snag_ids = [o for o in by_id["snag_type"]["options"] if o != "other"]
    if st is not None and st not in snag_ids:
        errs.append({"field": "snag_type_id", "reason": "must be null or one of this model's snag types"})
    answers = payload.get("answers")
    if not isinstance(answers, dict):
        return errs + [{"field": "answers", "reason": "must be an object of field id to text"}]
    for k in sorted(answers):
        f = by_id.get(k)
        if f is None or k in ("snag_type", "severity"):
            errs.append({"field": f"answers.{k}", "reason": "unknown field"})
    for f in form_schema:
        if f["id"] in ("snag_type", "severity"):
            continue
        k, v = f["id"], answers.get(f["id"])
        if v is None or (isinstance(v, str) and not v.strip()):
            if f.get("required"):
                errs.append({"field": f"answers.{k}", "reason": "required"})
            continue
        if not isinstance(v, str):
            errs.append({"field": f"answers.{k}", "reason": "must be text"})
        elif f["type"] == "dropdown":
            if v not in (f.get("options") or []):
                errs.append({"field": f"answers.{k}", "reason": "must be one of the listed options"})
        else:
            limit = min(f.get("max_length") or ANSWER_MAX_CHARS, ANSWER_MAX_CHARS)
            if len(v) > limit:
                errs.append({"field": f"answers.{k}", "reason": f"at most {limit} characters"})
    return errs


# ---------------------------------------------------------------- headers / instances
def get_header(store, machine_id: str) -> Optional[dict]:
    return store.get_doc(HISTORY, machine_id)


def create_instance(store, entry: dict, tag: str, install_year: Optional[int], handle: str, now: str) -> dict:
    if sum(1 for h in store.list_docs(HISTORY) if not h.get("seeded")) >= INSTANCES_MAX:
        raise TooMany(f"At most {INSTANCES_MAX} machine instances in this demo.")
    mid = "INST-" + uuid.uuid4().hex[:8]
    header = {"machine_id": mid, "tag": tag, "industry": entry["industry"], "model_key": entry["id"],
              "catalogue_id": entry["id"], "schema_version": None, "install_year": install_year,
              "seeded": False, "synthetic": False, "created_at": now, "created_by_handle": handle}
    store.create_if_absent(HISTORY, mid, header)
    return header


def list_instances(store, model_key: Optional[str]) -> List[dict]:
    rows = store.query_eq(HISTORY, "model_key", model_key) if model_key else store.list_docs(HISTORY)
    rows.sort(key=lambda h: (h.get("created_at") or "", h["machine_id"]), reverse=True)
    return rows[:200]


# ---------------------------------------------------------------- snags
def snags_of(store, machine_id: str) -> List[dict]:
    rows = store.query_eq(SNAGS, "machine_id", machine_id)
    return sorted(rows, key=lambda s: (s["reported_at"], s["snag_id"]))


def submit(store, header: dict, payload: dict, handle: str, now: str) -> Tuple[Optional[dict], List[dict], dict]:
    """Validate and store a new OPEN snag. Returns (snag, errors, template); nothing stored on error."""
    tpl, source, version = template_for(store, header["model_key"], header["industry"])
    errs = validate_answers(templates.build_form_schema(tpl), payload)
    if errs:
        return None, errs, tpl
    if len(store.query_eq(SNAGS, "machine_id", header["machine_id"])) >= SNAGS_PER_MACHINE_MAX:
        raise TooMany(f"At most {SNAGS_PER_MACHINE_MAX} snags per machine in this demo.")
    by_id = {f["id"]: f for f in templates.build_form_schema(tpl)}
    answers = {k: (redact(v) if by_id[k]["type"] == "text" else v) for k, v in payload["answers"].items()
               if isinstance(v, str) and v.strip()}
    st = next((s for s in tpl["snag_types"] if s["id"] == payload.get("snag_type_id")), None)
    sid = "SNG-" + uuid.uuid4().hex[:12]
    snag = {
        "snag_id": sid, "machine_id": header["machine_id"], "model_key": header["model_key"],
        "schema_version": version, "industry": header["industry"],
        "snag_type_id": st["id"] if st else None, "category": st["category"] if st else "other",
        "severity": payload["severity"], "answers": answers,
        "recommended_job_id": st["recommended_job_id"] if st else None, "template_source": source,
        "reported_at": now, "reporter_handle": handle, "reporter_role": REPORTER, "status": "OPEN",
        "events": [{"ts": now, "from_status": None, "to_status": "OPEN", "actor_handle": handle,
                    "actor_role": REPORTER}],
        "resolution": None, "photo_analysis_ref": payload.get("photo_analysis_ref"), "seeded": False,
    }
    store.create_if_absent(SNAGS, sid, snag)
    store.update_doc(HISTORY, header["machine_id"], lambda h: {**h, "schema_version": version})
    return snag, [], tpl


def transition(store, snag_id: str, action: str, role: str, handle: str, now: str,
               resolution: Optional[dict] = None) -> dict:
    """Atomic, append-only status change (E8). Raises KeyError, Forbidden or Conflict; the snag is
    never modified on rejection."""
    need_role, froms, to = TRANSITIONS[action]
    if role != need_role:
        raise Forbidden(f"Only a {need_role.lower()} can {action} a snag (role switch is workflow guidance).")

    def fn(doc: dict) -> dict:
        if doc["status"] not in froms:
            raise Conflict(f"Cannot {action} a snag that is {doc['status']}.")
        ev = {"ts": now, "from_status": doc["status"], "to_status": to, "actor_handle": handle,
              "actor_role": role}
        doc["events"] = list(doc["events"]) + [ev]
        doc["status"] = to
        if action == "resolve":
            r = dict(resolution or {})
            doc["resolution"] = {"resolver_handle": handle, "resolved_at": now,
                                 "notes": redact(r.get("notes") or ""),
                                 "actions_taken": redact(r.get("actions_taken") or ""),
                                 "downtime_hours": r.get("downtime_hours"), "cost_inr": r.get("cost_inr")}
        return doc
    return store.update_doc(SNAGS, snag_id, fn)


def audit_summary(snag: dict, action: str, handle: str) -> str:
    """Structured fields only; never answers, notes or actions_taken (E8 on close)."""
    r = snag.get("resolution") or {}
    s = (f"snag {snag['snag_id']} {action} on {snag['machine_id']}: type {snag['snag_type_id'] or 'other'}, "
         f"severity {snag['severity']}, resolver {r.get('resolver_handle')}")
    if action == "closed":
        s += f", closer {handle}"
    return s + f", downtime {r.get('downtime_hours')} h, cost {r.get('cost_inr')} INR"


# ---------------------------------------------------------------- guidance (E8, review M4)
def resolve_guidance(snag: dict, store) -> dict:
    """Stored AI template at the snag's schema version -> industry Base_Template -> none. Never Gemini."""
    sid = snag.get("snag_type_id")
    candidates = []
    doc = store.get_doc(templates.TEMPLATES, industries.template_id(snag["model_key"], snag.get("schema_version")))
    if doc is not None and doc.get("source") == "ai":
        candidates.append(("ai", doc["template"]))
    candidates.append(("fallback-base", templates.BASES[snag["industry"]]["template"]))
    for source, tpl in candidates:
        st = next((s for s in tpl["snag_types"] if sid and s["id"] == sid), None)
        if st is None:
            continue
        job = next((j for j in tpl["job_types"] if j["id"] == st.get("recommended_job_id")), None)
        return {"guidance_source": source, "label": GUIDANCE_LABEL, "snag_type_name": st["name"],
                "recommended_job": {"job_id": job["id"], "job_name": job["name"],
                                    "checklist": job.get("checklist", [])} if job else None,
                "immediate_action": st.get("immediate_action"),
                "time_to_fix_hours": st.get("time_to_fix_hours"), "safety_hazards": st.get("safety_hazards", []),
                "escalation_criteria": st.get("escalation_criteria")}
    return {"guidance_source": "none", "label": NO_GUIDANCE, "snag_type_name": None, "recommended_job": None,
            "immediate_action": None, "time_to_fix_hours": None, "safety_hazards": [], "escalation_criteria": None}


def queue(store) -> List[dict]:
    rows = store.query_in(SNAGS, "status", ["OPEN", "ASSIGNED"])
    rows.sort(key=lambda s: (-s["severity"], s["reported_at"], s["snag_id"]))
    return [{"snag": s, **resolve_guidance(s, store)} for s in rows]


# ---------------------------------------------------------------- facts and health (E7)
def _d(ts: str) -> date:
    return date.fromisoformat(ts[:10])


def snag_facts(docs: List[dict], as_of: date) -> List[engine.SnagFact]:
    """Replay each snag's append-only events up to as_of (E7.1)."""
    out = []
    for s in docs:
        evs = [e for e in s["events"] if _d(e["ts"]) <= as_of]
        if _d(s["reported_at"]) > as_of or not evs:
            continue
        resolved = next((_d(e["ts"]) for e in evs if e["to_status"] == "RESOLVED"), None)
        out.append(engine.SnagFact(s.get("snag_type_id"), s.get("category") or "other", int(s["severity"]),
                                   _d(s["reported_at"]), resolved, evs[-1]["to_status"] in ("OPEN", "ASSIGNED")))
    return out


def health_view(store, header: dict, as_of: date, machine: Optional[engine.Machine]) -> dict:
    entry = catalogue.get_entry(store, header["model_key"]) or {}
    ind = industries.INDUSTRY_BY_ID[header["industry"]]
    life = int(entry.get("design_life_years") or 25)
    docs = snags_of(store, header["machine_id"])[-HISTORY_RESPONSE_MAX:]
    hs = engine.health_score(snag_facts(docs, as_of), header.get("install_year"), life, as_of)
    ls = engine.remaining_lifespan(hs["score"], hs["age_days"], life, hs["total_snags"])

    def intensity(h: float, days: float = 0.0) -> dict:
        return engine.pollution_intensity(
            h, industries.domains_of(header["industry"]), industries.DOMAIN_INDICATORS,
            industries.INDICATOR_UNITS, ind["default_baselines"], float(entry.get("scale") or 1.0),
            entry.get("domain_scale"), machine, days)
    pol = intensity(hs["score"], ls["remaining_days"])
    r1 = lambda x: round(x, 1)
    return {
        "machine_id": header["machine_id"], "tag": header.get("tag"), "industry": header["industry"],
        "model_key": header["model_key"], "as_of": as_of.isoformat(),
        "age_years": None if hs["age_days"] is None else round(hs["age_days"] / 365, 1),
        "age_factor_penalty": r1(hs["age_penalty"]), "design_life_years": life,
        "health": {"score": r1(hs["score"]), "band": hs["band"], "confidence": hs["confidence"],
                   "confidence_reason": hs["confidence_reason"], "factors": hs["factors"],
                   "trend_label": hs["trend_label"], "wear_pct": r1(100 - hs["score"])},
        "lifespan": {"remaining_days": round(ls["remaining_days"]), "low_days": round(ls["low_days"]),
                     "high_days": round(ls["high_days"]), "basis": ls["basis"], "flags": ls["flags"],
                     "label": ls["label"]},
        "pollution": {"domains": pol["domains"], "sev_equiv": round(pol["sev_equiv"], 3),
                      "per_day": {k: {**v, "value": round(v["value"], 2),
                                      "baseline_reference": round(v["baseline_reference"], 2)}
                                  for k, v in pol["per_day"].items()},
                      "over_remaining_lifespan": {k: round(v) for k, v in pol["over_remaining_lifespan"].items()},
                      "label": "Illustrative estimate, not a measurement",
                      "air_source": "fleet machine fuel model (from snag history)" if machine else "industry baseline x scale"},
        "timeline": engine.health_timeline(lambda d: snag_facts(docs, d), header.get("install_year"), life,
                                           as_of, intensity),
        "snag_markers": [{"snag_id": s["snag_id"], "date": s["reported_at"][:10], "severity": s["severity"],
                          "status": s["status"], "snag_type_id": s.get("snag_type_id")} for s in docs],
        "assumptions": engine.HEALTH_ASSUMPTIONS, "synthetic": bool(header.get("synthetic")),
        "labels": ESTIMATE_LABELS,
    }


def health_summary(view: dict) -> dict:
    """Req 25.8 summary returned by /close."""
    return {"health": {k: view["health"][k] for k in ("score", "band", "confidence")},
            "lifespan": {k: view["lifespan"][k] for k in ("remaining_days", "low_days", "high_days", "label")},
            "pollution": {k: view["pollution"][k] for k in ("per_day", "over_remaining_lifespan", "label")},
            "labels": ESTIMATE_LABELS}
