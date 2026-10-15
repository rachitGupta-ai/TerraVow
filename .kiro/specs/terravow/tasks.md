# Implementation Plan: TerraVow

## Overview

This plan implements TerraVow incrementally and test-first in **Java** (the design targets a JVM
implementation on Cloud Run and the Testing Strategy mandates **jqwik** property-based tests). It
builds from the inside out: shared value types and the provenance spine first, then the two
structural safety components (Decision_Gate, Evidence/Audit), then the reasoning orchestrator over
Vertex AI Agent Engine, then each capability service, then the Google Cloud managed integrations
(function calling, Vertex AI Search grounding, BigQuery vector search, Pub/Sub + continuous queries,
two-tier Gemini routing, storage split), confidentiality masking, and graceful degradation. Every
step wires into what came before — no orphaned code. Correctness Properties 1–49 from the design are
implemented as jqwik property tests placed next to the code they validate.

## Tasks

- [x] 1. Project scaffolding and shared value types
  - [x] 1.1 Set up the Maven module, package layout, and test framework
    - Create the `com.terravow` package tree (`domain`, `evidence`, `gate`, `reasoning`, `capability.*`, `gcp`, `ingest`, `degradation`, `config`)
    - Add jqwik, JUnit 5, and AssertJ test dependencies; create the mirrored `src/test/java` tree
    - _Requirements: 12.1_
  - [x] 1.2 Implement shared value types
    - Implement `EvidenceChain`, `EvidenceLink` (SourceType READING | FILING | REGULATORY_TEXT | OPERATING_LOG | FALLBACK_PATH), `Confidence` (invariant 0..1 in compact constructor), `Money`, `SignedValue`, `DeterminationStatus`, `SyntheticLabel` as records/enums
    - _Requirements: 1.7, 2.1, 5.2, 13.2_
  - [x]* 1.3 Write property test for confidence bounds
    - **Property 5: Confidence stays within the unit interval**
    - **Validates: Requirements 1.7, 14.9**
  - [ ] 1.4 Implement shared integrity value types / data models
    - Implement `Instrument` (stable source identity, `CalibrationWindow` with calibrationStart/calibrationExpiry, `InstrumentHealth` HEALTHY | DEGRADED | FAILED), `AuthorizedActor` (actor identity ↔ Machine_Asset permission with grantedAt/revokedAt), `DataTrustScore` (invariant 0..1 in compact constructor), `RecordAdmission` with `AdmissionDecision` { ADMIT | ADMIT_REDUCED_TRUST | QUARANTINE | REJECT } + reason + Evidence_Chain, and `TamperEvidentLogEntry` (entry + entryHash = hash(entry || prevHash) + prevHash) as records/enums
    - _Requirements: 16.9, 16.14_
  - [ ]* 1.5 Write property test for Data_Trust_Score bounds
    - **Property 57: Data_Trust_Score stays within the unit interval**
    - **Validates: Requirements 16.14**

- [x] 2. Domain core: Machine_Asset, Maintenance, Job_Run, Health_State, Operating_Log, baselines
  - [x] 2.1 Implement domain core records and baselines
    - Implement `MachineAsset`, `MaintenanceEvent`, `MaintenanceSchedule`/`ScheduledAction`, `JobRun`, `MachineHealthState`, `OperatingLogEntry`, `CommissioningBaseline`, `FleetCohortBaseline`, `BaselineKind`
    - _Requirements: 14.1, 14.3, 14.7, 14.9_
  - [x] 2.2 Implement registration, event, and job stores with validation
    - Reject registrations/events missing required identity/fields (record missing field, create nothing); store full field sets; link Job_Run to its produced batch
    - Compute Deferral_Period from due time for past-due incomplete scheduled actions; set degraded-onset to the Deferral_Period start
    - Derive Machine_Health_State from telemetry/maintenance history, with Fleet_Cohort_Baseline fallback at strictly lower confidence
    - _Requirements: 14.2, 14.4, 14.5, 14.6, 14.9, 14.10_
  - [x] 2.3 Implement the append-only Operating_Log
    - Append telemetry, Maintenance_Event, Job_Run, and Health_State transitions; never modify or delete prior entries; expose log records for Evidence_Chains
    - _Requirements: 14.11, 14.12_
  - [x]* 2.4 Write property test for machine registration round-trip
    - **Property 40: Machine registration round-trips identity fields**
    - **Validates: Requirements 14.1**
  - [x]* 2.5 Write property test for maintenance/job round-trip
    - **Property 41: Maintenance and job records round-trip their fields**
    - **Validates: Requirements 14.3, 14.7**
  - [x]* 2.6 Write property test for deferral period measurement
    - **Property 42: Deferral period is measured from the due time**
    - **Validates: Requirements 14.5**
  - [x]* 2.7 Write property test for degraded onset anchoring
    - **Property 43: Degraded onset anchors ERD accrual to the deferral start**
    - **Validates: Requirements 14.6**
  - [x]* 2.8 Write property test for append-only Operating_Log
    - **Property 44: Operating_Log is append-only**
    - **Validates: Requirements 14.11**
  - [x]* 2.9 Write unit tests for registration/event missing-field rejection
    - Edge cases for required-field rejection (14.2, 14.4) and health-state cohort fallback (14.10)
    - _Requirements: 14.2, 14.4, 14.10_
  - [ ] 2.10 Extend the Operating_Log with a tamper-evident hash chain
    - Add a hash-chained append over the existing append-only Operating_Log: each `TamperEvidentLogEntry` carries `entryHash = hash(entry || prevHash)` and the prior entry's `prevHash` (genesis = zero), so any later modification or deletion of a prior entry breaks chain verification; preserve the Req 14.11 append-only payload and semantics unchanged (additive integrity layer only)
    - _Requirements: 16.9_
  - [ ]* 2.11 Write property test for tamper-evident Operating_Log
    - **Property 54: Tamper-evident Operating_Log detects any prior-entry mutation**
    - **Validates: Requirements 16.9**

