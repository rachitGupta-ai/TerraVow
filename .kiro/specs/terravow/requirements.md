# Requirements Document

## Introduction

TerraVow is an AI-powered environmental compliance, remediation-advisory, and certification platform for manufacturing supply chains, built on Google Cloud (Gemini on Vertex AI, BigQuery, Cloud Storage, Cloud Run, Firestore). Its tagline is "Enforce. Certify. Track."

The platform rests on a single core thesis: machine degradation is an invisible emissions event that propagates through supply chains as embedded pollution that nobody prices. Every deferred-maintenance decision is therefore an undeclared, unpriced emissions decision. Conventional MES, ERP, CMMS, and ESG systems optimise downtime cost or produce threshold-based compliance reports; none connect maintenance state to emission intensity, to supply-chain embedded footprint, to accruing regulatory liability.

TerraVow links machine health state to emissions intensity (EID), prices the resulting compounding financial liability (ERD), monitors air and water pollution with a cross-media displacement guardrail, measures harm caused by emission timing rather than quantity (AEI), synthesises sustainable substitution counterfactuals, reasons over real Indian regulatory and incentive instruments to produce cited drafts, propagates a machine-health-adjusted footprint through the supply chain (Embedded Pollution Passport), gates every recommendation with confidence-bounded ALLOW/ASK/BLOCK decisions and a hard veto against increasing harm, and certifies facilities on verified improvement trajectory into a public global registry. Every number the platform produces is a counterfactual or a legal/engineering interpretation that does not exist in any database; each is reasoned by Gemini and carries a machine-checkable evidence and citation chain back to a primary source.

This document specifies requirements for the full platform vision. Outputs that touch legal, tax, or engineering determinations are cited drafts for qualified professional review and are never authoritative sign-off.

## Glossary

- **TerraVow_System**: The complete AI-powered environmental compliance, remediation-advisory, and certification platform specified in this document.
- **EID (Emissions-Intensity Degradation)**: The quantity of emissions per unit of output attributable to a machine's current health state measured against its commissioning baseline, isolating the delta that is a maintenance decision rather than a process necessity.
- **ERD (Environmental Regulatory Debt)**: The compounding financial liability of operating degraded or legacy equipment, composed of expected environmental-compensation exposure, excess energy cost, forgone energy-saving-certificate value, forgone tax depreciation benefit, and cross-media treatment load cost.
- **AEI (Avoidable Exposure Index)**: The population-exposure harm attributable to the timing of an emission rather than its mass, measured as the difference between the harm of the actual emission schedule and the harm of the same total mass emitted at an optimally timed schedule. Expressed in person-microgram-per-cubic-metre-hours.
- **Substitution Dossier**: An AI-synthesised counterfactual for replacing a machine or process step, reporting change in emissions, change in energy, change in cost, product-quality risk, applicable incentives, payback period, regulatory admissibility, and supply-chain ripple, carrying a mandatory "requires vendor validation and process-safety review" flag.
- **Trajectory Certification**: A facility certification based on verified rate of improvement and effort history over time rather than a single-point-in-time snapshot, designed to be resistant to metric-gaming.
- **Embedded Pollution Passport**: A per-batch, machine-health-adjusted emissions footprint record that propagates tier to tier through the supply chain, replacing industry-average Scope-3 emission factors with machine-state-conditioned actual values.
- **Environmental_Compensation (EC)**: The financial compensation imposed by Indian environmental regulators for violations, whose documented formula shape is base rate multiplied by number of days of violation multiplied by a deterrence factor, with per-violation-class guideline chapters.
- **ESCerts (Energy Saving Certificates)**: Tradeable certificates issued under the Bureau of Energy Efficiency Perform-Achieve-Trade scheme to designated consumers that beat their Specific Energy Consumption reduction targets.
- **Commissioning_Baseline**: The reference emissions-intensity and energy-consumption profile of a machine at its original commissioning, used as the comparison point for EID.
- **Fleet_Cohort_Baseline**: A substitute reference profile derived from comparable machines, used when a machine's Commissioning_Baseline data is absent, carrying lower confidence.
- **Cross_Media_Guardrail**: The reasoning check that detects when a remediation reduces harm in one environmental medium while increasing it in another (air to water to solid waste).
- **Evidence_Chain**: A machine-checkable set of links from a produced value to the specific primary-source readings, filings, or regulatory text that support it.
- **Decision_Gate**: The confidence-bounded control that classifies every recommendation as ALLOW, ASK, or BLOCK and enforces the hard veto against increasing total emission mass or cross-media load.
- **Reasoning_Agent**: A Gemini-on-Vertex-AI reasoning unit that produces a schema-validated structured determination for a specific capability.
- **Receptor_Map**: A geographic map of downwind sensitive receptors such as schools, hospitals, and residential density used to weight population exposure.
- **Machine_Asset**: A tracked machine registered in the TerraVow_System with a stable identifier, asset class, facility location, and commissioning date, serving as the unit to which maintenance events, job runs, and health state are attributed.
- **Maintenance_Event**: A recorded maintenance occurrence on a Machine_Asset, classified as preventive (scheduled) or corrective (unscheduled/breakdown), carrying the maintenance action, affected components, start and end timestamps, downtime duration, and the acting technician or system identifier.
- **Maintenance_Schedule**: The planned set of due maintenance actions for a Machine_Asset, against which deferral and overdue status are detected.
- **Deferral_Period**: The elapsed time between the due time of a scheduled maintenance action and its completion (or the present time while still outstanding), used as the degraded-health-state period basis for ERD accrual.
- **Job_Run**: A single production job or run executed on a Machine_Asset, carrying a job identifier, machine reference, start and end timestamps, the batch produced, output quantity, and operating parameters such as load, throughput, and energy consumed.
- **Machine_Health_State**: A time-stamped assessment of a Machine_Asset's condition derived from telemetry and maintenance history, carrying a health score, the contributing factors, the onset timestamp of the current degraded-health-state period, and a confidence value.
- **Operating_Log**: An append-only per-machine record that ties together telemetry, Maintenance_Events, Job_Runs, and Machine_Health_State transitions so that every EID, ERD, and Embedded Pollution Passport value can be traced to its source records.
- **Maintenance_Effectiveness_Record**: A verified, observed record of the change in emissions intensity attributable to a completed Maintenance_Event on a given asset class, accumulated over time into a facility-specific maintenance-effectiveness knowledge base.
- **Emissions_Aware_Priority**: A maintenance-queue ranking that combines failure risk, downtime cost, and the forecast pollution cost of continued deferral (EID trajectory priced through ERD and weighted by AEI).
- **Integrity_Gate**: The ingestion-time control that establishes the authenticity, integrity, and source trust of every ingested reading, telemetry, Maintenance_Event, and Job_Run record before masking and before any reasoning or persistence, running ahead of the PII masking step. The Integrity_Gate rejects, quarantines, or admits-at-reduced-trust each record and records a reason for every non-admission.
- **Instrument_Registry**: The registry of detectors and data sources known to the TerraVow_System, in which each registered source carries a stable source identity, a Calibration_Window, and a health status of healthy, degraded, or failed.
- **Calibration_Window**: The validity interval during which a registered instrument's readings are treated as calibrated, bounded by a calibration start timestamp and a calibration expiry timestamp.
- **Data_Trust_Score**: A per-input value in the range 0 to 1 inclusive computed by the Integrity_Gate from the source-authentication outcome, the calibration and health status, the integrity-check outcome, and the cross-source corroboration outcome of an ingested record, used to bound the confidence of downstream results and the classification the Decision_Gate may assign.
- **Authorized_Actor**: A technician or system identity recorded in the TerraVow_System as permitted to perform maintenance on a specific Machine_Asset, against which the acting technician or system identifier of a Maintenance_Event is checked.
- **Source_Authentication**: The verification of a record's source identity through a signed payload or an authenticated device or gateway credential presented with the ingested record.
- **Baseline_Plausibility_Check**: The screening of a proposed Commissioning_Baseline, performed at machine registration time, against the Fleet_Cohort_Baseline range and the declared engineering/manufacturer limits for its asset class, used to detect an implausible baseline without silently rejecting a legitimate outlier.
- **Independent_Verification_Source**: An operator-uninfluenced data source external to the monitored facility's own reporting chain — such as third-party satellite or remote-sensing emissions estimates, independent utility meter readings, or regulator-held reference data — used to reconcile self-reported readings against a source the monitored party cannot influence.
- **Trust_Weighted_Profile**: The expected profile used for the Requirement 3.4 anomaly detection, constructed only from readings whose Data_Trust_Score is at or above the configured trust-weighting threshold and weighted by each contributing reading's Data_Trust_Score.
- **Anchored_Hash_Root**: A periodically published checkpoint of the current hash root of the TamperEvidentLogEntry chain, recorded at an externally-verifiable, operator-uninfluenced location, against which the live chain can be independently verified to detect retroactive modification predating the checkpoint.

