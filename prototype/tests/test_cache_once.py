"""Template cache-once, Gemini template generation path, and manual entry (Req 19.6-19.10, 20, 21)."""
import json
import os
import sys
import threading
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from fastapi.testclient import TestClient
from hypothesis import HealthCheck, given, settings as hsettings, strategies as st

from app import catalogue, gemini_client, settings, templates
from app.main import app, store
from app.store import Store

client = TestClient(app)
THIES = {"industry": "textile", "manufacturer": "Thies", "model": "iMaster"}
KEY = "textile--thies--imaster"


class FakeGemini:
    """Call-counting stand-in for gemini_client.generate_template."""

    def __init__(self, mode="ok", delay=0.0):
        self.calls = 0
        self.mode = mode
        self.delay = delay
        self._lock = threading.Lock()

    def __call__(self, data, *, timeout_s, hint=None):
        with self._lock:
            self.calls += 1
        if self.delay:
            time.sleep(self.delay)
        assert set(data) == {"machine", "pollution_domains", "specs", "spec_sources", "base_template", "requirements"}
        assert set(data["machine"]) == {"industry", "manufacturer", "model", "machine_type"}
        if self.mode == "error":
            raise RuntimeError("boom")
        if self.mode == "not-configured":
            raise gemini_client.GeminiNotConfigured("no key")
        if self.mode == "invalid":
            return json.dumps({"machine_profile": {}}), {"structured_output": "schema", "truncated": False}
        if self.mode == "truncated":
            return "", {"structured_output": "schema", "truncated": True}
        industry = "textile" if data["machine"]["industry"].startswith("Textile") else None
        t = templates.BASES[industry or "textile"]["template"]
        return json.dumps(t), {"structured_output": "schema", "truncated": False, "output_tokens": 1}


@pytest.fixture(autouse=True)
def clean(monkeypatch):
    for k in ("GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_GENAI_USE_VERTEXAI"):
        monkeypatch.delenv(k, raising=False)
    store._docs.pop("templates", None)
    store._docs.pop("catalogue", None)
    yield
    store._docs.pop("templates", None)
    store._docs.pop("catalogue", None)


def entry():
    return catalogue.get_entry(store, KEY)


# Feature: terravow, Property 67: A model's template is generated once and then served from the cache
@hsettings(max_examples=100, deadline=None, suppress_health_check=[HealthCheck.function_scoped_fixture])
@given(st.integers(1, 6), st.sampled_from([e["id"] for e in catalogue.SEED_ENTRIES if e["industry"] == "textile"]))
def test_property_67_generated_once_then_cache_hit(n, key):
    s = Store(docs={})
    fake = FakeGemini()
    orig = gemini_client.generate_template
    gemini_client.generate_template = fake
    try:
        assert templates.get_stored(s, key) is None
        e = catalogue.get_entry(s, key)
        outs = [templates.ensure_template(s, e) for _ in range(n)]
    finally:
        gemini_client.generate_template = orig
    assert fake.calls == 1
    assert [o["cache_status"] for o in outs] == ["generated"] + ["cache-hit"] * (n - 1)
    assert all(o["template"] == outs[0]["template"] and o["source"] == "ai" for o in outs)
    doc = templates.get_stored(s, key)
    for k in ("generated_at", "model_used", "schema_version", "grounding_sources"):
        assert k in doc and doc[k] is not None
    assert doc["source"] == "ai" and doc["schema_version"] == "t1" and doc["disclaimer"] == templates.DISCLAIMER
    assert len(s._docs["templates"]) == 1


def test_ensure_and_get_endpoints_with_fake_gemini(monkeypatch):
    fake = FakeGemini()
    monkeypatch.setattr(gemini_client, "generate_template", fake)
    assert client.get(f"/api/template/{KEY}").status_code == 404
    r1 = client.post("/api/template/ensure", json=THIES).json()
    r2 = client.post("/api/template/ensure", json=THIES).json()
    assert (r1["cache_status"], r2["cache_status"], fake.calls) == ("generated", "cache-hit", 1)
    assert r1["user_message"] == templates.USER_MESSAGES["generated"]
    assert r1["labels"] == ["AI-generated"] and r1["form_schema"][0]["id"] == "snag_type"
    g = client.get(f"/api/template/{KEY}")
    assert g.status_code == 200 and g.json()["template"] == r1["template"] and fake.calls == 1
    assert client.get("/api/template/" + "x" * 193).status_code == 422


def test_get_never_calls_gemini(monkeypatch):
    def boom(*a, **k):
        raise AssertionError("GET must never call Gemini")
    monkeypatch.setattr(gemini_client, "generate_template", boom)
    monkeypatch.setattr(gemini_client, "lookup_specs", boom)
    assert client.get(f"/api/template/{KEY}").status_code == 404


def test_ensure_requires_catalogue_entry():
    r = client.post("/api/template/ensure", json={"industry": "textile", "manufacturer": "Nobody", "model": "Z9"})
    assert r.status_code == 404 and r.json()["detail"] == "Select the machine or add it manually first"