- [x] 3. Evidence_Chain & Audit service
  - [x] 3.1 Implement EvidenceService and AuditService
    - `build`, `isFullyResolved`, `provenanceOf` (NOT_FOUND leaves audit unchanged); `persistDecision` completes synchronously before any decision-oriented output returns; mark unlinkable values unsupported and route to human review
    - _Requirements: 10.1, 10.2, 10.3, 10.5, 10.6, 10.7_
  - [x]* 3.2 Write property test for evidence link resolution
    - **Property 8: Evidence links resolve to primary sources**
    - **Validates: Requirements 2.4, 4.7, 5.5, 6.7, 7.1, 10.1, 14.12**
  - [x]* 3.3 Write property test for provenance round-trip
    - **Property 33: Provenance round-trip returns the stored chain**
    - **Validates: Requirements 10.5**
  - [x]* 3.4 Write property test for complete audit classification record
    - **Property 32: Classification is recorded completely in the audit trail**
    - **Validates: Requirements 10.2**
  - [x]* 3.5 Write unit tests for provenance not-found and unsupported value
    - Edge cases: provenance NOT_FOUND leaving audit unchanged (10.6); unsupported value routed to human review (10.7); audit-before-return ordering (10.3)
    - _Requirements: 10.3, 10.6, 10.7_

- [x] 4. Decision_Gate (hard-veto-before-confidence)
  - [x] 4.1 Implement DecisionGate ordered algorithm
    - Hard veto first (mass delta > 0 OR cross-media delta > 0 → BLOCK, vetoApplied, attach supporting readings), then confidence-validity check (absent/out-of-range → BLOCK + human review), then ALLOW/ASK/BLOCK classification, then human-review routing for any below-allow decision; never claim authoritative sign-off
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_
  - [x]* 4.2 Write property test for hard veto precedence
    - **Property 27: Hard veto forces BLOCK regardless of confidence**
    - **Validates: Requirements 8.3, 8.4**
  - [x]* 4.3 Write property test for confidence classification partition
    - **Property 28: Confidence classification partitions the decision space**
    - **Validates: Requirements 8.1, 8.2, 15.9**
  - [x]* 4.4 Write property test for invalid-confidence fail-closed
    - **Property 29: Invalid confidence forces BLOCK and human review**
    - **Validates: Requirements 8.5**
  - [ ] 4.5 Extend the Decision_Gate with the Data_Trust_Score trust cap
    - Extend `classify` input with a `TrustContext(governingTrust, trustThreshold)` (governingTrust = min over contributing inputs); add a trust-cap step composed AFTER the hard-veto-first ordering: when `governingTrust < trustThreshold`, cap the classification at ASK (lower an ALLOW to ASK and route to human review; leave ASK/BLOCK unchanged); the step only lowers, never raises, a classification, never lifts a hard-veto BLOCK, and never changes the existing step order or breaks existing gate behaviour/tests
    - _Requirements: 16.15, 16.16_
  - [ ]* 4.6 Write property test for sub-threshold trust capping at ASK
    - **Property 58: Sub-threshold trust caps the Decision_Gate at ASK**
    - **Validates: Requirements 16.15**
  - [ ]* 4.7 Write property test for trust never raising a classification or lifting the veto
    - **Property 59: Trust never raises a classification and never lifts the hard veto**
    - **Validates: Requirements 16.16**

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Reasoning_Agent Orchestrator over Vertex AI Agent Engine + ADK
  - [x] 6.1 Define the orchestrator contract and reasoning result types
    - Implement `ReasoningAgentOrchestrator`, `ReasoningResult`, `ToolDeclaration`, `GroundingSpec`, `RetryPolicy`, `ReasoningStatus`, `CapabilityId`, `OutputSchema`
    - _Requirements: 11.1, 11.2_
  - [x] 6.2 Implement schema-validate → bounded-retry → escalate loop
    - Validate agent output against its schema; accept valid output; retry on invalid up to clamped limit (1..5, default 3 when absent/out-of-range); on exhaustion set ESCALATED_HUMAN_REVIEW and record {capability, attemptsPerformed, failureReason}
    - _Requirements: 11.2, 11.3, 11.4, 11.5_
  - [x] 6.3 Wire the Agent Engine root-orchestrator + specialist-agent runtime adapter
    - Adapter that invokes the managed Agent Engine (root orchestrator delegating to per-capability specialist agents, ADK), passing only masked inputs; fold returned grounding citations into the Evidence_Chain; Memory Bank/Sessions store masked context only
    - _Requirements: 11.1, 13.3_
  - [x]* 6.4 Write property test for schema-valid acceptance
    - **Property 34: Schema-valid determinations are accepted**
    - **Validates: Requirements 11.3**
  - [x]* 6.5 Write property test for bounded retries
    - **Property 35: Retries are bounded by the clamped limit**
    - **Validates: Requirements 11.4**
  - [x]* 6.6 Write property test for exhausted-retry escalation
    - **Property 36: Exhausted retries escalate with a complete failure record**
    - **Validates: Requirements 11.5**

- [x] 7. Two-tier Gemini model routing and verified-tool function calling
  - [x] 7.1 Implement two-tier Gemini model router
    - Route deep reasoning/planning to the Gemini 3.1 Pro tier and high-volume classification (threshold checks, masking triage, intent routing) to the Gemini Flash tier
    - _Requirements: 11.1, 12.1_
  - [x] 7.2 Implement verified deterministic tools via Gemini function calling
    - Register tool declarations and execute them in TerraVow (not free-generated): EC formula (base × days × deterrence), ERD summation, mass-preservation check, net-position arithmetic; a tool-execution failure marks the dependent value UNAVAILABLE
    - _Requirements: 2.2, 4.5, 6.2, 6.5_