## Requirements

### Requirement 1: Emissions-Intensity Degradation (EID) Attribution

**User Story:** As a plant reliability engineer, I want excess emissions-per-unit-output attributed to machine health state, so that I can distinguish negligence emissions from unavoidable process emissions.

#### Acceptance Criteria

1. WHEN machine telemetry and production output are ingested for a machine, THE TerraVow_System SHALL compute the emissions intensity as emissions per unit of output for that machine.
2. IF machine telemetry or production output required to compute emissions intensity is absent or fails validation for a machine, THEN THE TerraVow_System SHALL report the emissions intensity as unavailable for that machine and record the reason.
3. WHEN a Commissioning_Baseline exists for a machine, THE TerraVow_System SHALL compute the EID as the delta between the current emissions intensity and the Commissioning_Baseline emissions intensity.
4. WHEN an EID is computed, THE TerraVow_System SHALL report two additive attributed quantities whose sum equals the EID: the emissions quantity attributable to the machine health state and the emissions quantity attributable to unavoidable process necessity.
5. IF a Commissioning_Baseline is absent and a Fleet_Cohort_Baseline is available for a machine, THEN THE TerraVow_System SHALL compute the EID against the Fleet_Cohort_Baseline and assign a confidence value strictly lower than the confidence value it would assign to the same result computed against a Commissioning_Baseline.
6. IF both a Commissioning_Baseline and a Fleet_Cohort_Baseline are absent for a machine, THEN THE TerraVow_System SHALL report the EID as undetermined for that machine and record the reason.
7. WHEN an EID result is produced, THE TerraVow_System SHALL attach a confidence value in the range 0 to 1 inclusive and an Evidence_Chain to the result.

### Requirement 2: Environmental Regulatory Debt (ERD) Quantification

**User Story:** As a chief financial officer, I want the compounding financial liability of running degraded equipment expressed as a single accruing number, so that deferred maintenance appears on the balance sheet rather than remaining a hidden engineering choice.

#### Acceptance Criteria

