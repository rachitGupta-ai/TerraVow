"""Seeding, demo reset, resolver workflow and guidance (Req 22.7-22.8, 25; design E2, E8)."""
import os
import sys
import threading
from datetime import date
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient
from hypothesis import given, settings as hsettings, strategies as st

from app import fleet, industries, main, seed, snags, templates
from app.main import app
from app.store import Store

client = TestClient(app)
FLEET = ["B-2", "C-7", "D-3", "F-1", "G-5", "K-4"]
NOW = "2026-10-17T10:00:00Z"


def _seeded_state(s):
    return {c: {d.get("snag_id") or d.get("machine_id") or d.get("id") or repr(sorted(d)): d
                for d in s.list_docs(c)} for c in (snags.HISTORY, snags.SNAGS, "catalogue", seed.META)}


def _required_answers(ind):
    a = {"description": "x"}
    for f in templates.build_form_schema(templates.BASES[ind]["template"]):
        if not f["core"] and f.get("required"):
            a[f["id"]] = f["options"][0] if f["type"] == "dropdown" else "ok"
    return a


def _scores(s, as_of):
    fl = fleet.fresh_fleet()
    return {m: snags.health_view(s, snags.get_header(s, m), as_of, fl[m])["health"]["score"] for m in FLEET}


# Feature: terravow, Property 88: Seeding is idempotent and independent of the replica that performs it
@hsettings(max_examples=100, deadline=None)
@given(n=st.integers(1, 4), concurrent=st.booleans())
def test_property_88_seed_idempotent_across_replicas(n, concurrent):
    ref = Store(docs={}); seed.run(ref)
    shared = {}
    stores = [Store(docs=shared) for _ in range(n)]
    if concurrent:
        ts = [threading.Thread(target=seed.run, args=(s,)) for s in stores]
        [t.start() for t in ts]; [t.join() for t in ts]
    else:
        for s in stores:
            seed.run(s)
    seed.run(stores[0])
    assert _seeded_state(stores[0]) == _seeded_state(ref)
    assert len([d for d in stores[0].list_docs(snags.SNAGS) if d["snag_id"].startswith("SEED-")]) == \
        sum(len(v) for v in seed.SEED["machines"].values())
    a = date.fromisoformat(seed.anchor_of(stores[0]))
    assert _scores(stores[0], a) == _scores(ref, a)


def test_property_88_seed_failure_does_not_crash(monkeypatch):
    s = Store(docs={})
    monkeypatch.setattr(s, "create_if_absent", lambda *a: (_ for _ in ()).throw(RuntimeError("store down")))
    assert seed.ensure(s) is False
    assert seed.status(s)["seeded"] is False and "store down" in seed.status(s)["seed_error"]


# Feature: terravow, Property 89: Snag guidance resolves in a fixed order and is labelled with the source actually used
def test_property_89_queue_on_fresh_store_uses_base(monkeypatch):
    from app import gemini_client
    monkeypatch.setattr(gemini_client, "generate_template", lambda *a, **k: (_ for _ in ()).throw(AssertionError))
    s = Store(docs={}); seed.run(s)
    q = snags.queue(s)
    assert {i["snag"]["machine_id"] for i in q} == {"C-7", "F-1", "G-5", "K-4"}  # B-2, D-3 seeds are all closed
    assert all(i["guidance_source"] == "fallback-base" and i["recommended_job"] for i in q)
    keys = [(-i["snag"]["severity"], i["snag"]["reported_at"], i["snag"]["snag_id"]) for i in q]
    assert keys == sorted(keys)
    # every seeded snag of all six machines resolves from the Base_Template
    for d in s.list_docs(snags.SNAGS):
        assert snags.resolve_guidance(d, s)["guidance_source"] == "fallback-base"


# Feature: terravow, Property 89: Snag guidance resolves in a fixed order and is labelled with the source actually used
@hsettings(max_examples=100, deadline=None)
@given(ai_present=st.booleans(), ai_has_type=st.booleans(), type_kind=st.sampled_from(["base", "none", "absent"]))
def test_property_89_guidance_order(ai_present, ai_has_type, type_kind):
    s = Store(docs={})
    base = templates.BASES["textile"]["template"]
    sid = {"base": base["snag_types"][0]["id"], "none": None, "absent": "zz-not-anywhere"}[type_kind]
    key = "textile--thermax--heatpac-th-1100"
    if ai_present:
        tpl = {**base, "snag_types": [dict(st_, name="AI " + st_["name"]) for st_ in base["snag_types"]
                                     if ai_has_type or st_["id"] != sid]}
        s.create_if_absent(templates.TEMPLATES, industries.template_id(key), {"source": "ai", "template": tpl})
    snag = {"snag_type_id": sid, "model_key": key, "industry": "textile", "schema_version": None}
    g = snags.resolve_guidance(snag, s)
    if type_kind != "base":
        assert g["guidance_source"] == "none" and g["recommended_job"] is None
    elif ai_present and ai_has_type:
        assert g["guidance_source"] == "ai" and g["snag_type_name"].startswith("AI ")
    else:
        assert g["guidance_source"] == "fallback-base"