- [x] 8. EID Attribution Service
  - [x] 8.1 Implement EidAttributionService
    - Emissions intensity = emissions / output; missing/invalid inputs → unavailable with reason; EID = current − baseline (Commissioning); two additive attributed quantities summing to EID; Fleet_Cohort fallback at strictly lower confidence; both baselines absent → undetermined; attach confidence [0,1] + Evidence_Chain
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_
  - [x]* 8.2 Write property test for emissions-intensity ratio
    - **Property 1: Emissions intensity is the output ratio**
    - **Validates: Requirements 1.1**
  - [x]* 8.3 Write property test for EID baseline delta
    - **Property 2: EID equals the baseline delta**
    - **Validates: Requirements 1.3**
  - [x]* 8.4 Write property test for additive EID attribution
    - **Property 3: EID attribution is additive**
    - **Validates: Requirements 1.4**
  - [x]* 8.5 Write unit tests for EID edge cases
    - Intensity unavailable on bad input (1.2); both baselines absent → undetermined (1.6)
    - _Requirements: 1.2, 1.6_

- [x] 9. ERD Quantification Service
  - [x] 9.1 Implement ErdQuantificationService
    - Single monetary amount in one declared currency; ERD = sum of available components only; accrue per elapsed operating hour from degraded onset while degraded; link each component to its source; confidence derived from EID confidence; uncomputable component excluded + reported unavailable with reason
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  - [x]* 9.2 Write property test for ERD component sum
    - **Property 6: ERD equals the sum of its available components**
    - **Validates: Requirements 2.2, 2.6**
  - [x]* 9.3 Write property test for monotonic ERD accrual
    - **Property 7: ERD accrues monotonically while degraded**
    - **Validates: Requirements 2.3**
  - [x]* 9.4 Write unit tests for ERD examples
    - Single-currency ERD (2.1); ERD-confidence derivation from EID confidence (2.5)
    - _Requirements: 2.1, 2.5_

- [x] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Pollution Monitor + Cross-Media Guardrail
  - [x] 11.1 Implement PollutionMonitor and CrossMediaGuardrail
    - Evaluate readings vs applicable regulatory threshold per pollutant+medium; no threshold → unevaluated with reason; exceedance raises a complete threshold-violation with Evidence_Chain; with ≥ min history raise anomaly on ≥ deviation threshold; below min history → deferred/insufficient-history; guardrail flags cross-media displacement with supporting readings + Evidence_Chain
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x]* 11.2 Write property test for complete threshold violation
    - **Property 9: Threshold exceedance raises a complete violation**
    - **Validates: Requirements 3.1, 3.3**
  - [x]* 11.3 Write property test for anomaly on sufficient history
    - **Property 10: Anomaly is raised exactly on sufficient-history deviation**
    - **Validates: Requirements 3.4**
  - [x]* 11.4 Write property test for cross-media displacement
    - **Property 11: Cross-media displacement is flagged on medium trade-off**
    - **Validates: Requirements 3.6, 3.7**
  - [x]* 11.5 Write unit tests for threshold/anomaly edge cases
    - No applicable threshold (3.2); insufficient anomaly-history boundary (3.5)
    - _Requirements: 3.2, 3.5_

- [x] 12. AEI + Harm-Aware Rescheduler
  - [x] 12.1 Implement AeiService
    - Assimilative-capacity forecast ≥ 24h at ≤ 1h steps when met + Receptor_Map present; else AEI undetermined with reason; AEI = harm(actual) − harm(harm-minimising same-mass schedule); recommend schedule with AEI ≤ actual; preserve total mass within 0.1%; reject mass/cross-media-increasing candidates and retain actual; report affected receptors + Evidence_Chain
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  - [x]* 12.2 Write property test for forecast horizon/granularity
    - **Property 12: AEI forecast horizon and granularity bounds**
    - **Validates: Requirements 4.1**
  - [x]* 12.3 Write property test for non-negative harm difference
    - **Property 13: AEI is the non-negative harm difference**
    - **Validates: Requirements 4.3**
  - [x]* 12.4 Write property test for rescheduling never worsening AEI
    - **Property 14: Rescheduling never worsens AEI**
    - **Validates: Requirements 4.4**
  - [x]* 12.5 Write property test for mass-preserving optimisation
    - **Property 15: Mass-preserving optimisation**
    - **Validates: Requirements 4.5, 7.5**
  - [x]* 12.6 Write property test for rejecting harm-increasing candidates
    - **Property 16: Harm-increasing candidates are rejected**
    - **Validates: Requirements 4.6**
  - [x]* 12.7 Write unit test for AEI undetermined on missing inputs
    - Edge case: missing met/receptor → undetermined with reason (4.2)
    - _Requirements: 4.2_

- [x] 13. Substitution Dossier Service
  - [x] 13.1 Implement SubstitutionDossierService
    - Infer functional role, generate 1–5 candidates; each candidate reports signed Δemissions/Δenergy/Δcost + quality risk, incentives, payback, admissibility, ripple; per-value confidence; mandatory vendor-validation flag; reasoning chain + Evidence_Chain; insufficient data → still produce, mark value unquantified hypothesis; no viable alternative → zero candidates with reason
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_
  - [x]* 13.2 Write property test for bounded candidate count
    - **Property 17: Substitution candidate count is bounded**
    - **Validates: Requirements 5.1**
  - [x]* 13.3 Write property test for complete candidate reporting
    - **Property 18: Each substitution candidate is complete**
    - **Validates: Requirements 5.2, 5.3**
  - [x]* 13.4 Write property test for mandatory vendor-validation flag
    - **Property 19: Every dossier carries the vendor-validation flag**
    - **Validates: Requirements 5.4**
  - [x]* 13.5 Write unit tests for substitution insufficient-data/no-alternative
    - Edge cases: unquantified hypothesis (5.6); zero candidates with reason (5.7)
    - _Requirements: 5.6, 5.7_

