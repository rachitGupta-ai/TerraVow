"""Snag submission, validation, redaction, instances and caps (Req 22, 25; design E8)."""
import copy
import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from fastapi.testclient import TestClient
from hypothesis import HealthCheck, given, settings as hsettings, strategies as st

from app import main, seed, snags, templates
from app.main import app
from app.store import Store

client = TestClient(app)
NOW = "2026-10-17T10:00:00Z"
REP = {"handle": "reporter_01", "role": "REPORTER"}
RES = {"handle": "resolver_01", "role": "RESOLVER"}


def fresh():
    s = Store(docs={})
    seed.run(s)
    return s


def schema(ind="textile"):
    return templates.build_form_schema(templates.BASES[ind]["template"])


def good_payload(ind="textile"):
    answers = {"description": "Flame yellow at high fire"}
    for f in schema(ind):
        if f["core"] or not f.get("required"):
            continue
        answers[f["id"]] = f["options"][0] if f["type"] == "dropdown" else "ok"
    sid = templates.BASES[ind]["template"]["snag_types"][0]["id"]
    return {"snag_type_id": sid, "severity": 3, "answers": answers}


def _snag_count(s):
    return len(s.list_docs(snags.SNAGS))


# Feature: terravow, Property 73: Snag submissions are validated against the stored template and history is append-only
@hsettings(max_examples=100, deadline=None)
@given(mut=st.sampled_from(["sev0", "sev6", "sevstr", "badtype", "core-in-answers", "unknown", "nodesc",
                            "baddrop", "toolong", "none"]),
       extra=st.sampled_from(["", "unknown", "nodesc"]))
def test_property_73_validation_lists_each_error_and_stores_nothing(mut, extra):
    s = fresh()
    h = snags.get_header(s, "B-2")
    p = good_payload()
    drop = next(f for f in schema() if f["type"] == "dropdown" and not f["core"])
    expected = set()
    for m in {mut, extra} - {"", "none"}:
        if m == "sev0": p["severity"] = 0; expected.add("severity")
        if m == "sev6": p["severity"] = 6; expected.add("severity")
        if m == "sevstr": p["severity"] = "3"; expected.add("severity")
        if m == "badtype": p["snag_type_id"] = "not-a-type"; expected.add("snag_type_id")
        if m == "core-in-answers": p["answers"]["snag_type"] = "burner-fouling"; expected.add("answers.snag_type")
        if m == "unknown": p["answers"]["zzz"] = "x"; expected.add("answers.zzz")
        if m == "nodesc": p["answers"].pop("description"); expected.add("answers.description")
        if m == "baddrop": p["answers"][drop["id"]] = "Not an option"; expected.add("answers." + drop["id"])
        if m == "toolong": p["answers"]["description"] = "x" * 2001; expected.add("answers.description")
    before = _snag_count(s)
    snag, errs, _ = snags.submit(s, h, p, "reporter_01", NOW)
    if expected:
        assert snag is None and {e["field"] for e in errs} == expected
        assert _snag_count(s) == before
    else:
        assert errs == [] and snag["status"] == "OPEN" and _snag_count(s) == before + 1
        assert snag["template_source"] == "base" and snag["events"][0]["from_status"] is None


# Feature: terravow, Property 73: Snag submissions are validated against the stored template and history is append-only
@hsettings(max_examples=100, deadline=None)
@given(actions=st.lists(st.sampled_from(["accept", "resolve", "close"]), min_size=1, max_size=6))
def test_property_73_transitions_append_only(actions):
    s = fresh()
    snag, _, _ = snags.submit(s, snags.get_header(s, "C-7"), good_payload(), "reporter_01", NOW)
    allowed = {("OPEN", "accept"), ("OPEN", "resolve"), ("ASSIGNED", "resolve"), ("RESOLVED", "close")}
    for a in actions:
        prev = s.get_doc(snags.SNAGS, snag["snag_id"])
        ok = (prev["status"], a) in allowed
        try:
            snags.transition(s, snag["snag_id"], a, "RESOLVER", "resolver_01", NOW, {"notes": "x"})
            assert ok
        except snags.Conflict:
            assert not ok
        cur = s.get_doc(snags.SNAGS, snag["snag_id"])
        assert cur["events"][:len(prev["events"])] == prev["events"]
        assert cur["status"] == cur["events"][-1]["to_status"]
        if not ok:
            assert cur == prev


