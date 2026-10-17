"""Idempotent, replica-independent seeding of the six fleet snag histories, and demo reset (design E2).

Every seeded document has a deterministic id and is derived only from data/seed_snags.json, the
fleet seed and the shared `seed_meta/anchor` date, so any replica writes byte-identical documents.
"""
from __future__ import annotations
import json
import logging
import os
import threading
import time
from datetime import date, timedelta
from typing import Dict, List, Optional, Tuple

from . import catalogue, fleet, industries, snags, sustain, templates

log = logging.getLogger("terravow.seed")
META = "seed_meta"
_PATH = os.path.join(industries.DATA_DIR, "seed_snags.json")
with open(_PATH, "r", encoding="utf-8") as _f:
    SEED = json.load(_f)
ANCHOR_DATE: str = SEED["anchor_date"]
SEED_VERSION: str = SEED["seed_version"]
RETRY_S = 60

_lock = threading.Lock()


def _check_seed_types() -> None:
    """Startup check (E7.4): every seeded snag type exists in its industry Base_Template."""
    machines = fleet.fresh_fleet()
    for mid, rows in SEED["machines"].items():
        ids = {s["id"] for s in templates.BASES[machines[mid].industry]["template"]["snag_types"]}
        bad = [r["snag_type_id"] for r in rows if r["snag_type_id"] not in ids]
        if bad:
            raise RuntimeError(f"seed_snags.json: {mid} uses snag types absent from its Base_Template: {bad}")


_check_seed_types()


def build_docs(anchor: str) -> Tuple[List[dict], List[dict]]:
    """Headers and SEED- snags relative to `anchor` (YYYY-MM-DD). Pure."""
    a = date.fromisoformat(anchor)
    machines = fleet.fresh_fleet()
    headers, out = [], []
    for mid in sorted(SEED["machines"]):
        m = machines[mid]
        base = templates.BASES[m.industry]
        headers.append({"machine_id": mid, "tag": m.name, "industry": m.industry, "model_key": m.catalogue_id,
                        "catalogue_id": m.catalogue_id, "schema_version": base["base_template_id"],
                        "install_year": m.install_year, "seeded": True, "synthetic": True,
                        "created_at": f"{anchor}T00:00:00Z", "created_by_handle": "seed-data"})
        types = {s["id"]: s for s in base["template"]["snag_types"]}
        for n, r in enumerate(SEED["machines"][mid], 1):
            st = types[r["snag_type_id"]]
            rep = f"{(a - timedelta(days=r['reported_days_before'])).isoformat()}T09:00:00Z"
            events = [{"ts": rep, "from_status": None, "to_status": "OPEN", "actor_handle": "seed-data",
                       "actor_role": snags.REPORTER}]
            status, resolution = "OPEN", None
            if r["resolved_days_before"] is not None:
                res = f"{(a - timedelta(days=r['resolved_days_before'])).isoformat()}T17:00:00Z"
                events += [{"ts": res, "from_status": "OPEN", "to_status": "RESOLVED", "actor_handle": "seed-data",
                            "actor_role": snags.RESOLVER},
                           {"ts": res, "from_status": "RESOLVED", "to_status": "CLOSED", "actor_handle": "seed-data",
                            "actor_role": snags.RESOLVER}]
                status = "CLOSED"
                resolution = {"resolver_handle": "seed-data", "resolved_at": res, "notes": "Synthetic seeded repair.",
                              "actions_taken": "", "downtime_hours": st.get("time_to_fix_hours"), "cost_inr": None}
            out.append({
                "snag_id": f"SEED-{mid}-{n:02d}", "machine_id": mid, "model_key": m.catalogue_id,
                "schema_version": base["base_template_id"], "industry": m.industry,
                "snag_type_id": st["id"], "category": st["category"], "severity": r["severity"],
                "answers": {"description": f"Synthetic seeded snag: {st['name']}."},
                "recommended_job_id": st.get("recommended_job_id"), "template_source": "base",
                "reported_at": rep, "reporter_handle": "seed-data", "reporter_role": snags.REPORTER,
                "status": status, "events": events, "resolution": resolution, "photo_analysis_ref": None,
                "seeded": True})
    return headers, out


def anchor_of(store) -> Optional[str]:
    doc = store.get_doc(META, "anchor")
    return doc["anchor_date"] if doc else None


def run(store) -> dict:
    """Idempotent: create-if-absent only, never overwrites. Skips with one read when complete."""
    catalogue.ensure_seeded(store)
    sustain.seed_incentives(store)
    done = store.get_doc(META, "complete")
    if not (done and done.get("seed_version") == SEED_VERSION):
        store.create_if_absent(META, "anchor", {"anchor_date": ANCHOR_DATE, "seed_version": SEED_VERSION,
                                                 "created_at": f"{ANCHOR_DATE}T00:00:00Z"})
        headers, docs = build_docs(anchor_of(store))
        for h in headers:
            store.create_if_absent(snags.HISTORY, h["machine_id"], h)
        for s in docs:
            store.create_if_absent(snags.SNAGS, s["snag_id"], s)
        store.create_if_absent(META, "complete", {"seed_version": SEED_VERSION})
    st = {"seeded": True, "error": None, "last_attempt": time.time()}
    store._seed_state = st
    return {"seeded": True, "seed_anchor": anchor_of(store)}


def ensure(store) -> bool:
    """Lazy re-seed after a failed startup seed, at most once per RETRY_S."""
    st = getattr(store, "_seed_state", None)
    if st and st["seeded"]:
        return True
    with _lock:
        st = getattr(store, "_seed_state", None)
        if st and (st["seeded"] or time.time() - st["last_attempt"] < RETRY_S):
            return bool(st["seeded"])
        try:
            run(store)
            return True
        except Exception as e:
            record_failure(store, e)
            return False


def record_failure(store, e: Exception) -> None:
    store._seed_state = {"seeded": False, "error": f"{type(e).__name__}: {str(e)[:300]}", "last_attempt": time.time()}
    log.error("seeding failed: %s", store._seed_state["error"])


def status(store) -> dict:
    st = getattr(store, "_seed_state", None) or {"seeded": False, "error": "not run"}
    try:
        anchor = anchor_of(store)
    except Exception:
        anchor = None
    return {"seeded": st["seeded"], "seed_error": st["error"], "seed_anchor": anchor}


def reset(store, today: date) -> dict:
    """E2 Reset: today's anchor, rewrite seeded docs, drop demo-created snags and instances, clear
    health overrides. Audit, templates, alternatives, manual catalogue, leases, markers untouched."""
    anchor = today.isoformat()
    store.put_doc(META, "anchor", {"anchor_date": anchor, "seed_version": SEED_VERSION,
                                   "created_at": f"{anchor}T00:00:00Z"})
    headers, docs = build_docs(anchor)
    for h in headers:
        store.put_doc(snags.HISTORY, h["machine_id"], h)
    for s in docs:
        store.put_doc(snags.SNAGS, s["snag_id"], s)
    for s in store.list_docs(snags.SNAGS):
        if not s.get("seeded"):
            store.delete_doc(snags.SNAGS, s["snag_id"])
    for h in store.list_docs(snags.HISTORY):
        if not h.get("seeded"):
            store.delete_doc(snags.HISTORY, h["machine_id"])
    store.clear_health()
    store.create_if_absent(META, "complete", {"seed_version": SEED_VERSION})
    store._seed_state = {"seeded": True, "error": None, "last_attempt": time.time()}
    return {"seed_anchor": anchor}