- [x] 14. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Economics Engine with Vertex AI Search grounding + BigQuery vector search
  - [x] 15.1 Implement RegulatoryGrounding over Vertex AI Search + BigQuery vector search
    - Resolve EC guideline chapter/base rate/days/deterrence from the regulatory corpus via Vertex AI Search (semantic + keyword), with BigQuery embeddings + vector search for co-located similarity; return passages + citations that feed the Evidence_Chain; a figure not retrievable → undetermined with recorded gap
    - _Requirements: 6.1, 6.7, 6.9, 10.1_
  - [x] 15.2 Implement EconomicsEngine
    - EC exposure via the verified function-calling tool (base × days × deterrence); report conflicting central/state/NGT instruments enumerating every instrument in the Evidence_Chain; assess PAT/ESCerts, IT Act §32, state policy, avoided EC; net position = incentives + avoided EC − penalty (verified tool); recommend timing maximising net position with recorded basis; label every output a cited draft, not advice
    - _Requirements: 6.2, 6.3, 6.4, 6.5, 6.6, 6.8_
  - [x]* 15.3 Write property test for EC documented formula
    - **Property 20: Environmental Compensation follows the documented formula**
    - **Validates: Requirements 6.2**
  - [x]* 15.4 Write property test for conflicting-instrument enumeration
    - **Property 21: Conflicting instruments are fully enumerated**
    - **Validates: Requirements 6.3**
  - [x]* 15.5 Write property test for net-position identity
    - **Property 22: Net position is the economics identity**
    - **Validates: Requirements 6.5**
  - [x]* 15.6 Write property test for net-position-maximising timing
    - **Property 23: Recommended timing maximises net position**
    - **Validates: Requirements 6.6**
  - [x]* 15.7 Write unit/integration tests for grounding and corpus gaps
    - EC corpus resolution against a controlled corpus (6.1); incentive-category coverage (6.4); missing corpus figure → undetermined (6.9)
    - _Requirements: 6.1, 6.4, 6.9_

- [x] 16. Embedded Pollution Passport Service
  - [x] 16.1 Implement PassportService
    - Create per-batch Passport from machine-state-conditioned actuals with confidence + Evidence_Chain; propagate downstream by adding each input's footprint in proportion to quantity consumed; use actuals not industry averages; missing actual → Fleet_Cohort_Baseline with reduced confidence + recorded substitution; mass-preserving airshed co-scheduling; cascade-risk to every dependent downstream tier; substitution ripple to every connected tier
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_
  - [x]* 16.2 Write property test for proportional footprint sum
    - **Property 24: Passport footprint is the proportional sum of consumed inputs**
    - **Validates: Requirements 7.2**
  - [x]* 16.3 Write property test for cascade-risk reachability
    - **Property 25: Cascade-risk reaches every dependent downstream tier**
    - **Validates: Requirements 7.6**
  - [x]* 16.4 Write property test for substitution ripple reachability
    - **Property 26: Substitution ripple reaches every connected tier**
    - **Validates: Requirements 7.7**
  - [x]* 16.5 Write unit test for actuals-path usage
    - Example: footprint computed from machine-state-conditioned actuals, not industry averages (7.3)
    - _Requirements: 7.3_

- [x] 17. Trajectory Certification Service
  - [x] 17.1 Implement CertificationService
    - Compute verdict from rate of improvement + verified effort history across ≥ configured min observation period; derive from ≥ 2 verified measurement points, not a snapshot; history shorter than min → withhold, insufficient-history, human review; persist certificate/confidence/Evidence_Chain; publish facility identity/certificate reference/verdict/validity window to the public registry; contradictory/unverifiable/gaming → withhold, omit from registry, record findings
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_
  - [x]* 17.2 Write property test for two-measurement-point certification
    - **Property 30: Issued certification uses at least two measurement points**
    - **Validates: Requirements 9.2**
  - [x]* 17.3 Write property test for gaming/contradiction withholding
    - **Property 31: Gaming or contradictory evidence withholds certification**
    - **Validates: Requirements 9.6**
  - [x]* 17.4 Write unit tests for certification happy path and persistence
    - Examples: certification happy path (9.1); certificate persistence/publication (9.4, 9.5); insufficient history (9.3)
    - _Requirements: 9.1, 9.3, 9.4, 9.5_

- [x] 18. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 19. Emissions-Aware Maintenance Lifecycle
  - [x] 19.1 Implement MaintenanceLifecycle
    - Forecast EID trajectory for degraded assets via a Reasoning_Agent over a configured horizon; price forecast deferral cost through ERD components (verified tools); compute Emissions_Aware_Priority combining failure risk, downtime cost, forecast pollution cost; AEI-weight the pollution-cost term where Receptor_Map + met forecast exist; rank higher-environmental-harm above higher-failure-probability-but-lower-harm with Evidence_Chain; on completion compute before/after intensity delta and record a Maintenance_Effectiveness_Record; surface records to substitution/certification; route the recommendation through the Decision_Gate; insufficient data → deferral cost undetermined with reason
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7, 15.8, 15.9, 15.10_
  - [x]* 19.2 Write property test for deferral-cost ERD pricing
    - **Property 46: Forecast deferral cost prices the trajectory through ERD**
    - **Validates: Requirements 15.2**
  - [x]* 19.3 Write property test for AEI-weighted priority monotonicity
    - **Property 47: AEI weighting is monotone in the priority**
    - **Validates: Requirements 15.4**
  - [x]* 19.4 Write property test for harm-outranks-failure-probability
    - **Property 48: Environmental harm can outrank failure probability**
    - **Validates: Requirements 15.3, 15.5**
  - [x]* 19.5 Write property test for maintenance-effectiveness delta
    - **Property 49: Maintenance effectiveness is the intensity delta**
    - **Validates: Requirements 15.6**
  - [x]* 19.6 Write unit tests for lifecycle examples and edge cases
    - Examples: agent routing (15.1); effectiveness-record completeness (15.7); effectiveness surfacing (15.8). Edge: insufficient trajectory data → undetermined (15.10)
    - _Requirements: 15.1, 15.7, 15.8, 15.10_

