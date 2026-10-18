"""Sustainable alternatives, process / assembly changes and government incentives (design E4, E6.6-6.9,
E7.6-7.8; Req 26, 27).

Gemini may only SUGGEST names, rationale and labelled input parameters; every number shown is computed
by engine.py, and every item passes the existing, unmodified gate.evaluate. Any Gemini failure degrades
to the curated illustrative set, which is never stored as AI output.
"""
from __future__ import annotations

import json
import logging
import os
import time
from datetime import date
from math import isfinite
from typing import Dict, List, Optional, Tuple

from . import cache_once, engine, gate, gemini_client, industries, settings, snags
from .settings import SUPPORTED_COUNTRIES

log = logging.getLogger("terravow.sustain")

INCENTIVES = "incentives"
ALTERNATIVES = "alternatives"
ALT_SCHEMA_VERSION = "s1"
KINDS = ("machine_alternative", "process_change")
SIZES = ("MSME", "LARGE")
MAX_PER_KIND = 3

CONF_CURATED, TRUST_CURATED = 0.70, 1.0
CONF_AI, TRUST_AI = 0.55, 0.7
LOW_HISTORY_CONF_CAP, CONF_CAP = 0.6, 0.9

# Point bounds of the input parameters (percent). A candidate outside them is DROPPED, never clamped
# into a different candidate (Property 79).
BOUNDS = {"capex_pct": (0.0, 300.0), "eff_pct": (0.0, 60.0), "prod_pct": (0.0, 30.0), "opex_pct": (0.0, 30.0)}
REDUCTION_BOUNDS = (-100.0, 100.0)
_AI_FIELDS = {"capex_pct": "capex_pct_of_replacement", "eff_pct": "efficiency_gain_pct",
              "prod_pct": "production_uplift_pct", "opex_pct": "extra_opex_pct"}

AI_LABEL = "AI-suggested, requires vendor validation and process-safety review"
CURATED_LABEL = "Curated illustrative alternative"
NO_SOURCES_LABEL = "no web sources retrieved"
DISCLAIMER = ("Informational estimate, not a quotation, engineering design, tax or legal advice; "
              "confirm eligibility with the issuing authority.")
MESSAGES = {
    "ai-suggested": "AI-suggested alternatives. Every number is computed by the deterministic engine from "
                    "labelled input parameters.",
    "mixed": "Some AI suggestions were blocked by the safety check or invalid, so curated illustrative "
             "alternatives were added.",
    "curated-fallback": "AI suggestions unavailable; showing curated illustrative alternatives",
}
NO_COUNTRY = "No data for this country."
NO_MATCH = "No matching scheme recorded for this selection."
GATE_BADGE = {"ALLOW": "Allowed by the safety check", "ASK": "Needs confirmation", "BLOCK": "Blocked by the safety check"}


def _load(name: str) -> dict:
    with open(os.path.join(industries.DATA_DIR, name), "r", encoding="utf-8") as f:
        return json.load(f)


CURATED: Dict[str, List[dict]] = _load("curated_alternatives.json")["industries"]
_INC = _load("incentives.json")
INCENTIVE_SEED_VERSION: str = _INC["seed_version"]
INCENTIVE_RECORDS: List[dict] = _INC["records"]
NAME_NOTE: str = _INC["name_note"]


def incentive_id(r: dict) -> str:
    return f"{r['country']}--{r['scheme_id']}"


def _self_check() -> None:
    """Startup check: curated set shape (E6.7) and incentive coverage per industry and size (Req 27.5)."""
    for ind in industries.INDUSTRY_IDS:
        items = CURATED.get(ind) or []
        kinds = [(i["kind"], i["stage"]) for i in items]
        if kinds != [("machine_alternative", "operation")] * 2 + [("process_change", "operation")] * 2 + \
                [("process_change", "assembly")]:
            raise RuntimeError(f"curated_alternatives.json: {ind} must list 2 machine, 2 operating, 1 assembly items")
        for i in items:
            if check_params(curated_params(i), industries.domains_of(ind)) is None:
                raise RuntimeError(f"curated_alternatives.json: {i['id']} has invalid parameters")
        for size in SIZES:
            if not any(engine.incentive_eligible(r, "IN", ind, size) for r in INCENTIVE_RECORDS):
                raise RuntimeError(f"incentives.json: no {size} scheme for {ind}")