# Feature: terravow, Property 69: A fallback-base template is never cached as AI-generated
@pytest.mark.parametrize("mode", ["not-configured", "error", "invalid", "truncated"])
def test_property_69_failure_falls_back_and_stores_nothing(monkeypatch, mode):
    bad = FakeGemini(mode)
    monkeypatch.setattr(gemini_client, "generate_template", bad)
    r = client.post("/api/template/ensure", json=THIES).json()
    assert r["cache_status"] == "fallback" and r["source"] == "fallback-base"
    assert r["user_message"] == templates.USER_MESSAGES["fallback"]
    assert "not-generated-for-this-model" in r["labels"]
    assert "Not generated for this model" in r["template"]["machine_profile"]["summary"]
    assert not store._docs.get("templates")
    assert bad.calls == (1 if mode == "not-configured" else settings.TEMPLATE_MAX_ATTEMPTS)
    good = FakeGemini()
    monkeypatch.setattr(gemini_client, "generate_template", good)
    r = client.post("/api/template/ensure", json=THIES).json()
    assert r["cache_status"] == "generated" and good.calls == 1 and len(store._docs["templates"]) == 1


def test_property_69_no_key_end_to_end_fallback():
    r = client.post("/api/template/ensure", json=THIES)
    assert r.status_code == 200 and r.json()["cache_status"] == "fallback"
    assert client.get(f"/api/template/{KEY}").status_code == 404


def test_property_69_persistence_failure_is_generated_unsaved(monkeypatch):
    fake = FakeGemini()
    monkeypatch.setattr(gemini_client, "generate_template", fake)
    s = Store(docs={})
    e = catalogue.get_entry(s, KEY)

    def broken(*a, **k):
        raise RuntimeError("firestore down")
    monkeypatch.setattr(s, "create_if_absent", broken)
    r = templates.ensure_template(s, e)
    assert r["cache_status"] == "generated-unsaved" and r["source"] == "ai"
    assert r["user_message"] == templates.USER_MESSAGES["generated-unsaved"]
    assert templates.ensure_template(s, e)["cache_status"] == "generated-unsaved" and fake.calls == 2


def test_schema_version_change_treats_old_doc_as_absent(monkeypatch):
    fake = FakeGemini()
    monkeypatch.setattr(gemini_client, "generate_template", fake)
    assert client.post("/api/template/ensure", json=THIES).json()["cache_status"] == "generated"
    monkeypatch.setattr(settings, "TEMPLATE_SCHEMA_VERSION", "t2")
    assert client.get(f"/api/template/{KEY}").status_code == 404
    r = client.post("/api/template/ensure", json=THIES).json()
    assert r["cache_status"] == "generated" and r["template_id"] == KEY + "--t2" and fake.calls == 2


