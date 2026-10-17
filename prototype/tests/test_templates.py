"""Template validator, Base_Templates, form schema and fallback (Req 20.2-20.10)."""
import copy
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from hypothesis import given, settings, strategies as st

from app import catalogue, industries, templates
from app.templates_schema import LAX, STRICT, GeneratedTemplateSchema

SEEDED_SNAG_IDS = {  # design E6.3 / E7.4 seeded fleet histories
    "textile": {"burner-fouling", "refractory-wear", "pump-seal", "filter-clog"},
    "metals": {"door-seal", "recuperator-leak"},
    "chemicals": {"insulation-wear"},
    "mining": {"injector-drift", "sensor-drift", "kiln-shell-hotspot", "preheater-fouling",
               "girth-gear-wear", "kiln-seal-leak"},
}


def base(ind="textile"):
    return copy.deepcopy(templates.BASES[ind]["template"])


def errors(t, ind="textile"):
    return templates.validate_template(t, ind)[1]


# Feature: terravow, Property 71: Base templates are valid, domain-covering, and materially different across industries
def test_property_71_bases_valid_distinct_and_cover_seeded_snags():
    ids = list(industries.INDUSTRY_IDS)
    for ind in ids:
        t = base(ind)
        assert errors(t, ind) == []
        assert len(t["job_types"]) >= 6 and len(t["snag_types"]) >= 12 and len(t["operating_parameters"]) >= 8
        assert {f["domain"] for f in t["pollutant_capture"]} >= set(industries.domains_of(ind))
        assert SEEDED_SNAG_IDS[ind] <= {s["id"] for s in t["snag_types"]}
    for i, a in enumerate(ids):
        for b in ids[i + 1:]:
            ta, tb = base(a), base(b)
            assert templates.overlap({s["id"] for s in ta["snag_types"]}, {s["id"] for s in tb["snag_types"]}) <= 0.5
            assert templates.overlap({f["id"] for f in ta["pollutant_capture"]},
                                     {f["id"] for f in tb["pollutant_capture"]}) <= 0.5
    for e in catalogue.SEED_ENTRIES:
        fb = templates._fallback_doc(e)
        assert fb["source"] == "fallback-base"
        assert errors(fb["template"], e["industry"]) == []
        assert "Not generated for this model" in fb["template"]["machine_profile"]["summary"]
        assert templates.adapt_base(e) == templates.adapt_base(e)          # deterministic


def test_strict_and_lax_families_do_not_drift():
    for name in ("SpecItem", "OperatingParam", "CostBand", "JobType", "SeverityDef", "PollutionEffect",
                 "SnagType", "FormField", "MachineProfile", "GeneratedTemplate"):
        s, l = getattr(STRICT, name), getattr(LAX, name)
        assert list(s.model_fields) == list(l.model_fields)
        for f in s.model_fields:
            assert str(s.model_fields[f].annotation).replace("STRICT", "") == \
                   str(l.model_fields[f].annotation).replace("LAX", ""), (name, f)
    assert "additionalProperties" not in json.dumps(GeneratedTemplateSchema.model_json_schema())


# ---------------------------------------------------------------- Property 70: one failing template per rule
def _mut(fn):
    t = base()
    fn(t)
    return t


def _set_field(t, where, i, **kw):
    t[where][i].update(kw)


FAILING = {
    "unknown key": lambda t: t.update(extra_key=1),
    "string number": lambda t: t["job_types"][0].update(est_downtime_hours="3"),
    "bad id": lambda t: t["job_types"][0].update(id="Bad Id"),
    "duplicate job id": lambda t: t["job_types"][1].update(id=t["job_types"][0]["id"]),
    "duplicate snag id": lambda t: t["snag_types"][1].update(id=t["snag_types"][0]["id"]),
    "duplicate param id": lambda t: t["operating_parameters"][1].update(id=t["operating_parameters"][0]["id"]),
    "form/capture id clash": lambda t: t["pollutant_capture"][0].update(id=t["form_fields"][0]["id"]),
    "core id clash": lambda t: t["form_fields"][0].update(id="description"),
    "too few jobs": lambda t: t.update(job_types=[j for j in t["job_types"] if j["id"] in {s["recommended_job_id"] for s in t["snag_types"]}][:5]),
    "too few snags": lambda t: t.update(snag_types=t["snag_types"][:11]),
    "too few params": lambda t: t.update(operating_parameters=t["operating_parameters"][:7]),
    "too few form fields": lambda t: t.update(form_fields=t["form_fields"][:3]),
    "four severity levels": lambda t: t["snag_types"][0].update(severity_levels=t["snag_types"][0]["severity_levels"][:4]),
    "empty severity definition": lambda t: t["snag_types"][0]["severity_levels"][2].update(definition="   "),
    "dangling job ref": lambda t: t["snag_types"][0].update(recommended_job_id="no-such-job"),
    "no in-domain pollution effect": lambda t: t["snag_types"][0].update(pollution_effects=[{"domain": "soil", "effect": "x"}]),
    "domain not covered": lambda t: t.update(pollutant_capture=[f for f in t["pollutant_capture"] if f["domain"] != "water"]),
    "dropdown without options": lambda t: t["form_fields"][0].update(options=[]),
    "dropdown 21 options": lambda t: t["form_fields"][0].update(options=[f"o{i}" for i in range(21)]),
    "dropdown only blank options": lambda t: t["form_fields"][0].update(options=["  ", ""]),
    "text without max_length": lambda t: t["form_fields"][3].update(max_length=None),
    "text max_length too big": lambda t: t["form_fields"][3].update(max_length=5000),
    "time to fix 721": lambda t: t["snag_types"][0].update(time_to_fix_hours=721.0),
    "negative downtime": lambda t: t["job_types"][0].update(est_downtime_hours=-1.0),
    "cost band inverted": lambda t: t["job_types"][0].update(est_cost_band={"low_inr": 10, "high_inr": 5}),
    "cost band too high": lambda t: t["job_types"][0].update(est_cost_band={"low_inr": 0, "high_inr": 10 ** 10}),
    "required not bool": lambda t: t["form_fields"][0].update(required="yes"),
    "invalid domain": lambda t: t["pollutant_capture"][0].update(domain="noise"),
}