- [x] 20. Confidentiality / PII masking and synthetic data
  - [x] 20.1 Implement deterministic masking gate before any reasoning, persistence, or managed-service call
    - Mask PII and commercially confidential supplier identifiers before data reaches reasoning, Agent Engine prompt/Session/Memory Bank, Vertex AI Search query, BigQuery embeddings/storage, or any persistence; Gemini Flash may assist triage but the deterministic masker is authoritative; if masking cannot complete, reject the data (no reasoning, no persistence, no managed-service call) with recorded reason
    - _Requirements: 13.3, 13.4_
  - [ ] 20.7 Wire the Integrity_Gate ahead of masking on the batch ingestion path
    - On the external MES/ERP/CMMS (Ingestion & Validation) path, invoke `IntegrityGate.evaluate` for every ingested reading, telemetry, Maintenance_Event, and Job_Run BEFORE the Task 20.1 masking step and before any reasoning or persistence; enforce the invariant ordering Integrity_Gate → masking → reasoning/persistence; REJECTED/QUARANTINED records never reach masking, validation, reasoning, or storage
    - _Requirements: 16.1_
  - [x] 20.2 Implement synthetic data grounding and label propagation
    - Produce synthetic data/records grounded in published regulatory parameter ranges and real instrument structures; label synthetic and propagate the label to every derived output; surface the "not legal, tax, or engineering advice" disclaimer on every decision-oriented output and include it on export
    - _Requirements: 13.1, 13.2, 13.5, 13.6, 14.15_
  - [x]* 20.3 Write property test for synthetic label propagation
    - **Property 37: Synthetic label propagates to all derived outputs**
    - **Validates: Requirements 13.2, 14.15**
  - [x]* 20.4 Write property test for masking before use
    - **Property 38: Masking removes raw identifiers before use**
    - **Validates: Requirements 13.3**
  - [x]* 20.5 Write property test for advisory disclaimer presence
    - **Property 39: Decision-oriented outputs carry the advisory disclaimer**
    - **Validates: Requirements 6.8, 8.6, 13.5, 13.6**
  - [x]* 20.6 Write unit test for masking-failure rejection
    - Edge case: masking cannot complete → data rejected, reason recorded (13.4)
    - _Requirements: 13.4_

- [x] 21. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 22. Event-driven streaming: Pub/Sub + BigQuery continuous queries
  - [x] 22.1 Implement Pub/Sub ingestion and BigQuery continuous-query triage
    - Stream readings/telemetry/job events through Pub/Sub into BigQuery; a continuous query evaluates incoming rows against thresholds/expected profiles in near-real-time and triggers Pollution Monitor and Maintenance Lifecycle triage; masking applied before persistence; deterministic threshold/anomaly logic unchanged
    - _Requirements: 3.1, 3.3, 3.4, 15.3_
  - [ ] 22.3 Wire the Integrity_Gate ahead of masking on the Pub/Sub streaming path
    - On the Pub/Sub streaming path, pass every record through `IntegrityGate.evaluate` BEFORE masking and before it lands in BigQuery / is used in reasoning; a REJECTED/QUARANTINED record is held and never reaches the continuous-query landing table, masking, or triage; preserve the invariant ordering Integrity_Gate → masking → persistence on the streaming front door
    - _Requirements: 16.1_
  - [x]* 22.2 Write integration test for streaming triage and continuous-query fallback
    - Against Pub/Sub + continuous-query stubs: a streamed reading raises the correct threshold/anomaly condition; the batch-micro-poll fallback retains readings when the continuous query is unavailable
    - _Requirements: 3.1, 3.3, 3.4_

- [x] 23. Storage mapping across BigQuery / Cloud Storage / Firestore
  - [x] 23.1 Implement the storage split and repositories
    - BigQuery for telemetry/analytics/EID-ERD series/embeddings and continuous-query landing tables; Cloud Storage for regulatory corpus/filings/document primary sources; Firestore for public registry/certificates/audit trail with ≥ 7-year retention
    - _Requirements: 12.2, 10.4, 9.5_
  - [x]* 23.2 Write integration tests for storage routing and registry publication
    - Route 1–3 representative records per store; publish issued and withheld certificates to a Firestore emulator (withheld omitted from registry)
    - _Requirements: 12.2, 9.5, 9.6_

- [x] 24. Graceful degradation across every managed path
  - [x] 24.1 Implement DegradationPolicy with per-path safe fallbacks
    - `executeWithFallback`: on error/timeout treat the path as unavailable and run the predefined safe fallback at strictly lower confidence, recording fallback id + replaced primary id in the Evidence_Chain; no safe fallback → undetermined, route to human review, record unavailable path. Wire the per-path fallbacks: Agent Engine → direct Gemini; Vertex AI Search → BigQuery vector search; BigQuery vector → corpus scan; continuous query → batch micro-poll; Pro → Flash; Memory Bank → stateless
    - _Requirements: 12.3, 12.4, 12.5, 12.6_
  - [x]* 24.2 Write property test for fallback confidence lowering
    - **Property 4: Fallback paths strictly lower confidence**
    - **Validates: Requirements 1.5, 7.4, 12.4, 12.5, 14.10**
  - [x]* 24.3 Write integration tests for managed-path fallbacks
    - Agent-Engine-error → direct-Gemini fallback lowers confidence and records substituted path (11, 12.3–12.5); Vertex AI Search → BigQuery-vector fallback; verified-tool failure → UNAVAILABLE; no-safe-fallback → undetermined (12.6)
    - _Requirements: 12.3, 12.6_
  - [ ] 24.4 Wire integrity-path graceful degradation through DegradationPolicy
    - Reuse `executeWithFallback` for the Integrity_Gate enrichment paths: when the Instrument_Registry lookup, the Source_Authentication verification service, or a corroboration source is unavailable/times out, continue through a predefined safe fallback at a strictly lower trust/confidence and record the fallback id + replaced primary id in the Evidence_Chain; when no safe fallback is defined, mark the record undetermined and route it to human review with the unavailable path recorded; degradation is strictly trust/confidence-lowering and never lifts the hard veto or fail-closed posture
    - _Requirements: 16.17_
  - [ ]* 24.5 Write integration test for integrity-path degradation
    - With the Instrument_Registry / auth-verification / corroboration source stubbed unavailable, verify the safe fallback engages at strictly lower trust/confidence and records the substituted path (reuses Property 4), or marks the record undetermined → human review when no fallback exists
    - _Requirements: 16.17_

