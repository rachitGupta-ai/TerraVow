"""Sustainable alternatives, gate integration and incentives (Req 26, 27; design E6.6-6.9, E7.6-7.8)."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from fastapi.testclient import TestClient

from datetime import date

from app import gemini_client, main, routes_ext, seed
from app.store import Store

client = TestClient(main.app)


@pytest.fixture(autouse=True)
def fresh_store(monkeypatch):
    for k in ("GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_GENAI_USE_VERTEXAI"):
        monkeypatch.delenv(k, raising=False)
    monkeypatch.setattr(main, "store", Store(docs={}))
    # health is evaluated at the seeding anchor so the E1 reference paybacks cannot drift
    monkeypatch.setattr(routes_ext, "_today", lambda: date.fromisoformat(seed.ANCHOR_DATE))
    yield


def _cand(kind, name, reductions, stage="operation"):
    return {"kind": kind, "stage": stage, "name": name, "rationale": "r", "assumptions": "",
            "params": {"capex_pct_of_replacement_low": 10, "capex_pct_of_replacement_high": 20,
                       "efficiency_gain_pct_low": 5, "efficiency_gain_pct_high": 9,
                       "production_uplift_pct_low": 0, "production_uplift_pct_high": 0,
                       "extra_opex_pct_low": 0, "extra_opex_pct_high": 0,
                       "payback_years": 0.01, "total_savings_inr": 10 ** 12,   # ignored: never read
                       "reductions": [{"domain": d, "reduction_pct_low": lo, "reduction_pct_high": hi}
                                      for d, lo, hi in reductions]}}


def test_ai_alternative_raising_emissions_is_blocked_and_curated_backfills(monkeypatch):
    """Req 26.7: an AI candidate that raises a domain is BLOCK; the kind is backfilled from the
    curated set (source 'mixed'); nothing reaches ALLOW; the second request is a cache hit."""
    calls = []

    def fake(data, *, timeout_s, max_attempts=2):
        calls.append(data)
        assert set(data) == {"industry", "manufacturer", "model", "machine_type", "pollution_domains", "country"}
        return {"kind": "ok", "sources": [{"uri": "https://example.org/a", "title": "A"}], "reason": "ok",
                "proposal": {"candidates": [
                    _cand("machine_alternative", "Scrubber swap", [("air", 20, 30), ("water", -40, -30)]),
                    _cand("process_change", "Heat recovery", [("air", 5, 9)])]}}
    monkeypatch.setattr(gemini_client, "suggest_alternatives", fake)
    d = client.post("/api/machines/B-2/recommendations", json={}).json()
    assert d["source"] == "mixed"
    blocked = next(i for i in d["items"] if i["name"] == "Scrubber swap")
    assert blocked["gate"]["verdict"] == "BLOCK" and blocked["actionable"] is False
    machine_alts = [i for i in d["items"] if i["kind"] == "machine_alternative"]
    assert any(i["source"] == "curated-fallback" and i["gate"]["verdict"] == "ASK" for i in machine_alts)
    ai_proc = next(i for i in d["items"] if i["name"] == "Heat recovery")
    assert ai_proc["gate"]["verdict"] == "ASK" and ai_proc["economics"]["payback_cash_years"] > 0.3
    assert all(i["gate"]["verdict"] != "ALLOW" for i in d["items"])
    again = client.post("/api/machines/B-2/recommendations", json={}).json()
    assert again["alternatives_cache_status"] == "cache-hit" and len(calls) == 1
    kinds = [e["kind"] for e in main.store.list_audit(10)]
    assert kinds.count("sustainability_recommendation") == 2


def test_incentive_adjustment_never_worsens_and_is_filtered_by_size():
    """Req 27.3-27.8: schemes filtered by enterprise size; adjusted capex/payback <= unadjusted;
    real-named schemes are qualitative and every adjusted figure is tagged illustrative."""
    for size in ("MSME", "LARGE"):
        d = client.post("/api/machines/F-1/recommendations", json={"enterprise_size": size}).json()
        assert d["enterprise_sizes"] == [size] and list(d["schemes"]) == [size]
        assert all(size in s["enterprise_sizes"] for s in d["schemes"][size])
        for s in d["schemes"][size]:
            assert s["status"] in ("UNVERIFIED/illustrative", "ILLUSTRATIVE") and s["verified"] is False
            if s["origin"] == "curated":
                assert s["params"] is None and s["retrieved_on"] is None
        for it in d["items"]:
            e, b = it["economics"], it["economics"]["by_size"][size]
            assert b["adjusted_capex_inr"] <= e["capex_inr"]
            for k in ("payback_cash_years", "payback_with_exposure_years"):
                if e[k] is not None:
                    assert b["adjusted_" + k] <= e[k]
            assert b["schemes_applied"] and b["uses_illustrative_values"] is True
    msme = client.get("/api/incentives?country=IN&industry=mining&enterprise_size=MSME").json()
    assert "ireda-finance" not in {s["scheme_id"] for s in msme["schemes"]}
    assert client.get("/api/incentives?country=FR&industry=mining").json()["message"] == "No data for this country."
    assert client.get("/api/incentives?country=in/../x&industry=mining").status_code == 422


def test_gemini_not_configured_returns_curated_fallback_with_reference_paybacks():
    """Req 26.5/26.6: not configured -> 200 curated-fallback, 2 machine + 3 process items, nothing
    cached; the F-1 paybacks match the E1 worked reference (87.5 / 51.9 beyond-design-life)."""
    d = client.post("/api/machines/F-1/recommendations", json={}).json()
    assert d["source"] == "curated-fallback" and d["alternatives_cache_status"] == "fallback"
    assert [i["kind"] for i in d["items"]].count("machine_alternative") == 2
    assert [i["kind"] for i in d["items"]].count("process_change") == 3
    assert not main.store.list_docs("alternatives")
    ind = next(i for i in d["items"] if i["id"] == "mt-induction-melting")["economics"]
    assert ind["payback_cash_years"] == pytest.approx(87.5, rel=0.02)
    assert ind["payback_with_exposure_years"] == pytest.approx(51.9, rel=0.02)
    assert ind["payback_cash_status"] == "beyond-design-life"
    quench = next(i for i in d["items"] if i["id"] == "mt-closed-loop-quench")["economics"]
    assert quench["payback_cash_years"] is None and quench["payback_cash_status"] == "no-payback"
    assert quench["payback_with_exposure_years"] == pytest.approx(723.3, rel=0.02)