# Feature: terravow, Property 84: The role guard and handle rules hold for every action
@hsettings(max_examples=100, deadline=None)
@given(action=st.sampled_from(["accept", "resolve", "close"]), role=st.sampled_from(["REPORTER", "RESOLVER"]),
       status=st.sampled_from(["OPEN", "ASSIGNED", "RESOLVED", "CLOSED"]))
def test_property_84_role_and_status_guard(action, role, status):
    s = Store(docs={}); seed.run(s)
    doc = s.get_doc(snags.SNAGS, "SEED-K-4-06")
    doc["status"] = status
    s.put_doc(snags.SNAGS, doc["snag_id"], doc)
    need, froms, _ = snags.TRANSITIONS[action]
    try:
        snags.transition(s, doc["snag_id"], action, role, "someone", NOW)
        assert role == need and status in froms
    except snags.Forbidden:
        assert role != need
        assert s.get_doc(snags.SNAGS, doc["snag_id"]) == doc
    except snags.Conflict:
        assert role == need and status not in froms
        assert s.get_doc(snags.SNAGS, doc["snag_id"]) == doc


def test_resolver_api_flow_audit_and_health():
    seed.reset(main.store, date.today())
    rep = {"handle": "rep_01", "role": "REPORTER"}
    res = {"handle": "res_01", "role": "RESOLVER"}
    sid = "SEED-K-4-06"
    assert client.post(f"/api/snags/{sid}/accept", json=rep).status_code == 403
    assert client.post(f"/api/snags/{sid}/close", json=res).status_code == 409
    assert client.post(f"/api/snags/{sid}/accept", json=res).json()["snag"]["status"] == "ASSIGNED"
    assert client.post(f"/api/snags/{sid}/accept", json=res).status_code == 409
    r = client.post(f"/api/snags/{sid}/resolve", json={**res, "notes": "mail me at a@b.example.com",
                                                       "actions_taken": "replaced seal", "downtime_hours": 4, "cost_inr": 5000})
    assert r.status_code == 200 and "@" not in r.json()["snag"]["resolution"]["notes"]
    assert "a@b" not in r.json()["audit"]["summary"] and "replaced" not in r.json()["audit"]["summary"]
    before = client.get("/api/machines/K-4/health").json()["health"]["score"]
    r = client.post(f"/api/snags/{sid}/close", json=res)
    assert r.status_code == 200 and r.json()["audit"]["kind"] == "snag_closed"
    h = r.json()["health"]
    assert set(h) >= {"health", "lifespan", "pollution"} and h["health"]["score"] >= before
    # test_core deliberately tampers an older entry of the shared app store; check the new links
    assert main.store.verify_chain(3)["chain_intact"]
    q = client.get("/api/resolver/queue").json()
    assert sid not in [i["snag"]["snag_id"] for i in q["queue"]]
    hist = client.get("/api/machines/K-4/history").json()
    assert hist["synthetic"] and hist["truncated"] is False and len(hist["snags"]) == 6
    assert client.get("/api/snags", params={"status": "OPEN", "machine_id": "K-4"}).json()["snags"]
    assert client.get("/api/machines/NOPE/history").status_code == 404


# Feature: terravow, Property 86: Demo reset restores seeded histories and preserves the audit trail and caches
@hsettings(max_examples=100, deadline=None)
@given(n_new=st.integers(0, 3), act=st.booleans())
def test_property_86_reset(n_new, act):
    s = Store(docs={}); seed.run(s)
    s.add_audit({"kind": "x", "summary": "before"})
    s.create_if_absent(templates.TEMPLATES, "t--t--t--t1", {"source": "ai", "template": {}})
    s.put_doc("catalogue", "textile--m--x", {"id": "textile--m--x", "origin": "manual", "industry": "textile"})
    for _ in range(n_new):
        snags.submit(s, snags.get_header(s, "B-2"), {"severity": 2, "snag_type_id": None,
                     "answers": _required_answers("textile")}, "rep_01", NOW)
        snags.create_instance(s, {"id": "textile--thermax--heatpac-th-1100", "industry": "textile"}, "L1", 2015, "rep_01", NOW)
    if act:
        snags.transition(s, "SEED-K-4-06", "resolve", "RESOLVER", "res_01", NOW)
        s.add_audit({"kind": "snag_resolved", "summary": "s"})
    audit_before = s.list_audit(100)
    today = date(2027, 1, 5)
    seed.reset(s, today)
    expected_h, expected_s = seed.build_docs(today.isoformat())
    assert sorted(s.list_docs(snags.HISTORY), key=lambda h: h["machine_id"]) == expected_h
    assert sorted(s.list_docs(snags.SNAGS), key=lambda d: d["snag_id"]) == sorted(expected_s, key=lambda d: d["snag_id"])
    assert s.list_audit(100) == audit_before and s.verify_chain()["chain_intact"]
    assert s.get_doc(templates.TEMPLATES, "t--t--t--t1") and s.get_doc("catalogue", "textile--m--x")
    assert len(set(_scores(s, today).values())) == 6
