# TerraVow Prototype Data Model

## Collections

All collections live in Firestore (or an in-memory fallback for local development).

### `templates`

**Key:** `{industry}--{manufacturer}--{model}` (slugified, max 192 chars per fix-notes L5)

**Purpose:** AI-generated or fallback machine-specific snag templates

**Fields:**
- `industry`, `manufacturer`, `model` (string)
- `source` (string): `"ai"` (Gemini-generated) or `"fallback-base"` (adapted from Base_Template)
- `schema_version` (string): `"t1"` for AI templates, base template ID for fallback
- `generated_at` (ISO datetime string)
- `sources` (list of strings): specification URLs retrieved via Google Search (AI templates only)
- `profile` (object): Gemini-generated machine overview (AI templates only)
- `operating_parameters` (list): structured fields
- `job_types` (list): structured job definitions
- `snag_taxonomy` (list): structured snag type definitions with capture fields
- `cache_status` (string): `"generated"`, `"cache-hit"`, `"generated-unsaved"`, `"fallback"`

**Seeding:** AI templates are created on-demand via `ensure_once` (task 4). Base_Template fallbacks are never stored (computed per-request).

**Labels:** AI templates carry `user_message` per E4 cache-status table.

**Reset:** AI templates are preserved across `seed.reset()`.

---

### `catalogue`

**Key:** auto-generated

**Purpose:** Machine model catalogue (sample data + manual entries)

**Fields:**
- `industry`, `manufacturer`, `model` (string)
- `sample_data` (boolean): `true` for seeded entries
- `economics` (object): illustrative capex/opex/efficiency
- `added_via` (string): `"seed"` or `"manual"`
- `specs` (list of strings, optional): specification URLs retrieved via Google Search (manual entries only)
- `lookup_at` (ISO datetime string, optional): when specs were retrieved

**Seeding:** 20 sample entries seeded at startup (5 per industry).

**Reset:** Seeded entries are preserved; manual entries are deleted by `seed.reset()`.

---

### `instances`

**Key:** auto-generated

**Purpose:** Shop-floor machine instances

**Fields:**
- `model_key` (string): FK to templates collection
- `tag` (string): shop-floor identifier
- `install_year` (integer, optional)
- `registered_at` (ISO datetime string)
- `registered_by` (string): handle

**Seeding:** No seeded instances; instances are always user-created.

**Reset:** All non-seeded instances are deleted by `seed.reset()`.

---

### `snags`

**Key:** `SNG-{12 hex digits}`

**Purpose:** Reported snags with status workflow

**Fields:**
- `machine_id` (string): FK to instances or fleet
- `template_source` (string): `"ai"`, `"fallback-base"`, or `"base"` (no stored template)
- `schema_version` (string)
- `answers` (object): redacted field values
- `snag_type` (string): from the template taxonomy
- `severity` (integer 1–5)
- `recommended_job_id` (string, optional): guidance from template
- `status` (string): `"OPEN"`, `"ASSIGNED"`, `"IN_PROGRESS"`, `"RESOLVED"`, `"CLOSED"`
- `events` (list): append-only status-transition log
- `reported_at` (ISO datetime string)
- `reported_by` (string): handle
- `resolution` (object, optional): resolver notes (redacted), resolved_at, resolved_by

**Seeding:** Six fleet machines have seeded snag histories anchored to `seed_meta/anchor` date. Seeded snag IDs: `SEED-{machine_tag}-{nn}`.

**Labels:** Seeded snags are marked via the `snag_history/{fleet_id}` document with `synthetic: true`.

**Reset:** Seeded snags are rewritten with today's anchor; user-created snags are deleted.

---

### `alternatives`

**Key:** `{model_key}--{country}--s1`

**Purpose:** AI-suggested sustainable alternatives (grounded via Google Search)

**Fields:**
- `model_key`, `country` (string)
- `source` (string): `"ai"` (Gemini-suggested) or `"curated-fallback"`
- `generated_at` (ISO datetime string)
- `items` (list): AI-suggested alternatives with input parameters only (totals computed by engine)

**Seeding:** Created on-demand via `ensure_once` with `profile=GROUNDED` (task 9).

**Labels:** AI items carry `source: "ai"` and confidence 0.55 / trust 0.7; curated items are never cached.

**Reset:** All alternatives documents are preserved.

---

### `incentives`

**Key:** `{country}--{scheme_id}`

**Purpose:** Government sustainability incentive schemes

**Fields:**
- `country` (string): ISO 3166-1 alpha-2
- `scheme_id`, `scheme_name` (string)
- `applies_to` (list): `["machine_alternative", "process_change"]` per fix-notes L8a
- `status` (string): `"UNVERIFIED"` (real-named) or `"ILLUSTRATIVE"` (demo-synthetic)
- `params` (object or null): `null` for real-named schemes, structured for demo-synthetic
- `retrieved_on` (ISO date string or null): landing page retrieval date (real-named), `null` for demo-synthetic
- `authored_on` (ISO date string): authoring date for demo-synthetic
- `source_url` (string or null): landing page URL (real-named), `null` for demo-synthetic

**Seeding:** Seeded at startup via `seed.run()` (7 real-named + 2 demo-synthetic for India).

**Reset:** Seeded incentives are preserved.

