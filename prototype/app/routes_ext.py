"""Prototype extension routes (design E4). Plain `def` handlers only: they run in FastAPI's
threadpool, so blocking Gemini or Firestore calls never block the event loop."""
from __future__ import annotations

import re
import time
from datetime import date, datetime, timezone
from typing import Literal, Optional

from fastapi import APIRouter, HTTPException, Path, Query
from pydantic import BaseModel, ConfigDict, Field, field_validator

from . import catalogue, industries, seed, snags, sustain, templates
from .settings import ANSWER_MAX_CHARS, CATALOGUE_QUERY_MAX, HISTORY_RESPONSE_MAX

COUNTRY_RE = re.compile(r"^[A-Z]{2}$")


def _country(v: object) -> str:
    """Upper-cased, then exactly two letters A-Z (keeps '/', '__x__' and free text out of ids)."""
    if not isinstance(v, str) or not COUNTRY_RE.fullmatch(v.upper()):
        raise ValueError("country must be a two-letter ISO code (A-Z)")
    return v.upper()

router = APIRouter()


def _store():
    from .main import store  # late import: main includes this router
    return store


def _err422(loc: list, msg: str) -> HTTPException:
    return HTTPException(422, detail=[{"loc": loc, "msg": msg, "type": "value_error"}])


def _check_industry(value: Optional[str], loc: list) -> str:
    if not industries.is_industry(value):
        raise _err422(loc, f"industry must be one of {', '.join(industries.INDUSTRY_IDS)}")
    return value


class MachineIdentity(BaseModel):
    """industry + manufacturer + model, validated per E1 (fullmatch, safe as prompt data)."""
    model_config = ConfigDict(extra="forbid")
    industry: str
    manufacturer: str
    model: str

    @field_validator("industry")
    @classmethod
    def _industry(cls, v: str) -> str:
        if not industries.is_industry(v):
            raise ValueError(f"industry must be one of {', '.join(industries.INDUSTRY_IDS)}")
        return v

    @field_validator("manufacturer", "model")
    @classmethod
    def _name(cls, v: str) -> str:
        # fullmatch on the raw value first, so a trailing newline never passes; then trim
        if not re.fullmatch(catalogue.MANUAL_NAME_PATTERN, v) or not v.strip():
            raise ValueError("1 to 80 characters: letters, digits, spaces and . , & ( ) / + - only")
        return v.strip()


def _key(req: MachineIdentity) -> str:
    try:
        return industries.model_key(req.industry, req.manufacturer, req.model)
    except industries.KeyError422 as e:
        raise _err422(["body", e.field], str(e))


# ---------------------------------------------------------------- industries and catalogue
@router.get("/api/industries")
def list_industries():
    return {"industries": industries.public_view(), "labels": []}


@router.get("/api/catalogue/search")
def catalogue_search(industry: Optional[str] = Query(None),
                     q: str = Query("", max_length=CATALOGUE_QUERY_MAX),
                     limit: int = Query(10)):
    _check_industry(industry, ["query", "industry"])
    rows = catalogue.search(catalogue.entries_for(_store(), industry), industry, q, limit)
    results = [catalogue.result_view(e) for e in rows]
    labels = sorted({"sample-data" if r["origin"] == "sample" else "user-added-unverified" for r in results})
    return {"industry": industry, "query": q.strip(), "results": results,
            "manual_entry_offered": not results, "labels": labels or ["sample-data"],
            "notice": catalogue.NOTICE}


@router.post("/api/catalogue/manual")
def catalogue_manual(req: MachineIdentity):
    _key(req)
    try:
        return catalogue.manual_entry(_store(), req.industry, req.manufacturer, req.model)
    except catalogue.ManualCapReached as e:
        raise HTTPException(429, str(e))


# ---------------------------------------------------------------- templates
@router.post("/api/template/ensure")
def template_ensure(req: MachineIdentity):
    key = _key(req)
    entry = catalogue.get_entry(_store(), key)
    if entry is None:
        raise HTTPException(404, "Select the machine or add it manually first")
    return templates.ensure_template(_store(), entry)


@router.get("/api/template/{key}")
def template_get(key: str = Path(..., max_length=192)):
    doc = templates.get_stored(_store(), key)
    if doc is None:
        raise HTTPException(404, "No saved template for this model.")
    return templates.response(doc, "cache-hit")


# ---------------------------------------------------------------- instances, snags, resolver (E4, E8)
def _today() -> date:
    return datetime.now(timezone.utc).date()  # the only wall-clock read for the health views


def _now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