- [x] 25. Final integration and wiring
  - [x] 25.1 Wire the cross-cutting control flow end to end
    - Every capability produces a Determination (value + confidence + Evidence_Chain); recommendations route through the Decision_Gate; decision-oriented outputs trigger synchronous audit persistence and carry the disclaimer; capabilities invoke the orchestrator which drives Agent Engine with masked inputs, verified tools, and grounding
    - _Requirements: 8.1, 10.3, 11.1, 13.5_
  - [ ] 25.3 Confirm the Integrity_Gate → masking → reasoning/persistence ordering end to end
    - Verify the Integrity_Gate runs ahead of masking on BOTH the batch and Pub/Sub ingestion paths, that the per-input Data_Trust_Score flows into derived-value confidence and caps the Decision_Gate classification at ASK when sub-threshold, and that no managed-service call or persistence ever precedes the gate; wire the Data_Trust_Score as the governing trust into the Decision_Gate `TrustContext` at every recommendation site
    - _Requirements: 16.1, 16.6, 16.15, 16.16_
  - [x]* 25.2 Write smoke tests for platform and model routing
    - Services start on Cloud Run with Vertex AI/Agent Engine reachable; Memory Bank/Sessions store masked context only; two-tier routing binds deep reasoning to Pro and classification to Flash; audit retention ≥ 7 years
    - _Requirements: 11.1, 12.1, 10.4_

- [x] 26. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 27. Integrity_Gate: data authenticity, integrity, and source trust
  - [ ] 27.1 Implement SourceAuthenticator
    - Verify each record's Source_Authentication via a signed payload or an authenticated device/gateway credential; a failed or absent credential → REJECT (not reasoned over, not persisted, reason recorded); a valid-but-below-full-trust credential → admit at a strictly reduced Data_Trust_Score with the reduced-trust reason in the Evidence_Chain; authentication never yields full trust on a failed credential
    - _Requirements: 16.1, 16.2, 16.3_
  - [ ] 27.2 Implement InstrumentRegistry lookup and calibration/health gating
    - Look up a registered source's `CalibrationWindow` and health status; an expired Calibration_Window, DEGRADED/FAILED health, or unregistered source → flag and assign a strictly lower confidence than a registered instrument with a current window and HEALTHY status, with the flag reason in the Evidence_Chain; propagate the reduced trust into the confidence of any EID/ERD/Embedded Pollution Passport/Machine_Health_State value computed from a flagged/reduced-trust record (trust acts as an upper bound on derived confidence)
    - _Requirements: 16.4, 16.5, 16.6_
  - [ ] 27.3 Implement replay/freshness integrity checks and tamper-evident append
    - Detect duplicate/replayed records against previously ingested records; raise an integrity condition for a timestamp not monotonically later than the latest accepted record for the same source or outside the configured freshness bound; reject or quarantine with a recorded reason; append accepted readings/telemetry to the Operating_Log via the tamper-evident hash chain from Task 2.10
    - _Requirements: 16.7, 16.8, 16.9_
  - [ ] 27.4 Implement ActorAuthorizer for Maintenance_Events
    - Verify the acting technician/system of a Maintenance_Event is an Authorized_Actor for the referenced Machine_Asset before the event may touch the ERD degraded-onset clock; an unauthorized or unauthenticated actor → reject/quarantine with a reason and leave the degraded-health-state onset for that Machine_Asset unchanged (closes the forge-maintenance-to-reset-ERD vector)
    - _Requirements: 16.10, 16.11_
  - [ ] 27.5 Implement CorroborationChecker (cross-signal plausibility)
    - Reconcile a reading against correlated signals (energy consumed, output quantity, redundant sensors) independently of the Req 3.4 statistical anomaly path; a physically implausible or internally inconsistent reading → raise an implausibility condition, attach the conflicting signals and an Evidence_Chain, and reduce the Data_Trust_Score
    - _Requirements: 16.12, 16.13_
  - [ ] 27.6 Implement the IntegrityGate facade and Data_Trust_Score computation
    - Compose SourceAuthenticator + InstrumentRegistry + IntegrityChecks + ActorAuthorizer + CorroborationChecker behind a single `evaluate(IngestedRecord)` entry point; emit a per-record `RecordAdmission` (ADMIT | ADMIT_REDUCED_TRUST | QUARANTINE | REJECT) with a recorded reason for every non-ADMIT/reduced-trust outcome; compute the `DataTrustScore` in [0,1] from the authentication, calibration/health, integrity-check, and corroboration outcomes and attach it plus an Evidence_Chain to the record
    - _Requirements: 16.14_
  - [ ]* 27.7 Write property test for failed source authentication never at full trust
    - **Property 50: Failed source authentication is never admitted at full trust**
    - **Validates: Requirements 16.2**
  - [ ]* 27.8 Write property test for degraded source conditions lowering trust
    - **Property 51: Degraded source conditions strictly lower the Data_Trust_Score**
    - **Validates: Requirements 16.3, 16.5**
  - [ ]* 27.9 Write property test for reduced trust bounding derived confidence
    - **Property 52: Reduced trust bounds derived-value confidence**
    - **Validates: Requirements 16.6**
  - [ ]* 27.10 Write property test for replay/stale/non-monotonic records not admitted as fresh
    - **Property 53: Duplicate, replayed, stale, or non-monotonic records are not admitted as fresh**
    - **Validates: Requirements 16.7, 16.8**
  - [ ]* 27.11 Write property test for unauthorized actor not resetting ERD onset
    - **Property 55: Unauthorized maintenance actor cannot reset the ERD onset**
    - **Validates: Requirements 16.10, 16.11**
  - [ ]* 27.12 Write property test for implausible readings reducing trust
    - **Property 56: Implausible readings reduce trust and record conflicting signals**
    - **Validates: Requirements 16.12, 16.13**
  - [ ]* 27.13 Write unit/integration tests for Integrity_Gate lookups and wiring
    - Examples: Integrity_Gate-before-masking ordering (16.1); Instrument_Registry calibration/health lookup (16.4); authorized-actor lookup (16.10); corroboration independence from the anomaly path (16.12)
    - _Requirements: 16.1, 16.4, 16.10, 16.12_

