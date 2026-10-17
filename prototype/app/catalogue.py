"""Machine catalogue: seed, search and manual entry with grounded spec lookup (E1; Req 18.5-19.10).

Sample entries come from data/catalogue.json and are mirrored into the `catalogue` collection by
idempotent create-if-absent, so user-added manual entries and seeds share one home. Search is a
pure function over the entries of one industry.
"""
from __future__ import annotations

import json
import logging
import os
import re
import statistics
import threading
import time
from datetime import datetime, timedelta, timezone
from typing import Dict, List, Optional, Tuple

from . import gemini_client, industries
from .settings import (CATALOGUE_SEARCH_LIMIT, GROUNDED_ATTEMPT_TIMEOUT_S, GROUNDED_MAX_ATTEMPTS,
                       MANUAL_ENTRY_MAX, SPEC_RETRY_AFTER_H)

log = logging.getLogger("terravow.catalogue")

CATALOGUE = "catalogue"
NOTICE = "Sample data, not an authoritative manufacturer database."
MANUAL_NAME_PATTERN = r"^[\w .,&()/+-]{1,80}$"

MESSAGES = {
    "ok": "AI-retrieved from web sources and unverified.",
    "not-found": "No specifications were found for this model. You can still select it.",
    "retry": ("Specifications could not be retrieved right now. You can still select this model, "
              "and you can retry the lookup."),
}


def _now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def _load_seed() -> Tuple[str, List[dict]]:
    with open(os.path.join(industries.DATA_DIR, "catalogue.json"), "r", encoding="utf-8") as f:
        raw = json.load(f)
    out = []
    for e in raw["entries"]:
        out.append({
            "origin": "sample", "spec_status": "sample", "spec_lookup": "n/a", "spec_checked_at": None,
            "specs": [], "sources": [], "economics_source": "sample", "seed_version": raw["seed_version"],
            "fleet_id": None, **e,
        })
    return raw["seed_version"], out


SEED_VERSION, SEED_ENTRIES = _load_seed()
_seeded: Dict[int, bool] = {}
_seed_lock = threading.Lock()


def ensure_seeded(store) -> None:
    """Idempotent: create-if-absent for every sample entry (re-runs if the store was cleared)."""
    probe = SEED_ENTRIES[-1]["id"]
    if _seeded.get(id(store)) and store.get_doc(CATALOGUE, probe) is not None:
        return
    with _seed_lock:
        for e in SEED_ENTRIES:
            store.create_if_absent(CATALOGUE, e["id"], {**e, "created_at": "2026-10-17T00:00:00Z"})
        _seeded[id(store)] = True


def get_entry(store, key: str) -> Optional[dict]:
    ensure_seeded(store)
    return store.get_doc(CATALOGUE, key)


def entries_for(store, industry: str) -> List[dict]:
    ensure_seeded(store)
    return store.query_eq(CATALOGUE, "industry", industry)


# ---------------------------------------------------------------- search (E1 steps 1-5)
_WORD_SPLIT = re.compile(r"[\s\W_]+", flags=re.U)


def _rank(entry: dict, fq: str) -> Optional[int]:
    fields = [industries.fold(entry.get(k, "") or "") for k in ("manufacturer", "model", "machine_type")]
    for f in fields:
        if any(w.startswith(fq) for w in _WORD_SPLIT.split(f) if w):
            return 0
    if fq in " ".join(fields):
        return 1
    return None


def _order_key(entry: dict):
    return (industries.fold(entry.get("manufacturer", "")), industries.fold(entry.get("model", "")), entry["id"])


def search(entries: List[dict], industry: str, q: str = "", limit: int = CATALOGUE_SEARCH_LIMIT) -> List[dict]:
    """Pure: entries of `industry` matching `q` (folded word prefix first, then substring)."""
    limit = max(1, min(CATALOGUE_SEARCH_LIMIT, int(limit)))
    rows = [e for e in entries if e.get("industry") == industry]
    fq = industries.fold((q or "").strip())
    if not fq:
        return sorted(rows, key=_order_key)[:limit]
    ranked = []
    for e in rows:
        r = _rank(e, fq)
        if r is not None:
            ranked.append((r, _order_key(e), e))
    ranked.sort(key=lambda t: (t[0], t[1]))
    return [e for _, _, e in ranked[:limit]]


