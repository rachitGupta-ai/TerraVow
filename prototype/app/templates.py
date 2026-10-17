"""Deep machine templates: validation, form schema, Base_Template fallback, ensure (E6, E5; Req 20, 21).

Gemini drafts the template; this module decides whether it is acceptable. Every limit is
enforced here because Gemini's response_schema does not honour patterns or lengths.
The fallback (an adapted Base_Template) is never stored and never labelled as AI output.
"""
from __future__ import annotations

import copy
import json
import logging
import math
import os
import re
import time
import unicodedata
from typing import Any, Dict, List, Optional, Tuple

from pydantic import ValidationError

from . import cache_once, gemini_client, industries, settings
from .settings import (ANSWER_MAX_CHARS, TEMPLATE_MAX_BYTES, TEMPLATE_MIN_FORM_FIELDS,
                       TEMPLATE_MIN_JOBS, TEMPLATE_MIN_PARAMS, TEMPLATE_MIN_SNAGS,
                       BASE_OVERLAP_MAX)
from .templates_schema import GeneratedTemplate

log = logging.getLogger("terravow.templates")

DISCLAIMER = ("AI-generated or generic guidance for qualified-engineer review. Intervals, costs and "
              "root-cause lists are not manufacturer instructions.")
TEMPLATES = "templates"
CORE_IDS = ("snag_type", "severity", "description")
ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,39}$")

USER_MESSAGES = {
    "cache-hit": "Loaded the saved template for this model.",
    "generated": "Generated now and saved for everyone using this model.",
    "generated-unsaved": "Generated now but could not be saved; it will be regenerated next time.",
    "fallback": ("AI generation is unavailable. This is a generic template for the industry, "
                 "not generated for this model."),
}

# ---------------------------------------------------------------- 1-3 sanitise and truncate
_ZERO_WIDTH = re.compile(r"[\u200b-\u200f\u2028\u2029\u202a-\u202e\u2060-\u2064\ufeff]")
_CONTROL = re.compile(r"[\u0000-\u001f\u007f-\u009f]")
NAME_KEYS = ("name", "label")
SHORT_LISTS = ("checklist", "parts_and_tools", "safety_and_permit_notes", "parts")


def _clean(s: str) -> str:
    s = unicodedata.normalize("NFC", s)
    s = _ZERO_WIDTH.sub("", s)
    s = _CONTROL.sub(" ", s)
    return re.sub(r"\s+", " ", s).strip()


def _sanitise(node: Any) -> Any:
    if isinstance(node, str):
        return _clean(node)
    if isinstance(node, list):
        return [_sanitise(x) for x in node]
    if isinstance(node, dict):
        return {k: _sanitise(v) for k, v in node.items()}
    return node


class _Counter:
    def __init__(self) -> None:
        self.n = 0

    def cut(self, s: str, limit: int) -> str:
        if len(s) <= limit:
            return s
        self.n += 1
        return s[: limit - 1] + "…"


def _truncate(node: Any, c: _Counter, key: str = "") -> Any:
    if isinstance(node, dict):
        out = {}
        for k, v in node.items():
            if isinstance(v, str):
                out[k] = c.cut(v, 120 if k in NAME_KEYS else 600)
            elif k == "options" and isinstance(v, list):
                opts = [c.cut(o, 60) for o in v if isinstance(o, str) and o.strip()]
                if len(opts) != len(v):
                    c.n += 1
                out[k] = opts
            else:
                out[k] = _truncate(v, c, k)
        return out
    if isinstance(node, list):
        # job_types / snag_types are capped by _cap_jobs_and_snags so no reference is orphaned
        cap = 10 ** 9 if key in ("job_types", "snag_types") else 15 if key in SHORT_LISTS else 25
        if len(node) > cap:
            c.n += 1
            node = node[:cap]
        return [c.cut(x, 240) if isinstance(x, str) else _truncate(x, c, key) for x in node]
    return node


