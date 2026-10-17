"""Industries, fleet tagging, model key and the Store document API (Req 18, 21.1, 21.5, 21.8)."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from fastapi.testclient import TestClient
from hypothesis import given, settings, strategies as st

from app import catalogue, fleet, industries
from app.main import app, store
from app.store import NotAiDocument, Store

client = TestClient(app)

EXPECTED_DOMAINS = {
    "chemicals": ["air", "water", "chemical", "soil"],
    "metals": ["air", "soil", "water"],
    "mining": ["soil", "water", "air"],
    "textile": ["air", "water", "chemical"],
}
FLEET_INDUSTRY = {"B-2": "textile", "C-7": "textile", "F-1": "metals", "D-3": "chemicals",
                  "K-4": "mining", "G-5": "mining"}
PINNED = {  # design E1 fleet economics table
    "B-2": (48180000, 240900000, {"water": 0.10, "chemical": 0.10}),
    "C-7": (22082500, 110412500, {"water": 0.05, "chemical": 0.05}),
    "F-1": (119136000, 595680000, {"soil": 0.50, "water": 0.50}),
    "D-3": (54312000, 271560000, None),
    "K-4": (59130000, 295650000, None),
    "G-5": (38544000, 192720000, {"water": 0.05, "soil": 0.05}),
}


def _doc_count() -> int:
    return sum(len(v) for v in store._docs.values())


# Feature: terravow, Property 65: Industry and fleet tagging invariants
def test_property_65_industry_and_fleet_tagging():
    assert {i["id"]: i["domains"] for i in industries.INDUSTRIES} == EXPECTED_DOMAINS
    assert [i["id"] for i in industries.INDUSTRIES] == sorted(EXPECTED_DOMAINS)
    covered = {d for i in industries.INDUSTRIES for d in i["domains"]}
    assert covered == set(industries.DOMAINS)

    entries = {e["id"]: e for e in catalogue.SEED_ENTRIES}
    for mid, m in fleet.fresh_fleet().items():
        assert m.industry == FLEET_INDUSTRY[mid]
        assert m.manufacturer and m.model and m.install_year
        e = entries[m.catalogue_id]
        assert e["industry"] == m.industry and e["manufacturer"] == m.manufacturer and e["model"] == m.model
        assert e["fleet_id"] == mid
        opex, repl, ds = PINNED[mid]
        assert e["annual_operating_cost_inr"] == opex == round(m.base_fuel_per_day * m.fuel_price * 365)
        assert e["replacement_value_inr"] == repl == 5 * opex
        assert e["annual_output_value_inr"] == 2 * repl
        assert e["design_life_years"] == 25 and e["scale"] == 1.0 and e["domain_scale"] == ds

    for ind in industries.INDUSTRY_IDS:
        rows = [e for e in catalogue.SEED_ENTRIES if e["industry"] == ind]
        assert 4 <= len(rows) <= 6 and all(e["sample_data"] is True for e in rows)
    for e in catalogue.SEED_ENTRIES:
        assert e["id"] == industries.model_key(e["industry"], e["manufacturer"], e["model"])
        assert e["specs"] == [] and e["spec_status"] == "sample"


def test_property_65_unknown_industry_is_rejected_and_writes_nothing():
    client.get("/api/catalogue/search", params={"industry": "textile"})  # seed first
    before = _doc_count()
    for r in (client.get("/api/catalogue/search", params={"industry": "pharma"}),
              client.get("/api/catalogue/search"),
              client.post("/api/catalogue/manual", json={"industry": "pharma", "manufacturer": "A", "model": "B"}),
              client.post("/api/template/ensure", json={"industry": "pharma", "manufacturer": "A", "model": "B"})):
        assert r.status_code == 422
        assert "industry" in str(r.json()["detail"])
    assert _doc_count() == before


def test_industries_endpoint_and_machine_view_fields():
    d = client.get("/api/industries").json()
    assert [i["id"] for i in d["industries"]] == ["chemicals", "metals", "mining", "textile"]
    assert d["industries"][3]["domains"] == ["air", "water", "chemical"]
    rows = client.get("/api/fleet").json()["machines"]
    assert len(rows) == 6
    for r in rows:
        for k in ("industry", "manufacturer", "model", "install_year", "catalogue_id"):
            assert r[k], k
        assert r["industry"] == FLEET_INDUSTRY[r["id"]]


# Feature: terravow, Property 66: The Machine_Model_Key is a deterministic, field-boundary-safe normalisation
name = st.text(alphabet=st.characters(codec="utf-8", exclude_categories=("Cs",)), min_size=0, max_size=30)


@settings(max_examples=100)
@given(name, name, name, name, name, name)
def test_property_66_model_key(i1, m1, n1, i2, m2, n2):
    import re
    s1 = [industries.slug(x) for x in (i1, m1, n1)]
    for s, raw in zip(s1, (i1, m1, n1)):
        assert industries.slug(s) == s                       # idempotent
        assert re.fullmatch(r"([a-z0-9]+(-[a-z0-9]+)*)?", s)  # no leading/trailing/doubled hyphens
        assert industries.slug(raw) == s                     # deterministic
    if all(s1):
        k = industries.model_key(i1, m1, n1)
        assert k == "--".join(s1) and k.split("--") == s1
        assert industries.template_id(k) == k + "--t1" != industries.template_id(k, "t2")
        s2 = [industries.slug(x) for x in (i2, m2, n2)]
        if all(s2) and s2 != s1:
            assert industries.model_key(i2, m2, n2) != k
    else:
        with pytest.raises(industries.KeyError422):
            industries.model_key(i1, m1, n1)


def test_property_66_boundary_shift_and_examples():
    assert industries.model_key("textile", "a b", "c") != industries.model_key("textile", "a", "b c")
    assert industries.model_key("textile", "Brückner", "Power-Frame") == "textile--bruckner--power-frame"
    with pytest.raises(industries.KeyError422) as e:
        industries.model_key("textile", "!!!", "x")
    assert e.value.field == "manufacturer"


# ---------------------------------------------------------------- Store document API
def test_store_create_if_absent_and_ai_guard():
    s = Store(docs={})
    assert s.create_if_absent("templates", "k", {"source": "ai", "v": 1}) == (True, {"source": "ai", "v": 1})
    created, stored = s.create_if_absent("templates", "k", {"source": "ai", "v": 2})
    assert created is False and stored["v"] == 1
    for coll in ("templates", "alternatives"):
        with pytest.raises(NotAiDocument):
            s.create_if_absent(coll, "z", {"source": "fallback-base"})
        assert s.get_doc(coll, "z") is None
    assert s.create_if_absent("catalogue", "c", {"origin": "manual"})[0] is True


def test_store_document_methods_and_shared_dict():
    shared = {}
    a, b = Store(docs=shared), Store(docs=shared)
    a.put_doc("snags", "1", {"machine_id": "B-2", "status": "OPEN"})
    a.put_doc("snags", "2", {"machine_id": "K-4", "status": "OPEN"})
    assert b.get_doc("snags", "1")["machine_id"] == "B-2"            # second "replica" sees it
    assert [d["machine_id"] for d in b.query_eq("snags", "machine_id", "K-4")] == ["K-4"]
    assert len(b.query_in("snags", "machine_id", ["B-2", "K-4", "X"])) == 2
    assert b.query_in("snags", "machine_id", []) == []
    new = b.update_doc("snags", "1", lambda d: {**d, "status": "ASSIGNED"})
    assert new["status"] == "ASSIGNED" == a.get_doc("snags", "1")["status"]
    with pytest.raises(KeyError):
        a.update_doc("snags", "missing", lambda d: d)
    assert a.replace_if("snags", "2", lambda cur: cur["status"] == "CLOSED", {"x": 1}) is False
    assert a.replace_if("snags", "2", lambda cur: cur["status"] == "OPEN", {"x": 1}) is True
    assert a.replace_if("snags", "3", lambda cur: cur is None, {"x": 3}) is True
    got = a.get_doc("snags", "3")
    got["x"] = 99                                                    # copies, not live references
    assert a.get_doc("snags", "3")["x"] == 3
    b.delete_doc("snags", "3")
    assert a.get_doc("snags", "3") is None
    assert a.backend == "memory"
