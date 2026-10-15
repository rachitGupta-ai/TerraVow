package com.terravow.domain;

/**
 * The outcome status of a capability determination.
 *
 * <p>Every TerraVow capability produces a determination whose status records whether a value could
 * be produced, could not be determined from the available inputs, or became unavailable because a
 * dependency (e.g. a verified tool or managed path) failed. This lets the system degrade gracefully
 * — marking a result undetermined or unavailable with a recorded reason — rather than failing hard.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.2, 1.6, 12.6</a>
 */
public enum DeterminationStatus {
    /** A value was successfully computed. */
    COMPUTED,
    /** No value could be determined from the available inputs; a reason is recorded. */
    UNDETERMINED,
    /** A dependency (verified tool or managed path) failed, so the value is unavailable. */
    UNAVAILABLE
}
