package com.terravow.capability.pollution;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based test for threshold-violation completeness (Req 3.1, 3.3).
 *
 * <p>Validates <b>Property 9: Threshold exceedance raises a complete violation</b>: for any
 * ingested reading whose measured value strictly exceeds its applicable regulatory threshold, a
 * threshold-violation condition is raised carrying the pollutant, the environmental medium, the
 * measured value, the exceeded threshold value, and a non-empty Evidence_Chain linking to the
 * reading.
 *
 * <p><b>Validates: Requirements 3.1, 3.3</b>
 *
 * @see DefaultPollutionMonitor
 */
// Feature: terravow, Property 9: Threshold exceedance raises a complete violation
class CompleteThresholdViolationProperties {

    private static final Pollutant PM25 = new Pollutant("PM2.5");
    private static final EnvironmentalMedium MEDIUM = EnvironmentalMedium.AIR;

    /**
     * For any threshold and any measured value strictly above it, the evaluation is a VIOLATION
     * carrying the full context (pollutant, medium, measured value, exceeded threshold) and a
     * non-empty Evidence_Chain whose link resolves to the reading (Req 3.3).
     */
    @Property(tries = 100)
    void strictExceedanceRaisesCompleteViolation(
            @ForAll("thresholds") double threshold,
            @ForAll("positiveDelta") double delta) {

        double measured = threshold + delta;

        ThresholdRegistry registry =
                ThresholdRegistry.builder().with(PM25, MEDIUM, threshold).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);

        SourceRef source = new SourceRef("reading-" + System.identityHashCode(this) + "-" + measured);
        Reading reading = new Reading(new StreamId("stack-1"), PM25, MEDIUM, measured, source);

        ThresholdEvaluation evaluation = monitor.evaluateThreshold(reading);

        assertThat(evaluation.status()).isEqualTo(ThresholdEvaluation.Status.VIOLATION);
        assertThat(evaluation.violation()).isPresent();

        ThresholdViolation violation = evaluation.violation().orElseThrow();
        assertThat(violation.pollutant()).isEqualTo(PM25);
        assertThat(violation.medium()).isEqualTo(MEDIUM);
        assertThat(violation.measuredValue()).isEqualTo(measured);
        assertThat(violation.exceededThreshold()).isEqualTo(threshold);
        // Evidence_Chain is non-empty and links to the reading's primary source (Req 3.3).
        assertThat(violation.evidence().links()).isNotEmpty();
        assertThat(violation.evidence().links())
                .anyMatch(link -> link.type() == SourceType.READING
                        && link.ref().equals(source));
    }

    /**
     * For any measured value at or below the threshold, the evaluation is WITHIN_LIMIT (no
     * violation) — the strict-exceedance boundary (Req 3.1).
     */
    @Property(tries = 100)
    void valueAtOrBelowThresholdIsWithinLimit(
            @ForAll("thresholds") double threshold,
            @ForAll @DoubleRange(min = 0.0, max = 1_000_000.0) double belowOrEqualOffset) {

        double measured = Math.max(0.0, threshold - belowOrEqualOffset);

        ThresholdRegistry registry =
                ThresholdRegistry.builder().with(PM25, MEDIUM, threshold).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);

        Reading reading = new Reading(
                new StreamId("stack-1"), PM25, MEDIUM, measured, new SourceRef("reading-1"));

        ThresholdEvaluation evaluation = monitor.evaluateThreshold(reading);

        assertThat(evaluation.status()).isEqualTo(ThresholdEvaluation.Status.WITHIN_LIMIT);
        assertThat(evaluation.violation()).isEmpty();
    }

    /** Finite, non-negative regulatory thresholds. */
    @Provide
    Arbitrary<Double> thresholds() {
        return Arbitraries.doubles().between(0.0, 1_000_000.0).ofScale(4);
    }

    /** A strictly-positive overshoot so {@code threshold + delta > threshold}. */
    @Provide
    Arbitrary<Double> positiveDelta() {
        return Arbitraries.doubles().between(0.0001, 1_000_000.0).ofScale(4);
    }
}