def _cap_jobs_and_snags(t: dict, c: _Counter) -> None:
    """snag_types are cut to 25 first; then jobs referenced by retained snags are kept first."""
    if len(t["snag_types"]) > 25:
        c.n += 1
        t["snag_types"] = t["snag_types"][:25]
    if len(t["job_types"]) > 25:
        c.n += 1
        refs = {s["recommended_job_id"] for s in t["snag_types"]}
        jobs = [j for j in t["job_types"] if j["id"] in refs] + \
               [j for j in t["job_types"] if j["id"] not in refs]
        t["job_types"] = jobs[:25]


# ---------------------------------------------------------------- 4-6 checks
def _structural(t: dict) -> List[str]:
    errs: List[str] = []

    def check_ids(items: List[dict], where: str) -> None:
        seen = set()
        for i, it in enumerate(items):
            if not ID_RE.match(it["id"]):
                errs.append(f"{where}[{i}].id: '{it['id'][:40]}' does not match {ID_RE.pattern}")
            if it["id"] in seen:
                errs.append(f"{where}[{i}].id: duplicate id '{it['id']}'")
            seen.add(it["id"])

    check_ids(t["job_types"], "job_types")
    check_ids(t["snag_types"], "snag_types")
    check_ids(t["operating_parameters"], "operating_parameters")
    fields = [("form_fields", i, f) for i, f in enumerate(t["form_fields"])] + \
             [("pollutant_capture", i, f) for i, f in enumerate(t["pollutant_capture"])]
    seen = set(CORE_IDS)
    for where, i, f in fields:
        if not ID_RE.match(f["id"]):
            errs.append(f"{where}[{i}].id: '{f['id'][:40]}' does not match {ID_RE.pattern}")
        if f["id"] in seen:
            errs.append(f"{where}[{i}].id: '{f['id']}' duplicates a core, form or capture field id")
        seen.add(f["id"])
        if f["type"] == "dropdown":
            n = len(f.get("options") or [])
            if not 1 <= n <= 20:
                errs.append(f"{where}[{i}].options: dropdown needs 1 to 20 options, has {n}")
        else:
            ml = f.get("max_length")
            if not isinstance(ml, int) or not 1 <= ml <= ANSWER_MAX_CHARS:
                errs.append(f"{where}[{i}].max_length: text field needs max_length 1..{ANSWER_MAX_CHARS}")

    for name, have, need in (("job_types", len(t["job_types"]), TEMPLATE_MIN_JOBS),
                             ("snag_types", len(t["snag_types"]), TEMPLATE_MIN_SNAGS),
                             ("operating_parameters", len(t["operating_parameters"]), TEMPLATE_MIN_PARAMS),
                             ("form_fields", len(t["form_fields"]), TEMPLATE_MIN_FORM_FIELDS)):
        if have < need:
            errs.append(f"{name}: at least {need} required, found {have}")

    for i, s in enumerate(t["snag_types"]):
        levels = sorted(d["level"] for d in s["severity_levels"])
        if levels != [1, 2, 3, 4, 5] or any(not d["definition"] for d in s["severity_levels"]):
            errs.append(f"snag_types[{i}].severity_levels: exactly five non-empty definitions for levels 1..5 required")
        if not (math.isfinite(s["time_to_fix_hours"]) and 0 <= s["time_to_fix_hours"] <= 720):
            errs.append(f"snag_types[{i}].time_to_fix_hours: must be 0..720")
    for i, j in enumerate(t["job_types"]):
        if not (math.isfinite(j["est_downtime_hours"]) and 0 <= j["est_downtime_hours"] <= 720):
            errs.append(f"job_types[{i}].est_downtime_hours: must be 0..720")
        b = j["est_cost_band"]
        if not 0 <= b["low_inr"] <= b["high_inr"] <= 10 ** 9:
            errs.append(f"job_types[{i}].est_cost_band: need 0 <= low_inr <= high_inr <= 1e9")
    return errs


