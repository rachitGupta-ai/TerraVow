/**
 * In-memory domain-core stores with required-field validation (task 2.2, Req 14.2, 14.4–14.6,
 * 14.9, 14.10).
 *
 * <p>The domain-core <em>records</em> in {@link com.terravow.domain} enforce their invariants
 * structurally — a constructed {@code MachineAsset}, {@code MaintenanceEvent}, or {@code JobRun}
 * can never hold a {@code null} required field. Requirement 14, however, specifies the behaviour at
 * the <em>ingestion boundary</em>: a registration or event submitted with a missing required field
 * must be <em>rejected</em>, the missing field recorded, and nothing created (Req 14.2, 14.4) —
 * which means validation must happen <em>before</em> the immutable record is constructed. This
 * package supplies that boundary: {@code *Submission} value types carry the raw, possibly-incomplete
 * fields; the stores validate them, construct the record only when complete, and otherwise return a
 * {@link com.terravow.domain.store.StoreResult} that records the missing field and creates nothing.
 *
 * <p>The package also hosts the deterministic derivations Requirement 14 attaches to the stores:
 * the {@link com.terravow.domain.store.DeferralCalculator} measures a Deferral_Period from a
 * scheduled action's due time and anchors the degraded-onset to the Deferral_Period start (Req
 * 14.5, 14.6), and the {@link com.terravow.domain.store.HealthStateDeriver} derives a
 * Machine_Health_State from telemetry and maintenance history, falling back to a
 * {@code FleetCohortBaseline} estimate at strictly lower confidence when history is insufficient
 * (Req 14.9, 14.10). All time-dependent logic takes an injected {@link java.time.Clock} so the
 * derivations stay deterministic and testable (no direct {@code Instant.now()}).
 *
 * <p>Persistence here is in-memory only; the Firestore/BigQuery persistence split is task 23.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.2, 14.4, 14.5,
 *      14.6, 14.9, 14.10</a>
 */
package com.terravow.domain.store;
