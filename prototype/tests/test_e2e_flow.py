"""End-to-end slice checkpoint (plan task 10): industry -> search -> template ensure (cached) ->
instance -> snag -> resolver accept/resolve/close -> health -> recommendations (MSME, LARGE) ->
audit chain intact. Walked once with a fake Gemini and once with Gemini not configured."""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from fastapi.testclient import TestClient

from app import gemini_client, main, templates
from app.store import Store

client = TestClient(main.app)
REP = {"handle": "rep_01", "role": "REPORTER"}
RES = {"handle": "res_01", "role": "RESOLVER"}


@pytest.fixture(autouse=True)
def fresh_store(monkeypatch):
    for k in ("GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_GENAI_USE_VERTEXAI"):
        monkeypatch.delenv(k, raising=False)
    monkeypatch.setattr(main, "store", Store(docs={}))   # test_core tampers the shared store's chain
    yield


def _answers(form_schema):
    out = {}
    for f in form_schema:
        if f["id"] in ("snag_type", "severity"):
            continue
        if f["id"] == "description":
            out[f["id"]] = "Flame yellow, call 9876543210 or a@b.com"
        elif f.get("required"):
            out[f["id"]] = f["options"][0] if f["type"] == "dropdown" else "observed"
    return out


def _walk(expect_ai: bool):
    inds = client.get("/api/industries").json()["industries"]
    assert [i["id"] for i in inds] == ["chemicals", "metals", "mining", "textile"]
    res = client.get("/api/catalogue/search", params={"industry": "textile", "q": "thies"}).json()["results"]
    entry = res[0]
    ident = {"industry": "textile", "manufacturer": entry["manufacturer"], "model": entry["model"]}

    t1 = client.post("/api/template/ensure", json=ident).json()
    t2 = client.post("/api/template/ensure", json=ident).json()
    if expect_ai:
        assert (t1["cache_status"], t2["cache_status"]) == ("generated", "cache-hit")
        assert t2["source"] == "ai"
    else:
        assert t1["cache_status"] == t2["cache_status"] == "fallback"
        assert t1["source"] == "fallback-base" and "generic" in t1["user_message"]

    inst = client.post("/api/instances", json={**REP, "model_key": entry["id"], "tag": "Line 3 jet",
                                               "install_year": 2015})
    assert inst.status_code == 201
    mid = inst.json()["machine_id"]
    snag_type = t2["template"]["snag_types"][0]["id"]
    r = client.post("/api/snags", json={**REP, "machine_id": mid, "snag_type_id": snag_type, "severity": 4,
                                        "answers": _answers(t2["form_schema"])})
    assert r.status_code == 201, r.text
    snag = r.json()["snag"]
    assert snag["template_source"] == ("ai" if expect_ai else "base")
    assert "9876543210" not in json.dumps(snag) and "a@b.com" not in json.dumps(snag)
    assert r.json()["recommendation"]["guidance_source"] == ("ai" if expect_ai else "fallback-base")

    q = client.get("/api/resolver/queue").json()["queue"]
    assert any(i["snag"]["snag_id"] == snag["snag_id"] for i in q)
    sid = snag["snag_id"]
    assert client.post(f"/api/snags/{sid}/accept", json=REP).status_code == 403
    assert client.post(f"/api/snags/{sid}/accept", json=RES).status_code == 200
    assert client.post(f"/api/snags/{sid}/resolve", json={**RES, "notes": "cleaned nozzle",
                                                          "downtime_hours": 3, "cost_inr": 12000}).status_code == 200
    closed = client.post(f"/api/snags/{sid}/close", json=RES).json()
    assert closed["snag"]["status"] == "CLOSED" and "score" in closed["health"]["health"]

    h = client.get(f"/api/machines/{mid}/health").json()
    assert 0 <= h["health"]["score"] <= 100 and "estimate" in h["labels"]

    for size in ("MSME", "LARGE"):
        rec = client.post(f"/api/machines/{mid}/recommendations", json={"enterprise_size": size})
        assert rec.status_code == 200
        d = rec.json()
        assert d["source"] == ("ai-suggested" if expect_ai else "curated-fallback")
        assert d["items"] and all(i["gate"]["verdict"] in ("ASK", "BLOCK") for i in d["items"])
        assert d["schemes"][size] and d["audit_id"]
    reg = client.get("/api/registry").json()
    assert reg["chain"]["chain_intact"] is True
    kinds = [e["kind"] for e in reg["entries"]]
    assert {"snag_resolved", "snag_closed", "sustainability_recommendation"} <= set(kinds)


def test_e2e_with_fake_gemini(monkeypatch):
    calls = {"template": 0, "alts": 0}

    def fake_template(data, *, timeout_s, hint=None):
        calls["template"] += 1
        return json.dumps(templates.BASES["textile"]["template"]), {"structured_output": "schema", "truncated": False}

    def fake_alts(data, *, timeout_s, max_attempts=2):
        calls["alts"] += 1
        c = lambda kind, name, stage="operation": {
            "kind": kind, "stage": stage, "name": name, "rationale": "r", "assumptions": "",
            "params": {"capex_pct_of_replacement_low": 5, "capex_pct_of_replacement_high": 15,
                       "efficiency_gain_pct_low": 5, "efficiency_gain_pct_high": 10,
                       "production_uplift_pct_low": 0, "production_uplift_pct_high": 0,
                       "extra_opex_pct_low": 0, "extra_opex_pct_high": 0,
                       "reductions": [{"domain": "water", "reduction_pct_low": 10, "reduction_pct_high": 20}]}}
        return {"kind": "ok", "reason": "ok", "sources": [{"uri": "https://example.org", "title": "x"}],
                "proposal": {"candidates": [c("machine_alternative", "Low-liquor jet"),
                                            c("process_change", "Water reuse")]}}
    monkeypatch.setattr(gemini_client, "generate_template", fake_template)
    monkeypatch.setattr(gemini_client, "suggest_alternatives", fake_alts)
    _walk(expect_ai=True)
    assert calls == {"template": 1, "alts": 1}   # second ensure and second recommendation served from cache


def test_e2e_gemini_not_configured():
    assert gemini_client.mode() == "not-configured"
    _walk(expect_ai=False)
    assert not main.store.list_docs("templates") and not main.store.list_docs("alternatives")