class Who(BaseModel):
    """Self-declared role and pseudonymous handle. Workflow guidance only, NOT access control."""
    model_config = ConfigDict(extra="forbid")
    handle: str
    role: Literal["REPORTER", "RESOLVER"]

    @field_validator("handle")
    @classmethod
    def _handle(cls, v: str) -> str:
        if not snags.handle_ok(v):
            raise ValueError("handle must be 3 to 32 letters, digits, _ or -")
        return v


class InstanceReq(Who):
    model_key: str = Field(..., max_length=192)
    tag: str
    install_year: Optional[int] = None

    @field_validator("tag")
    @classmethod
    def _tag(cls, v: str) -> str:
        if not (1 <= len(v) <= 40) or not snags.TAG_RE.fullmatch(v) or not v.strip():
            raise ValueError("tag: 1 to 40 letters, digits, spaces, _ . or -")
        return v.strip()

    @field_validator("install_year")
    @classmethod
    def _year(cls, v: Optional[int]) -> Optional[int]:
        if v is not None and not 1950 <= v <= datetime.now(timezone.utc).year:
            raise ValueError("install_year must be null or from 1950 to the current year")
        return v


class SnagReq(Who):
    machine_id: str = Field(..., max_length=80)
    snag_type_id: Optional[str] = Field(None, max_length=80)
    severity: object = None          # validated by snags.validate_answers so every error is listed
    answers: dict = Field(default_factory=dict)
    photo_analysis_ref: Optional[str] = Field(None, max_length=80)


class ResolveReq(Who):
    notes: str = Field("", max_length=ANSWER_MAX_CHARS)
    actions_taken: str = Field("", max_length=ANSWER_MAX_CHARS)
    downtime_hours: float = Field(0, ge=0, le=8760)
    cost_inr: int = Field(0, ge=0, le=10**10)


def _fleet_machine(machine_id: str):
    from .main import get_fleet
    return get_fleet().get(machine_id)


def _header(machine_id: str) -> dict:
    seed.ensure(_store())
    h = snags.get_header(_store(), machine_id)
    if h is None:
        raise HTTPException(404, f"Unknown machine '{machine_id}'")
    return h


@router.post("/api/instances", status_code=201)
def create_instance(req: InstanceReq):
    if req.role != snags.REPORTER:
        raise HTTPException(403, "Only a reporter registers a machine (role switch is workflow guidance).")
    entry = catalogue.get_entry(_store(), req.model_key)
    if entry is None:
        raise HTTPException(404, "Unknown model_key: select the model in the catalogue first")
    try:
        return snags.create_instance(_store(), entry, req.tag, req.install_year, req.handle, _now_iso())
    except snags.TooMany as e:
        raise HTTPException(429, str(e))


@router.get("/api/instances")
def list_instances(model_key: Optional[str] = Query(None, max_length=192)):
    seed.ensure(_store())
    return {"instances": snags.list_instances(_store(), model_key)}


@router.post("/api/snags", status_code=201)
def submit_snag(req: SnagReq):
    if req.role != snags.REPORTER:
        raise HTTPException(403, "Only a reporter submits a snag (role switch is workflow guidance).")
    header = _header(req.machine_id)
    if req.photo_analysis_ref is not None:
        rec = next((a for a in _store().list_audit(100) if a.get("id") == req.photo_analysis_ref), None)
        if rec is None or rec.get("kind") != "condition_assessment" or rec.get("machine_id") != req.machine_id:
            raise HTTPException(422, detail=[{"field": "photo_analysis_ref", "reason": "not an assessment of this machine"}])
    try:
        snag, errs, _ = snags.submit(_store(), header, req.model_dump(), req.handle, _now_iso())
    except snags.TooMany as e:
        raise HTTPException(429, str(e))
    if errs:
        raise HTTPException(422, detail=errs)
    g = snags.resolve_guidance(snag, _store())
    view = snags.health_view(_store(), header, _today(), _fleet_machine(header["machine_id"]))
    return {"snag": snag, "recommendation": g, "health": snags.health_summary(view)}


@router.get("/api/snags")
def list_snags(status: Optional[Literal["OPEN", "ASSIGNED", "RESOLVED", "CLOSED"]] = Query(None),
               machine_id: Optional[str] = Query(None, max_length=80)):
    seed.ensure(_store())
    st = _store()
    if machine_id:
        rows = [s for s in snags.snags_of(st, machine_id) if status is None or s["status"] == status]
    elif status:
        rows = st.query_eq(snags.SNAGS, "status", status)
    else:
        rows = st.list_docs(snags.SNAGS)
    rows.sort(key=lambda s: (s["reported_at"], s["snag_id"]))
    return {"snags": rows[-HISTORY_RESPONSE_MAX:]}


@router.get("/api/resolver/queue")
def resolver_queue():
    seed.ensure(_store())
    return {"queue": snags.queue(_store()), "order": "severity desc, reported_at asc"}


