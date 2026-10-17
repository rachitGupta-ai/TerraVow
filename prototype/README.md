# TerraVow prototype

**Industry-specific machine templates. AI-generated snag taxonomy. Health-linked pollution intensity. Sustainable alternatives with India incentives.**

This Python prototype implements **Requirements 18–30** (industry templates, snag workflow, health/lifespan engine, sustainability recommendations). It demonstrates the end-to-end flow from selecting a machine model → AI template generation → snag reporting → resolver workflow → health scoring → sustainable alternatives with government incentive schemes.

## What actually runs in this prototype

| Feature | Implementation | Google service / technology |
|---|---|---|
| **Industry-specific machine templates** | User selects industry + model → Gemini generates a deep snag taxonomy, job types, and operating parameters tailored to that model, cached in Firestore | **Gemini 2.5 Flash** with structured output + **Google Search** grounding for specs |
| **Snag reporting with template-driven validation** | Dynamic form built from the cached template; answers validated, PII redacted (emails, phone patterns), stored with recommended job guidance | Plain Python validation + regex redaction |
| **Resolver workflow** | Open/Assigned/In_Progress/Resolved/Closed states; severity-first queue; role-based UI guidance (not authentication) | Plain Python state machine |
| **Health score from snag history** | Deterministic formula combining maintenance deferral, snag severity, fleet cohort age, producing 0–100 score + remaining lifespan estimate | Plain Python, no LLM math |
| **Health-linked pollution intensity** | Air/water/chemical/soil pollution per day, scaled by machine health degradation and domain activity (e.g., textile dyeing → water + chemical) | Plain Python, fleet baselines |
| **Sustainable alternatives** | AI-suggested machine alternatives, operating process changes, assembly changes; each with emissions/energy/cost deltas, payback periods, gate classification (ALLOW/ASK/BLOCK) | **Gemini 2.5 Flash** with **Google Search** grounding (AI path) or curated fallback |
| **India government incentive schemes** | Real-named schemes (UNVERIFIED, qualitative, landing pages only) + demo-synthetic schemes (ILLUSTRATIVE, with params); filtered by enterprise size (MSME/LARGE) | Curated data (`data/incentives.json`) seeded at startup |
| **Decision Gate** | Hard veto on emission increases or cross-media shifts → confidence bands → trust cap; no recommendation reaches ALLOW in the demo (all ASK/BLOCK) | Plain Python, unit-tested |
| **Hash-chained audit trail** | Every snag submission, resolution, and sustainability recommendation is recorded with `hash(entry || prev_hash)` before returning | Plain Python + Firestore (or in-memory fallback) |
| **Hosting** | API and UI in one container; single uvicorn worker (no cross-replica state in the demo) | **Cloud Run** (or local uvicorn) |

**Data sources:**
- **Fleet machines:** 6 synthetic demo machines with seeded snag histories anchored to a configurable date (default 2026-10-17)
- **Catalogue:** 20 sample entries (5 per industry), marked `sample_data: true`
- **Base Templates:** 4 curated industry templates (textile, metals, chemicals, mining) with ≥6 job types, ≥12 snag types, ≥8 parameters
- **Incentive schemes:** 7 real-named India schemes (qualitative, UNVERIFIED, landing pages only) + 2 demo-synthetic (ILLUSTRATIVE, with params)
- **Sustainable alternatives:** 5 curated items per industry (2 machine alternatives, 2 operating process changes, 1 assembly change) with illustrative parameters

**Role switch and authentication:**
The header has a role switch (Reporter / Resolver). This is **workflow guidance only, not a security control**. The prototype has **no authentication**. A production system would gate resolver actions behind real RBAC.

**Demo reset:**
`POST /api/demo/reset` (gated by `DEMO_RESET_ENABLED`, default `true`) rewrites the seed anchor to today, restores seeded snags, deletes user-created instances/snags, and preserves the audit chain, templates, alternatives, and catalogue.

## What is NOT built

| Feature | Status |
|---|---|
| **Grounding display terms (inline citations in the UI)** | Specified (E6.5, E6.6) but not rendered in the prototype UI; Gemini returns citations, but the UI does not display them inline |
| **Template depth/latency measurement (T-MEASURE)** | Task 15 conditional on `GEMINI_API_KEY`; constants `TEMPLATE_ATTEMPT_TIMEOUT_S` and `GROUNDED_ATTEMPT_TIMEOUT_S` are provisional until measured |
| **Incentive discovery refresh (`POST /api/incentives/refresh`)** | Implemented behind `INCENTIVE_REFRESH_ENABLED=false` (task 14); demo path uses curated seeds only |
| **Cross-replica lease takeover edge cases** | Basic owner-checked release and expired-lease takeover implemented; complex network-partition / clock-skew scenarios not exhaustively covered (acceptable for demo) |
| **Official certification / public registry publication** | Specified (Req 9.5, design E9) but not wired in the prototype; the `/api/registry` endpoint verifies the audit chain only |
| **Live sensor ingestion (Pub/Sub), BigQuery analytics, Vertex AI Search, Agent Engine, supply-chain footprint propagation, emissions timing vs weather** | Specified in the full design (`.kiro/specs/terravow/`) and implemented in the Java reference model (`src/`), not in the Python prototype |

