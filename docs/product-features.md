# TerraVow Product Features (As Built)

**Cross-links:**  
- [System Architecture](./system-architecture.md) — component map, data model, APIs  
- [User Flow](./user-flow.md) — end-to-end journeys for Reporter and Resolver roles

This document describes the **TerraVow prototype features as actually implemented** (Requirements 18–30), covering the four industries and pollution domains, catalogue search, AI deep per-model templates, snag reporting with dynamic forms, reporter/resolver workflow, snag history, health score, pollution intensity, and sustainable machine alternative recommendations with India incentives. Where a capability is illustrative, a fallback, or not yet built, that is clearly stated.

---

## 1. Four Industries and Pollution Domains

**Built:** The platform recognizes four heavy-machinery industries that use air, water, chemical, and soil pollution domains together or separately:

- **Textile** — air, water  
- **Metal smelting and fabrication** — air, chemical, soil  
- **Chemical manufacturing** — air, water, chemical, soil  
- **Mining and aggregates** — air, water, soil

Each industry declares its active pollution domains and default baselines (CO2e, NOx, COD, BOD, effluent, hazardous-chemical release, solid waste, soil contamination index). The domain-to-indicator mapping ensures that every domain is covered by at least one baseline or capture field.

**Source:** `prototype/app/industries.py`, `prototype/data/industries.json`  
**Validation:** Startup self-check ensures exactly four industries, every industry has a baseline for each of its domains, and all four domains are collectively covered.

---

## 2. Catalogue Search and Manual Entry

### Catalogue Search

**Built:** Users select an industry, then search a catalogue of 20 sample machine models (5 per industry) by typing a manufacturer or model name. The search is:

- **Scoped** to the selected industry  
- **Case- and accent-insensitive**  
- **Returns at most 10 results** in a deterministic order  
- **Debounced** to avoid overwhelming the UI

Each entry carries `manufacturer`, `model`, `machine_type`, `sample_data: true` flag, and illustrative economics fields (replacement value, annual operating cost, annual output value, design life years).

Six of the 20 entries are the prototype's seeded fleet machines, tagged with industry/manufacturer/model metadata and real snag histories.

**Source:** `prototype/app/catalogue.py`, `prototype/data/catalogue.json`  
**API:** `GET /api/catalogue/search?industry={id}&q={query}` (422 if `q` > 80 chars or industry is unknown)  
**UI:** Auto-complete combobox with "Sample data, not an authoritative manufacturer database" notice and "Add machine manually" on no match.

### Manual Entry

**Built:** When the model is not in the catalogue, users can add it manually by entering the manufacturer and model name. The system looks up public specifications via Gemini with the Google Search tool (grounded call), extracts structured specs (JSON-in-text, Pydantic, one retry), stores the entry with `sample_data: false`, and flags spec sources as "AI-retrieved and unverified."

**Fallback:** If Gemini is not configured or the lookup fails, the entry is recorded with no specs (`specs_status: "sample"`), and the flow continues—**the manual entry never blocks the reporter**.

**Source:** `prototype/app/catalogue.py` (`lookup_specs`), `prototype/app/gemini_client.py`  
**API:** `POST /api/catalogue/manual` (validates manufacturer/model against `^[\w .,&()/+-]{1,80}$`, 429 if the manual-entry cap is reached)

---

## 3. AI Deep Per-Model Template (Cache-Once)

### Template Generation

**Built:** The first time a model is used, **Gemini generates a deep maintenance and snag template** specific to that machine's manufacturer and model. The template includes:

- **Machine profile** — summary, operating envelope, pollutant-capture fields covering every industry domain  
- **≥6 job types** — preventive/corrective maintenance, with checklist, parts, tools, safety notes, downtime estimate, cost band  
- **≥12 snag types** — organized by category (mechanical, electrical, control-system, etc.), each with five severity-level definitions (1 to 5), time to fix, safety hazards, immediate action, escalation criteria, pollution effects per domain, and a recommended job  
- **≥8 operating parameters** — machine-specific parameters (e.g., stack temperature, swirl-vane angle, quench rate)  
- **≥8 form fields** — custom snag-report fields beyond the three core fields (snag type, severity, description)

**Gemini prompt includes:**