def result_view(e: dict) -> dict:
    out = {k: e.get(k) for k in ("id", "industry", "manufacturer", "model", "machine_type", "origin",
                                  "sample_data", "spec_status", "spec_lookup", "specs", "sources")}
    out["label"] = "Sample data" if e.get("origin") == "sample" else "User-added, unverified"
    return out


# ---------------------------------------------------------------- manual entry (E1, E5 catalogue row)
def is_hit(doc: dict, now: Optional[datetime] = None) -> bool:
    """A stored entry that needs no new lookup. A 'retry' entry is never a hit."""
    if doc.get("origin") == "sample" or doc.get("spec_status") == "grounded":
        return True
    if doc.get("spec_lookup") == "not-found" and doc.get("spec_checked_at"):
        now = now or datetime.now(timezone.utc)
        checked = datetime.strptime(doc["spec_checked_at"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        return now - checked < timedelta(hours=SPEC_RETRY_AFTER_H)
    return False


def _industry_median(store, industry: str) -> dict:
    rows = [e for e in SEED_ENTRIES if e["industry"] == industry]
    return {k: int(statistics.median(e[k] for e in rows))
            for k in ("replacement_value_inr", "annual_operating_cost_inr", "annual_output_value_inr")}


class ManualCapReached(RuntimeError):
    pass


def manual_entry(store, industry: str, manufacturer: str, model: str) -> dict:
    """Store the identity entry and run the grounded lookup unless a stored entry is a hit."""
    key = industries.model_key(industry, manufacturer, model)       # KeyError422 on empty slugs
    existing = get_entry(store, key)
    if existing and is_hit(existing):
        return _manual_response(existing, created=False, lookup_performed=False)
    if existing is None and len(store.query_eq(CATALOGUE, "origin", "manual")) >= MANUAL_ENTRY_MAX:
        raise ManualCapReached(f"At most {MANUAL_ENTRY_MAX} manual catalogue entries are allowed.")

    res = gemini_client.lookup_specs(industries.INDUSTRY_BY_ID[industry]["name"], manufacturer, model,
                                     timeout_s=GROUNDED_ATTEMPT_TIMEOUT_S, max_attempts=GROUNDED_MAX_ATTEMPTS)
    kind = res.get("kind")
    base = existing or {}
    doc = {
        "id": key, "industry": industry, "manufacturer": manufacturer, "model": model,
        "machine_type": (res.get("machine_type") or "")[:80] or base.get("machine_type") or "Unspecified",
        "origin": "manual", "sample_data": False, "fuel": None, "fleet_id": None,
        "scale": 1.0, "domain_scale": None, "design_life_years": 25,
        **_industry_median(store, industry), "economics_source": "industry-default",
        "created_at": base.get("created_at") or _now_iso(), "seed_version": None,
    }
    if kind == "ok":
        doc.update(spec_status="grounded", spec_lookup="ok", spec_checked_at=_now_iso(),
                   specs=res["specs"][:15], sources=res["sources"][:8])
    elif kind == "definitive-negative":
        doc.update(spec_status="ungrounded", spec_lookup="not-found", spec_checked_at=_now_iso(),
                   specs=[], sources=[])
    else:  # transient: identity entry only, never a hit, nothing invented
        doc.update(spec_status="ungrounded", spec_lookup="retry", spec_checked_at=None, specs=[], sources=[])
        if base.get("machine_type") is None:
            doc["machine_type"] = "Unspecified"
    written = store.replace_if(CATALOGUE, key, lambda cur: cur is None or not is_hit(cur), doc)
    stored = doc if written else store.get_doc(CATALOGUE, key)
    return _manual_response(stored, created=existing is None and written, lookup_performed=True)


def _manual_response(e: dict, created: bool, lookup_performed: bool) -> dict:
    lookup = e.get("spec_lookup") if e.get("origin") == "manual" else "n/a"
    if e.get("origin") == "sample":
        message, labels = NOTICE, ["sample-data"]
    else:
        message = MESSAGES.get(lookup, MESSAGES["retry"])
        labels = ["user-added", "unverified"] + (["AI-generated"] if lookup == "ok" else [])
    return {"entry": result_view(e), "created": created, "lookup_performed": lookup_performed,
            "message": message, "labels": labels}
