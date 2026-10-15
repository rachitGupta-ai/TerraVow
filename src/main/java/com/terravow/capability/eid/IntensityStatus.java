package com.terravow.capability.eid;

/**
 * The outcome status of an emissions-intensity computation.
 *
 * <p>Emissions intensity is emissions per unit of output (Req 1.1). When the required telemetry or
 * production output is absent or fails validation, the intensity is reported as unavailable with a
 * recorded reason rather than failing hard (Req 1.2). This enum distinguishes the two cases so the
 * EID computation that consumes an {@link EmissionsIntensityResult} can degrade gracefully.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.1, 1.2</a>
 */
public enum IntensityStatus {
    /** Emissions intensity was successfully computed as emissions / output. */
    AVAILABLE,
    /** Required inputs were absent or invalid; intensity is unavailable with a recorded reason. */
    UNAVAILABLE
}
