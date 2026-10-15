package com.terravow.ingest;

import com.terravow.capability.pollution.AnomalyEvaluation;
import com.terravow.capability.pollution.PollutionMonitor;
import com.terravow.capability.pollution.ThresholdEvaluation;
import com.terravow.domain.MachineId;

import java.util.Objects;

/**
 * A deterministic in-memory {@link ContinuousQueryPort} that triages landed rows by delegating to
 * the existing {@link PollutionMonitor} and raising {@link LifecycleTriageTrigger}s, for local-first
 * operation and tests (Req 3.1, 3.3, 3.4, 15.3).
 *
 * <p>TerraVow is local-first and has no live BigQuery continuous query in the dev/test environment,
 * so this stub performs the near-real-time triage in-process while preserving the exact production
 * behaviour that matters: the deterministic threshold and anomaly logic is <em>not</em>
 * re-implemented here — a {@link ReadingEvent} is handed to the injected {@link PollutionMonitor},
 * which produces the {@link ThresholdEvaluation} (Req 3.1, 3.3) and {@link AnomalyEvaluation}
 * (Req 3.4) unchanged. A degraded {@link TelemetryEvent} or {@link JobEvent} raises a lifecycle
 * triage trigger (Req 15.3); a non-degraded one raises nothing.
 *
 * <p>Availability is a mutable flag so a test (and the batch-micro-poll fallback of task 22.2) can
 * simulate the continuous query going offline via {@link #setAvailable(boolean)}; the triage logic
 * is otherwise free of wall-clock time and randomness, so a given sequence of events always yields
 * the same outcomes.
 *
 * @see ContinuousQueryPort
 * @see PollutionMonitor
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4, 12.5,
 *      15.3</a>
 */
public final class InMemoryContinuousQueryPort implements ContinuousQueryPort {

    private final PollutionMonitor pollutionMonitor;
    private volatile boolean available = true;

    /**
     * Creates a continuous-query stub that delegates reading triage to the given Pollution Monitor
     * (reused unchanged, Req 3.1–3.4).
     *
     * @param pollutionMonitor the existing Pollution Monitor to delegate threshold/anomaly triage
     *                         to; never {@code null}
     * @throws NullPointerException if {@code pollutionMonitor} is {@code null}
     */
    public InMemoryContinuousQueryPort(PollutionMonitor pollutionMonitor) {
        this.pollutionMonitor =
                Objects.requireNonNull(pollutionMonitor, "pollutionMonitor must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public TriageOutcome triage(StreamEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event instanceof StreamEvent.ReadingEvent reading) {
            return triageReading(reading);
        }
        if (event instanceof StreamEvent.TelemetryEvent telemetry) {
            return triageTelemetry(telemetry);
        }
        if (event instanceof StreamEvent.JobEvent job) {
            return triageJob(job);
        }
        throw new IllegalStateException("Unhandled StreamEvent kind: " + event.kind());
    }

    private TriageOutcome triageReading(StreamEvent.ReadingEvent event) {
        // Req 3.1, 3.3 — threshold triage via the reused Pollution Monitor (logic unchanged).
        ThresholdEvaluation threshold = pollutionMonitor.evaluateThreshold(event.reading());
        // Req 3.4 — anomaly triage via the reused Pollution Monitor (logic unchanged).
        AnomalyEvaluation anomaly = pollutionMonitor.evaluateAnomaly(event.reading());
        return TriageOutcome.ofReading(event.recordId(), threshold, anomaly);
    }

    private TriageOutcome triageTelemetry(StreamEvent.TelemetryEvent event) {
        if (!event.degraded()) {
            return TriageOutcome.none(event.recordId());
        }
        // Req 15.3 — degraded telemetry triggers Maintenance Lifecycle triage.
        LifecycleTriageTrigger trigger = new LifecycleTriageTrigger(
                new MachineId(event.machineId()),
                LifecycleTriageTrigger.Reason.DEGRADED_TELEMETRY,
                event.recordId());
        return TriageOutcome.ofLifecycleTriage(event.recordId(), trigger);
    }

    private TriageOutcome triageJob(StreamEvent.JobEvent event) {
        if (!event.degraded()) {
            return TriageOutcome.none(event.recordId());
        }
        // Req 15.3 — a degraded job event triggers Maintenance Lifecycle triage.
        LifecycleTriageTrigger trigger = new LifecycleTriageTrigger(
                new MachineId(event.machineId()),
                LifecycleTriageTrigger.Reason.DEGRADED_JOB,
                event.recordId());
        return TriageOutcome.ofLifecycleTriage(event.recordId(), trigger);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Sets whether the continuous query reports available, simulating it going offline so the
     * batch-micro-poll fallback can be exercised (Req 12.5, task 22.2).
     *
     * @param available the availability to report
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }
}
