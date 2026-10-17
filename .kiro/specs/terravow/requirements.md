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

---

## Prototype Extension: Industry Templates, Snag History, and Sustainability Recommendations (Requirements 18–30)

### Scope, Product Intent, and Assumptions

Requirements 18–30 extend the **Python FastAPI prototype** (`prototype/`) so that it delivers the product described in the meetings (`meeting-transcripts/`) and in the follow-up product brief: industry-specific, AI-generated, cached machine-model templates; a per-machine snag history that drives health, remaining lifespan, and pollution intensity; and a recommendation of a better sustainable machine or process together with the government incentives that apply. They do not change Requirements 1–17. The Java library under `src/main/java/com/terravow` is not required to change; where an existing Java capability already covers a concern (for example `capability/substitution`, `DefaultEconomicsEngine.assessIncentives`), the prototype is not required to call it.

Each requirement below is tagged with the product intent it serves:

| Tag | Product intent (from the product brief) |
|---|---|
| **PI-1** | Product design and template differ by industry category; four heavy-machinery industries covering air, water, chemical, and soil pollution. |
| **PI-2** | Template is AI-decided once a machine model is selected; very deep on job and snag issues and recommendations. |
| **PI-3** | Template is saved in the database after model selection; AI generates it only if no template for that model exists. |
| **PI-4** | A snag-history document in the database drives machine health and lifespan, linked to pollution intensity (carbon, water, and so on). |
| **PI-5** | Recommend a better sustainable machine alternative or a more sustainable assembly/process change. |
| **PI-6** | Show government tax rebates and policies that incentivise sustainable upgrades, for small (MSME) and large industry. |
| **PI-M** | Meeting-derived workflow: industry dropdown, machine autocomplete, manual entry, reporter and resolver roles (`Meeting-16Oct`, TerraVow Part 2 summary). |

**Assumptions (explicit, to be confirmed in design review):**

- A1. The four default industries are (a) Textile dyeing and finishing [air, water, chemical] (air added in the design revision because textile mills run gas- and oil-fired boilers, thermal-oil heaters and stenters, and the air engine is reused for them), (b) Metals: steel and foundry [air, soil, water], (c) Chemicals and fertiliser [air, water, chemical, soil], (d) Mining and mineral processing [soil, water, air]. The meetings named no industries ("two industries" in `Meeting-16Oct`); the product brief overrides this with four.
- A2. The default country is India (currency INR). The incentive data model is keyed by country so that other countries can be added without schema change; only India is populated in this release.
- A3. The prototype has no authentication. Reporter and Resolver are a demo-grade role switch only.
- A4. Catalogue manufacturer and model names are plausible **sample data**, not an authoritative manufacturer database, and are labelled as such wherever shown.
- A5. Numeric limits stated in acceptance criteria (for example field lengths and result counts) are defaults; they SHALL be held as named configuration constants, not scattered literals.
- A6. The pollution-intensity and lifespan factors are illustrative engineering assumptions. They are never presented as measurements.
- A7. The 14 minutes missing from `Meeting-16Oct` may contain further resolver-workflow decisions; Requirement 25 defines a minimal resolver workflow and does not claim to reproduce them.

### Extension Glossary

- **Industry_Category**: One of the four configured heavy-machinery industries, carrying a stable identifier, a display name, and a set of Pollution_Domains.
- **Pollution_Domain**: One of `air`, `water`, `chemical`, or `soil`.
- **Machine_Catalogue**: The sample set of manufacturer and model entries per Industry_Category, each marked as sample data.
- **Machine_Model_Key**: The normalised slug of industry, manufacturer, and model, used with the schema version to key a Machine_Template.
- **Machine_Template**: The structured per-industry, per-model document describing the machine profile, operating parameters, job types, snag taxonomy, pollutant-capture fields, and form-field definitions.
- **Base_Template**: The deterministic, curated, per-Industry_Category template used as grounding input to Gemini and as the labelled fallback when generation is unavailable.
- **Template_Source**: One of `ai` (generated by Gemini and schema-validated) or `fallback-base` (the Base_Template adapted deterministically to the selected model).
- **Snag**: A reported machine issue recorded against a Machine_Instance, answering the form defined by the Machine_Template.
- **Machine_Instance**: A specific physical machine (for example `B-2`) of a Machine_Model, carrying an install year.
- **Snag_History_Document**: The per-Machine_Instance persisted record of all Snags and their lifecycle.
- **Health_Score**: A deterministic engine-computed value in the range 0 to 100 inclusive (100 is best) derived from the Snag_History_Document and Machine_Instance age.
- **Remaining_Lifespan_Estimate**: A deterministic engine-computed estimate of remaining useful operating time in days, with a stated basis and an uncertainty band, derived from the Health_Score and wear factors.
- **Pollution_Intensity**: The per-day and over-remaining-lifespan emission quantities per Pollution_Domain attributed to a Machine_Instance, computed from the Health_Score using labelled illustrative factors.
- **Sustainability_Recommendation**: The pair of (a) a sustainable machine alternative and (b) a sustainable process or assembly change for a Machine_Instance, each with a deterministic quantitative comparison.
- **Incentive_Scheme**: A government tax rebate, subsidy, or policy record for sustainable upgrades, keyed by country.
- **Enterprise_Size**: One of `MSME` or `LARGE`.
- **Role**: One of `REPORTER` or `RESOLVER`, a demo-grade label with no authentication.
- **Grounded_Source**: A URL (and title where available) returned by Gemini Google Search grounding metadata and retained verbatim with the content it supports.