# Feature: terravow, Property 84: The role guard and handle rules hold for every action
@hsettings(max_examples=100, deadline=None)
@given(handle=st.text(max_size=40))
def test_property_84_handle_rule(handle):
    import re
    assert snags.handle_ok(handle) == bool(re.fullmatch(r"[A-Za-z0-9_-]{3,32}", handle))


def test_property_84_bad_handle_creates_nothing_via_api():
    seed.run(main.store)
    before = len(main.store.list_docs(snags.SNAGS))
    r = client.post("/api/snags", json={"machine_id": "B-2", "handle": "a b", "role": "REPORTER", **good_payload()})
    assert r.status_code == 422
    r = client.post("/api/instances", json={"model_key": "textile--thermax--heatpac-th-1100", "tag": "L1",
                                            "install_year": 2015, "handle": "x", "role": "REPORTER"})
    assert r.status_code == 422
    assert len(main.store.list_docs(snags.SNAGS)) == before


# Feature: terravow, Property 87: Free-text redaction is deterministic, idempotent, and removes contact patterns
@hsettings(max_examples=100, deadline=None)
@given(prefix=st.text(alphabet="abc XYZ,.", max_size=20),
       user=st.from_regex(r"[a-z]{1,8}", fullmatch=True),
       digits=st.from_regex(r"[6-9][0-9]{9}", fullmatch=True),
       plain=st.text(alphabet="abcdefghij ,.;", max_size=40))
def test_property_87_redaction(prefix, user, digits, plain):
    text = f"{prefix} call {user}@plant.example.com or +91 {digits} on 2026-10-17T09:30 reading 1200"
    out = snags.redact(text)
    assert "@" not in out and digits not in out
    assert "2026-10-17T09:30" in out and "1200" in out
    assert snags.redact(out) == out and snags.redact(text) == out
    assert snags.redact(plain) == plain


def test_submit_api_guidance_and_unknown_machine():
    seed.run(main.store)
    r = client.post("/api/snags", json={"machine_id": "B-2", **REP, **good_payload()})
    assert r.status_code == 201, r.text
    d = r.json()
    assert d["recommendation"]["label"] == snags.GUIDANCE_LABEL and d["recommendation"]["recommended_job"]
    assert d["recommendation"]["guidance_source"] == "fallback-base" and "health" in d
    bad = good_payload(); bad["severity"] = 9; bad["answers"]["zzz"] = "x"
    r = client.post("/api/snags", json={"machine_id": "B-2", **REP, **bad})
    assert r.status_code == 422 and {e["field"] for e in r.json()["detail"]} == {"severity", "answers.zzz"}
    assert client.post("/api/snags", json={"machine_id": "NOPE", **REP, **good_payload()}).status_code == 404
    assert client.post("/api/snags", json={"machine_id": "B-2", **RES, **good_payload()}).status_code == 403


def test_instances_and_caps(monkeypatch):
    seed.run(main.store)
    body = {"model_key": "textile--thermax--heatpac-th-1100", "tag": "Line 3 dryer", "install_year": 2015, **REP}
    r = client.post("/api/instances", json=body)
    assert r.status_code == 201 and r.json()["machine_id"].startswith("INST-")
    keys = [i["machine_id"] for i in client.get("/api/instances", params={"model_key": body["model_key"]}).json()["instances"]]
    assert r.json()["machine_id"] in keys and "C-7" in keys
    assert client.post("/api/instances", json={**body, "model_key": "x--y--z"}).status_code == 404
    assert client.post("/api/instances", json={**body, "install_year": 1900}).status_code == 422
    monkeypatch.setattr(snags, "INSTANCES_MAX", 0)
    assert client.post("/api/instances", json=body).status_code == 429
    monkeypatch.setattr(snags, "SNAGS_PER_MACHINE_MAX", 0)
    assert client.post("/api/snags", json={"machine_id": "B-2", **REP, **good_payload()}).status_code == 429