- [ ] 28. Detection Hardening: Baseline Plausibility, Independent Verification, Trust-Weighted Profile, Anchored Hash Root (Req 17)
  - [ ] 28.1 Implement shared Req 17 data models
    - Implement `BaselinePlausibilityVerdict` (PlausibilityOutcome PLAUSIBLE | FLAGGED_REDUCED_CONFIDENCE + confidence + routedToHumanReview + reason + Evidence_Chain), `IndependentVerificationResult` (ReconciliationOutcome CORROBORATED_INDEPENDENT | DISCREPANCY | NO_INDEPENDENT_SOURCE + independentValue + trustWeight + Evidence_Chain) and the configured `CORROBORATION_ONLY_TRUST_CEILING` constant (strictly < 1.0), `TrustWeightedProfile`/`AdmittedReading` (contributingReadings, trustWeightingThreshold), and `AnchoredHashRoot`/`AnchorLocationRef` (machine, checkpointAt, hashRoot, location) as records/enums
    - _Requirements: 17.1, 17.6, 17.10, 17.12_
  - [ ] 28.2 Implement BaselinePlausibilityChecker and wire into Machine_Asset registration
    - Extends the Task 2.2 registration flow (does not duplicate it): at Commissioning_Baseline proposal time, screen against the Fleet_Cohort_Baseline range and the asset class's declared engineering/manufacturer limits; within the configured tolerance → `PLAUSIBLE`, admitted without routing to human review on that basis alone; outside tolerance → `FLAGGED_REDUCED_CONFIDENCE` (never a silent reject), confidence strictly lower than `PLAUSIBLE`, routed to human review, with the cohort range + declared limits used recorded in the Evidence_Chain; propagate the flagged baseline's reduced confidence into any EID/ERD computed from it until the verdict clears human review
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_
  - [ ]* 28.3 Write property test for out-of-tolerance baseline admission
    - **Property 60: An out-of-tolerance baseline is never admitted at full confidence**
    - **Validates: Requirements 17.2, 17.3, 17.4**
  - [ ]* 28.4 Write unit tests for baseline plausibility wiring and propagation
    - Examples/edge cases: Baseline_Plausibility_Check wired at Machine_Asset registration (17.1); in-tolerance baseline admitted without review on that basis alone (17.3); a flagged, not-yet-cleared baseline's reduced confidence bounding EID/ERD computed from it (17.5)
    - _Requirements: 17.1, 17.3, 17.5_
  - [ ] 28.5 Implement IndependentVerificationReconciler and wire into the Integrity_Gate Data_Trust_Score computation
    - Extends Task 27.5 (CorroborationChecker) / 27.6 (IntegrityGate facade), does not duplicate the Req 16.12-16.13 self-reported corroboration path: reconcile a self-reported reading against an Independent_Verification_Source when one is available; a divergence beyond the configured tolerance raises `DISCREPANCY` carrying both values + an Evidence_Chain linking to both; a `CORROBORATED_INDEPENDENT` outcome yields a trust weight strictly higher than the same reading would receive from self-reported-only corroboration; when no Independent_Verification_Source exists, record the absence and cap the Data_Trust_Score at the configured `corroborationOnlyTrustCeiling` strictly below full trust
    - _Requirements: 17.6, 17.7, 17.8, 17.9_
  - [ ]* 28.6 Write property test for corroboration-only trust ceiling and exogenous corroboration
    - **Property 61: Corroboration-only trust never exceeds the configured ceiling**
    - **Validates: Requirements 17.7, 17.8, 17.9**
  - [ ]* 28.7 Write unit tests for Independent_Verification_Source wiring
    - Examples/edge cases: reconciliation triggered only when a source is available (17.6); absence recorded and Data_Trust_Score capped at the configured ceiling when no source exists (17.9)
    - _Requirements: 17.6, 17.9_
  - [ ] 28.8 Implement TrustWeightedProfileBuilder and wire into Pollution Monitor anomaly detection
    - Extends Task 11.1 (does not duplicate the Pollution Monitor implementation): build/update the Req 3.4 expected profile as a `TrustWeightedProfile` using only readings whose Data_Trust_Score is at or above the configured `trustWeightingThreshold`, weighted by each reading's own score; an `ADMIT_REDUCED_TRUST` reading is excluded from `contributingReadings` (never shapes the profile) but is still passed to `evaluateAgainst` the current profile for Req 3.4 anomaly evaluation
    - _Requirements: 17.10, 17.11_
  - [ ]* 28.9 Write property test for trust-weighted profile exclusion
    - **Property 62: Reduced-trust readings are evaluated against, but never shape, the Trust_Weighted_Profile**
    - **Validates: Requirements 17.10, 17.11**
  - [ ] 28.10 Implement HashRootAnchorService over the tamper-evident Operating_Log chain
    - Extends Task 2.10 (does not duplicate the hash-chain append): `publish` periodically sends the current `TamperEvidentLogEntry` chain's hash root to an external, operator-uninfluenced anchoring location at a configured interval; `mostRecentAnchor` exposes the latest published root; `verify` recomputes the live chain to the anchored checkpoint and raises `tamperDetected = true` with the diverging Machine_Asset Operating_Log + the affected `AnchoredHashRoot` recorded in the Evidence_Chain and routed to human review on mismatch
    - _Requirements: 17.12, 17.13, 17.14_
  - [ ]* 28.11 Write property test for anchor verification tamper detection
    - **Property 63: Anchor verification detects any pre-checkpoint chain mutation**
    - **Validates: Requirements 17.14**
  - [ ]* 28.12 Write unit tests for anchor publish/retrieval scheduling
    - Examples: periodic anchor-publish scheduling (17.12); most-recent-anchor retrieval for independent verification (17.13)
    - _Requirements: 17.12, 17.13_
  - [ ] 28.13 Extend graceful degradation for the two Req 17 enrichment paths
    - Extends Task 24.1 `DegradationPolicy` (does not duplicate it): Independent_Verification_Source unavailable → proceed with self-reported-only corroboration capped at the configured `corroborationOnlyTrustCeiling`, recording the absence (never promoted to full trust); external anchoring sink unavailable at a scheduled interval → retain the unpublished hash root for the next scheduled attempt and record the missed attempt (never silently skip or fabricate a checkpoint)
    - _Requirements: 17.9, 17.15_
  - [ ] 28.15 Extend the Decision_Gate trust-cap composition to guard Req 17 non-interference
    - Extends Task 4.5's `TrustContext`/trust-cap step (does not reorder or duplicate it): verify that none of the BaselinePlausibilityChecker, IndependentVerificationReconciler, TrustWeightedProfileBuilder, or HashRootAnchorService outcomes can raise a Decision_Gate classification or a confidence value beyond what Req 8 and Req 16.15-16.16 already allow — each may only add a downgrade, a flag, or a human-review routing on top of the existing fail-closed posture
    - _Requirements: 17.16_
  - [ ]* 28.14 Write integration test for Req 17 enrichment-path degradation
    - With the Independent_Verification_Source and the external anchoring sink stubbed unavailable, verify the capped/retained-and-retried behaviour engages correctly (reuses Property 4) rather than failing the reading or the checkpoint
    - _Requirements: 17.9, 17.15_
  - [ ]* 28.16 Write property test for detection-hardening non-interference
    - **Property 64: Detection-hardening checks only ever downgrade, never upgrade**
    - **Validates: Requirements 17.16**