# Feature: terravow, Property 68: Concurrent ensure calls generate once and store one document
def test_property_68_same_instance_concurrency(monkeypatch):
    fake = FakeGemini(delay=0.05)
    monkeypatch.setattr(gemini_client, "generate_template", fake)
    s = Store(docs={})
    e = catalogue.get_entry(s, KEY)
    barrier, outs = threading.Barrier(8), []

    def run():
        barrier.wait()
        outs.append(templates.ensure_template(s, e))
    threads = [threading.Thread(target=run) for _ in range(8)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert fake.calls == 1 and len(s._docs["templates"]) == 1
    stored = s.get_doc("templates", KEY + "--t1")["template"]
    assert all(o["template"] == stored for o in outs)


def test_property_68_two_replicas_share_one_dict(monkeypatch):
    fake = FakeGemini(delay=0.05)
    monkeypatch.setattr(gemini_client, "generate_template", fake)
    shared = {}
    replicas = [Store(docs=shared), Store(docs=shared)]
    e = catalogue.get_entry(replicas[0], KEY)
    barrier, outs = threading.Barrier(6), []

    def run(s):
        barrier.wait()
        outs.append(templates.ensure_template(s, e))
    threads = [threading.Thread(target=run, args=(replicas[i % 2],)) for i in range(6)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert len(shared["templates"]) == 1
    stored = shared["templates"][KEY + "--t1"]
    assert all(o["template"] == stored["template"] for o in outs)
    assert {o["cache_status"] for o in outs} <= {"generated", "cache-hit"}
    assert fake.calls <= 2        # one per simulated replica until the lease lands (hardening task)


# ---------------------------------------------------------------- manual entry outcomes (E1 table)
class FakeLookup:
    def __init__(self, kind):
        self.kind, self.calls = kind, 0

    def __call__(self, industry_name, manufacturer, model, *, timeout_s, max_attempts=2):
        self.calls += 1
        if self.kind == "ok":
            return {"kind": "ok", "machine_type": "Stenter", "caveat": "",
                    "specs": [{"name": "Working width", "value": "2.2", "unit": "m"}],
                    "sources": [{"uri": "https://example.com/x", "title": "example.com"}], "reason": "ok"}
        if self.kind == "definitive-negative":
            return {"kind": "definitive-negative", "machine_type": "", "specs": [], "caveat": "",
                    "sources": [], "reason": "not-found"}
        return {"kind": "transient-failure", "machine_type": "", "specs": [], "caveat": "", "sources": [],
                "reason": "error"}


NEW = {"industry": "textile", "manufacturer": "Acme Looms", "model": "AL-9 (Mk II)"}


def test_manual_entry_ok_is_cached_and_selectable(monkeypatch):
    fake = FakeLookup("ok")
    monkeypatch.setattr(gemini_client, "lookup_specs", fake)
    r = client.post("/api/catalogue/manual", json=NEW).json()
    assert r["created"] is True and r["lookup_performed"] is True
    assert r["entry"]["spec_status"] == "grounded" and r["entry"]["spec_lookup"] == "ok"
    assert r["entry"]["sources"][0]["uri"].startswith("https://")
    assert r["message"] == catalogue.MESSAGES["ok"] and "unverified" in r["labels"]
    r2 = client.post("/api/catalogue/manual", json=NEW).json()
    assert r2["lookup_performed"] is False and r2["created"] is False and fake.calls == 1
    found = client.get("/api/catalogue/search", params={"industry": "textile", "q": "acme"}).json()
    assert found["results"][0]["label"] == "User-added, unverified"
    assert client.post("/api/template/ensure", json=NEW).json()["cache_status"] == "fallback"


def test_manual_entry_not_found_is_a_hit_within_window(monkeypatch):
    fake = FakeLookup("definitive-negative")
    monkeypatch.setattr(gemini_client, "lookup_specs", fake)
    r = client.post("/api/catalogue/manual", json=NEW).json()
    assert r["entry"]["spec_lookup"] == "not-found" and r["entry"]["specs"] == []
    assert r["message"] == catalogue.MESSAGES["not-found"]
    assert client.post("/api/catalogue/manual", json=NEW).json()["lookup_performed"] is False
    assert fake.calls == 1


def test_manual_entry_retry_is_never_a_hit(monkeypatch):
    fake = FakeLookup("transient-failure")
    monkeypatch.setattr(gemini_client, "lookup_specs", fake)
    r = client.post("/api/catalogue/manual", json=NEW).json()
    assert r["entry"]["spec_lookup"] == "retry" and r["entry"]["specs"] == [] and r["created"] is True
    assert r["message"] == catalogue.MESSAGES["retry"]
    r2 = client.post("/api/catalogue/manual", json=NEW).json()
    assert r2["lookup_performed"] is True and r2["created"] is False and fake.calls == 2
    ok = FakeLookup("ok")
    monkeypatch.setattr(gemini_client, "lookup_specs", ok)
    assert client.post("/api/catalogue/manual", json=NEW).json()["entry"]["spec_status"] == "grounded"


def test_manual_entry_without_gemini_key_is_retry():
    r = client.post("/api/catalogue/manual", json=NEW)
    assert r.status_code == 200 and r.json()["entry"]["spec_lookup"] == "retry"


def test_manual_entry_validation_and_cap(monkeypatch):
    for bad in ({**NEW, "manufacturer": "Acme<script>"}, {**NEW, "model": "x\n"}, {**NEW, "model": "O'Neil"},
                {**NEW, "manufacturer": ""}, {**NEW, "model": "a" * 81}, {**NEW, "extra": 1}):
        assert client.post("/api/catalogue/manual", json=bad).status_code == 422, bad
    r = client.post("/api/catalogue/manual", json={**NEW, "manufacturer": "---"})
    assert r.status_code == 422 and "manufacturer" in str(r.json()["detail"])
    monkeypatch.setattr(gemini_client, "lookup_specs", FakeLookup("transient-failure"))
    client.post("/api/catalogue/manual", json=NEW)
    monkeypatch.setattr(catalogue, "MANUAL_ENTRY_MAX", 1)
    assert client.post("/api/catalogue/manual", json={**NEW, "model": "Other"}).status_code == 429
    assert client.post("/api/catalogue/manual", json=NEW).status_code == 200   # existing entry unaffected


def test_extract_sources_keeps_only_http_and_dedupes():
    class W:
        def __init__(self, uri, title):
            self.web = type("web", (), {"uri": uri, "title": title})()
    gm = type("gm", (), {"grounding_chunks": [W("https://a.example/x", "a"), W("javascript:alert(1)", "bad"),
                                               W("https://a.example/x", "dup"), W("http://b.example", "b")]})()
    resp = type("r", (), {"candidates": [type("c", (), {"grounding_metadata": gm})()]})()
    assert gemini_client.extract_sources(resp) == [{"uri": "https://a.example/x", "title": "a"},
                                                   {"uri": "http://b.example", "title": "b"}]
