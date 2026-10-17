"""Client for the internal TerraVow Java engine.

In the single Cloud Run container the Python FastAPI app is the public front door and the
Java Spring Boot app runs alongside it on a private localhost port (default 8081). This module
is the one place that talks to that engine, so the rest of the Python app depends only on a
small, typed surface rather than on HTTP details.

The base URL is configurable via JAVA_ENGINE_URL (default http://127.0.0.1:8081); calls are
short-timeout and never raise to the caller — on any failure they return a structured
{"available": False, "error": ...} so the front door degrades gracefully if the engine is down.
"""
from __future__ import annotations

import os
from typing import Optional

import httpx

BASE_URL = os.getenv("JAVA_ENGINE_URL", "http://127.0.0.1:8081")
TIMEOUT_SECONDS = float(os.getenv("JAVA_ENGINE_TIMEOUT", "3.0"))


def _client() -> httpx.Client:
    return httpx.Client(base_url=BASE_URL, timeout=TIMEOUT_SECONDS)


def health() -> dict:
    """Return the Java engine's health document, or an unavailable marker on failure."""
    try:
        with _client() as c:
            r = c.get("/api/health")
            r.raise_for_status()
            return {"available": True, **r.json()}
    except Exception as e:  # never leak internals; keep the front door resilient
        return {"available": False, "error": f"{type(e).__name__}: {str(e)[:200]}"}


def evaluate_gate(deltas_kg: dict, confidence: Optional[float], data_trust: float = 1.0) -> dict:
    """Delegate a Decision_Gate classification to the verified Java engine.

    Returns the engine's decision document, or {"available": False, "error": ...} if the engine
    could not be reached (the caller may then fall back to the local Python gate)."""
    payload = {"deltasKg": deltas_kg, "confidence": confidence, "dataTrust": data_trust}
    try:
        with _client() as c:
            r = c.post("/api/gate/evaluate", json=payload)
            r.raise_for_status()
            return {"available": True, **r.json()}
    except Exception as e:
        return {"available": False, "error": f"{type(e).__name__}: {str(e)[:200]}"}