# ---------------------------------------------------------------- parameters (E6.6)
def curated_params(item: dict) -> dict:
    return {"capex_pct": item["capex_pct_of_replacement"], "eff_pct": item["efficiency_gain_pct"],
            "prod_pct": item.get("production_uplift_pct", 0), "opex_pct": item.get("extra_opex_pct", 0),
            "reductions": dict(item.get("reduction_pct") or {})}


def check_params(p: dict, domains: List[str]) -> Optional[dict]:
    """Bounds, finiteness and the zero-reduction rule. Out-of-domain reductions are ignored; a
    candidate with no in-domain reduction > 0 is dropped (it never reaches the gate)."""
    out: dict = {}
    for k, (lo, hi) in BOUNDS.items():
        v = p.get(k)
        if isinstance(v, bool) or not isinstance(v, (int, float)) or not isfinite(v) or not lo <= v <= hi:
            return None
        out[k] = float(v)
    red: Dict[str, float] = {}
    for d, v in (p.get("reductions") or {}).items():
        if isinstance(v, bool) or not isinstance(v, (int, float)) or not isfinite(v) \
                or not REDUCTION_BOUNDS[0] <= v <= REDUCTION_BOUNDS[1]:
            return None
        if d in domains:
            red[d] = float(v)
    if not any(v > 0 for v in red.values()):
        return None
    out["reductions"] = red
    return out


def ai_params(cand: dict, domains: List[str]) -> Optional[dict]:
    """Midpoint of each low/high pair; low <= high and both in bounds are required."""
    raw = cand.get("params") or {}

    def mid(lo, hi, bounds) -> Optional[float]:
        ok = all(isinstance(x, (int, float)) and not isinstance(x, bool) and isfinite(x) for x in (lo, hi))
        if not ok or lo > hi or lo < bounds[0] or hi > bounds[1]:
            return None
        return (float(lo) + float(hi)) / 2.0
    p: dict = {}
    for k, name in _AI_FIELDS.items():
        v = mid(raw.get(name + "_low", 0.0), raw.get(name + "_high", 0.0), BOUNDS[k])
        if v is None:
            return None
        p[k] = v
    red: Dict[str, float] = {}
    for r in raw.get("reductions") or []:
        v = mid(r.get("reduction_pct_low"), r.get("reduction_pct_high"), REDUCTION_BOUNDS)
        if v is None or r.get("domain") in red:
            return None
        red[r.get("domain")] = v
    p["reductions"] = red
    return check_params(p, domains)


# ---------------------------------------------------------------- incentives (E6.9, E7.7)
def seed_incentives(store) -> None:
    """Idempotent create-if-absent of the curated seed; skipped with one read when complete."""
    done = store.get_doc("seed_meta", "incentives")
    if done and done.get("seed_version") == INCENTIVE_SEED_VERSION:
        return
    for r in INCENTIVE_RECORDS:
        store.create_if_absent(INCENTIVES, incentive_id(r), dict(r))
    store.create_if_absent("seed_meta", "incentives", {"seed_version": INCENTIVE_SEED_VERSION})


def records_for(store, country: str) -> List[dict]:
    seed_incentives(store)
    return sorted(store.query_eq(INCENTIVES, "country", country), key=lambda r: r["scheme_id"])


def scheme_view(r: dict) -> dict:
    """What the UI shows beside each scheme: status text, qualitative tag and retrieval wording."""
    retrieved = (f"Retrieved on {r['retrieved_on']}" if r.get("retrieved_on") else
                 f"Not retrieved from the source; authored for the demo on {r.get('authored_on')}")
    return {**r, "quantifiable": r.get("params") is not None and r.get("origin") != "gemini-grounded",
            "retrieval_label": retrieved, "name_note": NAME_NOTE,
            "uses_illustrative_values": r.get("origin") == "demo-synthetic" or not r.get("verified")}


