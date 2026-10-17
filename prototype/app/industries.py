"""Industry categories, pollution domains and the Machine_Model_Key (design E1, E2; Req 18, 21.1).

The four industries live in data/industries.json. A self-check runs at import so a broken
configuration stops the service instead of silently mis-tagging machines.
"""
from __future__ import annotations

import json
import os
import re
import unicodedata
from typing import Dict, List, Optional

from . import settings

DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data")

DOMAINS = ("air", "water", "chemical", "soil")
DOMAIN_INDICATORS: Dict[str, List[str]] = {
    "air": ["co2e_kg", "nox_kg"],
    "water": ["cod_kg", "bod_kg", "effluent_m3"],
    "chemical": ["chem_release_kg"],
    "soil": ["solid_waste_kg", "soil_contam_index"],
}
INDICATOR_UNITS = {
    "co2e_kg": "kg CO2e/day", "nox_kg": "kg NOx/day", "cod_kg": "kg COD/day", "bod_kg": "kg BOD/day",
    "effluent_m3": "m3 effluent/day", "chem_release_kg": "kg hazardous-chemical release/day",
    "solid_waste_kg": "kg solid waste/day", "soil_contam_index": "soil-contamination index points/day",
}


def _load() -> List[dict]:
    with open(os.path.join(DATA_DIR, "industries.json"), "r", encoding="utf-8") as f:
        rows = json.load(f)["industries"]
    return sorted(rows, key=lambda r: r["id"])


INDUSTRIES: List[dict] = _load()
INDUSTRY_BY_ID: Dict[str, dict] = {r["id"]: r for r in INDUSTRIES}
INDUSTRY_IDS = tuple(INDUSTRY_BY_ID)


def is_industry(value: object) -> bool:
    return isinstance(value, str) and value in INDUSTRY_BY_ID


def domains_of(industry: str) -> List[str]:
    return list(INDUSTRY_BY_ID[industry]["domains"])


def public_view() -> List[dict]:
    return [{"id": r["id"], "name": r["name"], "domains": list(r["domains"])} for r in INDUSTRIES]


# ---------------------------------------------------------------- normalisation
def _strip_marks(s: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))


def fold(s: str) -> str:
    """Case- and accent-insensitive form used by catalogue search (E1 step 3)."""
    return _strip_marks(s or "").casefold()


def slug(s: str) -> str:
    """NFKD-normalise, drop non-ASCII, lower-case, runs of [^a-z0-9] -> '-', strip '-'."""
    a = _strip_marks(s or "").encode("ascii", "ignore").decode("ascii").lower()
    return re.sub(r"[^a-z0-9]+", "-", a).strip("-")


class KeyError422(ValueError):
    """A key part slugs to the empty string; carries the offending field name."""

    def __init__(self, field: str):
        super().__init__(f"{field} must contain letters or digits")
        self.field = field


def model_key(industry: str, manufacturer: str, model: str) -> str:
    parts = {"industry": industry, "manufacturer": manufacturer, "model": model}
    slugs = []
    for name, value in parts.items():
        s = slug(value)
        if not s:
            raise KeyError422(name)
        slugs.append(s)
    return "--".join(slugs)


def template_id(key: str, version: Optional[str] = None) -> str:
    """key + current TEMPLATE_SCHEMA_VERSION (read at call time): a new version is a new id."""
    return f"{key}--{version or settings.TEMPLATE_SCHEMA_VERSION}"


# ---------------------------------------------------------------- self-check
def self_check() -> None:
    ids = [r["id"] for r in INDUSTRIES]
    if len(ids) != 4 or len(set(ids)) != 4:
        raise RuntimeError(f"industries.json must define exactly four industries, found {ids}")
    covered = set()
    for r in INDUSTRIES:
        bad = [d for d in r["domains"] if d not in DOMAINS]
        if bad:
            raise RuntimeError(f"industry {r['id']} has unknown domains {bad}")
        covered.update(r["domains"])
        for d in r["domains"]:
            if not any(r["default_baselines"].get(i) is not None for i in DOMAIN_INDICATORS[d]):
                raise RuntimeError(f"industry {r['id']} has no baseline for domain {d}")
    missing = set(DOMAINS) - covered
    if missing:
        raise RuntimeError(f"pollution domains {sorted(missing)} are not covered by any industry")


self_check()