def _domains_and_refs(t: dict, industry: str) -> List[str]:
    errs: List[str] = []
    allowed = set(industries.domains_of(industry))
    jobs = {j["id"] for j in t["job_types"]}
    for i, s in enumerate(t["snag_types"]):
        s["pollution_effects"] = [e for e in s["pollution_effects"] if e["domain"] in allowed]
        if not s["pollution_effects"]:
            errs.append(f"snag_types[{i}].pollution_effects: at least one effect in {sorted(allowed)} required")
        if s["recommended_job_id"] not in jobs:
            errs.append(f"snag_types[{i}].recommended_job_id: '{s['recommended_job_id'][:40]}' is not a job_types id")
    t["pollutant_capture"] = [f for f in t["pollutant_capture"] if f.get("domain") is None or f["domain"] in allowed]
    covered = {f.get("domain") for f in t["pollutant_capture"]}
    for d in industries.domains_of(industry):
        if d not in covered:
            errs.append(f"pollutant_capture: no field covers the '{d}' domain")
    return errs


def validate_template(candidate: Any, industry: str) -> Tuple[Optional[dict], List[str], int]:
    """E6.2 pipeline: parse (strict) -> sanitise -> truncate -> structural -> domains/refs -> size.

    Returns (normalised_template or None, errors, truncated_fields). Any error means invalid.
    """
    try:
        if isinstance(candidate, (str, bytes)):
            model = GeneratedTemplate.model_validate_json(candidate)
        else:
            model = GeneratedTemplate.model_validate(candidate)
    except ValidationError as e:
        return None, [f"{'.'.join(str(p) for p in err['loc'])}: {err['msg']}" for err in e.errors()[:20]], 0
    t = _sanitise(model.model_dump())
    c = _Counter()
    t = _truncate(t, c)
    _cap_jobs_and_snags(t, c)
    errs = _structural(t)
    errs += _domains_and_refs(t, industry)
    if not errs:
        size = len(json.dumps(t, ensure_ascii=False).encode("utf-8"))
        if size > TEMPLATE_MAX_BYTES:
            errs.append(f"template: {size} bytes exceeds {TEMPLATE_MAX_BYTES}")
    return (None if errs else t), errs, c.n


# ---------------------------------------------------------------- form schema (E6.4)
def build_form_schema(template: dict) -> List[dict]:
    """core fields + form_fields + pollutant_capture. Shared by the UI and validate_answers."""
    snags = template["snag_types"]
    core = [
        {"id": "snag_type", "label": "Snag type", "type": "dropdown", "required": False, "core": True,
         "options": [s["id"] for s in snags] + ["other"],
         "option_labels": [s["name"] for s in snags] + ["Other / not listed"],
         "help_text": "Pick the closest match from this model's snag taxonomy, or Other.", "domain": None,
         "max_length": None},
        {"id": "severity", "label": "Severity (1 to 5)", "type": "dropdown", "required": True, "core": True,
         "options": ["1", "2", "3", "4", "5"], "option_labels": ["1", "2", "3", "4", "5"],
         "help_text": "The selected snag type shows what each level means.", "domain": None,
         "max_length": None},
        {"id": "description", "label": "What did you observe?", "type": "text", "required": True, "core": True,
         "max_length": ANSWER_MAX_CHARS, "options": None,
         "help_text": "Describe the symptom. Do not include names or phone numbers.", "domain": None},
    ]
    rest = [{**f, "core": False} for f in template["form_fields"] + template["pollutant_capture"]]
    return core + rest


# ---------------------------------------------------------------- Base_Templates (E6.3)
BASE_DIR = os.path.join(industries.DATA_DIR, "base_templates")


def _load_bases() -> Dict[str, dict]:
    out = {}
    for ind in industries.INDUSTRY_IDS:
        with open(os.path.join(BASE_DIR, f"{ind}.json"), "r", encoding="utf-8") as f:
            doc = json.load(f)
        tpl, errs, _ = validate_template(doc["template"], ind)
        if errs:  # a broken fallback is not acceptable: stop the service
            raise RuntimeError(f"Base_Template {ind} is invalid: {errs[:5]}")
        out[ind] = {"base_template_id": doc["base_template_id"], "industry": ind, "template": tpl}
    return out


def overlap(a: set, b: set) -> float:
    return len(a & b) / min(len(a), len(b)) if a and b else 0.0