1. WHEN an EID result is available for a machine, THE TerraVow_System SHALL compute an ERD value for that machine expressed as a single monetary amount in one declared currency unit.
2. WHEN the TerraVow_System computes an ERD value, THE TerraVow_System SHALL compute each ERD component — expected Environmental_Compensation exposure, excess energy cost, forgone ESCerts value, forgone tax depreciation benefit, and cross-media treatment load cost — as a monetary amount and SHALL set the ERD value equal to the sum of its available components.
3. WHILE a machine continues to operate at a degraded health state, THE TerraVow_System SHALL increase the accrued ERD value for each elapsed operating hour, measured from the start of the current degraded-health-state period.
4. WHEN an ERD value is produced, THE TerraVow_System SHALL attach an Evidence_Chain linking each ERD component to its source instrument or computed input.
5. WHEN an ERD value is produced, THE TerraVow_System SHALL attach a confidence value derived from the confidence value of the EID result the ERD was computed from.
6. IF an ERD component cannot be computed from the available data, THEN THE TerraVow_System SHALL exclude that component from the ERD sum, report that component as unavailable, and record the reason.

### Requirement 3: Air and Water Pollution Monitoring with Cross-Media Guardrail

**User Story:** As an environmental compliance officer, I want stack, effluent, and sensor readings monitored for threshold and anomaly conditions with cross-media displacement detection, so that a fix that merely relocates harm is not treated as an improvement.

#### Acceptance Criteria

1. WHEN a stack, effluent, or sensor reading is ingested, THE TerraVow_System SHALL evaluate the reading against the applicable regulatory threshold for that reading's pollutant and environmental medium.
2. IF an ingested reading has no applicable regulatory threshold available, THEN THE TerraVow_System SHALL record the reading as unevaluated-for-threshold and record the reason.
3. IF a reading exceeds its applicable regulatory threshold, THEN THE TerraVow_System SHALL raise a threshold-violation condition carrying the pollutant, the environmental medium, the measured value, the exceeded threshold value, and an Evidence_Chain linking to the reading.
4. WHILE a reading stream has accumulated at least the configured minimum number of readings required to establish an expected profile, WHEN a subsequent reading is ingested for that stream, THE TerraVow_System SHALL evaluate the reading against the expected profile for that stream and SHALL raise an anomaly condition when the reading deviates from the expected profile by at least the configured anomaly-deviation threshold.
5. IF a reading stream has fewer than the configured minimum number of readings required to establish an expected profile, THEN THE TerraVow_System SHALL defer anomaly evaluation for that stream and record the stream as insufficient-history for anomaly detection.
6. WHEN a remediation reduces a pollutant load in one environmental medium, THE Cross_Media_Guardrail SHALL evaluate whether the pollutant load increased in another environmental medium.
7. IF a remediation reduces pollutant load in one medium while increasing pollutant load in another medium, THEN THE TerraVow_System SHALL flag the remediation as cross-media displacement and attach the supporting readings and an Evidence_Chain.

### Requirement 4: Avoidable Exposure Index (AEI) and Harm-Aware Rescheduling

**User Story:** As a production scheduler, I want harm from emission timing measured and flexible operations rescheduled to reduce avoidable population exposure, so that identical emissions cause less public-health harm at zero additional cost.

#### Acceptance Criteria

1. WHEN meteorological forecast data and a Receptor_Map are available for a facility, THE TerraVow_System SHALL compute an airshed assimilative-capacity forecast over a horizon of at least 24 hours in time steps no coarser than 1 hour.
2. IF meteorological forecast data or a Receptor_Map is unavailable for a facility, THEN THE TerraVow_System SHALL report the AEI as undetermined for that facility and record the reason.
3. WHEN an emission schedule is evaluated, THE TerraVow_System SHALL compute the AEI, expressed in person-microgram-per-cubic-metre-hours, as the difference between the population-exposure harm of the actual schedule and the population-exposure harm of the harm-minimising schedule of the same total emission mass over the forecast horizon subject to the facility production constraints.
4. WHEN a facility has operations whose emission timing can be shifted within the facility production constraints, THE TerraVow_System SHALL recommend a revised schedule whose resulting AEI is no greater than the AEI of the actual schedule.
5. THE TerraVow_System SHALL ensure that every rescheduling recommendation preserves the total emission mass of the affected operations within a tolerance of 0.1 percent.
6. IF a candidate rescheduling recommendation would increase the total emission mass beyond the 0.1 percent tolerance or would increase the cross-media load, THEN THE TerraVow_System SHALL reject that candidate recommendation and retain the actual schedule.
7. WHEN an AEI result is produced, THE TerraVow_System SHALL report the affected receptors drawn from the Receptor_Map and attach an Evidence_Chain linking the result to the meteorological forecast and receptor inputs.

### Requirement 5: Sustainable Substitution Dossier Synthesis

**User Story:** As an operations director, I want an AI-synthesised counterfactual for replacing a machine or process step, so that I can evaluate a sustainable alternative against emissions, cost, quality risk, incentives, and payback before committing capital.

#### Acceptance Criteria

1. WHEN a user requests a substitution analysis for a machine or process step, THE TerraVow_System SHALL infer the functional role of the machine or process step and generate between 1 and 5 candidate alternatives.
2. WHEN a Substitution Dossier is generated, THE TerraVow_System SHALL report, for each candidate alternative, the change in emissions, change in energy, and change in cost each as a signed value relative to the incumbent machine or process step, together with the product-quality risk, applicable incentives, payback period, regulatory admissibility, and supply-chain ripple.
3. WHEN a Substitution Dossier is generated, THE TerraVow_System SHALL attach a confidence value to each reported value for each candidate alternative.
4. THE TerraVow_System SHALL attach a "requires vendor validation and process-safety review" flag to every Substitution Dossier.
5. WHEN a Substitution Dossier is produced, THE TerraVow_System SHALL attach the reasoning chain and an Evidence_Chain supporting each reported value.
6. IF the available data is insufficient to quantify a reported value in a Substitution Dossier, THEN THE TerraVow_System SHALL still produce the Substitution Dossier, mark that value as an unquantified hypothesis, and record the reason the value could not be quantified.
7. IF no viable candidate alternative can be synthesised for the requested machine or process step, THEN THE TerraVow_System SHALL return the substitution analysis with zero candidate alternatives and record the reason no alternative was generated.