def list_schemes(records: List[dict], country: str, industry: str, size: str) -> List[dict]:
    """Listing filter (ignores applies_to), ordered by scheme_id."""
    return [scheme_view(r) for r in sorted(records, key=lambda x: x["scheme_id"])
            if engine.incentive_eligible(r, country, industry, size)]


def incentives_response(store, country: str, industry: str, size: Optional[str]) -> dict:
    records = records_for(store, country)
    sizes = [size] if size else list(SIZES)
    seen, schemes = set(), []
    for sz in sizes:
        for s in list_schemes(records, country, industry, sz):
            if s["scheme_id"] not in seen:
                seen.add(s["scheme_id"])
                schemes.append(s)
    schemes.sort(key=lambda s: s["scheme_id"])
    message = NO_COUNTRY if not records else (NO_MATCH if not schemes else None)
    return {"country": country, "currency": records[0]["currency"] if records else None, "industry": industry,
            "enterprise_size": size, "schemes": schemes, "message": message, "disclaimer": DISCLAIMER}


def discovered_record(raw: dict, country: str, retrieved_on: str) -> dict:
    """Normalise a Gemini-discovered scheme (planned for optional refresh endpoint): always UNVERIFIED,
    qualitative only, never carries params, and its gx- id can never overwrite a curated record."""
    sizes = [s for s in (raw.get("enterprise_sizes") or []) if s in SIZES] or list(SIZES)
    inds = [i for i in (raw.get("industries") or []) if i in industries.INDUSTRY_IDS] or ["*"]
    url = raw.get("source_url")
    return {"country": country, "currency": "INR" if country == "IN" else None,
            "scheme_id": "gx-" + industries.slug(str(raw.get("name") or ""))[:40],
            "name": str(raw.get("name") or "")[:200], "authority": str(raw.get("authority") or "")[:200],
            "industries": inds, "enterprise_sizes": sizes, "applies_to": list(KINDS),
            "benefit_type": raw.get("benefit_type") if raw.get("benefit_type") in (
                "CAPITAL_SUBSIDY", "ACCELERATED_DEPRECIATION", "TRADEABLE_CERTIFICATE", "CONCESSIONAL_FINANCE",
                "RECOGNITION") else "RECOGNITION",
            "benefit_description": str(raw.get("benefit_description") or "")[:600],
            "eligibility_notes": str(raw.get("eligibility_notes") or "")[:600],
            "params": None, "source_url": url if isinstance(url, str) and url.lower().startswith(("http://", "https://")) else None,
            "retrieved_on": retrieved_on, "authored_on": retrieved_on, "verified": False,
            "status": "UNVERIFIED/illustrative", "origin": "gemini-grounded"}


# ---------------------------------------------------------------- AI suggestion cache (E6.6)
def alternatives_id(model_key: str, country: str) -> str:
    return f"{model_key}--{country}--{ALT_SCHEMA_VERSION}"


def _alt_prompt(entry: dict, country: str) -> dict:
    """Machine identity, its domains and the country only (never snag text or handles)."""
    return {"industry": industries.INDUSTRY_BY_ID[entry["industry"]]["name"],
            "manufacturer": entry["manufacturer"], "model": entry["model"],
            "machine_type": entry.get("machine_type") or "Unspecified",
            "pollution_domains": industries.domains_of(entry["industry"]), "country": country}


def _alt_generator(entry: dict, country: str):
    def generate(max_attempts: int, attempt_timeout_s: float) -> cache_once.GenResult:
        res = gemini_client.suggest_alternatives(_alt_prompt(entry, country), timeout_s=attempt_timeout_s,
                                                 max_attempts=max_attempts)
        if res.get("kind") != "ok" or not res.get("proposal"):
            log.warning("alternatives unavailable: %s", res.get("reason"))
            return cache_once.GenResult("transient-failure", None, max_attempts)
        cands = res["proposal"]["candidates"]
        domains = industries.domains_of(entry["industry"])
        if not any(ai_params(c, domains) for c in cands):
            log.warning("alternatives proposal had no valid candidate")
            return cache_once.GenResult("transient-failure", None, max_attempts)
        doc = {"id": alternatives_id(entry["id"], country), "model_key": entry["id"], "country": country,
               "schema_version": ALT_SCHEMA_VERSION, "source": "ai",
               "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
               "model_used": gemini_client.MODEL, "sources": list(res.get("sources") or []), "candidates": cands}
        return cache_once.GenResult("ok", doc, 1)
    return generate


