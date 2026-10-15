package com.terravow.capability.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based test for Correctness Property 47 of the Emissions-Aware Maintenance Lifecycle
 * (Req 15.4): AEI weighting is monotone in the Emissions_Aware_Priority.
 *
 * <p>For a queued maintenance action whose pollution-cost term is AEI-weighted (a Receptor_Map and
 * meteorological forecast are available), holding failure risk, downtime cost, and the base forecast
 * pollution cost fixed, increasing the AEI of the forecast emissions never decreases the
 * {@link EmissionsAwarePriority#score()}. The property generates two AEI values for the same fixed
 * base terms and asserts the priority is non-decreasing in the AEI; it also checks the degenerate
 * step where the AEI is unchanged leaves the priority exactly equal, and that an applied weighting
 * never falls below the unweighted ({@link AeiWeighting#none()}) priority.
 *
 * <p>Validates: Requirements 15.4
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 15.4</a>
 */
class AeiWeightedPriorityMonotonicityProperties {

    private static EmissionsAwarePriority priority(
            double failureRisk, double downtimeCost, double pollutionCost, AeiWeighting weighting) {
        return new EmissionsAwarePriority(failureRisk, downtimeCost, pollutionCost, weighting);
    }

    // Feature: terravow, Property 47: AEI weighting is monotone in the priority
    @Property(tries = 100)
    void increasingAeiNeverDecreasesThePriority(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double failureRisk,
            @ForAll @DoubleRange(min = 0.0, max = 10_000.0) double downtimeCost,
            @ForAll @DoubleRange(min = 0.0, max = 100_000.0) double pollutionCost,
            @ForAll("aeiPair") double[] aeiPair) {
        double lowerAei = Math.min(aeiPair[0], aeiPair[1]);
        double higherAei = Math.max(aeiPair[0], aeiPair[1]);

        // Hold failure risk, downtime cost, and base pollution cost fixed; vary only the AEI.
        EmissionsAwarePriority lower =
                priority(failureRisk, downtimeCost, pollutionCost, AeiWeighting.ofAei(lowerAei));
        EmissionsAwarePriority higher =
                priority(failureRisk, downtimeCost, pollutionCost, AeiWeighting.ofAei(higherAei));

        // Req 15.4 — increasing the AEI does not decrease the Emissions_Aware_Priority.
        assertThat(higher.score()).isGreaterThanOrEqualTo(lower.score());
        // The weighted pollution-cost term is itself monotone non-decreasing in the AEI.
        assertThat(higher.weightedPollutionCost())
                .isGreaterThanOrEqualTo(lower.weightedPollutionCost());

        // An applied weighting never ranks below the same action used unweighted.
        EmissionsAwarePriority unweighted =
                priority(failureRisk, downtimeCost, pollutionCost, AeiWeighting.none());
        assertThat(lower.score()).isGreaterThanOrEqualTo(unweighted.score());
    }

    // Feature: terravow, Property 47: AEI weighting is monotone in the priority
    @Property(tries = 100)
    void equalAeiYieldsEqualPriority(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double failureRisk,
            @ForAll @DoubleRange(min = 0.0, max = 10_000.0) double downtimeCost,
            @ForAll @DoubleRange(min = 0.0, max = 100_000.0) double pollutionCost,
            @ForAll @DoubleRange(min = 0.0, max = 1_000.0) double aei) {
        // The degenerate monotone step: the same AEI gives the same priority (reflexive).
        EmissionsAwarePriority a =
                priority(failureRisk, downtimeCost, pollutionCost, AeiWeighting.ofAei(aei));
        EmissionsAwarePriority b =
                priority(failureRisk, downtimeCost, pollutionCost, AeiWeighting.ofAei(aei));
        assertThat(a.score()).isEqualTo(b.score());
    }

    /** A pair of finite, non-negative AEI values to compare; the test orders them low→high. */
    @Provide
    Arbitrary<double[]> aeiPair() {
        Arbitrary<Double> aei = Arbitraries.doubles().between(0.0, 1_000.0);
        return aei.array(double[].class).ofSize(2);
    }
}