**Deviation from design E4 (task 5 decision):**
A model without a stored AI template may submit snags against its industry Base_Template form (`template_source="base"`). Design E4 requires a stored template (409 otherwise), but this demo-enabling fallback lets the prototype work end-to-end without Gemini credentials.

**Demo incentive values:**
All `params` (capex reduction, efficiency bonus) in demo-synthetic incentive records are **illustrative placeholders**, not retrieved from authoritative sources. Real-named schemes carry `params: null` and landing pages only.

**Cached AI alternatives never expire (fix-notes L8c):**
AI-suggested alternatives are cached indefinitely once generated. There is no expiry or refresh mechanism.

## Run locally

**Prerequisites:** Python 3.11+ (tested on 3.13)

**One-time setup:**
```bash
cd prototype
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
```

**With Gemini credentials (AI path):**
```bash
export GEMINI_API_KEY=...
cd prototype
.venv/bin/uvicorn app.main:app --port 8080
# Open http://localhost:8080
```

**Without Gemini (fallback path):**
```bash
cd prototype
.venv/bin/uvicorn app.main:app --port 8080
# All flows work with fallback labels (Generic industry template, curated alternatives)
```

**Run tests:**
```bash
cd prototype
.venv/bin/python -m pytest tests -v
# 151 tests: 27 from test_core.py (unchanged), 124 from Requirements 18–30
```

**Configuration:**
All settings live in `app/settings.py` with environment variable overrides:
- `GEMINI_API_KEY` (optional): Gemini API key; when absent, all AI paths return labelled fallbacks
- `DEMO_RESET_ENABLED` (default `true`): Whether `/api/demo/reset` is accessible
- `INCENTIVE_REFRESH_ENABLED` (default `false`): Whether `/api/incentives/refresh` is accessible (task 14, not used in demo)
- `FIRESTORE_EMULATOR_HOST` (optional): Use Firestore emulator instead of in-memory store
- `CLOUD_RUN_TIMEOUT_S` (default 300): Request deadline for deploy.sh and the env var (task 14)

**Single uvicorn worker only:**
The prototype runs a single worker process. Multi-replica leases (task 14) are implemented but not exercised without `FIRESTORE_EMULATOR_HOST` or a real Firestore connection.

## Deploy

**Cloud Run:**
```bash
cd prototype
PROJECT_ID=your-project ./deploy.sh
```

`deploy.sh` sets `--timeout ${CLOUD_RUN_TIMEOUT_S}` (default 300s) and `--max-instances 5`, passing `CLOUD_RUN_TIMEOUT_S` as an env var so the app respects the same deadline (task 14).

See `deploy.sh` for the exact steps (enable APIs, Firestore, IAM, Cloud Run build + deploy).

## API Endpoints

| Method | Path | Purpose |
|---|---|---|
| **Industry & Catalogue** | | |
| GET | `/api/industries` | List supported industries |
| GET | `/api/catalogue/search?industry={id}&q={query}` | Search catalogue (≤10 results, sample data) |
| POST | `/api/catalogue/manual` | Add a machine model manually (Gemini looks up specs via Google Search) |
| **Templates** | | |
| POST | `/api/template/ensure` | Ensure a template exists for a model (cache-once, Gemini generates if absent) |
| GET | `/api/template/{key}` | Get a cached template (never calls Gemini) |
| **Instances & Snags** | | |
| POST | `/api/instances` | Register a machine instance |
| GET | `/api/instances?model_key={key}` | List instances (+ fleet machines) for a model |
| POST | `/api/snags` | Submit a snag (validated against template, PII redacted, guidance returned) |
| GET | `/api/snags?status={status}&machine_id={id}` | Query snags |
| GET | `/api/resolver/queue` | Resolver queue (OPEN+ASSIGNED, severity-first) |
| POST | `/api/snags/{id}/accept` | Resolver: OPEN → ASSIGNED |
| POST | `/api/snags/{id}/resolve` | Resolver: ASSIGNED/IN_PROGRESS → RESOLVED (stores redacted notes) |
| POST | `/api/snags/{id}/close` | Resolver: RESOLVED → CLOSED (returns recomputed health) |
| GET | `/api/machines/{id}/history` | Snag history for a machine (≤500) |
| **Health & Lifespan** | | |
| GET | `/api/machines/{id}/health?as_of={date}` | Health score, remaining lifespan, pollution intensity, timeline (12 points) |
| **Sustainability** | | |
| POST | `/api/machines/{id}/recommendations` | Sustainable alternatives + India incentives (body: `enterprise_size`, optional `country`) |
| GET | `/api/incentives?country={CC}&industry={id}&enterprise_size={size}` | List incentive schemes |
| **System** | | |
| GET | `/api/health` | Health check (`seeded`, `seed_anchor`) |
| POST | `/api/demo/reset` | Reset demo (gated by `DEMO_RESET_ENABLED`) |
| GET | `/api/registry` | Audit chain integrity check (`chain_intact`) |

