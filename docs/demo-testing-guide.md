# TerraVow — Demo Testing Guide

Practical, step-by-step ways to exercise every feature of the prototype, the accounts (role
handles) you need, and the external services required for a full demo. Cross-links:
[product-features.md](./product-features.md) · [system-architecture.md](./system-architecture.md) ·
[user-flow.md](./user-flow.md).

This guide describes the app **as built** in `prototype/` (FastAPI + a single vanilla-JS page,
Firestore with an in-memory fallback, Gemini via `google-genai`). Everything deterministic (health,
lifespan, pollution, cost, payback) runs with **no external service at all**. Gemini and Firestore
are optional and only deepen the demo.

---

## 1. Decide your demo tier first

Pick the lowest tier that shows what you need. Each higher tier adds one external dependency.

| Tier | External services | What works | What falls back |
|---|---|---|---|
| **T0 — Zero-service (recommended for a safe demo)** | none | Whole flow: industries, catalogue search, snag reporting, resolver workflow, health/lifespan/pollution, alternatives, incentives | Templates use the curated **Base_Template** (labelled "generic, not generated for this model"); alternatives use the **curated** set; store is **in-memory** (resets on restart) |
| **T1 — + Gemini** | Gemini API (AI Studio key *or* Vertex AI project) | All of T0 **plus** AI-generated deep per-model templates, AI-suggested alternatives, photo condition analysis, grounded spec lookup | Firestore still in-memory |
| **T2 — + Firestore** | Gemini + Firestore (or just Firestore) | All of T1 **plus** data persists across restarts and across replicas; cache-once is durable | — |
| **T3 — Deployed (Cloud Run)** | Gemini + Firestore + Cloud Run | Public URL for judges | — |

**For a jury demo, T0 or T1 is the sweet spot.** T0 can never fail live. T1 shows the AI value with
one API key and no cloud setup. Only go to T2/T3 if a persistent or public URL is required.

---

## 2. Services and accounts needed (detailed)

### 2.1 Always required (T0 and up)
- **Python 3.13** and the prototype virtualenv.
- No account, no network. The deterministic engine and curated data files ship in the repo.

### 2.2 Gemini (T1+)
You need **one** of these two modes. The app auto-detects the mode and reports it at `/api/health`.

- **AI Studio (simplest — a single API key)**
  - Account: a Google account with access to Google AI Studio.
  - Create an API key in AI Studio.
  - Env: `GEMINI_API_KEY=<key>` (or `GOOGLE_API_KEY`), and `GOOGLE_GENAI_USE_VERTEXAI=false`.
  - Cost control: usage is per request; keep the demo short. There is no always-on cost.
- **Vertex AI (project-based)**
  - Account: a Google Cloud project with the **Vertex AI API** enabled and billing on.
  - Auth: `gcloud auth application-default login` (or a service account with the Vertex AI User role).
  - Env: `GOOGLE_GENAI_USE_VERTEXAI=true`, `GOOGLE_CLOUD_PROJECT=<project-id>`,
    `GOOGLE_CLOUD_LOCATION=<region or global>`.
- **Model names (optional overrides):** `GEMINI_MODEL` (default `gemini-2.5-flash`),
  `TEMPLATE_MODEL`, `GEMINI_EXPLAIN_MODEL`.

Verify the mode is live: `curl -s localhost:8080/api/health` should show `gemini_mode` as
`ai-studio` or `vertex-ai` (not `not-configured`).

### 2.3 Firestore (T2+)
- Account: a Google Cloud project with the **Firestore API** enabled (Native mode).
- Auth: application-default credentials (same `gcloud auth application-default login`) or a service
  account with Firestore read/write.
- Env: `USE_FIRESTORE=true`, `GOOGLE_CLOUD_PROJECT=<project-id>`.
- Without this, the app uses the in-memory store automatically. Data is lost on restart.

### 2.4 Cloud Run (T3)
- Google Cloud project with **Cloud Run**, **Artifact Registry**, Firestore and Vertex AI enabled;
  billing on.
- `gcloud` CLI authenticated.
- Deploy with `prototype/deploy.sh` (sets `--max-instances 1`, which is the current guard against
  duplicate Gemini spend until the cross-replica lease is built — do not raise it without that work).