---

### Requirement 18: Industry Categories and Fleet Tagging

**Product intent:** PI-1, PI-M. **User Story:** As a plant owner, I want the product to be organised around four heavy-machinery industries and their pollution domains, so that templates and recommendations fit my industry rather than being generic.

#### Acceptance Criteria

1. THE TerraVow_System SHALL define exactly four Industry_Categories by default: Textile dyeing and finishing, Metals (steel and foundry), Chemicals and fertiliser, and Mining and mineral processing.
2. THE TerraVow_System SHALL tag each Industry_Category with its Pollution_Domains, with the default tagging being Textile [air, water, chemical]; Metals [air, soil, water]; Chemicals and fertiliser [air, water, chemical, soil]; Mining and mineral processing [soil, water, air]; and every Pollution_Domain SHALL be covered by at least one Industry_Category.
3. WHEN a client requests the list of industries, THE TerraVow_System SHALL return every Industry_Category with its identifier, display name, and Pollution_Domains, ordered deterministically.
4. THE TerraVow_System SHALL tag each of the six existing fleet machines (B-2, F-1, K-4, C-7, D-3, G-5) with exactly one Industry_Category, and the fleet response SHALL include the industry identifier, manufacturer, and model for each machine.
5. WHEN a Machine_Catalogue is served, THE TerraVow_System SHALL provide at least 4 and at most 6 entries per Industry_Category, each carrying a manufacturer, a model name, a machine type, and a `sample_data: true` marker.
6. THE TerraVow_System SHALL display a "sample data, not an authoritative manufacturer database" label wherever Machine_Catalogue entries are shown.
7. THE TerraVow_System SHALL keep the existing six-machine fleet behaviour (ranking, `/api/analyze`, `/api/explain`, `/api/decide`, `/api/impact`) unchanged apart from the added industry, manufacturer, and model fields.
8. IF a request references an industry identifier that is not configured, THEN THE TerraVow_System SHALL reject the request with a client-error response naming the invalid field and SHALL NOT create or modify any record.

### Requirement 19: Catalogue Search and Manual Machine Entry

**Product intent:** PI-M, PI-1. **User Story:** As a reporter, I want to pick an industry and find my machine by typing a few letters, or enter a machine that is not listed, so that I can select the exact machine model without browsing a long list.

#### Acceptance Criteria

1. WHEN the user selects an Industry_Category, THE TerraVow_System SHALL restrict machine search to the Machine_Catalogue of that Industry_Category.
2. WHEN a client submits a search query to the catalogue search endpoint, THE TerraVow_System SHALL return catalogue entries whose manufacturer, model, or machine type contains the query as a case-insensitive prefix of any word or as a case-insensitive substring, ranking prefix matches above substring matches and breaking ties deterministically.
3. THE catalogue search endpoint SHALL accept a query of 1 to 80 characters, SHALL return at most 10 results, and SHALL treat an empty query as a request for the first 10 entries of the selected Industry_Category.
4. IF the search query exceeds 80 characters or the industry is missing or not configured, THEN THE TerraVow_System SHALL reject the request with a client-error response and SHALL NOT perform a search.
5. WHEN a search yields no result, THE TerraVow_System SHALL return an empty list and the user interface SHALL offer the manual machine entry option.
6. WHEN a user submits a manual machine entry consisting of an industry, a manufacturer, and a model name, THE TerraVow_System SHALL validate each field as required, non-blank, and at most 80 characters, and SHALL retrieve the machine specifications through Gemini with Google Search grounding.
7. WHEN grounded specifications are returned, THE TerraVow_System SHALL retain each Grounded_Source URL with the specifications, SHALL show those URLs in the user interface, and SHALL mark the specification record as AI-retrieved and unverified.
8. IF Google Search grounding or Gemini is unavailable, returns an error, or returns no Grounded_Source, THEN THE TerraVow_System SHALL return the manual entry with an explicit message stating that specifications could not be retrieved and with no specifications invented, SHALL record the specification record as `ungrounded`, and SHALL still allow the user to select the model.
9. WHEN the user selects a catalogue entry or confirms a manual entry, THE TerraVow_System SHALL treat the combination of industry, manufacturer, and model as the selected machine model and SHALL make it available to the template generation flow in Requirement 20.
10. THE TerraVow_System SHALL NOT send any contact detail or personal identifier to Gemini as part of a specification lookup; only industry, manufacturer, and model text SHALL be sent.