def _check_overlap(bases: Dict[str, dict]) -> None:
    ids = list(bases)
    for i, x in enumerate(ids):
        for y in ids[i + 1:]:
            tx, ty = bases[x]["template"], bases[y]["template"]
            for what, fx, fy in (
                ("snag ids", {s["id"] for s in tx["snag_types"]}, {s["id"] for s in ty["snag_types"]}),
                ("capture ids", {f["id"] for f in tx["pollutant_capture"]}, {f["id"] for f in ty["pollutant_capture"]})):
                if overlap(fx, fy) > BASE_OVERLAP_MAX:
                    raise RuntimeError(f"Base_Templates {x} and {y} overlap too much in {what}")


BASES: Dict[str, dict] = _load_bases()
_check_overlap(BASES)


def adapt_base(entry: dict) -> dict:
    """Deterministic labelled fallback: the industry Base_Template adapted to the entry."""
    tpl = copy.deepcopy(BASES[entry["industry"]]["template"])
    name = industries.INDUSTRY_BY_ID[entry["industry"]]["name"]
    tpl["machine_profile"]["summary"] = (
        f"Generic {name} template adapted for {entry['manufacturer']} {entry['model']}. "
        "Not generated for this model.")
    if entry.get("spec_status") == "grounded":
        tpl["machine_profile"]["specs"] = [
            {"name": s["name"], "value": s["value"], "unit": s.get("unit")} for s in entry.get("specs", [])]
    return tpl


# ---------------------------------------------------------------- ensure (E5)
def _now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def _prompt_data(entry: dict) -> dict:
    """Only machine identity, specs and the curated base. Never handles or snag text (Req 20.11)."""
    base = BASES[entry["industry"]]["template"]
    return {
        "machine": {"industry": industries.INDUSTRY_BY_ID[entry["industry"]]["name"],
                    "manufacturer": entry["manufacturer"], "model": entry["model"],
                    "machine_type": entry.get("machine_type") or "Unspecified"},
        "pollution_domains": industries.domains_of(entry["industry"]),
        "specs": [{"name": s["name"], "value": s["value"], "unit": s.get("unit")}
                  for s in entry.get("specs", [])] if entry.get("spec_status") == "grounded" else [],
        "spec_sources": entry.get("sources", []) if entry.get("spec_status") == "grounded" else [],
        "base_template": {
            "job_types": [{"id": j["id"], "name": j["name"]} for j in base["job_types"]],
            "snag_types": [{"id": s["id"], "name": s["name"], "category": s["category"]} for s in base["snag_types"]],
            "operating_parameters": [p["label"] for p in base["operating_parameters"]],
            "pollutant_capture": [{"id": f["id"], "label": f["label"], "domain": f["domain"]} for f in base["pollutant_capture"]],
        },
        "requirements": {
            "min_job_types": TEMPLATE_MIN_JOBS, "min_snag_types": TEMPLATE_MIN_SNAGS,
            "min_operating_parameters": TEMPLATE_MIN_PARAMS, "min_form_fields": TEMPLATE_MIN_FORM_FIELDS,
            "id_pattern": ID_RE.pattern, "severity_levels": "exactly five, levels 1 to 5",
            "reserved_field_ids": list(CORE_IDS),
            "every_domain_needs_a_pollutant_capture_field": industries.domains_of(entry["industry"]),
        },
    }


def _generator(entry: dict):
    def generate(max_attempts: int, attempt_timeout_s: float) -> cache_once.GenResult:
        data = _prompt_data(entry)
        hint: Optional[str] = None
        meta_total: dict = {"truncated": False, "truncated_fields": 0}
        for attempt in range(1, max_attempts + 1):
            try:
                text, meta = gemini_client.generate_template(data, timeout_s=attempt_timeout_s, hint=hint)
            except (gemini_client.GeminiNotConfigured, gemini_client.GeminiBudgetExceeded) as e:
                log.info("template generation unavailable: %s", type(e).__name__)
                return cache_once.GenResult("transient-failure", None, attempt)
            except Exception as e:  # timeout, API error: no secrets in the log
                log.warning("template attempt %d failed: %s: %s", attempt, type(e).__name__, str(e)[:200])
                hint = None
                continue
            meta_total.update({k: v for k, v in meta.items() if k != "truncated"})
            if meta.get("truncated"):
                meta_total["truncated"] = True
                hint = ("Your previous answer was cut off. Give at most 8 list items per list and one "
                        "sentence per field.")
                log.warning("template attempt %d truncated (MAX_TOKENS)", attempt)
                continue
            tpl, errs, ntrunc = validate_template(text, entry["industry"])
            if errs:
                log.warning("template attempt %d invalid: %s", attempt, "; ".join(errs[:10])[:800])
                hint = "Fix these problems: " + "; ".join(errs[:10]) + ". Be concise per item."
                continue
            meta_total["truncated_fields"] = ntrunc
            doc = _template_doc(entry, tpl, attempt, meta_total)
            return cache_once.GenResult("ok", doc, attempt)
        return cache_once.GenResult("transient-failure", None, max_attempts)
    return generate


