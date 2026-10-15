package com.terravow.ingest;

import com.terravow.capability.pollution.AnomalyEvaluation;
import com.terravow.capability.pollution.ThresholdEvaluation;

import java.util.Objects;
import java.util.Optional;

/**
 * The near-real-time triage result the continuous query produces for one landed row (Req 3.1, 3.3,
 * 3.4, 15.3).
 *
 * <p>A triage outcome records what the deterministic reused logic concluded for the event:
 * <ul>
 *   <li>For a reading, the Pollution Monitor's {@link #thresholdEvaluation()} (Req 3.1, 3.3) and
 *       {@link #anomalyEvaluation()} (Req 3.4) — produced by the existing
 *       {@link com.terravow.capability.pollution.PollutionMonitor}, reused unchanged.</li>
 *   <li>For a telemetry or job event, an optional {@link #lifecycleTrigger()} when the event
 *       indicates a degraded asset and so warrants Maintenance Lifecycle triage (Req 15.3).</li>
 * </ul>
 *
 * <p>All three carried values are optional because triage is per event kind: a reading carries the
 * threshold and anomaly evaluations and no lifecycle trigger; a telemetry/job event carries at most
 * a lifecycle trigger and no pollution evaluations. The static factory methods build the valid
 * combinations.
 *
 * @param recordId            the correlation id of the triaged event; never {@code null} or blank
 * @param thresholdEvaluation the Pollution Monitor threshold evaluation for a reading (Req 3.1,
 *                            3.3); empty for non-reading events
 * @param anomalyEvaluation   the Pollution Monitor anomaly evaluation for a reading (Req 3.4);
 *                            empty for non-reading events
 * @param lifecycleTrigger    the raised lifecycle triage trigger (Req 15.3); empty when no triage
 *                            was raised
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4, 15.3</a>
 */
public record TriageOutcome(
        String recordId,
        Optional<ThresholdEvaluation> thresholdEvaluation,
        Optional<AnomalyEvaluation> anomalyEvaluation,
        Optional<LifecycleTriageTrigger> lifecycleTrigger) {

    /**
     * Validates identity and the carried optionals (Req 3.1).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code recordId} is blank
     */
    public TriageOutcome {
        Objects.requireNonNull(recordId, "TriageOutcome recordId must not be null");
        Objects.requireNonNull(thresholdEvaluation, "thresholdEvaluation Optional must not be null");
        Objects.requireNonNull(anomalyEvaluation, "anomalyEvaluation Optional must not be null");
        Objects.requireNonNull(lifecycleTrigger, "lifecycleTrigger Optional must not be null");
        if (recordId.isBlank()) {
            throw new IllegalArgumentException("TriageOutcome recordId must not be blank");
        }
    }

    /**
     * Builds the triage outcome of a reading: the Pollution Monitor threshold and anomaly
     * evaluations, with no lifecycle trigger (Req 3.1, 3.3, 3.4).
     *
     * @param recordId  the correlation id; never {@code null} or blank
     * @param threshold the threshold evaluation; never {@code null}
     * @param anomaly   the anomaly evaluation; never {@code null}
     * @return the reading triage outcome
     */
    public static TriageOutcome ofReading(
            String recordId, ThresholdEvaluation threshold, AnomalyEvaluation anomaly) {
        return new TriageOutcome(
                recordId,
                Optional.of(threshold),
                Optional.of(anomaly),
                Optional.empty());
    }

    /**
     * Builds the triage outcome of a telemetry or job event that raised Maintenance Lifecycle
     * triage (Req 15.3).
     *
     * @param recordId the correlation id; never {@code null} or blank
     * @param trigger  the raised lifecycle triage trigger; never {@code null}
     * @return the lifecycle-triage outcome
     */
    public static TriageOutcome ofLifecycleTriage(String recordId, LifecycleTriageTrigger trigger) {
        return new TriageOutcome(
                recordId,
                Optional.empty(),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(trigger, "trigger must not be null")));
    }

    /**
     * Builds the triage outcome of an event that landed but raised no condition (a non-degraded
     * telemetry or job event).
     *
     * @param recordId the correlation id; never {@code null} or blank
     * @return a triage outcome carrying no evaluations and no trigger
     */
    public static TriageOutcome none(String recordId) {
        return new TriageOutcome(
                recordId, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * @return {@code true} iff this reading triage raised a threshold-violation (Req 3.3)
     */
    public boolean raisedThresholdViolation() {
        return thresholdEvaluation
                .map(e -> e.status() == ThresholdEvaluation.Status.VIOLATION)
                .orElse(false);
    }

    /**
     * @return {@code true} iff this reading triage raised an anomaly (Req 3.4)
     */
    public boolean raisedAnomaly() {
        return anomalyEvaluation
                .map(e -> e.status() == AnomalyEvaluation.Status.ANOMALY)
                .orElse(false);
    }
}