- **Cost note:** `deploy.sh` sets `--min-instances 1`, so the service stays warm and bills while up.
  Set a billing alert and tear the service down after the demo.

### 2.5 Java engine (optional)
- The Python prototype is self-contained. The Java engine (`/api/gate/evaluate`, `/api/health`) is
  only exercised through the prototype's `/api/java/health` and `/api/java/gate` passthrough and is
  not needed for the demo flow. Skip unless you specifically want to show it.

### 2.6 Accounts inside the app (roles)
There is **no authentication**. "Accounts" are just **role + handle** values sent with each write.
This is a deliberate demo-grade switch, stated in the UI footer and README. Use these in the demo:

| Role | Handle (suggested) | Used for |
|---|---|---|
| Reporter | `reporter-demo` | Selecting a machine, filling the template, submitting a snag |
| Resolver | `resolver-demo` | Accepting from the queue, resolving, closing |

Handle rules (enforced): `^[A-Za-z0-9_-]{3,32}$`. No names, emails or phone numbers — those are
redacted before storage, so do not type real PII in the demo.

---

## 3. Setup and run

```bash
cd /Users/rachit.gupta/IdeaProjects/terravow/prototype
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt

# T0 (zero-service): no env needed. In-memory store, templates/alternatives fall back to curated.
.venv/bin/uvicorn app.main:app --port 8080

# T1 (+Gemini, AI Studio):
export GOOGLE_GENAI_USE_VERTEXAI=false
export GEMINI_API_KEY=your-key
.venv/bin/uvicorn app.main:app --port 8080

# T2 (+Firestore):
export USE_FIRESTORE=true GOOGLE_CLOUD_PROJECT=your-project
.venv/bin/uvicorn app.main:app --port 8080
```

Open http://localhost:8080. Confirm readiness:

```bash
curl -s localhost:8080/api/health      # shows gemini_mode, store_backend, seeded, seed_anchor
```

Reset to a clean seeded state at any point (before each run-through):

```bash
curl -s -X POST localhost:8080/api/demo/reset
```

---

## 4. Reference data to use in the demo

**Four industries** (keys): `textile` (air, water, chemical), `metals` (air, soil, water),
`mining` (soil, water, air), `chemicals` (air, water, chemical, soil).

**Sample catalogue entries** (good search terms):

| Industry | Manufacturer | Model | Try typing |
|---|---|---|---|
| textile | Brückner | Power-Frame | `bru` |
| textile | Thies | iMaster | `thies` |
| textile | Monforts | Montex 6500 | `montex` |
| metals | Tenova | Walking-Beam Reheat Furnace WB-200 | `tenova` |

**Seeded fleet machines** (already have snag history and distinct health scores): `D-3`, `B-2`,
`G-5` and the other three fleet ids. Use these for the health/lifespan/pollution and sustainability
tabs so there is data to show immediately after a reset.

---

## 5. Feature-by-feature test playbook

Each feature has a **UI way** (what to click) and an **API way** (a curl you can run live or use to
verify). Run `POST /api/demo/reset` first.

### 5.1 Industry selection and catalogue search (Req 18, 19)
- **UI:** Open the "Report a snag" tab → pick an industry from the dropdown → type `bru` in the
  machine box → the Brückner Power-Frame appears in the autocomplete.
- **API:**
  ```bash
  curl -s localhost:8080/api/industries
  curl -s 'localhost:8080/api/catalogue/search?industry=textile&q=bru'
  ```
- **Pass:** search is scoped to the chosen industry, returns ≤10 ordered matches; an unknown
  industry or an over-long query returns a 422 naming the field.

### 5.2 Manual machine entry + spec lookup (Req 19, T1 only for grounded specs)
- **UI:** In the machine box, type a model that is not listed → "Add machine manually" → fill
  manufacturer/model.
- **API:**
  ```bash
  curl -s -X POST localhost:8080/api/catalogue/manual \
    -H 'content-type: application/json' \
    -d '{"industry":"textile","manufacturer":"Acme","model":"DX-1"}'
  ```
- **Pass (T0):** the model becomes selectable with no specs. **Pass (T1):** specs are fetched and
  shown with source links; if the lookup fails the model is still selectable (graceful).

### 5.3 AI deep template + cache-once (Req 20, 21) — the key AI feature
- **UI:** Select a catalogue model. A spinner shows while the template is prepared, then the
  template renders with collapsible sections (machine profile, operating parameters, job types,
  snag taxonomy). A status line states the source and whether it was served from cache.