def ai_suggestions(store, entry: dict, country: str) -> Tuple[Optional[dict], str]:
    """(cached AI document or None, cache_status). Gemini runs only on a cache miss."""
    if country not in SUPPORTED_COUNTRIES:
        return None, "not-requested"
    if settings.GEMINI_HEAVY_DISABLED:
        # Demo / unavailable-endpoint mode: use a pre-generated AI doc if present, else the
        # curated fallback immediately. Never calls Gemini and skips the lease round-trips.
        doc = store.get_doc(ALTERNATIVES, alternatives_id(entry["id"], country))
        if doc is not None and doc.get("source") == "ai":
            return doc, "cache-hit"
        return None, "fallback"
    out = cache_once.ensure_once(
        store, ALTERNATIVES, alternatives_id(entry["id"], country), profile=cache_once.GROUNDED,
        generate=_alt_generator(entry, country), fallback=lambda: {"source": "curated-fallback"},
        is_hit=lambda d: d.get("source") == "ai", persist="create")
    return (out.doc if out.doc.get("source") == "ai" else None), out.cache_status


# ---------------------------------------------------------------- assembly and gate (E7.8)
def _round(x):
    if isinstance(x, float):
        return round(x, 2)
    if isinstance(x, dict):
        return {k: _round(v) for k, v in x.items()}
    if isinstance(x, list):
        return [_round(v) for v in x]
    return x


def current_intensity(entry: dict, industry: str, machine: Optional[engine.Machine], health: float) -> dict:
    ind = industries.INDUSTRY_BY_ID[industry]
    return engine.pollution_intensity(
        health, industries.domains_of(industry), industries.DOMAIN_INDICATORS, industries.INDICATOR_UNITS,
        ind["default_baselines"], float(entry.get("scale") or 1.0), entry.get("domain_scale"), machine, 0.0)["per_day"]


def build_item(meta: dict, params: dict, source: str, ctx: dict) -> dict:
    """Economics from the engine, then the unmodified gate, then incentive adjustment per size."""
    ai = source == "ai-suggested"
    conf = min(CONF_AI if ai else CONF_CURATED, LOW_HISTORY_CONF_CAP if ctx["low_history"] else CONF_CAP)
    trust = TRUST_AI if ai else TRUST_CURATED
    econ = engine.candidate_economics(params, ctx["econ"], ctx["current"])
    if econ["status"] != "ok":
        g = gate.evaluate({}, None, trust)          # fail closed: routed to human review
    else:
        g = gate.evaluate(engine.gate_deltas(econ.pop("avoided_annual")), conf, trust)
        econ["by_size"] = {}
        for sz in ctx["sizes"]:
            eligible = [r for r in ctx["records"]
                        if engine.incentive_eligible(r, ctx["country"], ctx["industry"], sz, meta["kind"])]
            econ["by_size"][sz] = engine.apply_incentive(
                econ["capex_inr"], econ["cash_benefit_inr_per_year"], econ["benefit_with_exposure_inr_per_year"],
                econ["payback_horizon_years"], eligible)
    labels = [AI_LABEL if ai else CURATED_LABEL, "estimate", "illustrative assumption"]
    if ai and not ctx.get("ai_sources"):
        labels.append(NO_SOURCES_LABEL)
    return _round({
        "id": meta.get("id"), "kind": meta["kind"], "stage": meta.get("stage") or "operation",
        "name": meta["name"], "rationale": meta.get("rationale", ""), "assumptions": meta.get("assumptions", ""),
        "source": "ai-suggested" if ai else "curated-fallback",
        "sources": list(ctx.get("ai_sources") or []) if ai else [],
        "parameters": {**params, "label": "AI-estimated input parameter (midpoint of range)" if ai
                       else "illustrative assumption"},
        "economics": econ, "status": econ["status"], "confidence": conf,
        "gate": {**g, "badge": GATE_BADGE[g["verdict"]]}, "actionable": g["verdict"] != "BLOCK",
        "labels": labels,
    })


