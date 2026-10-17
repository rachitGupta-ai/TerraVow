"""Persistence: Firestore (default database) with an in-memory fallback for local dev.

The active backend is exposed via `backend` and shown in the UI, so a fallback can
never pass as the real thing. Audit entries are hash-chained (each entry stores the
hash of the previous one), which makes after-the-fact edits detectable.
"""
from __future__ import annotations

import copy
import hashlib
import json
import os
import threading
import time
import uuid
from typing import Callable, Dict, List, Optional, Tuple

AUDIT = "audit"
HEALTH = "health_overrides"

# Collections whose create_if_absent only ever stores AI output (Req 21.8 defence in depth).
AI_ONLY_COLLECTIONS = ("templates", "alternatives")

# Memory backends that share one documents dict (two simulated replicas) also share one lock,
# so create_if_absent stays atomic across them.
_SHARED_LOCKS: Dict[int, threading.Lock] = {}
_SHARED_LOCKS_GUARD = threading.Lock()


def _shared_lock(docs: dict) -> threading.Lock:
    with _SHARED_LOCKS_GUARD:
        return _SHARED_LOCKS.setdefault(id(docs), threading.Lock())


def _hash(prev: str, payload: dict) -> str:
    body = json.dumps(payload, sort_keys=True, separators=(",", ":"), default=str)
    return hashlib.sha256((prev + body).encode("utf-8")).hexdigest()


class NotAiDocument(ValueError):
    """Raised when a non-AI document is offered to an AI-only cache collection."""


