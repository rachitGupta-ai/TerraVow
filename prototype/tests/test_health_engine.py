"""Health score, remaining lifespan, health-linked pollution intensity (Req 23, 24; design E7)."""
import json
import os
import sys
from datetime import date, timedelta
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from fastapi.testclient import TestClient
from hypothesis import given, settings as hsettings, strategies as st

from app import engine, fleet, industries, main, seed, snags
from app.engine import SnagFact
from app.main import app
from app.store import Store

client = TestClient(app)
AS_OF = date(2026, 10, 17)
CAPS = {"frequency": 15, "severity": 15, "recurrence": 15, "trend": 10, "unresolved": 25, "age": 15, "repair": 8}

fact = st.builds(
    lambda t, sev, rep, res, open_: SnagFact(t, "cat-" + (t or "x"), sev, AS_OF - timedelta(days=rep),
                                             None if open_ else AS_OF - timedelta(days=max(0, rep - res)), open_),
    st.sampled_from([None, "a", "b", "c"]), st.integers(1, 5), st.integers(0, 900), st.integers(0, 60), st.booleans())
facts_st = st.lists(fact, max_size=12)
year_st = st.one_of(st.none(), st.integers(1960, 2030))


# Feature: terravow, Property 74: Health score is bounded, deterministic, and monotone in snag history
@hsettings(max_examples=100, deadline=None)
@given(facts=facts_st, year=year_st, life=st.integers(5, 60), extra=fact, sev=st.integers(1, 5))
def test_property_74_bounded_deterministic_monotone(facts, year, life, extra, sev):
    h = engine.health_score(facts, year, life, AS_OF)
    assert 0 <= h["score"] <= 100 and h == engine.health_score(facts, year, life, AS_OF)
    added = SnagFact(extra.snag_type_id, extra.category, sev, extra.reported_on, None, True)
    assert engine.health_score(facts + [added], year, life, AS_OF)["score"] <= h["score"]
    for i, f in enumerate(facts):
        if f.unresolved_at_as_of:
            fixed = SnagFact(f.snag_type_id, f.category, f.severity, f.reported_on, AS_OF, False)
            assert engine.health_score(facts[:i] + [fixed] + facts[i + 1:], year, life, AS_OF)["score"] >= h["score"]
    if year is None or year > AS_OF.year:
        assert h["confidence"] == "age-unknown" and h["age_penalty"] == 0
    if not facts and year is not None and year <= AS_OF.year:
        assert h["confidence"] == "low-history"
        age = (AS_OF - date(year, 1, 1)).days
        assert h["score"] == pytest.approx(100 - 15 * min(1, age / (life * 365)) - 8 * min(1, age / 730))


# Feature: terravow, Property 75: The health factors explain the score exactly
@hsettings(max_examples=100, deadline=None)
@given(facts=facts_st, year=year_st, life=st.integers(5, 60))
def test_property_75_factors_explain_score(facts, year, life):
    h = engine.health_score(facts, year, life, AS_OF)
    assert {f["id"] for f in h["factors"]} == set(CAPS)
    for f in h["factors"]:
        assert 0 <= f["penalty"] <= CAPS[f["id"]]
    assert max(0.0, 100 - sum(f["penalty"] for f in h["factors"])) == pytest.approx(h["score"], abs=0.05)


# Feature: terravow, Property 76: Remaining lifespan is bounded and non-decreasing in health
@hsettings(max_examples=100, deadline=None)
@given(h1=st.floats(0, 100), h2=st.floats(0, 100), life=st.integers(5, 60),
       age=st.one_of(st.none(), st.integers(0, 30000)), n=st.integers(0, 10))
def test_property_76_lifespan(h1, h2, life, age, n):
    lo, hi = sorted((h1, h2))
    a, b = (engine.remaining_lifespan(x, age, life, n) for x in (lo, hi))
    assert a["remaining_days"] <= b["remaining_days"]
    for r in (a, b):
        assert 0 <= r["low_days"] <= r["remaining_days"] <= r["high_days"] <= life * 365
    assert engine.remaining_lifespan(0, age, life, n)["remaining_days"] == 0
    assert engine.remaining_lifespan(lo, age, life, n) == a


# Feature: terravow, Property 77: Degraded health raises pollution intensity monotonically
@hsettings(max_examples=100, deadline=None)
@given(ind=st.sampled_from(list(industries.INDUSTRY_IDS)), h1=st.floats(0, 99.5), dh=st.floats(0.5, 100),
       scale=st.floats(0.2, 5.0), days=st.floats(0, 9000), fleet_air=st.booleans())
