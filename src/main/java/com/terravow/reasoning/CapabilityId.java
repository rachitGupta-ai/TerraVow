package com.terravow.reasoning;

/**
 * Identifies the determination family (capability) a reasoning request is produced for (Req 11.1,
 * 11.5).
 *
 * <p>The root-orchestrator agent on Vertex AI Agent Engine delegates each request to the
 * per-capability specialist agent named by this identifier. On exhausted retries, the capability is
 * recorded as part of the failure entry routed to human review (Req 11.5).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 11.5</a>
 */
public enum CapabilityId {
    /** Emissions-Intensity Delta attribution (Req 1). */
    EID_ATTRIBUTION,
    /** Emissions-Related Debt quantification (Req 2). */
    ERD_QUANTIFICATION,
    /** Pollution monitoring and cross-media guardrail (Req 3). */
    POLLUTION_MONITOR,
    /** Avoidable Exposure Index and harm-aware rescheduling (Req 4). */
    AEI,
    /** Substitution dossier synthesis (Req 5). */
    SUBSTITUTION_DOSSIER,
    /** Regulatory and incentive economics reasoning (Req 6). */
    ECONOMICS,
    /** Embedded Pollution Passport propagation (Req 7). */
    POLLUTION_PASSPORT,
    /** Trajectory certification (Req 9). */
    CERTIFICATION,
    /** Emissions-aware maintenance lifecycle (Req 15). */
    MAINTENANCE_LIFECYCLE
}
