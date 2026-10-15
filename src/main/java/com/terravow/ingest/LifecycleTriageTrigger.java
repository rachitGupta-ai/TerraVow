package com.terravow.ingest;

import com.terravow.domain.MachineId;

import java.util.Objects;

/**
 * A near-real-time signal that a streamed telemetry or job event warrants Emissions-Aware
 * Maintenance Lifecycle triage (Req 15.3).
 *
 * <p>The continuous query raises this trigger when an incoming telemetry or job row indicates a
 * degraded asset. It is deliberately a lightweight hand-off, not a maintenance decision: it names
 * the machine, the {@link Reason} the event raised, and the correlation id of the originating event
 * so the Maintenance Lifecycle can enqueue the asset for prioritisation. Prioritisation itself (the
 * Emissions_Aware_Priority ranking where environmental harm can outrank failure probability,
 * Req 15.3) remains the deterministic lifecycle logic and is reused unchanged — this trigger only
 * decides <em>when</em> to invoke it, never <em>how</em> it ranks.
 *
 * @param machine  the degraded asset to triage; never {@code null}
 * @param reason   why the streamed event raised lifecycle triage; never {@code null}
 * @param recordId the correlation id of the originating event; never {@code null} or blank
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 15.3</a>
 */
public record LifecycleTriageTrigger(MachineId machine, Reason reason, String recordId) {

    /** Why a streamed event raised Maintenance Lifecycle triage (Req 15.3). */
    public enum Reason {
        /** Telemetry indicated the asset is degraded. */
        DEGRADED_TELEMETRY,
        /** A job-run event indicated the asset is degraded. */
        DEGRADED_JOB
    }

    /**
     * Validates the trigger carries a machine, reason, and non-blank correlation id (Req 15.3).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code recordId} is blank
     */
    public LifecycleTriageTrigger {
        Objects.requireNonNull(machine, "LifecycleTriageTrigger machine must not be null");
        Objects.requireNonNull(reason, "LifecycleTriageTrigger reason must not be null");
        Objects.requireNonNull(recordId, "LifecycleTriageTrigger recordId must not be null");
        if (recordId.isBlank()) {
            throw new IllegalArgumentException("LifecycleTriageTrigger recordId must not be blank");
        }
    }
}