### Requirement 6: Economics Engine over Real Regulatory and Incentive Instruments

**User Story:** As a compliance and finance analyst, I want penalty exposure and incentive eligibility reasoned from real regulatory instrument structures with citations, so that I receive an auditable cited draft rather than invented figures.

#### Acceptance Criteria

1. WHEN a specific violation fact pattern is submitted, THE TerraVow_System SHALL determine the applicable Environmental_Compensation guideline chapter, base rate, days of violation, and deterrence factor from the regulatory corpus.
2. WHEN computing an Environmental_Compensation exposure, THE TerraVow_System SHALL apply the documented formula shape of base rate multiplied by number of days of violation multiplied by deterrence factor.
3. IF central and state regulatory instruments or National Green Tribunal determinations conflict for a fact pattern, THEN THE TerraVow_System SHALL report the conflict and identify every conflicting instrument in the Evidence_Chain.
4. WHEN an incentive eligibility is assessed, THE TerraVow_System SHALL evaluate PAT and ESCerts eligibility, Income Tax Act Section 32 depreciation eligibility, state industrial-policy incentives, and avoided Environmental_Compensation exposure.
5. WHEN penalty exposure and incentive values are available, THE TerraVow_System SHALL compute a net-position value equal to the total incentive value plus avoided Environmental_Compensation exposure minus the penalty exposure.
6. WHEN a net-position value is available over the evaluated timing horizon, THE TerraVow_System SHALL recommend the replacement timing that maximises the net-position value and record the basis for that timing.
7. THE TerraVow_System SHALL attach a citation to the primary source for every regulatory figure, rate, factor, and eligibility conclusion it produces.
8. THE TerraVow_System SHALL label every economics-engine output as a cited draft for qualified professional review that is not legal or tax advice.
9. IF a required regulatory figure cannot be located in the regulatory corpus, THEN THE TerraVow_System SHALL report the figure as undetermined and record the gap.

### Requirement 7: Embedded Pollution Passport and Supply-Chain Propagation

**User Story:** As a supply-chain sustainability lead, I want a machine-health-adjusted footprint carried per batch and propagated across tiers, so that Scope-3 accounting reflects machine-state-conditioned actuals rather than industry averages.

#### Acceptance Criteria

1. WHEN a batch is produced, THE TerraVow_System SHALL create an Embedded Pollution Passport for that batch carrying the machine-health-adjusted emissions footprint, a confidence value, and an Evidence_Chain linking the footprint to its contributing machine-state readings.
2. WHEN a batch with an Embedded Pollution Passport is consumed by a downstream tier, THE TerraVow_System SHALL add the embedded footprint of each consumed input batch to the Embedded Pollution Passport of the resulting downstream batch in proportion to the quantity of each input batch consumed.
3. THE TerraVow_System SHALL compute the embedded footprint from machine-state-conditioned actual values rather than industry-average emission factors.
4. IF a machine-state-conditioned actual value required for the embedded footprint is absent for a batch, THEN THE TerraVow_System SHALL compute that portion of the footprint from a Fleet_Cohort_Baseline, assign a reduced confidence value to the Embedded Pollution Passport, and record the substituted input in the Evidence_Chain.
5. WHEN two or more facilities share an airshed, THE TerraVow_System SHALL evaluate airshed co-scheduling across those facilities and report a co-scheduling recommendation that preserves the total emission mass of the affected facilities.
6. WHEN a degraded machine condition is detected at an upstream tier, THE TerraVow_System SHALL propagate the resulting cascade-risk to each dependent downstream tier and report, for each dependent tier, the identified tier and the cascade-risk attributed to it.
7. WHEN a substitution is applied at one tier, THE TerraVow_System SHALL compute the supply-chain ripple on the delivery requirements of each connected tier and report, for each connected tier, the change to its delivery requirements.

### Requirement 8: Decision Gating and Safety

**User Story:** As a plant supervisor, I want every recommendation gated by confidence with a hard veto against increasing harm, so that no action is auto-applied beyond its confidence and no recommendation can relocate or increase pollution.

#### Acceptance Criteria

1. WHEN a recommendation is produced, THE Decision_Gate SHALL classify the recommendation as exactly one of ALLOW, ASK, or BLOCK, where confidence at or above the allow threshold yields ALLOW, confidence at or above the ask threshold and below the allow threshold yields ASK, and confidence below the ask threshold yields BLOCK.
2. IF the confidence value of a recommendation is below the allow threshold, THEN THE Decision_Gate SHALL withhold automatic application of the recommendation and route the recommendation to human review.
3. IF a recommendation would increase the total emission mass or the cross-media load by any amount above zero, THEN THE Decision_Gate SHALL classify the recommendation as BLOCK and attach the readings supporting the increase.
4. THE Decision_Gate SHALL apply the hard veto against increasing total emission mass or cross-media load before classifying the recommendation by confidence value, such that a vetoed recommendation is classified as BLOCK regardless of its confidence value.
5. IF a recommendation is produced without a confidence value or with a confidence value outside the range 0 to 1 inclusive, THEN THE Decision_Gate SHALL classify the recommendation as BLOCK and route it to human review.
6. THE TerraVow_System SHALL present every decision-oriented output without any claim of authoritative legal, tax, or engineering sign-off.

### Requirement 9: Trajectory Certification and Global Registry

**User Story:** As a procurement manager, I want facilities certified on verified improvement trajectory and published to a public registry, so that certification rewards real, sustained effort rather than a gameable audit-day snapshot.