def _template_doc(entry: dict, tpl: dict, attempts: int, meta: dict) -> dict:
    key = entry["id"]
    grounded = entry.get("spec_status") == "grounded"
    return {
        "id": industries.template_id(key), "model_key": key, "schema_version": settings.TEMPLATE_SCHEMA_VERSION,
        "industry": entry["industry"], "manufacturer": entry["manufacturer"], "model": entry["model"],
        "machine_type": entry.get("machine_type") or "Unspecified",
        "source": "ai", "generated_at": _now_iso(), "model_used": gemini_client.TEMPLATE_MODEL,
        "generation_attempts": attempts, "generation_meta": meta,
        "grounding_sources": list(entry.get("sources", [])) if grounded else [],
        "specs_status": entry.get("spec_status", "sample"),
        "base_template_id": BASES[entry["industry"]]["base_template_id"],
        "template": tpl, "size_bytes": len(json.dumps(tpl, ensure_ascii=False).encode("utf-8")),
        "disclaimer": DISCLAIMER,
    }


def _fallback_doc(entry: dict) -> dict:
    return {
        "id": industries.template_id(entry["id"]), "model_key": entry["id"],
        "schema_version": settings.TEMPLATE_SCHEMA_VERSION, "industry": entry["industry"],
        "manufacturer": entry["manufacturer"], "model": entry["model"],
        "machine_type": entry.get("machine_type") or "Unspecified",
        "source": "fallback-base", "generated_at": None, "model_used": None,
        "grounding_sources": [], "specs_status": entry.get("spec_status", "sample"),
        "base_template_id": BASES[entry["industry"]]["base_template_id"],
        "template": adapt_base(entry), "disclaimer": DISCLAIMER,
    }


def is_hit(doc: dict) -> bool:
    return doc.get("source") == "ai" and doc.get("schema_version") == settings.TEMPLATE_SCHEMA_VERSION


def response(doc: dict, cache_status: str) -> dict:
    """The ensure / fetch response body (E4). form_schema is derived, never stored."""
    labels = ["AI-generated"] if doc["source"] == "ai" else ["generic-industry-template", "not-generated-for-this-model"]
    return {
        "key": doc["model_key"], "template_id": doc["id"], "cache_status": cache_status,
        "source": doc["source"], "generated_at": doc.get("generated_at"), "model_used": doc.get("model_used"),
        "schema_version": doc["schema_version"], "grounding_sources": doc.get("grounding_sources", []),
        "specs_status": doc.get("specs_status"), "base_template_id": doc.get("base_template_id"),
        "industry": doc["industry"], "manufacturer": doc["manufacturer"], "model": doc["model"],
        "machine_type": doc.get("machine_type"),
        "user_message": USER_MESSAGES[cache_status],
        "template": doc["template"], "form_schema": build_form_schema(doc["template"]),
        "disclaimer": DISCLAIMER, "labels": labels,
    }


def ensure_template(store, entry: dict) -> dict:
    """Get-or-generate the template for a catalogue entry: Gemini runs only on a cache miss."""
    out = cache_once.ensure_once(
        store, TEMPLATES, industries.template_id(entry["id"]),
        profile=cache_once.TEMPLATE, generate=_generator(entry),
        fallback=lambda: _fallback_doc(entry), is_hit=is_hit, persist="create")
    return response(out.doc, out.cache_status)


def get_stored(store, key: str) -> Optional[dict]:
    """Fetch-only (Req 21.10): never calls Gemini."""
    doc = store.get_doc(TEMPLATES, industries.template_id(key))
    return doc if doc and is_hit(doc) else None