### Requirement 20: AI-Generated Deep Per-Model Template

**Product intent:** PI-1, PI-2, PI-M. **User Story:** As a reporter, I want a deep, model-specific snag and job template created for the machine I selected, so that I record the right information and the resolver gets a precise, actionable recommendation.

#### Acceptance Criteria

1. WHEN a machine model is selected and no Machine_Template exists for its Machine_Model_Key, THE TerraVow_System SHALL generate a Machine_Template through Gemini, grounded on the Base_Template of the selected Industry_Category and on any retained Grounded_Source specifications.
2. THE Machine_Template SHALL contain a machine profile and specifications section, a list of operating parameters to log, a list of job and run types, a snag taxonomy, pollutant-capture fields, and form-field definitions.
3. THE Machine_Template SHALL define, for each job or run type, a name, an interval or trigger, a maintenance or inspection schedule, a checklist, parts and tools, safety and permit notes, an estimated downtime, and an estimated cost band.
4. THE Machine_Template SHALL define, for each snag type, a stable identifier, a category, symptoms, likely root causes, a detection method, a definition for each of the severity levels 1 to 5, an immediate action, a recommended repair job referencing a job or run type, a time-to-fix, parts, safety hazards, escalation criteria, and the Pollution_Domains it affects with a one-sentence description of how each is affected.
5. THE Machine_Template SHALL define pollutant-capture fields specific to the Pollution_Domains of the Industry_Category (for example effluent colour and flow for Textile, stack opacity and slag disposal for Metals), and every Pollution_Domain of the Industry_Category SHALL be covered by at least one pollutant-capture field.
6. THE Machine_Template SHALL define form-field definitions, each with an identifier, a label, a type of `text` or `dropdown`, a required flag, a maximum length for `text` fields, and a non-empty option list for `dropdown` fields, so that the user interface can render the snag form dynamically from the template without per-industry code.
7. WHEN Gemini returns a template, THE TerraVow_System SHALL validate it against the defined template schema, including referential integrity (each snag's recommended repair job exists) and minimum depth (by default at least 6 job or run types, at least 12 snag types, and at least 8 operating parameters), and SHALL apply the bounded retry policy of Requirement 11.4 for a failed validation.
8. THE Base_Template of each Industry_Category SHALL be deterministic, curated, and schema-valid, and the Base_Templates of any two Industry_Categories SHALL differ materially, meaning that the overlap between their snag-type identifiers and between their pollutant-capture field identifiers is at most a configured fraction (default 0.5).
9. IF Gemini is unavailable, errors, times out, or fails schema validation on every permitted attempt, THEN THE TerraVow_System SHALL return the Base_Template adapted deterministically to the selected manufacturer and model with `source = "fallback-base"` and an explicit user-visible label stating that it is a generic industry template and not AI-generated for this model.
10. THE TerraVow_System SHALL treat all Machine_Template text as untrusted content, SHALL bound its total size (default at most 200 kilobytes) and field lengths, and SHALL reject or truncate content beyond the bounds before storing or returning it.
11. THE Gemini prompt for template generation SHALL NOT include any reporter handle, contact detail, or free-text Snag content.
12. THE Machine_Template SHALL carry a disclaimer that its maintenance intervals, costs, and root-cause lists are AI-generated or generic guidance for qualified-engineer review and are not manufacturer instructions.

### Requirement 21: Template Persistence with Cache-Once Semantics

**Product intent:** PI-3. **User Story:** As a platform operator, I want each model's template generated once and then reused, so that Gemini cost and latency are not repeated and every user of the same model sees the same template.

#### Acceptance Criteria

1. THE TerraVow_System SHALL key each stored Machine_Template by the Machine_Model_Key (the normalised slug of industry, manufacturer, and model: lower-cased, with runs of non-alphanumeric characters collapsed to a single hyphen, and trimmed) combined with the template schema version.
2. THE TerraVow_System SHALL store, with each Machine_Template, `generated_at`, `model_used`, `schema_version`, `source`, and the Grounded_Source list.
3. WHEN a template is requested for a Machine_Model_Key for which a stored Machine_Template with `source = "ai"` exists, THE TerraVow_System SHALL return the stored template with `cache_status = "cache-hit"` and SHALL NOT call Gemini.
4. WHEN a template is requested for a Machine_Model_Key with no stored Machine_Template and Gemini generation succeeds, THE TerraVow_System SHALL persist the template and return it with `cache_status = "generated"`.
5. THE TerraVow_System SHALL persist a Machine_Template only through an atomic create-if-absent operation (Firestore `create()` or an equivalent transaction, and a per-key lock for the in-memory fallback), such that concurrent requests across replicas for the same key result in exactly one stored document.
6. IF a create-if-absent operation reports that a document already exists, THEN THE TerraVow_System SHALL discard its own generated result and return the stored document with `cache_status = "cache-hit"`.
7. WHILE a generation for a Machine_Model_Key is in progress on the same instance, THE TerraVow_System SHALL make concurrent requests for that key wait for and share the single result rather than starting additional Gemini calls.
8. THE TerraVow_System SHALL NOT persist a template with `source = "fallback-base"` as a cache entry for the Machine_Model_Key, SHALL return it with `cache_status = "fallback"`, and SHALL attempt Gemini generation again on the next request for that key.
9. WHEN the template schema version changes, THE TerraVow_System SHALL treat templates stored under a different schema version as absent for the new key and SHALL leave the older documents unmodified.
10. THE TerraVow_System SHALL provide an endpoint that ensures a template for a selected model (returning the template and `cache_status`) and an endpoint that fetches a stored template by key without ever invoking Gemini (returning not-found when absent).
11. IF persistence of a newly generated template fails, THEN THE TerraVow_System SHALL return the generated template to the caller with `cache_status = "generated-unsaved"`, SHALL log the failure at error level without secrets, and SHALL NOT report the template as cached.
12. THE TerraVow_System SHALL show the user whether a template came from the cache, was newly generated, or is a fallback, together with `source` and `generated_at`.

### Requirement 22: Snag History Document

**Product intent:** PI-4, PI-M. **User Story:** As a maintenance manager, I want every snag on a machine recorded in one history document, so that the machine's health and remaining life can be assessed from facts rather than memory.

#### Acceptance Criteria

1. THE TerraVow_System SHALL persist one Snag_History_Document per Machine_Instance, realised as a header document plus the Snag records that reference it by machine identifier and read together as one document, in Firestore when configured and in an in-memory store otherwise, using the same backend-selection and visible backend indicator as the existing store.
2. WHEN a Snag is submitted, THE TerraVow_System SHALL append a Snag record carrying a Snag identifier, the Machine_Instance identifier, the Machine_Model_Key and template schema version used, the answers to the template form fields, a snag type identifier when selected, a severity from 1 to 5, a category, the report timestamp, the pseudonymous reporter handle and Role, a status, and optional photo-analysis reference.
3. THE Snag status SHALL be one of `OPEN`, `ASSIGNED`, `RESOLVED`, or `CLOSED`, and THE TerraVow_System SHALL permit only the transitions OPEN to ASSIGNED, ASSIGNED to RESOLVED, RESOLVED to CLOSED, and OPEN to RESOLVED.
4. WHEN a Snag is resolved, THE TerraVow_System SHALL record the resolver handle, the resolution timestamp, the resolution notes and actions taken, the downtime hours, and the cost in INR.
5. WHEN a Snag is submitted or updated, THE TerraVow_System SHALL validate every answer against the field definition of the stored template: required fields present, `dropdown` values drawn from the option list, `text` values within the maximum length, severity an integer from 1 to 5, and SHALL reject an invalid submission with a client-error response listing each invalid field and SHALL NOT store it.
6. THE TerraVow_System SHALL append Snag records without modifying or deleting prior Snag records; status changes SHALL be recorded as appended events with timestamps.
7. WHEN the demo reset endpoint is called, THE TerraVow_System SHALL restore the seeded Snag_History_Documents and clear Snags created during the demo, and SHALL leave the existing reset behaviour for the fleet and audit trail intact.
8. THE TerraVow_System SHALL provide a seeded demo Snag_History_Document for each of the six existing machines, labelled as synthetic, with Snag counts, severities, and open/closed mixes that produce distinct Health_Scores across the fleet.
9. THE TerraVow_System SHALL expose an endpoint to read the Snag_History_Document of a Machine_Instance and an endpoint to list Snags filtered by status.
10. THE TerraVow_System SHALL NOT send a Snag's free-text answers, photo, or reporter handle to any LLM except through the existing `/api/analyze` photo flow, and, because the prototype has no PII masking module today, SHALL redact email-address and phone-number patterns in free text with a simple deterministic filter before the text is persisted, as the prototype-level realisation of Requirement 13.3.

### Requirement 23: Health Score and Remaining Lifespan from Snag History

**Product intent:** PI-4. **User Story:** As a maintenance manager, I want machine health and remaining lifespan computed from the past snags in a way I can reproduce, so that I can plan repair or replacement with a defensible basis.

#### Acceptance Criteria

1. WHEN a Snag_History_Document and an as-of date are supplied, THE TerraVow_System SHALL compute a Health_Score from 0 to 100 inclusive in engine code, with no LLM involvement.
2. THE Health_Score computation SHALL take as inputs the Snag frequency over a configured window, the recurrence of the same category or snag type, the severity trend (realised as a capped, recency-weighted load of high-severity Snags), the number and severity of unresolved Snags, the Machine_Instance age (as-of date minus 1 January of the install year, in days), and the time since the last major repair (a RESOLVED or CLOSED Snag of severity 4 or 5).
3. THE TerraVow_System SHALL compute a Remaining_Lifespan_Estimate in days from the Health_Score and the age factor, SHALL attach a basis string and an uncertainty band, and SHALL label it as an estimate and not a prediction of failure.
4. THE Health_Score and Remaining_Lifespan_Estimate SHALL be deterministic: identical Snag_History_Document, install year, configuration, and as-of date SHALL always produce identical outputs, and the engine SHALL NOT read the wall clock except to supply a default as-of date at the API boundary.
5. WHEN an additional unresolved Snag of severity 1 to 5 is added to a Snag_History_Document, THE TerraVow_System SHALL compute a Health_Score no greater than, and a Remaining_Lifespan_Estimate no greater than, the values before the addition.
6. WHEN an unresolved Snag is resolved with all else unchanged, THE TerraVow_System SHALL compute a Health_Score no less than the value before resolution.
7. WHEN the Snag_History_Document is empty, THE TerraVow_System SHALL compute the Health_Score from age alone and SHALL mark the result `low-history` with reduced confidence.
8. THE TerraVow_System SHALL return, with each Health_Score, the contributing factors and their individual penalties so that the user interface can explain the score ("How this is calculated").
9. THE Remaining_Lifespan_Estimate SHALL be bounded below by 0 days and above by a configured maximum design life, and a Health_Score of 0 SHALL yield 0 days.
10. IF the install year is absent or later than the as-of year, THEN THE TerraVow_System SHALL compute the Health_Score without the age factor, mark the result `age-unknown`, and record the reason.

### Requirement 24: Pollution Intensity Linked to Health

**Product intent:** PI-4, PI-1. **User Story:** As an environmental officer, I want a machine's pollution intensity to rise as its health falls, across air, water, chemical, and soil, so that deferred maintenance is visible as an environmental cost.

#### Acceptance Criteria

1. WHEN a Health_Score is available for a Machine_Instance, THE TerraVow_System SHALL compute the Pollution_Intensity per day for each Pollution_Domain applicable to the machine's Industry_Category.
2. THE Pollution_Intensity SHALL be reported with these indicators: for air, CO2e and NOx; for water, COD, BOD, and effluent volume; for chemical, a hazardous-chemical release proxy; for soil, a solid-waste or sludge and soil-contamination proxy; each with its unit.
3. THE TerraVow_System SHALL compute Pollution_Intensity as a deterministic function of a per-machine healthy baseline, a labelled illustrative degradation factor per Pollution_Domain, and the Health_Score, using engine code only.
4. WHEN the Health_Score of a Machine_Instance decreases with all other inputs unchanged, THE TerraVow_System SHALL report a per-day intensity for each applicable indicator that is no lower than before, and strictly higher for every indicator whose healthy baseline is greater than zero.
5. THE TerraVow_System SHALL report the cumulative Pollution_Intensity over the Remaining_Lifespan_Estimate for each indicator, computed deterministically as the per-day intensity multiplied by the remaining days.
6. WHEN the existing deferral-cost engine already computes excess fuel, CO2, and NOx for a machine, THE TerraVow_System SHALL reuse its air-domain loss curve and computation so that air figures for the same machine are produced by the same function, with the defect-severity input and the snag-history input both shown and labelled.
7. THE TerraVow_System SHALL label every Pollution_Intensity value as an illustrative estimate, show the factor source or "illustrative assumption" tag beside the value, and SHALL NOT present it as a measurement or as a regulatory exceedance.
8. IF a Pollution_Domain is not applicable to the machine's Industry_Category, THEN THE TerraVow_System SHALL omit that domain rather than report zero.
9. THE TerraVow_System SHALL show on the machine view the Health_Score, Remaining_Lifespan_Estimate, and per-domain Pollution_Intensity together, so the linkage is visible without navigating elsewhere.

### Requirement 25: Reporter and Resolver Roles and Workflow

**Product intent:** PI-M, PI-4. **User Story:** As a plant team, I want a reporter to submit snags using the generated template and a resolver to pick up, fix, and close them, so that issue history is captured end to end.

#### Acceptance Criteria

1. THE TerraVow_System SHALL provide a demo-grade Role switch between Reporter and Resolver in the user interface and SHALL record the selected Role on every Snag creation and status change.
2. THE TerraVow_System SHALL state prominently, in a code comment at the role-handling module, in the user interface footer, and in the README, that no authentication or authorisation exists and that the Role switch is not a security control.
3. THE TerraVow_System SHALL require the reporter handle to be a pseudonym of 3 to 32 characters from `A-Z a-z 0-9 _ -`, SHALL reject any handle that does not match, and SHALL NOT collect names, emails, or phone numbers.
4. WHEN the Role is Reporter, THE TerraVow_System SHALL let the user select an industry and machine model, obtain the template, fill the dynamically rendered form, optionally attach a photo analysed through the existing `/api/analyze` flow, and submit a Snag.
5. WHEN a Snag is submitted, THE TerraVow_System SHALL show the reporter the AI-recommended repair job from the selected snag type of the template, labelled as template guidance.
6. WHEN the Role is Resolver, THE TerraVow_System SHALL show an open-Snag queue ordered deterministically by severity descending, then report time ascending, with each entry showing the recommended repair job, time-to-fix, safety hazards, and escalation criteria drawn from the template.
7. WHEN a Resolver accepts a Snag, THE TerraVow_System SHALL set the status to ASSIGNED and record the resolver handle; WHEN the Resolver records a resolution, THE TerraVow_System SHALL set RESOLVED with the data in Requirement 22.4; WHEN the Resolver closes the Snag, THE TerraVow_System SHALL set CLOSED.
8. WHEN a Snag is CLOSED, THE TerraVow_System SHALL recompute the Health_Score, Remaining_Lifespan_Estimate, and Pollution_Intensity of the Machine_Instance.
9. WHERE a closed Snag has a before-photo analysis and the Resolver supplies an after-photo, THE TerraVow_System SHALL pass the after-photo through the existing verify-improvement flow, and a verified improvement SHALL still require the existing evidence conditions (notes alone never verify).
10. IF an action is requested that is not permitted for the current status or Role (for example a Reporter closing a Snag), THEN THE TerraVow_System SHALL reject it with a client-error response stating the reason and SHALL leave the Snag unchanged.
11. THE role-based restrictions in criterion 10 SHALL be described as workflow guidance only and SHALL NOT be described anywhere as access control.

### Requirement 26: Sustainable Machine Alternative and Process Change

**Product intent:** PI-5. **User Story:** As an operations director, I want a better sustainable machine alternative and a more sustainable process change for the machine I selected, with numbers I can check, so that I can decide on an upgrade.

#### Acceptance Criteria

1. WHEN a Sustainability_Recommendation is requested for a Machine_Instance, THE TerraVow_System SHALL return (a) between 1 and 3 sustainable machine alternatives and (b) between 1 and 3 sustainable process or assembly changes, each with a name, a rationale, and a source label.
2. THE TerraVow_System SHALL compute, in engine code, for every alternative and process change: capex in INR, efficiency gain in percent, production-value effect in INR per year, per-domain emissions reduction relative to the current Pollution_Intensity, and simple payback in years (capex divided by net annual benefit, reported as "no payback" when the net annual benefit is not positive).
3. WHEN Gemini is used, THE TerraVow_System SHALL ask it only to propose candidate names, rationale, and the input parameters needed by the engine (for example a claimed efficiency range), SHALL use Google Search grounding where available, SHALL retain Grounded_Source URLs, SHALL validate the response against a schema, and SHALL NOT accept any payback, savings, or emission total computed by Gemini.
4. THE TerraVow_System SHALL label Gemini-proposed candidates "AI-suggested, requires vendor validation and process-safety review", consistent with Requirement 5.4.
5. THE TerraVow_System SHALL provide, for every Industry_Category, a curated fallback set of alternatives and process changes with explicit illustrative parameters, so that the recommendation works with no Gemini and is labelled `curated-fallback`.
6. IF Gemini or grounding is unavailable or returns invalid output, THEN THE TerraVow_System SHALL return the curated fallback with a visible message and SHALL NOT fail the request.
7. THE TerraVow_System SHALL NOT recommend an alternative or process change that increases total emission mass or shifts load across Pollution_Domains, applying the same hard veto as Requirement 8.3, and SHALL pass each recommendation through the existing Decision_Gate before it is shown as actionable.
8. WHEN a recommendation is shown, THE TerraVow_System SHALL show the current Health_Score and Pollution_Intensity beside the alternative so the comparison against the current usage is visible.
9. THE TerraVow_System SHALL show a sustainable-assembly process change as a distinct item from a machine replacement, and a machine alternative SHALL NOT be presented as the only option.
10. THE TerraVow_System SHALL label every Sustainability_Recommendation as an estimate and not a quotation, an engineering design, or a compliance determination.
11. IF a required engine input (for example the current Pollution_Intensity) is unavailable, THEN THE TerraVow_System SHALL report the affected comparison as undetermined with the reason and SHALL NOT substitute an invented value.

### Requirement 27: Government Incentives by Country and Enterprise Size

**Product intent:** PI-6. **User Story:** As a small or large manufacturer, I want to see the tax rebates and policies available for the sustainable upgrade I am considering and how they change payback, so that I can judge affordability.

#### Acceptance Criteria

1. THE TerraVow_System SHALL store Incentive_Schemes in a structure keyed by country, with India populated by default and INR as its currency.
2. THE TerraVow_System SHALL record for each Incentive_Scheme a name, issuing authority, applicable Industry_Categories, applicable Enterprise_Sizes, benefit type, benefit description, eligibility notes, source URL, `retrieved_on` date, and a `verified` flag; a record that is an explicitly labelled illustrative demo record (no real issuing body) MAY have a null source URL and a null `retrieved_on` and SHALL carry an `authored_on` date instead, and a curated record nobody retrieved SHALL have a null `retrieved_on`.
3. THE TerraVow_System SHALL NOT fabricate a scheme, a rate, a cap, or a source URL; every Incentive_Scheme lacking a checked primary source SHALL carry `status = "UNVERIFIED/illustrative"` and SHALL be labelled as such in the user interface.
4. WHEN a user selects an Enterprise_Size, THE TerraVow_System SHALL return the Incentive_Schemes applicable to the machine's Industry_Category and to that Enterprise_Size, and THE TerraVow_System SHALL include schemes applicable to both sizes in either selection.
5. THE TerraVow_System SHALL include, in the default India set, at least one scheme applicable to MSME and at least one applicable to LARGE for each Industry_Category, or SHALL show an explicit "no matching scheme recorded" message when none is recorded.
6. WHEN a scheme has a quantifiable benefit parameter (for example a percentage capital subsidy with a cap), THE TerraVow_System SHALL compute the incentive-adjusted capex and incentive-adjusted payback in engine code and SHALL show them beside the unadjusted capex and payback.
7. WHEN a scheme has no quantifiable parameter, THE TerraVow_System SHALL list it as qualitative and exclude it from the adjusted calculation.
8. THE TerraVow_System SHALL guarantee that the incentive-adjusted capex is no greater than the unadjusted capex and that the incentive-adjusted payback is no greater than the unadjusted payback when payback is defined.
9. WHEN Gemini with grounding is used to discover or refresh schemes, THE TerraVow_System SHALL store its output as `UNVERIFIED` with the Grounded_Source URLs and `retrieved_on`, and SHALL NOT mark a scheme `verified` without a recorded human-checked primary source.
10. THE TerraVow_System SHALL label every incentive output "informational, not tax or legal advice; confirm eligibility with the issuing authority", consistent with Requirement 6.8.
11. IF a country other than the populated set is requested, THEN THE TerraVow_System SHALL return an empty scheme list with an explicit "no data for this country" message.

### Requirement 28: Honesty, Determinism, and Security Constraints for the Extension

**Product intent:** PI-1 to PI-6. **User Story:** As a judge or auditor, I want the new features to be honest about what is estimated, generated, or unverified and safe against untrusted content, so that no new claim exceeds its evidence.

#### Acceptance Criteria

1. THE TerraVow_System SHALL compute every number shown in Requirements 18–27 (Health_Score, Remaining_Lifespan_Estimate, Pollution_Intensity, capex, payback, adjusted payback, emissions reduction) in engine code and SHALL NOT take any number from Gemini output as a computed result; a number supplied by Gemini SHALL be treated only as a labelled input parameter.
2. THE Decision_Gate and the verify-improvement flow SHALL behave exactly as before, and THE audit hash chain SHALL keep its record format, hash computation, verification, and ordering semantics unchanged (the only permitted change being that, in Firestore mode, the append is made transactional so the chain stays unforked when several service replicas write at once), and THE TerraVow_System SHALL pass existing tests for all three unchanged.
3. WHEN a decision-oriented output from Requirements 23–27 is produced (a sustainability recommendation, or a workflow decision such as resolving or closing a Snag), THE TerraVow_System SHALL append an audit entry using the existing hash-chained store before returning the output.
4. THE TerraVow_System SHALL label estimates as estimates, AI-generated content as AI-generated, sample data as sample data, and unverified items as unverified, and SHALL state "not an official certification" wherever health or lifespan is shown.
5. THE TerraVow_System SHALL NOT present any source, URL, regulation, or scheme that was not returned by grounding metadata or entered in a curated data file with its origin recorded.
6. THE TerraVow_System SHALL render all template, AI, catalogue, and user-supplied text in the user interface through text-safe means (text nodes or escaped output), SHALL NOT insert it as markup, and SHALL render only `http` or `https` Grounded_Source URLs as links with `rel="noopener noreferrer"`.
7. THE TerraVow_System SHALL read Gemini and Firestore credentials only from environment or platform identity and SHALL NOT embed or log any secret.
8. THE TerraVow_System SHALL validate every new endpoint input for type, required presence, and length (default free-text answer maximum 2000 characters, identifiers maximum 80 characters), and SHALL return a client-error response, not a server error, on invalid input.
9. THE TerraVow_System SHALL NOT send raw personal data to Gemini or persist it, consistent with Requirement 13.3, and SHALL store only pseudonymous reporter handles.
10. IF a Gemini call fails or returns invalid output in any new flow, THEN THE TerraVow_System SHALL degrade to the labelled deterministic fallback or an explicit unavailable message, never to a silently invented value.
11. THE TerraVow_System SHALL provide automated tests for the deterministic engines (Requirements 23, 24, 26, 27), the cache-once logic (Requirement 21) with a fake Gemini that counts calls, input validation, and the template schema validator.

### Requirement 29: Lower-Priority Items (Possible Concern Flag, Wear Tracking, Theme, Registry Exclusion)

**Product intent:** PI-M, PI-4. **User Story:** As a product owner, I want the lower-priority meeting items captured explicitly, so that they are either delivered carefully or recorded as follow-ups instead of dropped silently.

#### Acceptance Criteria

1. WHERE a Machine_Catalogue entry or Machine_Instance is flagged as an outdated or possibly restricted machine type, THE TerraVow_System SHALL show a "possible concern" flag that cites a dated authoritative source and the text "verify with the regulator", and SHALL NOT assert that the machine is illegal or banned.
2. IF no dated authoritative source is recorded for a flag, THEN THE TerraVow_System SHALL NOT display the flag. This item is a follow-up and MAY ship with zero flagged entries.
3. THE TerraVow_System SHALL show machine age (as-of date minus 1 January of the install year, displayed in years) and the age factor used in the Health_Score on the machine view (the wear tracking required by the meetings).
4. THE user interface SHALL use a light theme with white as the base, green as the primary accent, and blue as the secondary accent, and SHALL neutralise the `prefers-color-scheme: dark` block in `prototype/static/index.html` so the dark theme does not apply.
5. THE visual theme change SHALL keep the existing ALLOW, ASK, and BLOCK states distinguishable by label and icon or text as well as colour.
6. THE TerraVow_System SHALL NOT build or publish a public registry or certification for the prototype extension; the public registry and certification remain specification-only, and the deck and README SHALL say so.

### Requirement 30: Documentation Accuracy

**Product intent:** All. **User Story:** As a judge reading the repository, I want the README claims to match what runs, so that no claim exceeds the implementation.

#### Acceptance Criteria

1. THE root `README.md` SHALL be corrected so that the opening paragraph and the "Google Cloud — how the stack is used" table distinguish services used by the running prototype (Gemini via Vertex AI or AI Studio, Firestore, Cloud Run, and Google Search grounding once implemented) from services that are specified, designed, or stubbed only (Vertex AI Agent Engine, Vertex AI Search, BigQuery, Pub/Sub, Cloud Storage).
2. THE root `README.md` section "Honesty about scope" SHALL be corrected to state that a working prototype exists, SHALL state which of Requirements 16 and 17 (Integrity_Gate and detection hardening) are specified but not implemented, and SHALL correct the "64 correctness properties each validated" claim to the number actually implemented.
3. THE root `README.md` SHALL state that the public global registry and certification are specification-only and are not part of the running prototype.
4. THE root `README.md` SHALL replace its reference to Gemini model versions that the running prototype does not use with the model identifiers actually configured in the prototype, or state which are target-only.
5. THE `prototype/README.md` SHALL be updated to list the new endpoints, the Template cache-once behaviour, the Role switch, the data sources and their sample/illustrative status, the "no authentication" note, and a "What is NOT built" list that includes the items still outstanding.
6. THE `prototype/README.md` SHALL state that Google Search grounding is used only if and when the manual entry, template, recommendation, or incentive flows actually call it, and SHALL NOT list it before it is implemented.
7. THE documentation SHALL state that catalogue entries, Base_Templates, seeded snag histories, illustrative factors, and any `UNVERIFIED` incentive records are sample or illustrative data.
8. THE root `README.md` link to `competitive-landscape.md` SHALL point to the file's actual location under `docs/`.