def _curated_items(ctx: dict, kind: str) -> List[dict]:
    out = []
    for it in CURATED[ctx["industry"]]:
        p = check_params(curated_params(it), ctx["domains"]) if it["kind"] == kind else None
        if p is not None:
            out.append(build_item(it, p, "curated-fallback", ctx))
    return out[:MAX_PER_KIND]


def recommend(store, header: dict, entry: Optional[dict], machine: Optional[engine.Machine], as_of: date,
              size: Optional[str], country: str) -> dict:
    """The recommendation body (without the audit id, which the route adds after appending)."""
    industry = header["industry"]
    view = snags.health_view(store, header, as_of, machine)
    h = view["health"]["score"]
    entry = entry or {}
    current = current_intensity(entry, industry, machine, h) if entry else {}
    records = records_for(store, country)
    sizes = [size] if size else list(SIZES)
    ctx = {"industry": industry, "domains": industries.domains_of(industry), "country": country,
           "sizes": sizes, "records": records, "low_history": view["health"]["confidence"] == "low-history",
           "current": current,
           "econ": {k: entry.get(k) for k in ("replacement_value_inr", "annual_operating_cost_inr",
                                              "annual_output_value_inr", "design_life_years")}}
    ai_doc, cache_status = ai_suggestions(store, entry, country) if entry else (None, "not-requested")
    ctx["ai_sources"] = (ai_doc or {}).get("sources") or []
    items: List[dict] = []
    backfilled = False
    ai_used = 0
    for kind in KINDS:
        if ai_doc is None:
            items += _curated_items(ctx, kind)
            continue
        ai_k = []
        for c in ai_doc["candidates"]:
            if c.get("kind") != kind or len(ai_k) == MAX_PER_KIND:
                continue
            p = ai_params(c, ctx["domains"])
            if p is not None:
                ai_k.append(build_item(c, p, "ai-suggested", ctx))
        ai_used += len(ai_k)
        items += ai_k
        if not ai_k or all(i["gate"]["verdict"] == "BLOCK" for i in ai_k):
            items += _curated_items(ctx, kind)
            backfilled = True
    source = "curated-fallback" if ai_doc is None or ai_used == 0 else ("mixed" if backfilled else "ai-suggested")
    schemes, schemes_msg = {}, {}
    for sz in sizes:
        schemes[sz] = list_schemes(records, country, industry, sz)
        schemes_msg[sz] = NO_COUNTRY if not records else (NO_MATCH if not schemes[sz] else None)
    return {
        "machine_id": header["machine_id"], "industry": industry, "model_key": header["model_key"],
        "as_of": as_of.isoformat(), "country": country, "currency": records[0]["currency"] if records else None,
        "enterprise_sizes": sizes,
        "current": {"health": h, "band": view["health"]["band"], "confidence": view["health"]["confidence"],
                    "domains": ctx["domains"],
                    "per_day": {k: {"value": round(v["value"], 2), "unit": v["unit"], "domain": v["domain"],
                                    "factor_source": v["factor_source"]} for k, v in current.items()}},
        "source": source, "message": MESSAGES[source], "alternatives_cache_status": cache_status,
        "ai_sources": ctx["ai_sources"], "items": items, "schemes": schemes, "schemes_message": schemes_msg,
        "name_note": NAME_NOTE, "assumptions": engine.ECONOMICS_ASSUMPTIONS, "disclaimer": DISCLAIMER,
        "labels": ["estimate", "illustrative", "not-an-official-certification"]
                  + (["AI-generated"] if source != "curated-fallback" else ["curated-fallback"]),
    }


def audit_summary(body: dict) -> str:
    """Structured summary only (no AI free text)."""
    v = {"ALLOW": 0, "ASK": 0, "BLOCK": 0}
    for i in body["items"]:
        v[i["gate"]["verdict"]] += 1
    return (f"sustainability recommendation for {body['machine_id']}: {len(body['items'])} items "
            f"({body['source']}), ASK {v['ASK']}, BLOCK {v['BLOCK']}, ALLOW {v['ALLOW']}; country {body['country']}; "
            f"sizes {','.join(body['enterprise_sizes'])}")


_self_check()
