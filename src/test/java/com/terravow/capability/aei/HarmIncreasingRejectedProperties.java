package com.terravow.capability.aei;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.FacilityLocation;

import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the rejection of mass-violating rescheduling candidates by
 * {@link DefaultAeiService#reschedule} (Req 4.6).
 *
 * <p>Validates Correctness Property 16: a candidate schedule that would change the total emission
 * mass beyond the configured tolerance (or increase cross-media load) is rejected, and the actual
 * schedule is retained unchanged (Req 4.6). This test drives the service into the rejection path
 * deterministically: when the facility's <em>total</em> per-step capacity is strictly below the
 * actual schedule's total mass, no same-mass redistribution is feasible, so the harm-minimising
 * candidate the service builds cannot preserve the total mass. The mass-preservation guard must
 * then reject it and retain the actual schedule — the recommendation is not applied, the recommended
 * schedule equals the actual, and the actual's total mass is unchanged. The result stays
 * {@link AeiStatus#COMPUTED} (the forecast inputs are present) and the AEIs are equal.
 *
 * <p><strong>Validates: Requirements 4.6</strong>
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.6</a>
 */
class HarmIncreasingRejectedProperties {

    private static final FacilityLocation FACILITY = new FacilityLocation("plant-A");

    /** Absolute epsilon mirroring the service's floating-point comparison tolerance. */
    private static final double EPSILON = 1e-6;

    /** The default forecast step count — the grid the schedule/constraints are sized to. */
    private static final int STEPS = AeiConfig.defaults().stepCount();

    private final DefaultAeiService service = new DefaultAeiService();

    // Feature: terravow, Property 16: Harm-increasing candidates are rejected
    @Property(tries = 100)
    void capacityShortfallCandidateIsRejectedAndActualRetained(
            @ForAll("shortfallScenarios") Scenario scenario) {

        EmissionSchedule actual = scenario.actual();
        // Precondition: the scenario is constructed so total capacity < total actual mass, i.e. no
        // same-mass redistribution exists, so any candidate must change the total mass.
        assertThat(scenario.constraints().totalCapacity()).isLessThan(actual.totalMass());

        RescheduleResult result = service.reschedule(
                actual, scenario.constraints(), scenario.met(), scenario.receptors());

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        // The mass-violating candidate is rejected and the actual schedule retained (Req 4.6).
        assertThat(result.recommendationApplied()).isFalse();
        assertThat(result.recommendedSchedule()).isEqualTo(actual);
        assertThat(result.recommendedSchedule().totalMass())
                .isEqualTo(actual.totalMass(), org.assertj.core.api.Assertions.within(EPSILON));
        assertThat(result.recommendedAei().orElseThrow())
                .isEqualTo(result.actualAei().orElseThrow(), org.assertj.core.api.Assertions.within(EPSILON));
    }

    /**
     * A reschedule scenario whose total per-step capacity is strictly below the actual schedule's
     * total mass, so no same-mass redistribution is feasible and the harm-minimising candidate must
     * violate mass preservation. The actual carries a strictly-positive total mass (ensured by a
     * positive floor on one step), and each per-step cap is a strict fraction of that step's mass so
     * the summed capacity cannot reach the total mass.
     */
    @Provide
    Arbitrary<Scenario> shortfallScenarios() {
        Arbitrary<List<Double>> masses =
                Arbitraries.doubles().between(1.0, 500.0).filter(Double::isFinite)
                        .list().ofSize(STEPS);
        // Fraction in [0, 0.9] applied to each step's mass as its cap, guaranteeing
        // totalCapacity <= 0.9 * totalMass < totalMass.
        Arbitrary<List<Double>> capFractions =
                Arbitraries.doubles().between(0.0, 0.9).filter(Double::isFinite)
                        .list().ofSize(STEPS);
        Arbitrary<List<Double>> capacities =
                Arbitraries.doubles().between(0.01, 1_000.0).filter(Double::isFinite)
                        .list().ofSize(STEPS);
        Arbitrary<List<Double>> weights =
                Arbitraries.doubles().between(0.0, 5_000.0).filter(Double::isFinite)
                        .list().ofMinSize(1).ofMaxSize(5);

        return Combinators.combine(masses, capFractions, capacities, weights)
                .as((m, fractions, caps, ws) -> {
                    EmissionSchedule actual = new EmissionSchedule(m);
                    List<Double> capList = new ArrayList<>(STEPS);
                    for (int i = 0; i < STEPS; i++) {
                        capList.add(m.get(i) * fractions.get(i));
                    }
                    FacilityConstraints constraints = new FacilityConstraints(capList);
                    MeteorologicalForecast met = new MeteorologicalForecast(FACILITY, caps);
                    List<Receptor> receptors = new ArrayList<>();
                    for (int i = 0; i < ws.size(); i++) {
                        receptors.add(new Receptor("r" + i, ws.get(i)));
                    }
                    ReceptorMap receptorMap = new ReceptorMap(FACILITY, receptors);
                    return new Scenario(actual, constraints, met, receptorMap);
                })
                // Guard against floating-point ties: keep only scenarios with a genuine shortfall.
                .filter(s -> s.constraints().totalCapacity() < s.actual().totalMass() - EPSILON);
    }

    /** A bundle of self-consistent reschedule inputs over the default 24-step grid. */
    record Scenario(
            EmissionSchedule actual,
            FacilityConstraints constraints,
            MeteorologicalForecast met,
            ReceptorMap receptors) {}
}