- Machine identity (industry, manufacturer, model, machine type)  
- Public specs retrieved earlier (if grounded)  
- The industry's Base_Template structure (job/snag IDs and names, parameter labels, capture-field IDs) as a guide  
- Explicit depth requirements (min job types, min snag types, min parameters, min form fields)

**Validation:** Every generated template is validated against 10 structural rules (schema compliance, referential integrity, ID pattern, severity-level completeness, dropdown-options bounds, text-field max-length bounds, minimum depth, domain coverage, 200 KB size cap). Fields are sanitized (NFC normalization, zero-width/control removal) and truncated (names 120 chars, descriptions 600 chars, short lists 15 items); any truncation is recorded but not treated as an error. An invalid template triggers a bounded retry (1–5 attempts, default 3), with the validation errors sent back to Gemini as a hint.

**Source:** `prototype/app/templates.py`, `prototype/app/gemini_client.py`  
**Model:** `gemini-2.5-flash` with `response_schema` (lax twin of the strict Pydantic model), `thinking_config` enabled, HTTP timeout per `TEMPLATE_ATTEMPT_TIMEOUT_S`  
**Grounding sources:** Manufacturer/model specs (if retrieved) are recorded; the Base_Template itself is always part of the prompt.

### Cache-Once Behavior

**Built:** The template is generated **at most once per model key** across all users and sessions, with three layers guaranteeing "one stored document" without a cross-replica lease (the lease is added in task 14, not yet implemented):

1. **Double-checked read** — the store is read before and after becoming the flight leader, so a caller that read "absent" just before another finished never calls Gemini again  
2. **Same-instance single-flight** — one leader per key within each process; waiters share the leader's outcome (including a fallback), so a failing Gemini is not retried per waiter  
3. **Store `create_if_absent` as the final guard** — a loser discards its own result and returns the stored document as a `cache-hit`

A persistence error is reported as `generated-unsaved` (the template is returned but not cached); the next request will generate again. A fallback (see below) is never stored.

**Cache status values:**

- `cache-hit` — the document was already in the store  
- `generated` — generated now and saved for everyone  
- `generated-unsaved` — generated now but persistence failed; will be regenerated next time  
- `fallback` — Gemini unavailable or generation failed; using the generic industry template

**Source:** `prototype/app/cache_once.py` (ensure_once, Profile, GenResult, EnsureOutcome)  
**Collection:** `templates`, keyed by `{model_key}--{TEMPLATE_SCHEMA_VERSION}`  
**API:** `POST /api/template/ensure` (404 if the catalogue entry does not exist), `GET /api/template/{key}` (fetch-only, never calls Gemini)

### Base_Template Fallback

**Built:** When Gemini is not configured or generation fails, the system falls back to a **generic industry template** deterministically adapted to the entry. There are four curated Base_Templates (one per industry), each validated at startup against the same 10 rules. The adapted template uses the industry's structure with a summary line acknowledging it is "not generated for this model." The fallback is never stored in the `templates` collection and never labeled as AI output.

**Overlap rule:** Any two industry Base_Templates must have ≤50% overlap in snag IDs and capture-field IDs, ensuring each template is industry-specific.

**Source:** `prototype/data/base_templates/{textile,metals,chemicals,mining}.json`, `templates.adapt_base`

---

## 4. Snag Reporting with Dynamic Forms

**Built:** A Reporter selects a machine (from the fleet or from their registered instances), fills a dynamic form whose fields are driven by the stored template (or the Base_Template fallback if no AI template exists), and submits a snag report.

### Dynamic Form Schema

The form always includes **three core fields**:

1. **Snag type** — dropdown listing every snag type in the template plus "Other / not listed"  
2. **Severity** — dropdown 1 to 5 (the selected snag type's severity-level definitions are shown to guide the choice)  
3. **Description** — free text, required, "What did you observe?"

Then **all template form fields and pollutant-capture fields** are appended (text or dropdown with 1–20 options, each with a label, help text, required flag, and max-length or options list). The form schema is built on the fly from the stored or Base_Template structure; nothing is hard-coded.

**Validation:** Every field is validated before the snag is stored. Errors are collected for all fields rather than stopping at the first error (Req 22.5). A `snag_type` field inside `answers` is rejected (it is already a top-level field). Invalid submissions return a 422 with `{field, reason}` for each error; nothing is stored.

**Redaction:** Free-text answers are redacted before storage—**emails and 10–15-digit phone numbers are removed** deterministically and idempotently, while ISO dates are protected. The redaction is client-invisible (happens in the service).

**Recommended job:** If the snag type is recognized, the submission response includes the `recommended_job_id` and its checklist, labeled "Template guidance (AI-generated or generic), for qualified-engineer review."

**Source:** `prototype/app/snags.py` (validate_answers, redact, submit), `prototype/app/templates.py` (build_form_schema)  
**API:** `POST /api/snags` (409 "Open the template for this model first" if no stored template **and** the model has no instance or fleet tagging; the Base-template fallback lets a snag submit against the industry form with `template_source="base"` recorded—this is a demo-enabling deviation from the design, which is documented in the README)  
**Caps:** At most `SNAGS_PER_MACHINE_MAX` snags per machine (429 if exceeded)

### Machine Instances

**Built:** Reporters can register a new machine instance by providing a shop-floor tag and optional install year. Each instance is assigned a stable `INST-{8 hex}` ID, linked to the selected model key and industry, and stored in the `snag_history` collection as a header. The same machine selector shows the six seeded fleet machines and all registered instances.

**Source:** `prototype/app/snags.py` (create_instance, list_instances)  
**API:** `POST /api/instances`, `GET /api/instances?model_key={key}` (optional filter)  
**Caps:** At most `INSTANCES_MAX` non-seeded instances (429)

---

## 5. Reporter / Resolver Workflow

### Roles

**Built:** Two roles exist — **Reporter** and **Resolver** — selected via a client-side radio toggle. The role is sent with every write request. **There is NO authentication or authorisation.** The role check is **workflow guidance only**; it keeps the demo flow honest (a Reporter cannot accept, resolve, or close a snag) but is not a security control (Req 25.2, 25.11). Handles are pseudonyms (3–32 chars, alphanumeric/underscore/dash); no names, emails, or phone numbers are collected.

### Status Transitions

**Built:** Each snag has a status (`OPEN`, `ASSIGNED`, `RESOLVED`, `CLOSED`) and an append-only events list. Three actions are permitted:

| Action   | Role     | From status      | To status   |
|----------|----------|------------------|-------------|
| `accept` | RESOLVER | `OPEN`           | `ASSIGNED`  |
| `resolve`| RESOLVER | `ASSIGNED`, `OPEN` | `RESOLVED` |
| `close`  | RESOLVER | `RESOLVED`       | `CLOSED`    |

Each action appends an event (`{ts, from_status, to_status, actor_handle, actor_role}`) to the snag's events list and updates the status atomically via `store.update_doc`. A wrong role raises 403; an invalid transition raises 409. The snag is never modified on rejection.

**`resolve` captures:** resolver handle, resolved timestamp, redacted notes, redacted actions_taken, downtime hours, cost (INR).

**Source:** `prototype/app/snags.py` (transition, TRANSITIONS)  
**API:** `POST /api/snags/{id}/accept|resolve|close`

### Resolver Queue

**Built:** The Resolver queue lists all `OPEN` and `ASSIGNED` snags, sorted by severity descending, then reported timestamp, then snag ID. Each queue card shows the snag summary, severity number and glyph, the template guidance (from the stored AI template, the Base_Template fallback, or "no template guidance available"), and inline buttons for Accept / Resolve / Close (role-gated).

**Guidance resolution:** The system first tries to find the snag's `snag_type_id` in the stored AI template (at the snag's recorded `schema_version`), then in the industry Base_Template, then returns "none." The guidance includes the snag-type name, recommended job (ID, name, checklist), immediate action, time to fix, safety hazards, and escalation criteria. The guidance source is always labeled (`ai`, `fallback-base`, or `none`).

**Source:** `prototype/app/snags.py` (queue, resolve_guidance)  
**API:** `GET /api/resolver/queue`

---

## 6. Snag History

**Built:** Every machine (fleet or instance) has a snag history stored in the `snag_history/{machine_id}` header and a list of snag documents in the `snags` collection, keyed by `SNG-{12 hex}`. The history is append-only (events are never deleted or modified) and synthetic/seeded histories are flagged (`seeded: true`).