def _act(snag_id: str, action: str, who: Who, resolution: Optional[dict] = None) -> dict:
    try:
        return snags.transition(_store(), snag_id, action, who.role, who.handle, _now_iso(), resolution)
    except KeyError:
        raise HTTPException(404, f"Unknown snag '{snag_id}'")
    except snags.Forbidden as e:
        raise HTTPException(403, str(e))
    except snags.Conflict as e:
        raise HTTPException(409, str(e))


@router.post("/api/snags/{snag_id}/accept")
def accept_snag(req: Who, snag_id: str = Path(..., max_length=80)):
    return {"snag": _act(snag_id, "accept", req)}


@router.post("/api/snags/{snag_id}/resolve")
def resolve_snag(req: ResolveReq, snag_id: str = Path(..., max_length=80)):
    snag = _act(snag_id, "resolve", req, req.model_dump())
    audit = _store().add_audit({"kind": "snag_resolved", "machine_id": snag["machine_id"],
                                "summary": snags.audit_summary(snag, "resolved", req.handle), "verdict": None})
    return {"snag": snag, "audit": audit}


@router.post("/api/snags/{snag_id}/close")
def close_snag(req: Who, snag_id: str = Path(..., max_length=80)):
    snag = _act(snag_id, "close", req)
    audit = _store().add_audit({"kind": "snag_closed", "machine_id": snag["machine_id"],
                                "summary": snags.audit_summary(snag, "closed", req.handle), "verdict": None})
    header = _header(snag["machine_id"])
    view = snags.health_view(_store(), header, _today(), _fleet_machine(header["machine_id"]))
    return {"snag": snag, "audit": audit, "health": snags.health_summary(view)}


@router.get("/api/machines/{machine_id}/history")
def machine_history(machine_id: str = Path(..., max_length=80)):
    header = _header(machine_id)
    rows = snags.snags_of(_store(), machine_id)
    return {"header": header, "snags": rows[-HISTORY_RESPONSE_MAX:], "synthetic": bool(header.get("synthetic")),
            "truncated": len(rows) > HISTORY_RESPONSE_MAX}


@router.get("/api/machines/{machine_id}/health")
def machine_health(machine_id: str = Path(..., max_length=80), as_of: Optional[str] = Query(None, max_length=10)):
    header = _header(machine_id)
    today = _today()
    if as_of is None:
        d = today
    else:
        try:
            d = date.fromisoformat(as_of)
        except ValueError:
            raise _err422(["query", "as_of"], "as_of must be YYYY-MM-DD")
        if not date(1990, 1, 1) <= d <= today:
            raise _err422(["query", "as_of"], "as_of must be from 1990-01-01 to today")
    return snags.health_view(_store(), header, d, _fleet_machine(machine_id))


# ---------------------------------------------------------------- sustainability and incentives (E4, Req 26-27)
class RecommendReq(BaseModel):
    model_config = ConfigDict(extra="forbid")
    enterprise_size: Optional[Literal["MSME", "LARGE"]] = None    # null or absent: both sizes
    country: str = "IN"

    @field_validator("country", mode="before")
    @classmethod
    def _c(cls, v: object) -> str:
        return _country(v)


@router.post("/api/machines/{machine_id}/recommendations")
def machine_recommendations(req: Optional[RecommendReq] = None, machine_id: str = Path(..., max_length=80)):
    req = req or RecommendReq()
    header = _header(machine_id)
    entry = catalogue.get_entry(_store(), header["model_key"])
    body = sustain.recommend(_store(), header, entry, _fleet_machine(machine_id), _today(),
                             req.enterprise_size, req.country)
    try:   # one audit entry BEFORE returning; no output without it (Audit Durability, Req 28.3)
        audit = _store().add_audit({"kind": "sustainability_recommendation", "machine_id": machine_id,
                                    "summary": sustain.audit_summary(body), "verdict": None,
                                    "source": body["source"], "model": None})
    except Exception as e:
        import logging
        logging.getLogger("terravow").error("audit append failed: %s", type(e).__name__)
        raise HTTPException(503, "Audit trail unavailable; the recommendation was not returned.")
    return {**body, "audit_id": audit["id"]}


@router.get("/api/incentives")
def list_incentives(country: str = Query("IN", max_length=8), industry: Optional[str] = Query(None),
                    enterprise_size: Optional[Literal["MSME", "LARGE"]] = Query(None)):
    try:
        c = _country(country)
    except ValueError as e:
        raise _err422(["query", "country"], str(e))
    _check_industry(industry, ["query", "industry"])
    return sustain.incentives_response(_store(), c, industry, enterprise_size)
