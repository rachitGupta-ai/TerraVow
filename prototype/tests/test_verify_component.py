"""Same-component verification: a photo of a different assembly, or notes alone, must never verify."""
import io
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient

from app import gemini_client
from app.main import app

client = TestClient(app)


def _jpeg(color=(60, 60, 60)):
    from PIL import Image
    buf = io.BytesIO(); Image.new("RGB", (80, 60), color).save(buf, "JPEG"); return buf.getvalue()


def _health(sev, conf=0.9):
    def f(ctx, notes, image=None, mime_type="image/jpeg"):
        return gemini_client.HealthState(
            component_category="burner", component_name="Burner nozzle", defect="x", severity=sev,
            confidence=conf, evidence=["e"], uncertainty="u", suggested_inspection="i")
    return f


def _after(sev, conf=0.9, same=True, same_conf=0.95, reason="Same housing and bolt layout."):
    def f(ctx, notes, image, mime, before_thumb, prior):
        return gemini_client.AfterAssessment(
            component_category="burner", component_name="Burner nozzle", defect="x", severity=sev,
            confidence=conf, evidence=["e"], uncertainty="u", suggested_inspection="i",
            same_component=same, same_component_confidence=same_conf, same_component_reason=reason)
    return f


def _assess_before(monkeypatch, with_photo=True, sev=4):
    client.post("/api/demo/reset")
    monkeypatch.setattr(gemini_client, "analyze_condition", _health(sev))
    files = {"image": ("b.jpg", _jpeg(), "image/jpeg")} if with_photo else None
    r = client.post("/api/analyze", data={"machine_id": "B-2", "notes": "soot"}, files=files)
    assert r.status_code == 200, r.text


def _verify(files=True, notes="nozzle cleaned"):
    kw = {"files": {"image": ("a.jpg", _jpeg((200, 200, 200)), "image/jpeg")}} if files else {}
    return client.post("/api/verify_improvement", data={"machine_id": "B-2", "notes": notes}, **kw).json()


def test_verified_when_same_assembly_and_severity_drops(monkeypatch):
    _assess_before(monkeypatch)
    monkeypatch.setattr(gemini_client, "reassess_after_repair", _after(1))
    d = _verify()
    assert d["status"] == "VERIFIED" and d["component_check"]["same"] is True
    assert client.get("/api/machine/B-2").json()["health"]["severity"] == 1
    client.post("/api/demo/reset")


def test_different_component_is_never_verified(monkeypatch):
    _assess_before(monkeypatch)
    monkeypatch.setattr(gemini_client, "reassess_after_repair",
                        _after(1, conf=0.99, same=False, same_conf=0.97, reason="This is a filter."))
    d = _verify()
    assert d["status"] == "NOT_VERIFIED" and d["gate"]["rule"] == "different_component"
    assert client.get("/api/machine/B-2").json()["health"]["severity"] == 4
    client.post("/api/demo/reset")


def test_unclear_identity_needs_review(monkeypatch):
    _assess_before(monkeypatch)
    monkeypatch.setattr(gemini_client, "reassess_after_repair", _after(1, same=True, same_conf=0.5))
    d = _verify()
    assert d["status"] == "NEEDS_REVIEW" and d["gate"]["rule"] == "component_unclear"
    client.post("/api/demo/reset")


def test_no_baseline_photo_cannot_verify(monkeypatch):
    _assess_before(monkeypatch, with_photo=False)
    monkeypatch.setattr(gemini_client, "analyze_condition", _health(1, 0.95))
    d = _verify()
    assert d["status"] == "NEEDS_REVIEW" and d["gate"]["rule"] == "no_baseline_photo"
    client.post("/api/demo/reset")


def test_notes_only_after_never_verifies(monkeypatch):
    _assess_before(monkeypatch)
    monkeypatch.setattr(gemini_client, "analyze_condition", _health(1, 0.95))
    d = _verify(files=False, notes="we cleaned it")
    assert d["status"] == "NEEDS_REVIEW" and d["gate"]["rule"] == "photo_required"
    client.post("/api/demo/reset")


def test_no_severity_drop_is_not_verified(monkeypatch):
    _assess_before(monkeypatch)
    monkeypatch.setattr(gemini_client, "reassess_after_repair", _after(4))
    d = _verify()
    assert d["status"] == "NOT_VERIFIED" and d["gate"]["rule"] == "no_improvement"
    client.post("/api/demo/reset")


def test_stored_photo_thumbnail_never_leaks_into_responses(monkeypatch):
    _assess_before(monkeypatch)
    body = client.get("/api/machine/B-2").text + client.get("/api/fleet").text
    assert "ref_image_b64" not in body
    assert client.get("/api/machine/B-2").json()["has_baseline_photo"] is True
    client.post("/api/demo/reset")