- **API:**
  ```bash
  # First call for a model: generates (T1) or returns the base fallback (T0)
  curl -s -X POST localhost:8080/api/template/ensure \
    -H 'content-type: application/json' \
    -d '{"industry":"textile","manufacturer":"Brückner","model":"Power-Frame"}'
  # Second call for the SAME model: served from cache, no new Gemini call
  curl -s -X POST localhost:8080/api/template/ensure \
    -H 'content-type: application/json' \
    -d '{"industry":"textile","manufacturer":"Brückner","model":"Power-Frame"}'
  ```
- **Pass (T0):** `cache_status` is `fallback`, source `fallback-base`, labelled as generic.
- **Pass (T1):** first call `cache_status: generated`, source `ai`; second call `cache_status:
  cache-hit`. This is the "generate once per model, then reuse" guarantee — call it out to judges.
- **Depth check:** the template should carry ≥6 job types, ≥12 snag types, ≥8 operating parameters,
  and per-snag fields (symptoms, root causes, detection, severity 1–5, immediate action, repair job,
  parts, hazards, escalation, affected pollution domains). Open a snag entry in the UI to show this.

### 5.4 Snag reporting with a dynamic form (Req 22) — Reporter account
- **UI:** Set the role switch to **Reporter**, handle `reporter-demo`. With a model selected, the
  snag form is rendered from the template (text fields and dropdowns). Fill it, optionally attach a
  photo (fleet machines, T1), submit. A guidance card appears with the recommended repair job.
- **API:**
  ```bash
  curl -s -X POST localhost:8080/api/snags \
    -H 'content-type: application/json' \
    -d '{"machine_id":"B-2","role":"REPORTER","handle":"reporter-demo","severity":3,
         "snag_type_id":"refractory-wear","answers":{"description":"cracking near burner"}}'
  ```
- **Pass:** a valid submission is stored and returns guidance; an invalid one (missing required
  field, bad dropdown value, severity out of 1–5) is rejected with a per-field 422 and stores
  nothing. Any email/phone typed into a text field is redacted in the stored record.

### 5.5 Resolver workflow (Req 22) — Resolver account
- **UI:** Switch role to **Resolver**, handle `resolver-demo`, open the "Resolver queue" tab. Open
  snags are listed worst-first with the recommended job. Accept → Resolve (notes, downtime, cost) →
  Close. Closing shows the updated health summary.
- **API:**
  ```bash
  curl -s localhost:8080/api/resolver/queue
  curl -s -X POST localhost:8080/api/snags/<SNAG_ID>/accept  -H 'content-type: application/json' -d '{"handle":"resolver-demo","role":"RESOLVER"}'
  curl -s -X POST localhost:8080/api/snags/<SNAG_ID>/resolve -H 'content-type: application/json' -d '{"handle":"resolver-demo","role":"RESOLVER","notes":"replaced liner","actions_taken":"swap","downtime_hours":6,"cost_inr":45000}'
  curl -s -X POST localhost:8080/api/snags/<SNAG_ID>/close   -H 'content-type: application/json' -d '{"handle":"resolver-demo","role":"RESOLVER"}'
  ```
- **Pass:** a Reporter cannot resolve (403); an out-of-order transition is rejected (409); the queue
  is ordered by severity then age. Closing returns the recomputed health/lifespan/pollution summary.

### 5.6 Snag history, health score, remaining lifespan (Req 23)
- **UI:** "Machine health" tab → select a seeded machine (e.g. `B-2`). Shows a health gauge, a
  remaining-lifespan estimate (with "estimate, not a prediction of failure"), the age/age-penalty, a
  "how this is calculated" factor table, and a snag timeline.
- **API:**
  ```bash
  curl -s localhost:8080/api/machines/B-2/health
  curl -s localhost:8080/api/machines/B-2/history
  ```
- **Pass:** the six seeded fleet machines show six distinct health scores; closing/adding snags
  changes the score deterministically (same inputs → same number).

### 5.7 Pollution intensity linked to health (Req 24, 25)
- **UI:** On the "Machine health" tab, the pollution-intensity trend reacts to health: as health
  degrades, per-day intensity rises. An indicator selector switches domains (air/water/chemical/soil
  as applicable to that industry). Everything is tagged "illustrative estimate, not a measurement".