---

### `audit`

**Key:** auto-generated

**Purpose:** Hash-chained audit trail (7-year retention per E10)

**Fields:**
- `seq` (integer): monotonic sequence (max of time_ns, head.seq + 1 per task 14)
- `timestamp` (ISO datetime string)
- `kind` (string): `"snag_reported"`, `"snag_resolved"`, `"snag_closed"`, `"sustainability_recommendation"`, etc.
- `summary` (object): structured summary (no free text)
- `handle` (string, optional): actor handle
- `entry_hash` (string): `hash(entry || prev_hash)`
- `prev_hash` (string): previous entry's `entry_hash` (genesis = zero hash)

**Seeding:** Not seeded directly; audit entries are created by workflow actions.

**Reset:** Audit chain is never cleared by `seed.reset()`; it is append-only and never deleted.

---

### `registry`

**Key:** auto-generated

**Purpose:** Public verification registry (E2)

**Fields:**
- `written_at` (ISO datetime string)
- `chain_intact` (boolean): hash-chain verification result

**Seeding:** Created by `/api/registry` endpoint (E2).

**Reset:** Registry entries are never deleted.

---

### `seed_meta`

**Key:** `"anchor"` or `"complete"` or collection name (e.g., `"incentives"`)

**Purpose:** Seeding state and anchor date

**Fields (anchor):**
- `anchor_date` (ISO date string): seed anchor for snag histories
- `written_at` (ISO datetime string)

**Fields (complete):**
- `complete` (boolean): whether startup seeding finished
- `written_at` (ISO datetime string)

**Fields (collection markers):**
- `seeded` (boolean): whether that collection was seeded
- `written_at` (ISO datetime string)

**Reset:** `seed.reset()` writes a new anchor with today's date; other markers are preserved.

---

### `leases` (task 14)

**Key:** `{collection}::{doc_id}`

**Purpose:** Multi-replica cache-once coordination (Firestore only)

**Fields:**
- `owner` (string): `INSTANCE_ID`
- `acquired_at` (ISO datetime string)
- `expires_at` (ISO datetime string): `acquired_at + LEASE_TTL_S` (profile-dependent)
- `result` (object, optional): cached result (leader writes, waiters read)

**Seeding:** Not seeded; leases are ephemeral.

**Reset:** Leases are not touched by `seed.reset()`.

**Notes:** Leases expire via TTL. In-memory store does not implement cross-replica leases (single-process only).

---

### `refresh_markers` (task 14, optional)

**Key:** `{collection}::{doc_id}`

**Purpose:** Rate-limit grounded refreshes (manual entry specs, incentive discovery)

**Fields:**
- `last_refresh` (ISO datetime string)

**Seeding:** Not seeded.

**Reset:** Not touched by `seed.reset()`.

**Notes:** Only used when `INCENTIVE_REFRESH_ENABLED=true` or manual-entry spec retry is allowed.

---

### `snag_history` (seeding metadata)

**Key:** `{fleet_machine_id}`

**Purpose:** Mark seeded snag histories as synthetic

**Fields:**
- `synthetic` (boolean): `true`
- `machine_id` (string)

**Seeding:** Created by `seed.run()` for each of the six fleet machines.

**Reset:** Preserved.

---

## Indexes

No composite indexes are required for the prototype. All queries use single-field equality or `in` filters with small result sets.

---

## Unmeasured Template Latency and Depth

Task 15 (T-MEASURE) has not run with real Gemini credentials. The following constants remain **provisional** until measured:

- `TEMPLATE_ATTEMPT_TIMEOUT_S`: 60s (will be set to `ceil(1.3 × p95)` after measurement)
- `GROUNDED_ATTEMPT_TIMEOUT_S`: 30s (will be set to `max(30, ceil(1.3 × p95))` after measurement)

**Template depth and index size are unmeasured.** The design requires ≥6 job types, ≥12 snag types, and ≥8 operating parameters per template. Actual generated depth and Firestore index entry count are unknown until T-MEASURE runs.

---

## Demo Incentive Values

All incentive scheme `params` (capex reduction, efficiency bonus) for demo-synthetic records are **illustrative placeholders**, not retrieved from authoritative sources. Real-named schemes (`status: "UNVERIFIED"`) carry `params: null` and landing pages only.

---

## Base-Template Fallback Deviation (task 5)

Per task 5 decision: a model without a stored AI template may accept snags against its industry Base_Template form. This is a demo-enabling deviation from design E4 (which requires a stored template, returning 409 otherwise). Snags submitted via this path are recorded with `template_source: "base"`.

---

## Cached AI Alternatives Never Expire (fix-notes L8c)

AI-suggested alternatives (collection `alternatives`) are cached indefinitely once generated. There is no expiry or refresh mechanism in the prototype.

---

## What Is NOT Modeled

- **Cross-replica lease takeover edge cases:** The prototype implements owner-checked release and expired-lease takeover, but complex scenarios (network partition, clock skew, failed leader with a still-valid lease) are not exhaustively covered (acceptable for the demo per task 14).
- **Firestore transactions beyond audit:** The audit append uses a transaction over `audit_head`; other writes are not transactional.
- **Compliance data retention:** The 7-year retention requirement (E10) is specified but not enforced by the prototype.