class Store:
    def __init__(self, docs: Optional[Dict[str, Dict[str, dict]]] = None) -> None:
        self._lock = threading.Lock()
        self._audit: List[dict] = []
        self._health: Dict[str, dict] = {}
        # Document API (design E3). An injected dict forces the memory backend and lets two
        # Store objects share state, which is how tests simulate two replicas.
        self._docs: Dict[str, Dict[str, dict]] = docs if docs is not None else {}
        self._doc_lock = _shared_lock(self._docs) if docs is not None else threading.Lock()
        self._fs = None
        self.backend = "memory"
        self.backend_error: Optional[str] = None
        if docs is None and os.getenv("USE_FIRESTORE", "false").lower() in ("1", "true", "yes"):
            try:
                from google.cloud import firestore  # imported lazily

                self._fs = firestore.Client(project=os.getenv("GOOGLE_CLOUD_PROJECT") or None)
                self.backend = "firestore"
            except Exception as e:  # surfaced, not hidden
                self.backend_error = f"{type(e).__name__}: {e}"

    # ---- audit -----------------------------------------------------------
    def add_audit(self, entry: dict) -> dict:
        with self._lock:
            last = self._latest_audit()
            prev = last["hash"] if last else "GENESIS"
            record = {
                "id": uuid.uuid4().hex[:12],
                "seq": time.time_ns(),
                "ts": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime()) + " UTC",
                **entry,
                "prev_hash": prev,
            }
            record["hash"] = _hash(prev, {k: v for k, v in record.items() if k != "hash"})
            if self._fs is not None:
                self._fs.collection(AUDIT).document(record["id"]).set(record)
            else:
                self._audit.append(record)
            return record

    def list_audit(self, limit: int = 20) -> List[dict]:
        if self._fs is not None:
            from google.cloud import firestore

            docs = (self._fs.collection(AUDIT)
                    .order_by("seq", direction=firestore.Query.DESCENDING)
                    .limit(limit).stream())
            return [d.to_dict() for d in docs]
        return list(reversed(self._audit))[:limit]

    def _latest_audit(self) -> Optional[dict]:
        rows = self.list_audit(1)
        return rows[0] if rows else None

    def verify_chain(self, limit: int = 200) -> dict:
        rows = list(reversed(self.list_audit(limit)))  # oldest first
        ok = True
        for i, r in enumerate(rows):
            expected_prev = rows[i - 1]["hash"] if i > 0 else r["prev_hash"]
            recomputed = _hash(r["prev_hash"], {k: v for k, v in r.items() if k != "hash"})
            if r["prev_hash"] != expected_prev or recomputed != r["hash"]:
                ok = False
                break
        return {"entries_checked": len(rows), "chain_intact": ok}

    # ---- live health overrides (so a Gemini analysis re-ranks the fleet) ---
    def save_health(self, machine_id: str, health: dict) -> None:
        if self._fs is not None:
            self._fs.collection(HEALTH).document(machine_id).set(health)
        else:
            self._health[machine_id] = health

    def load_health(self) -> Dict[str, dict]:
        if self._fs is not None:
            return {d.id: d.to_dict() for d in self._fs.collection(HEALTH).stream()}
        return dict(self._health)

    def clear_health(self) -> None:
        if self._fs is not None:
            for d in self._fs.collection(HEALTH).stream():
                d.reference.delete()
        else:
            self._health.clear()

    # ---- document API (design E3) -----------------------------------------
    # Memory: every method holds the documents lock, so each call is atomic. Firestore:
    # create() for create-if-absent, transactions for read-modify-write. Documents are
    # deep-copied in and out so callers can never mutate stored state by accident.
    def _ref(self, coll: str, doc_id: str):
        return self._fs.collection(coll).document(doc_id)

    def get_doc(self, coll: str, doc_id: str) -> Optional[dict]:
        if self._fs is not None:
            snap = self._ref(coll, doc_id).get()
            return snap.to_dict() if snap.exists else None
        with self._doc_lock:
            d = self._docs.get(coll, {}).get(doc_id)
            return copy.deepcopy(d) if d is not None else None

    def create_if_absent(self, coll: str, doc_id: str, data: dict) -> Tuple[bool, dict]:
        """Write `data` only if no document exists. Returns (created, stored_doc)."""
        if coll in AI_ONLY_COLLECTIONS and data.get("source") != "ai":
            raise NotAiDocument(f"{coll} only stores source='ai' documents")
        if self._fs is not None:
            from google.api_core.exceptions import AlreadyExists

            ref = self._ref(coll, doc_id)
            try:
                ref.create(data)
                return True, copy.deepcopy(data)
            except AlreadyExists:
                return False, ref.get().to_dict()
        with self._doc_lock:
            bucket = self._docs.setdefault(coll, {})
            if doc_id in bucket:
                return False, copy.deepcopy(bucket[doc_id])
            bucket[doc_id] = copy.deepcopy(data)
            return True, copy.deepcopy(data)

    def put_doc(self, coll: str, doc_id: str, data: dict) -> None:
        if self._fs is not None:
            self._ref(coll, doc_id).set(data)
            return
        with self._doc_lock:
            self._docs.setdefault(coll, {})[doc_id] = copy.deepcopy(data)

    def update_doc(self, coll: str, doc_id: str, fn: Callable[[dict], dict]) -> dict:
        """Atomic read-modify-write. Raises KeyError if the document does not exist."""
        if self._fs is not None:
            from google.cloud import firestore

            ref = self._ref(coll, doc_id)

            @firestore.transactional
            def _tx(tx):
                snap = ref.get(transaction=tx)
                if not snap.exists:
                    raise KeyError(doc_id)
                new = fn(snap.to_dict())
                tx.set(ref, new)
                return new

            return _tx(self._fs.transaction())
        with self._doc_lock:
            bucket = self._docs.setdefault(coll, {})
            if doc_id not in bucket:
                raise KeyError(doc_id)
            new = fn(copy.deepcopy(bucket[doc_id]))
            bucket[doc_id] = copy.deepcopy(new)
            return copy.deepcopy(new)

    def replace_if(self, coll: str, doc_id: str, predicate: Callable[[Optional[dict]], bool],
                   data: dict) -> bool:
        """Transactional conditional overwrite: writes only if predicate(current) is true."""
        if self._fs is not None:
            from google.cloud import firestore

            ref = self._ref(coll, doc_id)

            @firestore.transactional
            def _tx(tx):
                snap = ref.get(transaction=tx)
                if not predicate(snap.to_dict() if snap.exists else None):
                    return False
                tx.set(ref, data)
                return True

            return _tx(self._fs.transaction())
        with self._doc_lock:
            bucket = self._docs.setdefault(coll, {})
            cur = bucket.get(doc_id)
            if not predicate(copy.deepcopy(cur) if cur is not None else None):
                return False
            bucket[doc_id] = copy.deepcopy(data)
            return True

    def query_eq(self, coll: str, field: str, value) -> List[dict]:
        if self._fs is not None:
            from google.cloud.firestore_v1.base_query import FieldFilter

            q = self._fs.collection(coll).where(filter=FieldFilter(field, "==", value))
            return [d.to_dict() for d in q.stream()]
        with self._doc_lock:
            return [copy.deepcopy(d) for d in self._docs.get(coll, {}).values() if d.get(field) == value]

    def query_in(self, coll: str, field: str, values: list) -> List[dict]:
        values = list(values)
        if not values:
            return []
        if self._fs is not None:
            from google.cloud.firestore_v1.base_query import FieldFilter

            out: List[dict] = []
            for i in range(0, len(values), 30):  # Firestore 'in' accepts at most 30 values
                q = self._fs.collection(coll).where(filter=FieldFilter(field, "in", values[i:i + 30]))
                out.extend(d.to_dict() for d in q.stream())
            return out
        with self._doc_lock:
            return [copy.deepcopy(d) for d in self._docs.get(coll, {}).values() if d.get(field) in values]

    def list_docs(self, coll: str) -> List[dict]:
        """Every document of a small collection (catalogue, industries-sized data only)."""
        if self._fs is not None:
            return [d.to_dict() for d in self._fs.collection(coll).stream()]
        with self._doc_lock:
            return [copy.deepcopy(d) for d in self._docs.get(coll, {}).values()]

    def delete_doc(self, coll: str, doc_id: str) -> None:
        if self._fs is not None:
            self._ref(coll, doc_id).delete()
            return
        with self._doc_lock:
            self._docs.get(coll, {}).pop(doc_id, None)