- **Pass:** degraded health never *lowers* intensity (monotonic). Out-of-domain indicators are not
  shown for a machine whose industry does not have that domain.

### 5.8 Sustainable alternatives + process change (Req 26)
- **UI:** "Sustainability" tab → "Analyse alternatives". Shows the current usage card, then items
  grouped as machine alternatives / operating-process changes / assembly change, each with capex,
  efficiency gain, per-domain emission reduction, and payback (including "beyond design life" or
  "no payback" wording where that is the honest answer). A safety-gate badge marks each item.
- **API:**
  ```bash
  curl -s -X POST localhost:8080/api/machines/B-2/recommendations \
    -H 'content-type: application/json' -d '{"enterprise_size":"MSME","country":"IN"}'
  ```
- **Pass (T0):** curated alternatives are shown, labelled curated-fallback. **Pass (T1):**
  AI-suggested alternatives appear, labelled "AI-suggested, requires vendor validation"; any
  AI item that would *raise* emissions is blocked by the safety gate. All displayed numbers are
  computed by the engine, never by Gemini.

### 5.9 Government incentives by enterprise size (Req 27)
- **UI:** On the "Sustainability" tab, toggle MSME ↔ Large. Incentive schemes are filtered by
  industry and enterprise size; adjusted payback is shown alongside the unadjusted payback.
- **API:**
  ```bash
  curl -s 'localhost:8080/api/incentives?country=IN&industry=textile&enterprise_size=MSME'
  ```
- **Pass:** every real-named scheme carries a source link and a **UNVERIFIED/illustrative** label;
  demo-synthetic schemes with numbers are labelled illustrative. Nothing is presented as a verified
  government figure. Incentive-adjusted payback is never worse than the unadjusted payback.

### 5.10 Honesty, determinism, safety (Req 28)
- Re-run any numeric endpoint twice with the same input → identical numbers (determinism).
- Inspect any AI or template text in the page: it is rendered as text, not HTML (no script runs).
- `curl -s localhost:8080/api/registry` → `chain_intact: true` (audit hash chain unbroken after the
  workflow).

### 5.11 Graceful degradation (run this to de-risk the live demo)
- Start the server in **T0** (no `GEMINI_API_KEY`). Walk the entire flow. Everything must work with
  curated/base fallbacks and clear labels. This is your safety net if the network or key fails on
  stage.

---

## 6. One-command end-to-end check (automated)

The repo's own tests already walk the full flow with a fake Gemini (so no key needed):

```bash
cd /Users/rachit.gupta/IdeaProjects/terravow/prototype
.venv/bin/python -m pytest -q tests            # expect: all passing
.venv/bin/python -m pytest -q tests/test_e2e_flow.py   # the end-to-end slice only
```

The e2e test covers: industry → search → template ensure (generate then cache-hit at generator call
count 1) → instance → snag submit → resolver accept/resolve/close → health → recommendations (MSME
and LARGE) → audit chain intact, and the same walk with Gemini not configured.

---

## 7. Pre-demo checklist

- [ ] `pip install -r requirements.txt` done in `.venv`.
- [ ] `curl /api/health` shows the expected `gemini_mode` and `store_backend` for your tier.
- [ ] `POST /api/demo/reset` run; seeded machines present.
- [ ] Full click-through once in **T0** (fallback) as the safety net.
- [ ] If demoing T1: one template generated live, then re-selected to show `cache-hit`.
- [ ] Reporter and Resolver handles ready (`reporter-demo`, `resolver-demo`).
- [ ] Light theme confirmed; browser zoom set for projection.
- [ ] (T3 only) billing alert set; plan to delete the Cloud Run service after.

---

## 8. Things that will NOT work in the demo (be upfront)

- **No login/accounts.** Roles are an unauthenticated switch (by design, stated in the UI).
- **Incentive numbers are illustrative,** not verified government figures. Say so when you show them.
- **Pollution and payback are estimates** from illustrative factors, not measurements.
- **Cross-replica cache-once is not yet durable** beyond `--max-instances 1`; single instance only.
- **Certification/public registry** is intentionally out of the demo flow.
- **Browser interactivity was not automated-tested** in this build; do a manual click-through first.