- [ ] 29. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP.
- Each task references specific requirements (granular sub-requirement clauses) for traceability.
- Checkpoints ensure incremental validation at reasonable breaks.
- Property-based tests (jqwik, `@Property(tries = 100)`, tagged **Feature: terravow, Property N: ...**)
  validate the universal Correctness Properties 1–64; unit/integration/smoke tests cover examples,
  edge cases, external-service wiring, and configuration per the design's Testing Strategy.
- The managed Google services (Agent Engine, Vertex AI Search, BigQuery vector search, Pub/Sub +
  continuous queries, two-tier Gemini) are platform substrates for existing behaviours; the safety
  invariants they must not weaken are enforced by Properties 4, 8, 27–29, 35–36, and 38.
- The Integrity_Gate (Req 16, Task 27) is the ingestion trust boundary that runs AHEAD of masking on
  both the batch and Pub/Sub paths (ordering: Integrity_Gate → masking → reasoning/persistence). It
  only adds a trust floor — Properties 50–59 cover its authentication/trust-monotonicity,
  confidence-propagation, replay/freshness, tamper-evident-log, unauthorized-actor, implausibility,
  trust-score-bound, and Decision_Gate-capping/composition invariants, and its graceful degradation
  (16.17) reuses Property 4. It never weakens Properties 4, 8, or 27–29.
- Task 28 (Req 17) is a second, narrower defense-in-depth layer over the Req 16 Integrity_Gate:
  Baseline_Plausibility_Check extends Task 2's registration flow, Independent_Verification_Source
  reconciliation extends Task 27's corroboration/Data_Trust_Score computation, the Trust_Weighted_Profile
  extends Task 11.1's Pollution Monitor anomaly detection, and the Hash_Root_Anchor_Service extends
  Task 2.10's tamper-evident log — none of these duplicate their base task's implementation. Properties
  60–64 cover baseline-plausibility, corroboration-ceiling/exogenous-corroboration, trust-weighted-profile
  exclusion, anchored-checkpoint tamper detection, and non-interference-with-the-Decision_Gate; the two
  new enrichment-path degradations (17.9, 17.15) reuse Property 4, consistent with how 16.17 was folded in.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "1.4", "2.1", "3.1", "4.1"] },
    { "id": 3, "tasks": ["1.5", "2.2", "2.3", "3.2", "3.3", "3.4", "3.5", "4.2", "4.3", "4.4", "4.5"] },
    { "id": 4, "tasks": ["2.4", "2.5", "2.6", "2.7", "2.8", "2.9", "2.10", "4.6", "4.7", "6.1", "27.1", "27.2", "27.4", "27.5"] },
    { "id": 5, "tasks": ["2.11", "6.2", "6.3", "27.3"] },
    { "id": 6, "tasks": ["6.4", "6.5", "6.6", "7.1", "7.2", "27.6", "27.7", "27.8", "27.9", "27.10", "27.11", "27.12"] },
    { "id": 7, "tasks": ["27.13", "8.1", "11.1", "12.1", "13.1", "16.1", "17.1"] },
    { "id": 8, "tasks": ["8.2", "8.3", "8.4", "8.5", "9.1", "11.2", "11.3", "11.4", "11.5", "12.2", "12.3", "12.4", "12.5", "12.6", "12.7", "13.2", "13.3", "13.4", "13.5", "16.2", "16.3", "16.4", "16.5", "17.2", "17.3", "17.4"] },
    { "id": 9, "tasks": ["9.2", "9.3", "9.4", "15.1"] },
    { "id": 10, "tasks": ["15.2"] },
    { "id": 11, "tasks": ["15.3", "15.4", "15.5", "15.6", "15.7", "19.1"] },
    { "id": 12, "tasks": ["19.2", "19.3", "19.4", "19.5", "19.6", "20.1"] },
    { "id": 13, "tasks": ["20.2", "20.7"] },
    { "id": 14, "tasks": ["20.3", "20.4", "20.5", "20.6", "22.1", "22.3", "23.1"] },
    { "id": 15, "tasks": ["22.2", "23.2", "24.1"] },
    { "id": 16, "tasks": ["24.2", "24.3", "24.4", "25.1", "28.1"] },
    { "id": 17, "tasks": ["24.5", "25.2", "25.3", "28.2", "28.5", "28.8", "28.10"] },
    { "id": 18, "tasks": ["28.3", "28.4", "28.6", "28.7", "28.9", "28.11", "28.12", "28.13", "28.15"] },
    { "id": 19, "tasks": ["28.14", "28.16"] }
  ]
}
```