def test_property_77_intensity_monotone(ind, h1, dh, scale, days, fleet_air):
    h2 = min(100.0, h1 + dh)
    row = industries.INDUSTRY_BY_ID[ind]
    m = fleet.fresh_fleet()["K-4"] if fleet_air else None
    args = (industries.domains_of(ind), industries.DOMAIN_INDICATORS, industries.INDICATOR_UNITS,
            row["default_baselines"], scale, None, m, days)
    p1, p2 = engine.pollution_intensity(h1, *args), engine.pollution_intensity(h2, *args)
    allowed = {i for d in industries.domains_of(ind) for i in industries.DOMAIN_INDICATORS[d]}
    assert set(p1["per_day"]) <= allowed and set(p1["per_day"]) == set(p2["per_day"])
    for d in set(industries.DOMAINS) - set(industries.domains_of(ind)):
        assert not set(industries.DOMAIN_INDICATORS[d]) & set(p1["per_day"])
    for k, v in p1["per_day"].items():
        assert v["value"] > p2["per_day"][k]["value"]  # every baseline here is > 0
        assert p1["over_remaining_lifespan"][k] == pytest.approx(v["value"] * days)


# Feature: terravow, Property 78: Air intensity reuses the existing deferral engine
@pytest.mark.parametrize("mid", ["B-2", "F-1", "K-4", "C-7", "D-3", "G-5"])
@pytest.mark.parametrize("s", [1, 2, 3, 4, 5])
def test_property_78_air_reuses_deferral_engine(mid, s):
    m = fleet.fresh_fleet()[mid]
    h = 100 - 25 * (s - 1)  # sev_equiv(h) == s
    assert engine.sev_equiv(h) == pytest.approx(s)
    p = engine.pollution_intensity(h, ["air"], industries.DOMAIN_INDICATORS, industries.INDICATOR_UNITS,
                                   {}, 1.0, None, m, 0)["per_day"]
    ex = engine.daily_excess(m, s, 0)
    assert p["co2e_kg"]["value"] - p["co2e_kg"]["baseline_reference"] == pytest.approx(ex["co2_kg"])
    assert p["nox_kg"]["value"] - p["nox_kg"]["baseline_reference"] == pytest.approx(ex["nox_kg"])
    assert ex == engine.excess_from_loss(m, engine.loss_at(s, 0))


def test_seeded_table_six_distinct_scores():
    s = Store(docs={}); seed.run(s)
    anchor = date.fromisoformat(seed.anchor_of(s))
    fl = fleet.fresh_fleet()
    got = {m: snags.health_view(s, snags.get_header(s, m), anchor, fl[m])["health"]["score"]
           for m in ["D-3", "B-2", "G-5", "F-1", "C-7", "K-4"]}
    order = ["D-3", "B-2", "G-5", "F-1", "C-7", "K-4"]
    assert [got[m] for m in order] == sorted(got.values(), reverse=True) and len(set(got.values())) == 6
    table = {"D-3": 87.0, "B-2": 73.0, "G-5": 71.6, "F-1": 60.5, "C-7": 55.8, "K-4": 28.4}
    for m, v in table.items():
        assert abs(got[m] - v) <= 3
    with open(os.path.join(industries.DATA_DIR, "catalogue.json"), encoding="utf-8") as f:
        fleet_entries = [e for e in json.load(f)["entries"] if e.get("fleet_id")]
    assert len(fleet_entries) == 6 and all(e["design_life_years"] == 25 for e in fleet_entries)


def test_health_endpoint_bounds_labels_and_no_audit():
    seed.reset(main.store, date.today())
    n = len(main.store.list_audit(100))
    d = client.get("/api/machines/K-4/health").json()
    assert d["labels"] == ["estimate", "illustrative", "not-an-official-certification"]
    assert len(d["timeline"]) == 12 and d["timeline"][-1]["as_of"] == d["as_of"]
    assert set(d["pollution"]["domains"]) == {"air", "water", "soil"} and "chem_release_kg" not in d["pollution"]["per_day"]
    assert d["lifespan"]["label"].startswith("Estimate")
    assert len(main.store.list_audit(100)) == n
    assert client.get("/api/machines/K-4/health", params={"as_of": "1989-12-31"}).status_code == 422
    assert client.get("/api/machines/K-4/health", params={"as_of": "2999-01-01"}).status_code == 422
    assert client.get("/api/machines/K-4/health", params={"as_of": "bad"}).status_code == 422
    assert client.get("/api/machines/NOPE/health").status_code == 404