#### Acceptance Criteria

1. WHEN a facility is evaluated for certification and its verified effort history spans at least the configured minimum trajectory observation period, THE TerraVow_System SHALL compute the Trajectory Certification verdict from the facility rate of improvement and verified effort history measured across that period.
2. WHEN the Trajectory Certification verdict is computed, THE TerraVow_System SHALL derive the verdict from at least two verified measurement points separated across the observation period rather than from a single-point-in-time snapshot.
3. IF a facility's verified effort history spans less than the configured minimum trajectory observation period, THEN THE TerraVow_System SHALL withhold the Trajectory Certification verdict, report the condition as insufficient history, and route the evaluation to human review.
4. WHEN a Trajectory Certification verdict is issued, THE TerraVow_System SHALL persist the certificate, its confidence value, and its Evidence_Chain.
5. WHEN a certificate is issued, THE TerraVow_System SHALL publish the certified facility identity, the certificate reference, the verdict, and the certificate validity window to the public global registry.
6. IF the evidence supporting a facility improvement claim contains contradictory measurement points, unverifiable readings, or a pattern consistent with metric-gaming, THEN THE TerraVow_System SHALL withhold certification, omit the facility from the public global registry, and record the supporting findings in the Evidence_Chain.

### Requirement 10: Evidence Chains and Auditability

**User Story:** As an auditor, I want every produced number backed by a machine-checkable evidence chain and a persisted decision trail, so that I can trace any value to its primary source and review how each decision was reached.

#### Acceptance Criteria

1. WHEN the TerraVow_System produces a counterfactual value, a penalty exposure, an incentive eligibility, or a certification verdict, THE TerraVow_System SHALL attach a machine-checkable Evidence_Chain in which every link resolves to an identified primary-source reading, filing, or regulatory-text reference.
2. WHEN a Decision_Gate classifies a recommendation, THE TerraVow_System SHALL record in the audit trail the recommendation identifier, the ALLOW, ASK, or BLOCK classification, the confidence value, and the Evidence_Chain supporting the recommendation.
3. WHEN a decision-oriented output is produced, THE TerraVow_System SHALL complete persistence of its audit-trail entry before the output is returned.
4. THE TerraVow_System SHALL retain the audit trail of decisions for a configured retention period of at least 7 years.
5. WHEN a user requests the provenance of an identified produced value that has an Evidence_Chain, THE TerraVow_System SHALL return the complete Evidence_Chain for that value.
6. IF a user requests the provenance of a produced value for which no Evidence_Chain exists, THEN THE TerraVow_System SHALL return a not-found result and leave the audit trail unchanged.
7. IF a produced value cannot be linked to a primary source, THEN THE TerraVow_System SHALL mark the value as unsupported, route it to human review, and record the reason.

### Requirement 11: Multi-Agent Reasoning and Structured Output

**User Story:** As a platform architect, I want each determination produced by a Gemini reasoning agent with schema-validated structured output and bounded retries, so that counterfactual and interpretive results are reliably structured and verifiable.

#### Acceptance Criteria

1. WHEN a determination that does not exist in any database is required, THE TerraVow_System SHALL produce the determination through a Reasoning_Agent on Gemini on Vertex AI.
2. WHEN a Reasoning_Agent returns a determination, THE TerraVow_System SHALL validate the determination against its defined output schema.
3. WHEN a Reasoning_Agent determination passes schema validation, THE TerraVow_System SHALL accept the determination and pass it to the requesting capability.
4. IF a Reasoning_Agent determination fails schema validation, THEN THE TerraVow_System SHALL retry the reasoning request up to a configured bounded retry limit between 1 and 5 attempts inclusive, defaulting to 3 attempts.
5. IF a Reasoning_Agent determination fails schema validation on every attempt up to and including the configured bounded retry limit, THEN THE TerraVow_System SHALL route the determination to human review and record a failure entry containing the capability, the attempt count performed, and the schema-validation failure reason.

### Requirement 12: Google Cloud Deployment and Graceful Degradation

**User Story:** As a platform operator, I want the system deployed on Google Cloud with graceful degradation on every enrichment path, so that a single data or service gap lowers confidence rather than failing the determination.

#### Acceptance Criteria

1. THE TerraVow_System SHALL run its services on Google Cloud using Cloud Run for services and Gemini on Vertex AI for reasoning.
2. THE TerraVow_System SHALL store time-series telemetry and emissions analytics in BigQuery, documents and the regulatory corpus in Cloud Storage, and the registry and certificates in Firestore.
3. IF an enrichment or retrieval path returns an error or does not respond within its configured timeout, THEN THE TerraVow_System SHALL treat that path as unavailable and continue the determination using a predefined safe fallback path for the affected result.
4. WHEN a result is produced using a fallback path, THE TerraVow_System SHALL assign the affected result a confidence value strictly lower than the confidence value the result would carry on the primary path.
5. WHEN a result is produced using a fallback path, THE TerraVow_System SHALL record the identifier of the fallback path used and the unavailable primary path it replaced in the Evidence_Chain of the affected result.
6. IF an enrichment or retrieval path is unavailable and no safe fallback path is defined for the affected result, THEN THE TerraVow_System SHALL mark the affected result as undetermined, route it to human review, and record the unavailable path in the Evidence_Chain.

### Requirement 13: Synthetic Data, Confidentiality, and Advisory Disclaimer

**User Story:** As a data governance lead, I want synthetic data grounded in published parameter ranges, protected supplier confidentiality, and a disclaimer on every decision-oriented output, so that the platform is demonstrable, confidential, and never mistaken for professional advice.

#### Acceptance Criteria

