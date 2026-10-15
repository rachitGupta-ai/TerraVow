package com.terravow.capability.pollution;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultPollutionMonitor}, exercising threshold evaluation (Req 3.1–3.3) and
 * anomaly evaluation (Req 3.4, 3.5) with specific examples and boundary values.
 */
class DefaultPollutionMonitorTest {

    private static final Pollutant PM25 = new Pollutant("PM2.5");
    private static final Pollutant SOX = new Pollutant("SOx");
    private static final EnvironmentalMedium AIR = EnvironmentalMedium.AIR;

    private static Reading airReading(String stream, double value, String ref) {
        return new Reading(new StreamId(stream), PM25, AIR, value, new SourceRef(ref));
    }

    // Req 3.3 — a reading strictly above its threshold raises a complete violation.
    @Test
    void exceedanceRaisesCompleteViolation() {
        ThresholdRegistry registry =
                ThresholdRegistry.builder().with(PM25, AIR, 50.0).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);

        ThresholdEvaluation eval =
                monitor.evaluateThreshold(airReading("stack-1", 75.0, "r-1"));

        assertThat(eval.status()).isEqualTo(ThresholdEvaluation.Status.VIOLATION);
        ThresholdViolation v = eval.violation().orElseThrow();
        assertThat(v.pollutant()).isEqualTo(PM25);
        assertThat(v.medium()).isEqualTo(AIR);
        assertThat(v.measuredValue()).isEqualTo(75.0);
        assertThat(v.exceededThreshold()).isEqualTo(50.0);
        assertThat(v.evidence().links())
                .anyMatch(l -> l.type() == SourceType.READING
                        && l.ref().equals(new SourceRef("r-1")));
    }

    // Req 3.1 — a reading exactly at the threshold is within-limit (strict exceedance boundary).
    @Test
    void readingAtThresholdIsWithinLimit() {
        ThresholdRegistry registry =
                ThresholdRegistry.builder().with(PM25, AIR, 50.0).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);

        ThresholdEvaluation eval =
                monitor.evaluateThreshold(airReading("stack-1", 50.0, "r-1"));

        assertThat(eval.status()).isEqualTo(ThresholdEvaluation.Status.WITHIN_LIMIT);
        assertThat(eval.violation()).isEmpty();
    }

    // Req 3.2 — a reading with no applicable threshold is unevaluated-for-threshold with a reason.
    @Test
    void noApplicableThresholdIsUnevaluatedWithReason() {
        // Registry has a threshold for PM2.5/AIR but not for SOx/AIR.
        ThresholdRegistry registry =
                ThresholdRegistry.builder().with(PM25, AIR, 50.0).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);

        Reading sox = new Reading(new StreamId("stack-1"), SOX, AIR, 10.0, new SourceRef("r-1"));
        ThresholdEvaluation eval = monitor.evaluateThreshold(sox);

        assertThat(eval.status()).isEqualTo(ThresholdEvaluation.Status.UNEVALUATED);
        assertThat(eval.violation()).isEmpty();
        assertThat(eval.reason()).isPresent();
        assertThat(eval.reason().orElseThrow()).contains("SOx").contains("AIR");
    }

    // Req 3.2 — an entirely empty registry leaves every reading unevaluated.
    @Test
    void emptyRegistryLeavesReadingUnevaluated() {
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(
                        ThresholdRegistry.empty(), PollutionMonitorConfig.DEFAULT);

        ThresholdEvaluation eval =
                monitor.evaluateThreshold(airReading("stack-1", 10.0, "r-1"));

        assertThat(eval.status()).isEqualTo(ThresholdEvaluation.Status.UNEVALUATED);
    }

    // Req 3.1 — pollutant lookup is case-insensitive.
    @Test
    void thresholdLookupIsCaseInsensitive() {
        ThresholdRegistry registry =
                ThresholdRegistry.builder().with(new Pollutant("pm2.5"), AIR, 50.0).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);

        // Reading uses the "PM2.5" casing; threshold registered as "pm2.5".
        ThresholdEvaluation eval =
                monitor.evaluateThreshold(airReading("stack-1", 75.0, "r-1"));

        assertThat(eval.status()).isEqualTo(ThresholdEvaluation.Status.VIOLATION);
    }

    // Req 3.5 — below the minimum history, anomaly evaluation is deferred as insufficient-history.
    @Test
    void belowMinHistoryDefersAnomalyEvaluation() {
        PollutionMonitorConfig config = new PollutionMonitorConfig(3, 2.0);
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), config);

        // First three readings each have fewer than 3 prior readings → insufficient-history.
        for (int i = 0; i < 3; i++) {
            AnomalyEvaluation eval =
                    monitor.evaluateAnomaly(airReading("stack-1", 10.0, "r-" + i));
            assertThat(eval.status())
                    .isEqualTo(AnomalyEvaluation.Status.INSUFFICIENT_HISTORY);
            assertThat(eval.reason()).isPresent();
        }
    }

    // Req 3.5 → 3.4 — the exact boundary: the reading with exactly minHistory prior readings is the
    // first one evaluated against an established profile.
    @Test
    void insufficientHistoryBoundaryIsExact() {
        PollutionMonitorConfig config = new PollutionMonitorConfig(3, 2.0);
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), config);

        // Seed exactly minHistory (3) readings — all insufficient-history.
        monitor.evaluateAnomaly(airReading("stack-1", 10.0, "r-0"));
        monitor.evaluateAnomaly(airReading("stack-1", 10.0, "r-1"));
        monitor.evaluateAnomaly(airReading("stack-1", 10.0, "r-2"));

        // The 4th reading now has 3 prior readings == minHistory → profile is evaluated.
        AnomalyEvaluation eval =
                monitor.evaluateAnomaly(airReading("stack-1", 10.0, "r-3"));

        assertThat(eval.status()).isNotEqualTo(AnomalyEvaluation.Status.INSUFFICIENT_HISTORY);
        assertThat(eval.deviation()).isPresent();
    }

    // Req 3.4 — a large departure from a stable profile raises an anomaly with evidence.
    @Test
    void largeDeviationFromStableProfileRaisesAnomaly() {
        PollutionMonitorConfig config = new PollutionMonitorConfig(3, 3.0);
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), config);

        // Profile with small variance around ~10.
        monitor.evaluateAnomaly(airReading("stack-1", 9.0, "r-0"));
        monitor.evaluateAnomaly(airReading("stack-1", 10.0, "r-1"));
        monitor.evaluateAnomaly(airReading("stack-1", 11.0, "r-2"));

        // A reading far from the mean → high z-score → anomaly.
        AnomalyEvaluation eval =
                monitor.evaluateAnomaly(airReading("stack-1", 500.0, "r-3"));

        assertThat(eval.status()).isEqualTo(AnomalyEvaluation.Status.ANOMALY);
        assertThat(eval.evidence()).isPresent();
        assertThat(eval.evidence().orElseThrow().links())
                .anyMatch(l -> l.ref().equals(new SourceRef("r-3")));
    }

    // Req 3.4 — a reading consistent with the profile is normal (no anomaly).
    @Test
    void readingConsistentWithProfileIsNormal() {
        PollutionMonitorConfig config = new PollutionMonitorConfig(3, 3.0);
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), config);

        monitor.evaluateAnomaly(airReading("stack-1", 9.0, "r-0"));
        monitor.evaluateAnomaly(airReading("stack-1", 10.0, "r-1"));
        monitor.evaluateAnomaly(airReading("stack-1", 11.0, "r-2"));

        AnomalyEvaluation eval =
                monitor.evaluateAnomaly(airReading("stack-1", 10.0, "r-3"));

        assertThat(eval.status()).isEqualTo(AnomalyEvaluation.Status.NORMAL);
    }

    // Req 3.4, 3.5 — anomaly history is per-stream: one stream's readings do not affect another's.
    @Test
    void anomalyHistoryIsPerStream() {
        PollutionMonitorConfig config = new PollutionMonitorConfig(2, 3.0);
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), config);

        // Build history only on stack-1.
        monitor.evaluateAnomaly(airReading("stack-1", 10.0, "a-0"));
        monitor.evaluateAnomaly(airReading("stack-1", 10.0, "a-1"));

        // stack-2 has no history yet → insufficient-history despite stack-1 being established.
        AnomalyEvaluation eval =
                monitor.evaluateAnomaly(airReading("stack-2", 999.0, "b-0"));

        assertThat(eval.status()).isEqualTo(AnomalyEvaluation.Status.INSUFFICIENT_HISTORY);
    }
}
