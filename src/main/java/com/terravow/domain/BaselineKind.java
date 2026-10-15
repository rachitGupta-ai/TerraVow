package com.terravow.domain;

/**
 * Which baseline an EID (or derived) determination was computed against.
 *
 * <p>EID is computed against a {@link CommissioningBaseline} when one exists (Req 1.3), and falls
 * back to a {@link FleetCohortBaseline} at strictly lower confidence when the commissioning baseline
 * is absent (Req 1.5). When both are absent the determination is undetermined (Req 1.6). This enum
 * records which path was taken so the confidence rule and the Evidence_Chain can reflect it.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.3, 1.5, 1.6</a>
 */
public enum BaselineKind {
    /** Computed against the machine's Commissioning_Baseline. */
    COMMISSIONING,
    /** Computed against a Fleet_Cohort_Baseline fallback at strictly lower confidence. */
    FLEET_COHORT,
    /** No baseline available; the determination is undetermined. */
    NONE
}