1. WHERE real facility data is unavailable, THE TerraVow_System SHALL use synthetic data grounded in published regulatory parameter ranges and real instrument structures.
2. WHEN synthetic data is used, THE TerraVow_System SHALL label the data as synthetic and propagate the synthetic label to every output derived from that synthetic data.
3. WHEN supplier data is ingested, THE TerraVow_System SHALL mask personally identifiable information and commercially confidential supplier identifiers, and SHALL complete the masking before the data is used in any reasoning and before the data is persisted.
4. IF the masking of personally identifiable information or commercially confidential supplier identifiers cannot be completed for ingested supplier data, THEN THE TerraVow_System SHALL reject the ingested data without using it in reasoning and without persisting it, and SHALL record the reason the masking could not be completed.
5. THE TerraVow_System SHALL surface a "not legal, tax, or engineering advice" disclaimer on every decision-oriented output.
6. WHEN a decision-oriented output is exported, THE TerraVow_System SHALL include the "not legal, tax, or engineering advice" disclaimer in the export.

### Requirement 14: Machine, Maintenance, and Job/Run Tracking

**User Story:** As a maintenance manager, I want every machine, its maintenance events, and its production runs tracked in one provenance-linked record, so that the platform can attribute emissions to machine health and prove how each figure was derived.

#### Acceptance Criteria

1. WHEN a machine is registered, THE TerraVow_System SHALL create a Machine_Asset carrying a stable identifier, an asset class, a facility location, a commissioning date, and a link to its Commissioning_Baseline when a Commissioning_Baseline is known.
2. IF a machine registration is missing a required identity field, THEN THE TerraVow_System SHALL reject the registration, record the missing field, and create no Machine_Asset.
3. WHEN a Maintenance_Event is recorded for a Machine_Asset, THE TerraVow_System SHALL store the event type classified as preventive or corrective, the start timestamp, the end timestamp, the maintenance action performed, the components replaced, the downtime duration, and the acting technician or system identifier.
4. IF a Maintenance_Event is submitted with a missing required field, THEN THE TerraVow_System SHALL reject the Maintenance_Event and record the rejection with the missing field.
5. WHILE a scheduled maintenance action in the Maintenance_Schedule is past its due time and not yet completed, THE TerraVow_System SHALL mark the action as deferred and record the Deferral_Period measured from the due time.
6. WHEN a Machine_Asset enters a degraded health state, THE TerraVow_System SHALL set the degraded-health-state period onset used by ERD accrual to the start of the associated Deferral_Period.
7. WHEN a Job_Run is recorded on a Machine_Asset, THE TerraVow_System SHALL store the job identifier, the machine reference, the start timestamp, the end timestamp, the batch produced, the output quantity, and the operating parameters, and SHALL link the Job_Run to the batch it produced.
8. WHEN a Job_Run and its machine telemetry are available, THE TerraVow_System SHALL make the Job_Run output quantity and operating parameters available as the per-run basis for computing the EID and the Embedded Pollution Passport of the produced batch.
9. WHEN telemetry and maintenance history are available for a Machine_Asset, THE TerraVow_System SHALL derive a time-stamped Machine_Health_State carrying a health score, the contributing factors, the degraded-health-state onset timestamp, and a confidence value in the range 0 to 1 inclusive.
10. IF telemetry or maintenance history for a Machine_Asset is insufficient to derive a Machine_Health_State, THEN THE TerraVow_System SHALL derive the Machine_Health_State from a Fleet_Cohort_Baseline estimate and assign a confidence value strictly lower than the confidence value it would assign from full telemetry and maintenance history.
11. WHEN telemetry, a Maintenance_Event, a Job_Run, or a Machine_Health_State transition is recorded for a Machine_Asset, THE TerraVow_System SHALL append the record to that Machine_Asset's Operating_Log without modifying or deleting prior Operating_Log entries.
12. WHEN an EID, an ERD, or an Embedded Pollution Passport value is produced for a Machine_Asset, THE TerraVow_System SHALL include in the Evidence_Chain the Operating_Log records that the value was derived from.
13. WHEN machine, maintenance, or job/run records are ingested from an external system, THE TerraVow_System SHALL validate each record against its required fields and route valid records into the Machine_Asset, Maintenance_Event, or Job_Run stores.
14. IF an ingested external record fails validation, THEN THE TerraVow_System SHALL reject the record, record the validation failure, and continue ingesting the remaining records.
15. WHERE external machine, maintenance, or job/run records are unavailable, THE TerraVow_System SHALL use synthetic records grounded in published parameter ranges and label the records as synthetic.

### Requirement 15: AI-Driven Emissions-Aware Maintenance Lifecycle

**User Story:** As a maintenance planner, I want maintenance prioritised by the pollution cost of deferral and verified by its measured emissions outcome, so that the maintenance lifecycle minimises environmental harm rather than only downtime cost.

#### Acceptance Criteria

