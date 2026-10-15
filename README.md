# TerraVow

**Enforce. Certify. Track.**

TerraVow is an AI-powered environmental compliance, remediation-advisory, and certification
platform for manufacturing supply chains, built end-to-end on Google Cloud (Gemini on Vertex AI,
Vertex AI Agent Engine, BigQuery, Cloud Storage, Cloud Run, Firestore, Pub/Sub).

Machine degradation is an invisible, unpriced emissions event. TerraVow links machine health
state to emissions intensity, prices the resulting compounding liability, monitors pollution
with a cross-media guardrail, synthesizes AI-reasoned remediation counterfactuals, and certifies
facilities on verified improvement trajectory — all gated by a confidence-bounded Decision Gate
with a hard veto against increasing harm, and defended by a structural data-integrity layer.

For the full product story and architecture deep dive, see:

- [`overview.md`](overview.md) — product overview and design deep dive, written for both a
  general audience and technical reviewers.
- [`competitive-landscape.md`](competitive-landscape.md) — where TerraVow sits against adjacent
  categories (carbon accounting, predictive maintenance, EHS, CEMS/PEMS, carbon-credit MRV).
- [`.kiro/specs/terravow/`](.kiro/specs/terravow/) — the full specification: `requirements.md`
  (EARS-format acceptance criteria), `design.md` (architecture and correctness properties),
  and `tasks.md` (the implementation plan).

## Tech stack

- **Java 17**, **Spring Boot 3.4.13**
- **Maven** (wrapper included — no local Maven install required)
- **JUnit 5 + jqwik** (property-based testing) + **AssertJ**
- Target deployment: **Google Cloud** — Cloud Run, Vertex AI (Gemini 3.1 Pro / Flash), Vertex AI
  Agent Engine, Vertex AI Search, BigQuery, Cloud Storage, Firestore, Pub/Sub

## Getting started

### Prerequisites

- JDK 17+

### Build

```bash
./mvnw clean install
```

### Run tests

```bash
./mvnw test
```

Tests include both conventional unit/integration tests (`*Test`, `*Tests`) and jqwik
property-based tests (`*Properties`) that validate the formally stated correctness properties
in `design.md` — e.g. "a vetoed recommendation is BLOCK regardless of confidence," "EID
attribution is additive," "trust never raises a Decision_Gate classification."

### Run the application

```bash
./mvnw spring-boot:run
```

Configuration lives in `src/main/resources/application.yml` under the `terravow.*` namespace
and is config-gated with safe, behaviour-preserving defaults.

## Project structure

```
.kiro/specs/terravow/     Requirements, design, and task-list specification
src/main/java/com/terravow/
  domain/                 Machine_Asset, Maintenance_Event, Job_Run, Operating_Log, baselines
  gate/                   Decision_Gate (hard veto -> confidence -> trust cap)
  evidence/               Evidence_Chain & Audit service
  reasoning/              Reasoning_Agent Orchestrator (schema-validate -> retry -> escalate)
  llm/                    Two-tier Gemini model routing (Pro / Flash)
  capability/             EID, ERD, pollution monitor, AEI, substitution dossier,
                          economics engine, embedded pollution passport,
                          certification, maintenance lifecycle
  pii/                    PII/confidentiality masking and synthetic data
  ingest/                 Pub/Sub + BigQuery continuous-query streaming ingestion
  degradation/            Graceful degradation with confidence-lowering fallbacks
  gcp/                    Agent Engine, storage adapters (BigQuery / Cloud Storage / Firestore)
src/test/java/com/terravow/   Mirrors the main tree; property tests validate design.md's
                               correctness properties (jqwik, @Property(tries = 100))
```

## Status

This is a specification-driven build: requirements, design, and a fully decomposed,
test-driven implementation task list are complete (see `tasks.md` for the current state of
each task). Figures used in economics-engine examples are retrieved-and-cited at runtime
against a regulatory corpus — the platform is designed to never state a regulatory figure it
cannot cite back to a primary source.

Outputs that touch legal, tax, or engineering determinations are cited drafts for qualified
professional review and are never authoritative sign-off.

## License

MIT — see [`LICENSE`](LICENSE).
