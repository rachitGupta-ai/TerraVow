package com.terravow.capability.pollution;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.SourceRef;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for sufficient-history anomaly detection (Req 3.4).
 *
 * <p>Validates <b>Property 10: Anomaly is raised exactly on sufficient-history deviation</b>: for
 * any reading stream that has accumulated at least the configured minimum history, a subsequently
 * ingested reading raises an anomaly condition if and only if its deviation from the expected
 * profile is at least the configured anomaly-deviation threshold.
 *
 * <p>The test seeds a stream with at least {@code minHistory} readings so a profile is established,
 * computes the deviation of the next reading the same deterministic way the monitor does, and
 * asserts the biconditional: {@code status == ANOMALY} iff {@code deviation >= threshold}.
 *
 * <p><b>Validates: Requirements 3.4</b>
 *
 * @see DefaultPollutionMonitor
 */
// Feature: terravow, Property 10: Anomaly is raised exactly on sufficient-history deviation
class AnomalySufficientHistoryProperties {

    private static final Pollutant COD = new Pollutant("COD");
    private static final EnvironmentalMedium MEDIUM = EnvironmentalMedium.WATER;

    /**
     * With a profile established from at least {@code minHistory} readings, a next reading raises an
     * anomaly iff its deviation from the profile is at least the threshold (Req 3.4). The expected
     * deviation is recomputed independently from the seeded history to form the oracle.
     */
    @Property(tries = 100)
    void anomalyRaisedIffDeviationAtOrAboveThreshold(
            @ForAll @IntRange(min = 1, max = 8) int minHistory,
            @ForAll @Size(min = 1, max = 20) List<@net.jqwik.api.constraints.DoubleRange(min = 1.0, max = 500.0) Double> baseHistory,
            @ForAll @net.jqwik.api.constraints.DoubleRange(min = 0.0, max = 10.0) double deviationThreshold,
            @ForAll @net.jqwik.api.constraints.DoubleRange(min = 0.0, max = 1000.0) double nextValue) {

        // Ensure the stream has at least minHistory readings before the evaluated one.
        List<Double> history = ensureMinSize(baseHistory, minHistory);

        PollutionMonitorConfig config =
                new PollutionMonitorConfig(minHistory, deviationThreshold);
        PollutionMonitor monitor = new DefaultPollutionMonitor(ThresholdRegistry.empty(), config);

        StreamId stream = new StreamId("effluent-1");
        int i = 0;
        for (double v : history) {
            monitor.evaluateAnomaly(reading(stream, v, "seed-" + (i++)));
        }

        double expectedDeviation = deviation(history, nextValue);

        AnomalyEvaluation evaluation =
                monitor.evaluateAnomaly(reading(stream, nextValue, "eval"));

        boolean raised = evaluation.status() == AnomalyEvaluation.Status.ANOMALY;
        boolean shouldRaise = expectedDeviation >= deviationThreshold;

        assertThat(raised)
                .as("anomaly raised iff deviation %s >= threshold %s",
                        expectedDeviation, deviationThreshold)
                .isEqualTo(shouldRaise);

        if (raised) {
            // Req 3.4 — a raised anomaly carries the deviation and a non-empty Evidence_Chain.
            assertThat(evaluation.deviation()).contains(expectedDeviation);
            assertThat(evaluation.evidence()).isPresent();
            assertThat(evaluation.evidence().orElseThrow().links()).isNotEmpty();
        } else {
            assertThat(evaluation.status()).isEqualTo(AnomalyEvaluation.Status.NORMAL);
        }
    }

    private static List<Double> ensureMinSize(List<Double> base, int minSize) {
        if (base.size() >= minSize) {
            return base;
        }
        List<Double> padded = new java.util.ArrayList<>(base);
        while (padded.size() < minSize) {
            padded.add(100.0);
        }
        return padded;
    }

    /** Mirrors the monitor's robust z-score: {@code |value - mean| / stddev}. */
    private static double deviation(List<Double> history, double value) {
        int n = history.size();
        double sum = 0.0;
        for (double v : history) {
            sum += v;
        }
        double mean = sum / n;
        double sq = 0.0;
        for (double v : history) {
            double d = v - mean;
            sq += d * d;
        }
        double stddev = Math.sqrt(sq / n);
        double absDelta = Math.abs(value - mean);
        if (stddev == 0.0) {
            return absDelta == 0.0 ? 0.0 : Double.MAX_VALUE;
        }
        return absDelta / stddev;
    }

    private static Reading reading(StreamId stream, double value, String refId) {
        return new Reading(stream, COD, MEDIUM, value, new SourceRef(refId + "-" + value));
    }

    /** Unused placeholder provider kept for symmetry with other property tests. */
    @Provide
    Arbitrary<Double> values() {
        return Arbitraries.doubles().between(0.0, 1000.0).ofScale(4);
    }
}
