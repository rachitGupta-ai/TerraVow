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
 * Property-based test for the mass-preserving optimisation guarantee of
 * {@link DefaultAeiService#reschedule} (Req 4.5, 7.5).
 *
 * <p>Validates Correctness Property 15: a rescheduling recommendation preserves the total emission
 * mass within the configured tolerance (0.1 percent by default), redistributing the same airshed
 * mass across time rather than adding or removing any (Req 4.5, 7.5). For any valid actual
 * schedule, constraints, meteorological forecast, and Receptor_Map yielding a
 * {@link AeiStatus#COMPUTED} result — whether a revised schedule is applied or the actual is
 * retained — the recommended schedule's total mass must stay within the tolerance fraction of the
 * actual's total mass. When the actual total mass is zero the recommended total must equal it
 * exactly (relative deviation is undefined, so absolute preservation is required).
 *
 * <p><strong>Validates: Requirements 4.5, 7.5</strong>
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.5, 7.5</a>
 */
class MassPreservingOptimisationProperties {

    private static final FacilityLocation FACILITY = new FacilityLocation("plant-A");

    /** The default forecast step count — the grid the schedule/constraints are sized to. */
    private static final int STEPS = AeiConfig.defaults().stepCount();

    /** The default mass-preservation tolerance fraction (0.1 percent) per Req 4.5. */
    private static final double TOLERANCE = AeiConfig.DEFAULT_TOLERANCE_FRACTION;

    private final DefaultAeiService service = new DefaultAeiService();

    // Feature: terravow, Property 15: Mass-preserving optimisation
    @Property(tries = 100)
    void recommendedScheduleTotalMassIsWithinToleranceOfActual(
            @ForAll("scenarios") Scenario scenario) {

        RescheduleResult result = service.reschedule(
                scenario.actual(), scenario.constraints(), scenario.met(), scenario.receptors());

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        double actualMass = result.actualSchedule().totalMass();
        double recommendedMass = result.recommendedSchedule().totalMass();

        if (actualMass == 0.0) {
            assertThat(recommendedMass).isEqualTo(0.0, org.assertj.core.api.Assertions.within(1e-9));
        } else {
            double relativeDeviation = Math.abs(recommendedMass - actualMass) / actualMass;
            assertThat(relativeDeviation).isLessThanOrEqualTo(TOLERANCE);
        }
    }

    /**
     * A self-consistent reschedule scenario over the default 24-step grid where mass can be freely
     * redistributed: per-step caps always cover the actual's mass plus headroom, so the recommended
     * schedule (revised or retained) is feasible and must preserve the total mass.
     */
    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<List<Double>> masses =
                Arbitraries.doubles().between(0.0, 500.0).filter(Double::isFinite)
                        .list().ofSize(STEPS);
        Arbitrary<List<Double>> headrooms =
                Arbitraries.doubles().between(0.0, 500.0).filter(Double::isFinite)
                        .list().ofSize(STEPS);
        Arbitrary<List<Double>> capacities =
                Arbitraries.doubles().between(0.01, 1_000.0).filter(Double::isFinite)
                        .list().ofSize(STEPS);
        Arbitrary<List<Double>> weights =
                Arbitraries.doubles().between(0.0, 5_000.0).filter(Double::isFinite)
                        .list().ofMinSize(1).ofMaxSize(5);

        return Combinators.combine(masses, headrooms, capacities, weights)
                .as((m, h, caps, ws) -> {
                    EmissionSchedule actual = new EmissionSchedule(m);
                    List<Double> capList = new ArrayList<>(STEPS);
                    for (int i = 0; i < STEPS; i++) {
                        capList.add(m.get(i) + h.get(i));
                    }
                    FacilityConstraints constraints = new FacilityConstraints(capList);
                    MeteorologicalForecast met = new MeteorologicalForecast(FACILITY, caps);
                    List<Receptor> receptors = new ArrayList<>();
                    for (int i = 0; i < ws.size(); i++) {
                        receptors.add(new Receptor("r" + i, ws.get(i)));
                    }
                    ReceptorMap receptorMap = new ReceptorMap(FACILITY, receptors);
                    return new Scenario(actual, constraints, met, receptorMap);
                });
    }

    /** A bundle of self-consistent reschedule inputs over the default 24-step grid. */
    record Scenario(
            EmissionSchedule actual,
            FacilityConstraints constraints,
            MeteorologicalForecast met,
            ReceptorMap receptors) {}
}
