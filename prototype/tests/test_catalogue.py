"""Catalogue search and endpoint behaviour (Req 18.6, 18.7, 19.1-19.5)."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient
from hypothesis import given, settings, strategies as st

from app import catalogue, industries
from app.main import app

client = TestClient(app)


def _matches(e, fq):
    fields = [industries.fold(e[k]) for k in ("manufacturer", "model", "machine_type")]
    words = [w for f in fields for w in catalogue._WORD_SPLIT.split(f) if w]
    prefix = any(w.startswith(fq) for w in words)
    return prefix, prefix or fq in " ".join(fields)


synthetic = st.lists(st.fixed_dictionaries({
    "manufacturer": st.sampled_from(["Brückner", "Acme", "Ébène", "Zeta Works", "acme"]),
    "model": st.text(alphabet="abcXYZ é-1", min_size=1, max_size=8),
    "machine_type": st.sampled_from(["Stenter", "Jet dyeing machine", "Boiler"]),
}), max_size=15)


# Feature: terravow, Property 72: Catalogue search is industry-scoped, bounded, and deterministically ordered
@settings(max_examples=100)
@given(st.sampled_from(industries.INDUSTRY_IDS),
       st.text(alphabet="abcdeéüBRUKmnrst -", min_size=0, max_size=6),
       synthetic, st.randoms(use_true_random=False))
def test_property_72_search(industry, q, extra, rnd):
    entries = list(catalogue.SEED_ENTRIES) + [
        {**x, "id": f"{industry}--x{i}", "industry": industry, "origin": "manual"} for i, x in enumerate(extra)]
    out = catalogue.search(entries, industry, q)
    assert len(out) <= 10 and all(e["industry"] == industry for e in out)
    fq = industries.fold(q.strip())
    if fq:
        flags = [_matches(e, fq) for e in out]
        assert all(m for _, m in flags)
        prefixes = [p for p, _ in flags]
        assert prefixes == sorted(prefixes, reverse=True)       # every prefix match before substring-only
    else:
        assert out == sorted([e for e in entries if e["industry"] == industry],
                             key=catalogue._order_key)[:10]
    shuffled = entries[:]
    rnd.shuffle(shuffled)
    assert catalogue.search(shuffled, industry, q) == out == catalogue.search(entries, industry, q)


def test_search_endpoint_bru_finds_bruckner():
    r = client.get("/api/catalogue/search", params={"industry": "textile", "q": "bru"})
    assert r.status_code == 200
    d = r.json()
    assert [x["id"] for x in d["results"]] == ["textile--bruckner--power-frame"]
    assert d["results"][0]["manufacturer"] == "Brückner"
    assert d["labels"] == ["sample-data"] and d["notice"].startswith("Sample data, not an authoritative")
    assert d["manual_entry_offered"] is False


def test_search_validation_and_no_match():
    assert client.get("/api/catalogue/search", params={"industry": "textile", "q": "a" * 81}).status_code == 422
    assert client.get("/api/catalogue/search", params={"industry": "textile", "q": "a" * 80}).status_code == 200
    r = client.get("/api/catalogue/search", params={"industry": "unknown", "q": "x"})
    assert r.status_code == 422 and "industry" in str(r.json()["detail"])
    d = client.get("/api/catalogue/search", params={"industry": "mining", "q": "zzzz"}).json()
    assert d["results"] == [] and d["manual_entry_offered"] is True
    d = client.get("/api/catalogue/search", params={"industry": "metals"}).json()
    assert len(d["results"]) == 5 and all(x["industry"] == "metals" for x in d["results"])


def test_fleet_unchanged_apart_from_added_fields():
    rows = client.get("/api/fleet").json()["machines"]
    base_keys = {"id", "name", "kind", "fuel", "fuel_unit", "base_fuel_per_day", "repair_cost",
                 "downtime_cost_now", "downtime_cost_planned", "planned_window_day", "health", "data_trust",
                 "footprint", "daily_liability_today", "downtime_savings_if_wait", "waiting_cost_at_window",
                 "break_even_day", "recommendation", "options", "assumptions", "formula", "rank"}
    added = {"industry", "manufacturer", "model", "install_year", "catalogue_id"}
    for r in rows:
        assert set(r) == base_keys | added