1. WHEN a Machine_Asset is in a degraded health state, THE TerraVow_System SHALL forecast the EID trajectory of the Machine_Asset over a configured deferral horizon through a Reasoning_Agent.
2. WHEN an EID trajectory forecast is produced, THE TerraVow_System SHALL compute the forecast pollution cost of continued deferral by pricing the forecast EID trajectory through the ERD components over the deferral horizon.
3. WHEN maintenance actions are queued across Machine_Assets, THE TerraVow_System SHALL compute an Emissions_Aware_Priority for each queued maintenance action that combines failure risk, downtime cost, and the forecast pollution cost of continued deferral.
4. WHERE a Receptor_Map and meteorological forecast are available for a Machine_Asset, THE TerraVow_System SHALL weight the forecast pollution cost in the Emissions_Aware_Priority by the AEI of the forecast emissions.
5. WHEN the TerraVow_System ranks the maintenance queue by Emissions_Aware_Priority, THE TerraVow_System SHALL rank a higher-environmental-harm maintenance action above a lower-environmental-harm maintenance action that has a higher failure probability, and SHALL attach an Evidence_Chain to the ranking.
6. WHEN a Maintenance_Event completes on a Machine_Asset, THE TerraVow_System SHALL compare the emissions intensity before the Maintenance_Event with the emissions intensity after the Maintenance_Event and compute the attributable change in emissions intensity.
7. WHEN an attributable change in emissions intensity is computed for a completed Maintenance_Event, THE TerraVow_System SHALL record a Maintenance_Effectiveness_Record carrying the asset class, the maintenance action, the measured change in emissions intensity, a confidence value, and an Evidence_Chain.
8. WHEN a Substitution Dossier or a Trajectory Certification verdict is produced for a Machine_Asset, THE TerraVow_System SHALL make the applicable Maintenance_Effectiveness_Records available as supporting evidence for that output.
9. WHEN an Emissions_Aware_Priority ranking or a forecast pollution cost of deferral is produced, THE Decision_Gate SHALL classify the resulting maintenance recommendation as ALLOW, ASK, or BLOCK and SHALL route a recommendation below the allow threshold to human review.
10. IF the data required to forecast an EID trajectory for a Machine_Asset is insufficient, THEN THE TerraVow_System SHALL report the forecast pollution cost of deferral as undetermined for that Machine_Asset and record the reason.

### Requirement 16: Data Authenticity, Integrity, and Source Trust

**User Story:** As a compliance assurance lead, I want every ingested reading, telemetry, maintenance event, and job run authenticated, calibration-checked, tamper-evident, actor-authorized, and corroborated before it is trusted, so that fabricated or tampered inputs cannot drive liability pricing, remediation, or certification.

#### Acceptance Criteria

1. WHEN a reading, telemetry, Maintenance_Event, or Job_Run record is ingested, THE Integrity_Gate SHALL evaluate the record for Source_Authentication, calibration and health status, integrity, and cross-source corroboration before the PII masking step defined in Requirement 13 and before the record is used in any reasoning and before the record is persisted.
2. IF an ingested record fails Source_Authentication, THEN THE Integrity_Gate SHALL reject the record without using the record in reasoning and without persisting the record, and SHALL record the reason the record was rejected.
3. WHERE an ingested record presents a valid Source_Authentication credential but the credential authority is below the full-trust level, THE Integrity_Gate SHALL admit the record at a strictly reduced Data_Trust_Score and SHALL record the reduced-trust reason in the Evidence_Chain.
4. WHEN a record is ingested from a registered instrument, THE Integrity_Gate SHALL retrieve the instrument's Calibration_Window and health status from the Instrument_Registry.
5. IF a record is ingested from an instrument whose Calibration_Window is expired, whose health status is degraded or failed, or that is unregistered in the Instrument_Registry, THEN THE Integrity_Gate SHALL flag the record and assign the record a confidence value strictly lower than the confidence value it would assign to a record from a registered instrument with a current Calibration_Window and a healthy status, and SHALL record the flag reason in the Evidence_Chain.
6. WHEN an EID, an ERD, an Embedded Pollution Passport, or a Machine_Health_State value is computed from a flagged or reduced-trust record, THE TerraVow_System SHALL propagate the reduced confidence of that record into the confidence value of the computed value.
7. WHEN a record is ingested, THE Integrity_Gate SHALL detect a duplicate or replayed record by comparing the record against previously ingested records and SHALL reject or quarantine a duplicate or replayed record and record the reason.
8. IF an ingested record carries a timestamp that is not monotonically later than the latest accepted record for the same source or that falls outside the configured freshness bound, THEN THE Integrity_Gate SHALL raise an integrity condition and SHALL reject or quarantine the record and record the reason.
9. WHEN a reading or telemetry record is accepted, THE TerraVow_System SHALL append the record to the Machine_Asset Operating_Log tamper-evidently, such that any later modification or deletion of a prior Operating_Log entry is detectable.
10. WHEN a Maintenance_Event is ingested, THE Integrity_Gate SHALL verify that the acting technician or system identifier of the Maintenance_Event is an Authorized_Actor for the referenced Machine_Asset.
11. IF the acting technician or system identifier of an ingested Maintenance_Event is not an Authorized_Actor for the referenced Machine_Asset or cannot be authenticated, THEN THE Integrity_Gate SHALL reject or quarantine the Maintenance_Event without applying the Maintenance_Event to the degraded-health-state onset used by ERD accrual, and SHALL record the reason.
12. WHEN a reading is ingested, THE Integrity_Gate SHALL reconcile the reading against its correlated signals, including energy consumed, output quantity, and any redundant sensor reading for the same quantity, independently of the statistical anomaly detection defined in Requirement 3.
13. IF a reading is physically implausible or internally inconsistent with its correlated signals, THEN THE Integrity_Gate SHALL raise an implausibility integrity condition, attach the conflicting signals and an Evidence_Chain, and reduce the Data_Trust_Score of the reading.
14. WHEN the Integrity_Gate completes evaluation of an ingested record, THE Integrity_Gate SHALL compute a Data_Trust_Score in the range 0 to 1 inclusive from the Source_Authentication outcome, the calibration and health status, the integrity-check outcome, and the cross-source corroboration outcome, and SHALL attach the Data_Trust_Score and an Evidence_Chain to the record.
15. WHEN a recommendation is derived from inputs whose Data_Trust_Score is below the configured trust threshold, THE Decision_Gate SHALL classify the recommendation as at most ASK and SHALL route the recommendation to human review.
16. THE Integrity_Gate SHALL preserve the Decision_Gate hard veto and fail-closed posture defined in Requirement 8, such that a reduced Data_Trust_Score lowers but never raises the classification a recommendation receives.
17. IF the Instrument_Registry lookup, the Source_Authentication verification service, or a cross-source corroboration source is unavailable or does not respond within its configured timeout, THEN THE TerraVow_System SHALL treat that path as unavailable and continue through a predefined safe fallback path at a confidence value strictly lower than the primary-path confidence value, or, when no safe fallback path is defined, SHALL mark the affected record as undetermined and route the record to human review, and SHALL record the unavailable path in the Evidence_Chain.

