package com.terravow.capability.pollution;

import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic implementation of {@link PollutionMonitor} (Req 3.1–3.5).
 *
 * <p><b>Threshold evaluation (Req 3.1–3.3)</b> is a pure function of the reading and the configured
 * {@link ThresholdRegistry}: look up the applicable threshold for the reading's pollutant and
 * medium; if none is registered, return unevaluated-for-threshold with a reason (Req 3.2); if the
 * measured value strictly exceeds the threshold, raise a complete {@link ThresholdViolation}
 * carrying the pollutant, medium, measured value, exceeded threshold, and an Evidence_Chain linking
 * to the reading (Req 3.3); otherwise it is within-limit.
 *
 * <p><b>Anomaly evaluation (Req 3.4, 3.5)</b> is stateful per {@link StreamId}: the monitor keeps
 * the ordered history of measured values seen on each stream. A reading is evaluated against the
 * history accumulated <em>before</em> it. With fewer than {@link PollutionMonitorConfig#minHistory()}
 * prior readings the stream cannot establish an expected profile, so evaluation is deferred as
 * insufficient-history (Req 3.5). With at least that many prior readings the expected profile is
 * the mean and (population) standard deviation of the history, and the reading's deviation is the
 * robust z-score {@code |value - mean| / stddev}. When the historical standard deviation is zero
 * (a perfectly flat history), any value equal to the mean has zero deviation and any differing
 * value is treated as maximally deviant. A deviation at or above
 * {@link PollutionMonitorConfig#anomalyDeviationThreshold()} raises an anomaly with an
 * Evidence_Chain linking to the reading (Req 3.4).
 *
 * <p>The per-stream history is held in a {@link ConcurrentHashMap} so the monitor is safe to share
 * across concurrent ingestion of <em>different</em> streams; evaluation of a single stream is
 * serialised on that stream's history list. The logic itself reads no wall-clock time and uses no
 * randomness, so a given sequence of readings always yields the same evaluations.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.2, 3.3, 3.4,
 *      3.5</a>
 */
public final class DefaultPollutionMonitor implements PollutionMonitor {

    private final ThresholdRegistry thresholds;
    private final PollutionMonitorConfig config;
    private final Map<StreamId, List<Double>> history = new ConcurrentHashMap<>();

    /**
     * Creates a monitor over the given threshold registry and configuration.
     *
     * @param thresholds the applicable regulatory thresholds; never {@code null}
     * @param config     the anomaly-detection configuration; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public DefaultPollutionMonitor(ThresholdRegistry thresholds, PollutionMonitorConfig config) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public ThresholdEvaluation evaluateThreshold(Reading reading) {
        Objects.requireNonNull(reading, "reading must not be null");

        Optional<Double> applicable =
                thresholds.thresholdFor(reading.pollutant(), reading.medium());
        if (applicable.isEmpty()) {
            // Req 3.2 — no applicable threshold: record as unevaluated-for-threshold with a reason.
            return ThresholdEvaluation.unevaluated(
                    "No applicable regulatory threshold for pollutant '"
                            + reading.pollutant().name() + "' in medium " + reading.medium());
        }

        double threshold = applicable.get();
        if (reading.measuredValue() > threshold) {
            // Req 3.3 — strict exceedance raises a complete violation linking to the reading.
            EvidenceChain chain = EvidenceChain.EMPTY.with(
                    new EvidenceLink(SourceType.READING, reading.source()));
            ThresholdViolation violation = new ThresholdViolation(
                    reading.pollutant(),
                    reading.medium(),
                    reading.measuredValue(),
                    threshold,
                    chain);
            return ThresholdEvaluation.violation(violation);
        }

        // Req 3.1 — a threshold applied and the reading was within it.
        return ThresholdEvaluation.withinLimit();
    }

    /** {@inheritDoc} */
    @Override
    public AnomalyEvaluation evaluateAnomaly(Reading reading) {
        Objects.requireNonNull(reading, "reading must not be null");

        List<Double> stream =
                history.computeIfAbsent(reading.streamId(), k -> new ArrayList<>());

        synchronized (stream) {
            int priorCount = stream.size();
            if (priorCount < config.minHistory()) {
                // Req 3.5 — insufficient history: defer, record the reading for future profiles.
                stream.add(reading.measuredValue());
                return AnomalyEvaluation.insufficientHistory(
                        "Stream '" + reading.streamId().id() + "' has " + priorCount
                                + " prior reading(s); minimum " + config.minHistory()
                                + " required to establish an expected profile");
            }

            // Req 3.4 — sufficient history: evaluate deviation against the expected profile.
            double deviation = deviationFromProfile(stream, reading.measuredValue());
            stream.add(reading.measuredValue());

            if (deviation >= config.anomalyDeviationThreshold()) {
                EvidenceChain chain = EvidenceChain.EMPTY.with(
                        new EvidenceLink(SourceType.READING, reading.source()));
                return AnomalyEvaluation.anomaly(deviation, chain);
            }
            return AnomalyEvaluation.normal(deviation);
        }
    }

    /**
     * Computes the robust z-score deviation of {@code value} from the expected profile defined by
     * {@code history}: {@code |value - mean| / stddev}. A zero-variance history yields {@code 0.0}
     * when {@code value} equals the mean and {@link Double#MAX_VALUE} (treated as maximally
     * deviant) otherwise, so a departure from a perfectly flat baseline is always flagged.
     *
     * @param history the prior measured values (non-empty); the expected profile
     * @param value   the measured value to score
     * @return the non-negative deviation
     */
    private static double deviationFromProfile(List<Double> history, double value) {
        int n = history.size();
        double sum = 0.0;
        for (double v : history) {
            sum += v;
        }
        double mean = sum / n;

        double sqDiff = 0.0;
        for (double v : history) {
            double d = v - mean;
            sqDiff += d * d;
        }
        double stddev = Math.sqrt(sqDiff / n);

        double absDelta = Math.abs(value - mean);
        if (stddev == 0.0) {
            return absDelta == 0.0 ? 0.0 : Double.MAX_VALUE;
        }
        return absDelta / stddev;
    }
}