### Seeding

**Built:** Six fleet machines have **pre-seeded snag histories** with 7 open snags at startup (anchor date `2026-10-17` by default, reset to today's date on demo reset). The seeding is idempotent (create-if-absent over stable `SEED-{machine}-{nn}` IDs) and lazy (reseeds at most once per 60 seconds). Every seeded snag has `synthetic: true` and `seeded: true` flags. The seed date is stored in `seed_meta/anchor` and surfaced via `/api/health`.

**Source:** `prototype/app/seed.py`, `prototype/data/seed_snags.json`  
**API:** `/api/health` includes `seeded: true|false` and `seed_anchor`

### Demo Reset

**Built:** `POST /api/demo/reset` restores the seeded snag histories with today's anchor date, deletes all demo-created snags and instances, and clears any live Gemini health overrides. **Audit log, templates, manual catalogue entries, alternatives, and incentives are preserved.** Reset is gated by `DEMO_RESET_ENABLED=true` (default true).

**Source:** `prototype/app/seed.py` (reset), `prototype/app/main.py`

---

## 7. Health Score and Remaining Lifespan

### Health Score

**Built:** The health score is computed from the snag history by the **deterministic engine** (`prototype/app/engine.py`), never by Gemini. The score (0–100, higher is better) aggregates six penalty terms, each capped and each non-decreasing in the snag multiset:

- **Frequency penalty** (≤15): 1.5 × count of snags in the 365-day window  
- **Severity penalty** (≤15): 1.0 × sum of (severity − 1) in the window  
- **Recurrence penalty** (≤15): 3.0 × sum of max(0, count − 1) per snag-type group (repeat failures of the same type)  
- **Trend penalty** (≤10): 1.0 × sum of max(0, severity − 2) for snags in the 90-day recent window  
- **Unresolved penalty** (≤25): 2.0 × sum of severity for currently-unresolved snags  
- **Age penalty** (≤15): 15 × min(1, age_days / (design_life × 365)) if install year is known

Score = 100 − (frequency + severity + recurrence + trend + unresolved + age penalty).

**Bands:** Good (≥80), Fair (≥60), Poor (≥40), Critical (<40).

**Confidence:** `low-history` if the machine has fewer than 3 snags in the 365-day window (seeded or real); otherwise `estimate`. The `low-history` cap is applied in the sustainable alternatives path (lowers item confidence to 0.6).

**Source:** `prototype/app/engine.py` (health_score, SnagFact, snag_facts replay from append-only events)  
**API:** `GET /api/machines/{id}/health?as_of={YYYY-MM-DD}` (default today UTC; bounded 1990-01-01 to today; 404 if machine unknown, 422 if date is invalid)

### Remaining Lifespan

**Built:** Lifespan is estimated from the health score, machine age, and design life (default 25 years, overridden by catalogue economics if present). The formula is:

**Remaining days = (design_life × 365 − age_days) × (health_score / 100)^1.5**

If install year is unknown, the basis is `design-life-only` and the residual is capped at a configured minimum (180 days by default). The estimate is returned as a central value, a low–high band (±15%), and a set of flags (`low-history`, `age-unknown`, `health-poor`).

**Label:** "Estimate, not a prediction of failure."

**Source:** `prototype/app/engine.py` (remaining_lifespan)  
**Returned with health API.**

---

## 8. Pollution Intensity (Health-Linked)

**Built:** The pollution intensity per day (kg CO2e, kg NOx, kg COD, kg BOD, m3 effluent, kg hazardous-chemical release, kg solid waste, soil-contamination index points) is computed as:

**intensity_per_day = baseline × scale × domain_scale × (1 + degradation_factor)**

where:

- `baseline` = industry default baseline (fleet machines inherit a per-machine fuel-based air baseline when available)  
- `scale` = catalogue entry's overall scale factor (default 1.0)  
- `domain_scale` = per-domain scale override (optional)  
- `degradation_factor = k_domain × (1 − health_score / 100)`, capped at 0.60

The `k_domain` illustrative weights are: air 1.0, water 2.5, chemical 3.0, soil 2.0 (a degraded machine's chemical releases grow faster than its air emissions).

**Over remaining lifespan:** Each per-day value is multiplied by the remaining lifespan days and summed.

**Omissions:** Indicators not in the machine's industry domain are omitted from the response (e.g., a textile machine does not report soil contamination).

**Source:** `prototype/app/engine.py` (pollution_intensity, degradation_loss, excess_from_loss)  
**Returned with health API.**

### Health Timeline

**Built:** The health timeline is an array of 12 points (one per month over the past year, plus the current date) showing health score, per-day intensity, and total over-remaining-lifespan pollution for air and water at each point. The UI renders the timeline as an SVG line chart with a trend line and an accessible table.

**Source:** `prototype/app/engine.py` (health_timeline), `prototype/static/index.html` (SVG via createElementNS)

---

## 9. Sustainable Machine Alternative Recommendations

**Built:** The Sustainability tab lets users request alternative machine models, operating-process changes, and assembly-stage changes, together with applicable India government incentives for small (MSME) and large enterprises. The flow is:

1. **Current usage card** — displays the machine's health score, band, and per-day pollution intensity (computed from the snag history)  
2. **Analyse alternatives button** — user selects enterprise size (MSME / Large) and country (default IN, validated `^[A-Z]{2}$`)  
3. **Alternatives and incentives** — retrieved from curated illustrative data and/or AI-suggested via Gemini (cached)

### Alternatives (Curated and AI-Suggested)

**Curated alternatives:** Five items per industry (2 machine alternatives, 2 operating-process changes, 1 assembly change) in `data/curated_alternatives.json`, each with illustrative input parameters:

- `capex_pct_of_replacement` (0–300%)  
- `efficiency_gain_pct` (0–60%)  
- `production_uplift_pct` (0–30%)  
- `extra_opex_pct` (0–30%)  
- `reduction_pct` per domain (−100% to +100%)

**AI-suggested alternatives (grounded):** If Gemini is configured, the system calls `gemini_client.suggest_alternatives` with the machine identity and country, using the Google Search tool (grounded). Gemini returns names, rationale, and labelled input parameter ranges (low–high pairs); the system computes the **midpoint** of each range and validates the candidate. A candidate is **dropped** (never clamped into a different candidate) if:

- Any parameter is out of bounds, non-finite, or `low > high`  
- The candidate has no in-domain reduction > 0 (the zero-reduction rule)

Valid AI candidates are labeled "AI-suggested, requires vendor validation and process-safety review" with confidence 0.55 / trust 0.7.

Curated items have confidence 0.70 / trust 1.0.

**Cache:** AI alternatives are cached via `ensure_once("alternatives", "{model_key}--{country}--s1", profile=GROUNDED)`. A second request is a cache hit; Gemini is never called again for the same model+country.

**Fallback:** If Gemini is not configured or the proposal is invalid, the curated set is returned with `source: "curated-fallback"`.

**Source:** `prototype/app/sustain.py`, `prototype/app/gemini_client.py`, `prototype/data/curated_alternatives.json`  
**Model:** `gemini-2.5-flash` with Google Search tool, JSON-in-text, Pydantic, one retry  
**API:** `POST /api/machines/{id}/recommendations` (body: `enterprise_size`, optional `country` default `IN`; 503 if audit write fails before return)

### Economics (Deterministic Engine)

**Built:** For each candidate (curated or AI), the **deterministic engine** computes:

- **Avoided annual emissions/energy** (from the reduction percentages and the current per-day intensity)  
- **Adjusted capex** (replacement value × capex_pct / 100, in INR)  
- **Cash benefit per year** (energy savings − extra opex, in INR)  
- **Benefit with exposure per year** (cash + avoided environmental-compensation exposure, priced at illustrative rates: ₹1500/t CO2e, ₹40/kg NOx, plus water/chemical/soil proxies)  
- **Payback horizon** (25 years)  
- **Payback status** (`within-design-life`, `beyond-design-life`, `no-payback`) for cash and for exposure+cash—**never capped**

**Source:** `prototype/app/engine.py` (candidate_economics, E7.6, payback_status)

### Decision Gate Integration

**Built:** Every candidate passes through the existing `gate.evaluate` with its emission deltas, confidence, and trust. The gate applies the hard veto first (any increase in total emission mass or cross-media load → BLOCK), then confidence classification (ALLOW if conf ≥ allow threshold, ASK if ≥ ask threshold, BLOCK otherwise). The verdict is recorded in the response with a badge (✓ ALLOW "Allowed by the safety check", ? ASK "Needs confirmation", ✕ BLOCK "Blocked by the safety check").

**Backfill rule:** If all AI candidates of a kind (machine_alternative or process_change) are BLOCK or invalid, the system appends curated items of that kind (≤3 per kind). The overall source becomes "mixed" when backfill occurs.

**Source:** `prototype/app/gate.py` (evaluate), `prototype/app/sustain.py` (build_item, gate_deltas)  
**Result:** No item reaches ALLOW in practice (the demo parameters and confidences are tuned so everything is ASK or BLOCK, ensuring human review).

### India Government Incentives

**Built:** Seven real-named India schemes plus two `demo-synthetic` schemes are seeded from `data/incentives.json`:

**Real-named schemes (qualitative, UNVERIFIED/illustrative):**

- Production Linked Incentive (PLI) for Textiles  
- FAME India II (electric vehicles/batteries)  
- Green Credit Programme  
- National Clean Air Programme (NCAP)  
- National Mission for Clean Ganga (NMCG)  
- MSME green technology upgrades  
- Accelerated depreciation under Income Tax Act  

Each real-named scheme has:

- Name, authority, benefit type (CAPITAL_SUBSIDY, ACCELERATED_DEPRECIATION, TRADEABLE_CERTIFICATE, CONCESSIONAL_FINANCE, RECOGNITION)  
- Benefit description and eligibility notes (text only, ≤600 chars)  
- `params: null` (no quantitative subsidy rate or cap)  
- `retrieved_on: null`, `authored_on` (the date it was added to the demo), `verified: false`, `status: "UNVERIFIED/illustrative"`  
- `source_url` (landing page only, e.g., pib.gov.in, jalshakti-dowr.gov.in) or null  
- Industries and enterprise sizes the scheme applies to (filtered by industry+size in the response)  
- `applies_to` (machine_alternative, process_change, or both)

**Demo-synthetic schemes (ILLUSTRATIVE):**

- Two schemes with quantitative `params` (subsidy rate, cap), no source URL, `origin: "demo-synthetic"`, `status: "ILLUSTRATIVE"`

**Incentive adjustment:** For each enterprise size, the system filters schemes by industry+size+applies_to and applies the **single largest incentive** to the capex and paybacks (adjusted ≤ unadjusted). The UI shows a toggle (MSME / Large) that recomputes the adjusted values client-side.

**Retrieval label:** Each scheme shows:

- "Retrieved on {date}" if `retrieved_on` is present  
- "Not retrieved from the source; authored for the demo on {date}" otherwise

**Name note:** "Scheme names and descriptions are for illustrative purposes; confirm the current scheme terms and eligibility with the issuing authority."

**Source:** `prototype/app/sustain.py`, `prototype/data/incentives.json` (seed_incentives), `prototype/app/engine.py` (incentive_eligible, apply_incentive)  
**API:** `GET /api/incentives?country={code}&industry={id}&enterprise_size={size}`

**Unsupported country:** If `country` is not `IN`, the response returns curated items, empty schemes, and "No data for this country." Gemini is not called.

**Optional incentive discovery:** The design specifies an optional `POST /api/incentives/refresh` endpoint (Gemini searches the web for new schemes), gated by `INCENTIVE_REFRESH_ENABLED=false`. This is not yet implemented.

---

## 10. Honesty and Determinism Principles

**Honesty about scope:**

- **No authentication or authorisation** — the role switch is workflow guidance, not access control  
- **Sample catalogue data** — the 20 models are illustrative; not an authoritative manufacturer database  
- **Illustrative economics** — replacement value, operating cost, design life, incentive rates, degradation factors are unmeasured demo assumptions (surfaced to the UI in every response)  
- **Unverified incentives** — real-named schemes are qualitative only; quantitative figures come from two demo-synthetic schemes labeled `ILLUSTRATIVE`  
- **Not an official certification** — health scores, lifespan estimates, pollution intensity, and verified improvements are evidence records, not certification  
- **AI-generated template disclaimer** — "AI-generated or generic guidance for qualified-engineer review. Intervals, costs and root-cause lists are not manufacturer instructions."  
- **Sustainable alternatives disclaimer** — "Informational estimate, not a quotation, engineering design, tax or legal advice; confirm eligibility with the issuing authority."

**Determinism:**

- Every **number** shown to the user (health score, lifespan, pollution intensity, capex, payback) is computed by the **deterministic engine** (`prototype/app/engine.py`), never free-generated by Gemini  
- Gemini may only **suggest** names, rationale, and labelled input parameter ranges; the engine validates and computes totals  
- The Decision Gate (`prototype/app/gate.py`) is deterministic: same inputs + config always produce the same verdict  
- Redaction (`snags.redact`) is deterministic and idempotent: the same text always redacts to the same result  
- Template validation (`templates.validate_template`) is deterministic: structural rules are checked in a fixed order  
- All seeded data (snag histories, alternatives, incentives) is versioned and idempotent

**Graceful degradation:**

- Gemini not configured → Base_Template fallback for templates, curated alternatives, no AI suggestions, nothing cached  
- Gemini timeout/error → same fallback, labeled `"fallback"` or `"curated-fallback"`  
- Template generation invalid after max retries → Base_Template fallback  
- Alternatives proposal invalid → curated backfill with `"mixed"` source  
- Missing economics fields → `status: "undetermined"`, item still shown with available fields  
- Sub-threshold confidence or trust → ASK or BLOCK verdict, never ALLOW

---

## 11. What Is NOT Built (Specified Only)

The following capabilities are **specified in Requirements 1–17 and the full design** but are **not implemented in the prototype** (Requirements 18–30):

- **Integrity_Gate** (Req 16) — source authentication, calibration/health gating, replay/freshness defence, authorized-actor verification, cross-signal corroboration, Data_Trust_Score  
- **Detection hardening** (Req 17) — Baseline_Plausibility_Check, Independent_Verification_Source reconciliation, Trust_Weighted_Profile, Anchored_Hash_Root  
- **Multi-replica safety** — cross-replica lease, request Deadline, MAX_ENSURE_WAITERS semaphore, strict float coercion, Firestore audit transaction  
- **Measured timeouts** — T-MEASURE (task 15) not yet run; template/grounded timeouts are provisional  
- **Vertex AI Agent Builder / Agent Engine** — the prototype uses direct Gemini calls, not the managed agent runtime  
- **Pub/Sub + BigQuery continuous queries** — the prototype has no streaming front door  
- **Vertex AI Search / BigQuery vector search** — the prototype has no corpus grounding  
- **Java Decision_Gate trust cap** — core logic implemented (TrustContext, trust-cap step after hard veto), but two test files need import fixes; tests not yet green  
- **Verified-improvement trajectory certification** — the prototype records improvement evidence but does not issue trajectory certificates or publish to a global registry  
- **Full EID/ERD/AEI/Substitution Dossier/Economics Engine** — the prototype's `engine.py` is a simplified illustrative model for the demo, not the full Req 1–9 + design E1–E15 implementation  
- **Regulatory corpus RAG** — the prototype has no EC guideline chapter retrieval, no IT Act §32 cited figures, no conflicting-instrument enumeration  
- **Embedded Pollution Passport** — the prototype computes a per-machine footprint but does not propagate it across supply-chain tiers  
- **Emissions-Aware Maintenance Lifecycle** — the prototype computes health + pollution but does not forecast EID trajectory, price deferral cost through ERD, or rank by Emissions_Aware_Priority  
- **Cross-media guardrail** — the Decision Gate checks for cross-media displacement (air-to-water trade-off) but the prototype has no live pollution monitor or anomaly detector  
- **Harm-aware rescheduling (AEI)** — the prototype has no Receptor_Map, no airshed assimilative-capacity forecast, no AEI computation, no harm-minimising schedule

---

**See also:**

- [System Architecture](./system-architecture.md) — component map, data model, cache-once sequence, API endpoints  
- [User Flow](./user-flow.md) — end-to-end journeys, Mermaid flowcharts, fallback experience
