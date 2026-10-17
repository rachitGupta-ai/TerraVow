"""Get-or-generate with "call Gemini at most once per key" (design E5; Req 21.3-21.8, 21.11).

Layers implemented here (slice stage):
 1. double-checked read: the store is read before AND after becoming the flight leader, so a
    caller that read "absent" just before another finished never calls Gemini again;
 2. same-instance single-flight: one leader per key; waiters share the leader's outcome,
    including a fallback, so a failing Gemini is not retried per waiter (Req 21.7);
 3. create-if-absent as the final guard: a loser discards its own result and returns the
    stored document as a cache hit (Req 21.6); a persistence error is reported as
    generated-unsaved, never as cached (Req 21.11); a fallback is never stored (Req 21.8).
The cross-replica lease, request Deadline and waiter cap are added in the hardening task;
`profile` and `deadline` are already part of the signature so callers do not change.
"""
from __future__ import annotations

import logging
import threading
import uuid
from dataclasses import dataclass
from typing import Callable, Dict, Literal, Optional, Tuple

from . import settings
from .store import NotAiDocument

log = logging.getLogger("terravow.cache_once")

# Generated ONCE per process. Never K_REVISION, a service name or a hostname: those repeat
# across replicas and would let one replica release another's lease.
INSTANCE_ID = uuid.uuid4().hex


@dataclass(frozen=True)
class Profile:
    name: str
    max_attempts: int
    attempt_timeout_s: float
    lease_ttl_s: int


TEMPLATE = Profile("template", settings.TEMPLATE_MAX_ATTEMPTS, settings.TEMPLATE_ATTEMPT_TIMEOUT_S,
                   settings.LEASE_TTL_S)
GROUNDED = Profile("grounded", settings.GROUNDED_MAX_ATTEMPTS, settings.GROUNDED_ATTEMPT_TIMEOUT_S,
                   settings.GROUNDED_LEASE_TTL_S)


@dataclass
class GenResult:
    """What a generator returns. It never raises for expected failures."""
    kind: Literal["ok", "definitive-negative", "transient-failure"]
    doc: Optional[dict]
    attempts_used: int


@dataclass
class EnsureOutcome:
    doc: dict
    cache_status: Literal["cache-hit", "generated", "generated-unsaved", "fallback"]


class _Flight:
    def __init__(self) -> None:
        self.event = threading.Event()
        self.outcome: Optional[EnsureOutcome] = None

    def finish(self, outcome: EnsureOutcome) -> EnsureOutcome:
        self.outcome = outcome
        return outcome


_flights: Dict[Tuple[int, str, str], _Flight] = {}
_flights_lock = threading.Lock()


def _join_or_create(key) -> Tuple[_Flight, bool]:
    with _flights_lock:
        f = _flights.get(key)
        if f is not None:
            return f, False
        f = _Flight()
        _flights[key] = f
        return f, True


def ensure_once(store, coll: str, doc_id: str, *, profile: Profile,
                generate: Callable[[int, float], GenResult],
                fallback: Callable[[], dict],
                is_hit: Callable[[dict], bool],
                persist: Literal["create"] = "create",
                deadline=None) -> EnsureOutcome:
    if persist != "create":
        raise NotImplementedError("only persist='create' is implemented in this stage")
    doc = store.get_doc(coll, doc_id)                               # (1) first read
    if doc and is_hit(doc):
        return EnsureOutcome(doc, "cache-hit")
    # The flight key includes the store identity so two Store objects sharing one dict
    # behave like two replicas in tests; production has one store per process.
    key = (id(store), coll, doc_id)
    flight, leader = _join_or_create(key)                           # (2)
    if not leader:
        flight.event.wait(settings.FLIGHT_WAIT_S)
        if flight.outcome is not None:
            return flight.outcome
        doc = store.get_doc(coll, doc_id)                           # timed out: re-read once
        if doc and is_hit(doc):
            return EnsureOutcome(doc, "cache-hit")
        return EnsureOutcome(fallback(), "fallback")                # never starts a Gemini call
    try:
        doc = store.get_doc(coll, doc_id)                           # (3) re-read as leader
        if doc and is_hit(doc):
            return flight.finish(EnsureOutcome(doc, "cache-hit"))
        result = generate(profile.max_attempts, profile.attempt_timeout_s)   # the only Gemini call site
        return flight.finish(_write_and_report(store, coll, doc_id, result, fallback, is_hit))
    except Exception:
        log.exception("ensure_once leader failed for %s/%s", coll, doc_id)
        return flight.finish(EnsureOutcome(fallback(), "fallback"))
    finally:
        with _flights_lock:
            _flights.pop(key, None)
        flight.event.set()


def _write_and_report(store, coll, doc_id, result: GenResult, fallback, is_hit) -> EnsureOutcome:
    if result.kind != "ok" or result.doc is None:
        return EnsureOutcome(fallback(), "fallback")                # nothing written
    try:
        created, stored = store.create_if_absent(coll, doc_id, result.doc)
    except NotAiDocument:
        log.error("refused to cache a non-AI document in %s", coll)
        return EnsureOutcome(fallback(), "fallback")
    except Exception as e:
        log.error("persisting %s/%s failed: %s", coll, doc_id, type(e).__name__)
        return EnsureOutcome(result.doc, "generated-unsaved")
    if created:
        return EnsureOutcome(stored, "generated")
    if stored and is_hit(stored):                                  # another writer won: discard ours
        return EnsureOutcome(stored, "cache-hit")
    return EnsureOutcome(result.doc, "generated-unsaved")
