"""Named configuration constants for the prototype extension (design E3, Req A5).

Every value can be overridden by an environment variable of the same name. The lease
TTLs and the request deadline are DERIVED from the timeouts and are never set as literals,
so a tuned timeout can never leave a lease shorter than its own worst-case generation.
"""
from __future__ import annotations

import os


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def _float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        return default


def _bool(name: str, default: bool) -> bool:
    return os.getenv(name, str(default)).strip().lower() in ("1", "true", "yes", "on")


# When true, the three slow/heavy Gemini paths (snag-template generation, grounded spec lookup,
# grounded sustainability alternatives) skip the model call and serve their instant fallback
# (base template / curated list). A pre-generated template already in the store is still served
# as a cache-hit. The core photo/explain/verify Gemini calls are unaffected. Use for demos or
# when grounding/thinking is unavailable on the configured Vertex endpoint.
GEMINI_HEAVY_DISABLED = _bool("GEMINI_HEAVY_DISABLED", False)

# When true, the photo assessment, after-repair re-assessment and the explain call cache their
# result in memory keyed on their exact inputs (image bytes + notes + context). Repeating the same
# inputs - e.g. the fixed demo sample photos - returns the real, previously generated Gemini result
# instantly instead of making the model call again. A new/different photo still runs live.
GEMINI_CACHE = _bool("GEMINI_CACHE", False)


# ---- template generation (provisional until T-MEASURE) ---------------------------------
TEMPLATE_SCHEMA_VERSION = "t1"
TEMPLATE_MAX_ATTEMPTS = max(1, min(3, _int("TEMPLATE_MAX_ATTEMPTS", 2)))
TEMPLATE_ATTEMPT_TIMEOUT_S = _float("TEMPLATE_ATTEMPT_TIMEOUT_S", 120)
TEMPLATE_MAX_OUTPUT_TOKENS = _int("TEMPLATE_MAX_OUTPUT_TOKENS", 32768)
TEMPLATE_MAX_BYTES = _int("TEMPLATE_MAX_BYTES", 204800)
TEMPLATE_MIN_JOBS = _int("TEMPLATE_MIN_JOBS", 6)
TEMPLATE_MIN_SNAGS = _int("TEMPLATE_MIN_SNAGS", 12)
TEMPLATE_MIN_PARAMS = _int("TEMPLATE_MIN_PARAMS", 8)
TEMPLATE_MIN_FORM_FIELDS = _int("TEMPLATE_MIN_FORM_FIELDS", 4)

# ---- grounded calls (spec lookup, alternatives, incentive discovery) ------------------
GROUNDED_ATTEMPT_TIMEOUT_S = _float("GROUNDED_ATTEMPT_TIMEOUT_S", 30)
GROUNDED_MAX_ATTEMPTS = max(1, min(3, _int("GROUNDED_MAX_ATTEMPTS", 2)))

# ---- request / lease budget (derived, never literals) -------------------------------
CLOUD_RUN_TIMEOUT_S = _int("CLOUD_RUN_TIMEOUT_S", 360)
DEADLINE_SLACK_S = 70
REQUEST_DEADLINE_S = CLOUD_RUN_TIMEOUT_S - 40
LEASE_TTL_S = int(max(270, TEMPLATE_MAX_ATTEMPTS * TEMPLATE_ATTEMPT_TIMEOUT_S + 30))
GROUNDED_LEASE_TTL_S = int(max(100, GROUNDED_MAX_ATTEMPTS * GROUNDED_ATTEMPT_TIMEOUT_S + 30))
TAKEOVER_MARGIN_S = _int("TAKEOVER_MARGIN_S", 15)
FLIGHT_WAIT_S = _float("FLIGHT_WAIT_S", 300)
MAX_ENSURE_WAITERS = _int("MAX_ENSURE_WAITERS", 16)

# ---- caps and bounds ---------------------------------------------------------------
SUPPORTED_COUNTRIES = frozenset({"IN"})
MANUAL_ENTRY_MAX = _int("MANUAL_ENTRY_MAX", 200)
INSTANCES_MAX = _int("INSTANCES_MAX", 300)
SNAGS_PER_MACHINE_MAX = _int("SNAGS_PER_MACHINE_MAX", 500)
HISTORY_RESPONSE_MAX = _int("HISTORY_RESPONSE_MAX", 500)
SPEC_RETRY_AFTER_H = _int("SPEC_RETRY_AFTER_H", 24)
BASE_OVERLAP_MAX = _float("BASE_OVERLAP_MAX", 0.5)
GEMINI_CALLS_PER_MIN = _int("GEMINI_CALLS_PER_MIN", 20)
ANSWER_MAX_CHARS = _int("ANSWER_MAX_CHARS", 2000)
ID_MAX_CHARS = _int("ID_MAX_CHARS", 80)
HEALTH_WINDOW_DAYS = _int("HEALTH_WINDOW_DAYS", 365)
OUTPUT_MARGIN = _float("OUTPUT_MARGIN", 0.15)
CATALOGUE_SEARCH_LIMIT = 10
CATALOGUE_QUERY_MAX = 80
