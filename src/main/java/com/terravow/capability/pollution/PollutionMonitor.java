package com.terravow.capability.pollution;

/**
 * Monitors ingested stack, effluent, and sensor readings for threshold and anomaly conditions
 * (Req 3.1–3.5).
 *
 * <p>Each ingested reading is evaluated against the applicable regulatory threshold for its
 * pollutant and environmental medium (Req 3.1); a reading with no applicable threshold is recorded
 * as unevaluated-for-threshold with a reason (Req 3.2); a reading that strictly exceeds its
 * threshold raises a complete threshold-violation carrying the pollutant, medium, measured value,
 * exceeded threshold, and an Evidence_Chain linking to the reading (Req 3.3).
 *
 * <p>Independently, a reading is evaluated against its stream's expected profile: once a stream has
 * accumulated at least the configured minimum history, a reading deviating by at least the
 * configured anomaly-deviation threshold raises an anomaly condition (Req 3.4); below the minimum
 * history, anomaly evaluation is deferred as insufficient-history (Req 3.5).
 *
 * <p>Threshold evaluation is a pure function of the reading and the registry. Anomaly evaluation is
 * stateful per stream — the expected profile is built from the readings a stream has already seen —
 * but is otherwise deterministic: the same sequence of readings always yields the same evaluations.
 *
 * @see DefaultPollutionMonitor
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.2, 3.3, 3.4,
 *      3.5</a>
 */
public interface PollutionMonitor {

    /**
     * Evaluates a reading against its applicable regulatory threshold (Req 3.1–3.3).
     *
     * @param reading the ingested reading; never {@code null}
     * @return a within-limit, violation, or unevaluated-for-threshold result
     * @throws NullPointerException if {@code reading} is {@code null}
     */
    ThresholdEvaluation evaluateThreshold(Reading reading);

    /**
     * Evaluates a reading against its stream's expected profile, then records the reading into that
     * stream's history (Req 3.4, 3.5).
     *
     * <p>The evaluation is performed against the history accumulated <em>before</em> this reading;
     * the reading is then appended so it contributes to the profile seen by subsequent readings.
     *
     * @param reading the ingested reading; never {@code null}
     * @return a normal, anomaly, or insufficient-history result
     * @throws NullPointerException if {@code reading} is {@code null}
     */
    AnomalyEvaluation evaluateAnomaly(Reading reading);
}