**Cache statuses (returned by `/api/template/ensure`):**
- `"cache-hit"`: Template was already stored
- `"generated"`: Gemini generated and stored the template
- `"generated-unsaved"`: Gemini generated but Firestore persistence failed (E4)
- `"fallback"`: Gemini failed or not configured; fallback Base_Template used (E4)

**Guidance sources (returned in snag submissions and resolver queue):**
- `"ai"`: From a Gemini-generated template
- `"fallback-base"`: From a Base_Template fallback
- `"base"`: From the industry Base_Template (no stored template; task 5 deviation)

**Gate classifications (sustainability alternatives):**
- `"ALLOW"`: Not reached in the demo (all alternatives are ASK or BLOCK per E7.8)
- `"ASK"`: Requires human confirmation
- `"BLOCK"`: Hard-vetoed (emission increase or cross-media shift) or below confidence threshold

## Layout

```
app/
  main.py              FastAPI app, startup seeding, /api/health, /api/demo/reset
  settings.py          Named constants (E3), env overrides, profile constants
  industries.py        Four industries (textile, metals, chemicals, mining), domain indicators
  catalogue.py         Catalogue search (scoped, ≤10, deterministic order)
  templates.py         Base_Templates, GeneratedTemplate schema, validator, form schema, adapt_base
  cache_once.py        ensure_once (read → Flight → generate → create_if_absent), profiles, INSTANCE_ID
  gemini_client.py     generate_template, lookup_specs, suggest_alternatives (JSON-in-text + Pydantic + retry)
  snags.py             validate_answers, redact, snag document, resolve_guidance
  seed.py              run() (idempotent), reset() (rewrite anchor, delete user data, preserve audit)
  engine.py            health_score, remaining_lifespan, degradation_loss, pollution_intensity, health_timeline, candidate_economics, apply_incentive
  sustain.py           Assemble alternatives, zero-reduction rule, gate integration, backfill
  store.py             Firestore + in-memory Store (create_if_absent, get_doc, put_doc, update_doc, query_eq, query_in, add_audit, replace_if)
  fleet.py             Synthetic fleet (6 machines re-tagged with industry/manufacturer/model per E1)
  gate.py              Decision_Gate (hard veto → confidence → trust cap, deterministic)
  routes_ext.py        All /api endpoints (Pydantic Request/Response, extra="forbid", 422 field names)
data/
  industries.json      Four industries with domain indicators and default_baselines
  catalogue.json       20 sample entries (5 per industry, economics, 6 fleet-mapped with E1 values)
  base_templates/*.json  Curated templates (≥6 job types, ≥12 snag types, ≥8 parameters, overlap ≤ 0.5)
  seed_snags.json      Seeded snag histories for 6 fleet machines (anchor_date: "2026-10-17")
  curated_alternatives.json  5 items per industry (2 machine, 2 operating, 1 assembly)
  incentives.json      7 real-named India schemes (UNVERIFIED) + 2 demo-synthetic (ILLUSTRATIVE)
static/
  index.html           Single-page UI (tabs, role switch, light theme, textContent/createElementNS only, 2 allow-listed innerHTML)
tests/
  test_industries_store.py  Property 65, 66, store create_if_absent
  test_catalogue.py    Property 72, search scoped/deterministic
  test_templates.py    Property 70 (validator), Property 71 (bases valid, overlap ≤ 0.5)
  test_cache_once.py   Property 67, 68, 69 (cache-hit, fallback, concurrent)
  test_snags.py        Property 73, 84, 87 (validation, role guards, redaction)
  test_seed_workflow.py  Property 88, 89, 86 (seed idempotent, queue, reset)
  test_health_engine.py  Properties 74–78 (hypothesis), seeded table check
  test_sustain.py      Properties 79–81, 83, 91, 95 (curated/AI path, gate, seed integrity, paybacks)
  test_e2e_flow.py     End-to-end walk (fake Gemini + not-configured paths)
  test_ui_static.py    Property 82 (UI safety: no unsafe HTML, light theme, ALLOW/ASK/BLOCK text+glyph, accessibility)
  test_core.py         27 unchanged tests from prior prototype version
docs/
  data-model.md        Collections, keys, seeding, labels, reset behavior, unmeasured constants, deviations
```

**Test count:** 151 tests total (27 from `test_core.py`, 124 from Requirements 18–30).

**Property coverage:** Properties 65–95 (Python prototype) are tested. Property-based tests use `hypothesis` with `max_examples=100` where applicable; others are unit tests with tagged comments referencing the property number.