### Requirement 17: Baseline Integrity, Independent Verification, and Trust-Weighted Detection Hardening

**User Story:** As a fraud-risk and MRV assurance lead, I want the Commissioning_Baseline screened for plausibility, self-reported data reconciled against independent sources, the anomaly-detection baseline weighted by trust, and the tamper-evident log externally anchored, so that fraud patterns outside the endogenous reporting chain — an inflated counterfactual baseline, physically-consistent falsified readings, poisoned anomaly history, and insider rewriting of the audit chain — cannot escape detection.

#### Acceptance Criteria

1. WHEN a Commissioning_Baseline is proposed at Machine_Asset registration, THE TerraVow_System SHALL perform a Baseline_Plausibility_Check that screens the proposed Commissioning_Baseline against the Fleet_Cohort_Baseline range and the declared engineering/manufacturer limits for the Machine_Asset's asset class.
2. IF a proposed Commissioning_Baseline falls outside the configured plausibility tolerance of the Fleet_Cohort_Baseline range or the declared engineering/manufacturer limits for its asset class, THEN THE TerraVow_System SHALL flag the Commissioning_Baseline, assign it a confidence value strictly lower than the confidence value assigned to a Commissioning_Baseline within tolerance, and route the Commissioning_Baseline to human review without rejecting it.
3. THE TerraVow_System SHALL admit a Commissioning_Baseline that falls within the configured plausibility tolerance without routing it to human review on that basis alone.
4. WHEN a Commissioning_Baseline is flagged by the Baseline_Plausibility_Check, THE TerraVow_System SHALL record the Fleet_Cohort_Baseline range and the declared engineering/manufacturer limits used in the screening in the Evidence_Chain of the flagged Commissioning_Baseline.
5. WHEN an EID or ERD value is computed from a Commissioning_Baseline that was flagged by the Baseline_Plausibility_Check and has not yet cleared human review, THE TerraVow_System SHALL propagate the Commissioning_Baseline's reduced confidence into the confidence value of the computed EID or ERD value.
6. WHERE an Independent_Verification_Source is available for a self-reported reading, THE TerraVow_System SHALL reconcile the self-reported reading against the Independent_Verification_Source.
7. IF a self-reported reading diverges from its reconciled Independent_Verification_Source value beyond a configured tolerance, THEN THE TerraVow_System SHALL raise a discrepancy condition carrying the self-reported value, the Independent_Verification_Source value, and an Evidence_Chain linking to both.
8. THE TerraVow_System SHALL assign a reading corroborated by an Independent_Verification_Source a strictly higher confidence weight than the confidence weight assigned to the same reading supported only by self-reported cross-source corroboration under Requirement 16.
9. WHERE no Independent_Verification_Source is available for a reading, THE TerraVow_System SHALL record the absence of an Independent_Verification_Source for that reading and SHALL cap the Data_Trust_Score of a reading supported only by self-reported corroboration at or below a configured maximum corroboration-only trust ceiling that is strictly below full trust.
10. WHEN the TerraVow_System builds the expected profile used for the Requirement 3.4 anomaly detection for a reading stream, THE TerraVow_System SHALL construct the expected profile, as a Trust_Weighted_Profile, only from readings whose Data_Trust_Score is at or above the configured trust-weighting threshold, and SHALL weight each contributing reading by its Data_Trust_Score.
11. IF a reading was admitted at ADMIT_REDUCED_TRUST, THEN THE TerraVow_System SHALL exclude that reading from establishing or shifting the Trust_Weighted_Profile for its reading stream, while still permitting that reading to be evaluated against the existing Trust_Weighted_Profile for anomaly detection.
12. THE TerraVow_System SHALL periodically publish an Anchored_Hash_Root of the current TamperEvidentLogEntry hash chain to an externally-verifiable, operator-uninfluenced location at a configured anchoring interval.
13. THE TerraVow_System SHALL make the most recently published Anchored_Hash_Root available for independent verification against the live TamperEvidentLogEntry chain.
14. IF the live TamperEvidentLogEntry chain does not reproduce a previously published Anchored_Hash_Root when recomputed to that checkpoint, THEN THE TerraVow_System SHALL raise a tamper-detected condition, record the diverging Machine_Asset Operating_Log and the affected Anchored_Hash_Root in the Evidence_Chain, and route the finding to human review.
15. IF the externally-verifiable anchoring location is unavailable or does not respond within its configured timeout at a scheduled anchoring interval, THEN THE TerraVow_System SHALL treat the anchoring path as unavailable, retain the unpublished hash root for the next scheduled attempt, and record the missed anchoring attempt.
16. THE TerraVow_System SHALL ensure that none of the Baseline_Plausibility_Check, the Independent_Verification_Source reconciliation, the Trust_Weighted_Profile construction, or the Anchored_Hash_Root verification raises a Decision_Gate classification or a confidence value beyond what Requirement 8 and Requirements 16.15 and 16.16 otherwise allow, such that each hardening check in this requirement may only add an additional downgrade or human-review path and preserves the existing fail-closed posture.
