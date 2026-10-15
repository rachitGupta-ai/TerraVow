package com.terravow.capability.eid;

/**
 * The outcome status of an EID (Emissions-Intensity Degradation) determination.
 *
 * <p>An EID is computed as the delta between the current emissions intensity and a baseline
 * intensity (Req 1.3, 1.5). When neither a Commissioning_Baseline nor a Fleet_Cohort_Baseline is
 * available — or the current intensity itself is unavailable — the EID is reported as undetermined
 * with a recorded reason (Req 1.6). This enum captures that distinction.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.3, 1.5, 1.6</a>
 */
public enum EidStatus {
    /** An EID value was successfully computed against a baseline. */
    COMPUTED,
    /** No baseline (or no current intensity) was available; EID is undetermined with a reason. */
    UNDETERMINED
}