# Feature: terravow, Property 70: The template validator accepts exactly the schema-valid, deep, referentially intact, bounded templates
@pytest.mark.parametrize("rule", sorted(FAILING))
def test_property_70_each_rule_rejects(rule):
    t = _mut(FAILING[rule])
    tpl, errs, _ = templates.validate_template(t, "textile")
    assert tpl is None and errs, rule


def test_property_70_size_limit(monkeypatch):
    monkeypatch.setattr(templates, "TEMPLATE_MAX_BYTES", 1000)
    assert any("bytes exceeds" in e for e in errors(base()))


def test_property_70_json_text_input_and_normalisation():
    t = base()
    t["snag_types"][0]["pollution_effects"].append({"domain": "soil", "effect": "dropped, outside textile"})
    t["pollutant_capture"].append({"id": "soil-extra", "label": "x", "type": "text", "required": False,
                                   "max_length": 10, "options": None, "help_text": "", "domain": "soil"})
    t["form_fields"][0]["options"] = ["A" * 70, "  ", "B"]
    t["snag_types"][1]["name"] = "N" * 300
    t["snag_types"][2]["symptoms"].append("zero\u200bwidth\u0007 and   spaces")
    tpl, errs, ntrunc = templates.validate_template(json.dumps(t), "textile")
    assert errs == [] and ntrunc >= 3
    assert all(e["domain"] != "soil" for e in tpl["snag_types"][0]["pollution_effects"])
    assert all(f["id"] != "soil-extra" for f in tpl["pollutant_capture"])
    assert tpl["form_fields"][0]["options"] == ["A" * 59 + "…", "B"]
    assert len(tpl["snag_types"][1]["name"]) == 120 and tpl["snag_types"][1]["name"].endswith("…")
    assert tpl["snag_types"][2]["symptoms"][-1] == "zerowidth and spaces"


def test_property_70_truncation_never_orphans_a_job():
    t = base()
    extra_jobs = [dict(copy.deepcopy(t["job_types"][0]), id=f"zz-job-{i}") for i in range(30)]
    t["job_types"] = extra_jobs + t["job_types"]        # referenced jobs are at the END
    tpl, errs, _ = templates.validate_template(t, "textile")
    assert errs == [] and len(tpl["job_types"]) == 25
    assert {s["recommended_job_id"] for s in tpl["snag_types"]} <= {j["id"] for j in tpl["job_types"]}


BOUNDS = {"name": 120, "label": 120}


def _walk_bounds(node, key=""):
    if isinstance(node, dict):
        for k, v in node.items():
            if isinstance(v, str):
                assert len(v) <= BOUNDS.get(k, 600)
            elif k == "options" and isinstance(v, list):
                assert all(0 < len(o) <= 60 for o in v)
            else:
                _walk_bounds(v, k)
    elif isinstance(node, list):
        assert len(node) <= (15 if key in templates.SHORT_LISTS else 25)
        for x in node:
            if isinstance(x, str):
                assert len(x) <= 240
            else:
                _walk_bounds(x, key)


@settings(max_examples=100, deadline=None)
@given(st.integers(0, 11), st.sampled_from(["name", "detection_method", "immediate_action", "escalation_criteria"]),
       st.integers(0, 2000), st.integers(0, 40), st.integers(0, 400))
def test_property_70_accepted_templates_respect_every_bound(i, field, n, extra_items, item_len):
    t = base()
    t["snag_types"][i][field] = "x" * max(1, n)
    t["snag_types"][i]["symptoms"] += ["s" * max(1, item_len)] * extra_items
    t["job_types"][0]["checklist"] += ["c"] * extra_items
    tpl, errs, _ = templates.validate_template(t, "textile")
    assert errs == []
    _walk_bounds(tpl)


def test_form_schema_core_fields_first():
    fs = templates.build_form_schema(base())
    assert [f["id"] for f in fs[:3]] == ["snag_type", "severity", "description"]
    assert fs[0]["options"][-1] == "other" and len(fs[0]["options"]) == 13
    ids = [f["id"] for f in fs]
    assert len(ids) == len(set(ids))
    assert {f["id"] for f in fs[3:]} == {f["id"] for f in base()["form_fields"] + base()["pollutant_capture"]}
