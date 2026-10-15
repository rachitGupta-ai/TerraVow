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
 * Property-based test for the AEI-monotonicity guarantee of {@link DefaultAeiService#reschedule}
 * (Req 4.4).
 *
 * <p>Validates Correctness Property 14: rescheduling recommends a revised schedule whose AEI is no
 * greater than the actual schedule's, and when no admissible improvement exists (or a candidate is
 * rejected), the actual is retained — which yields equal AEIs. Either way the invariant
 * {@code recommendedAei <= actualAei} holds (Req 4.4). For any valid actual schedule, constraints,
 * meteorological forecast, and Receptor_Map yielding a {@link AeiStatus#COMPUTED} result, the
 * recommended AEI must never exceed the actual AEI. When a strictly-better revised schedule is
 * applied, {@link RescheduleResult#recommendationApplied()} is {@code true} and the recommended
 * schedule differs from the actual; otherwise the actual schedule is retained unchanged.
 *
 * <p><strong>Validates: Requirements 4.4</strong>
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.4</a>
 */
class RescheduleNeverWorsensAeiProperties {

    private static final FacilityLocation FACILITY = new FacilityLocation("plant-A");

    /** Absolute epsilon mirroring the service's floating-point comparison tolerance. */
    private static final double EPSILON = 1e-6;

    /** The default forecast step count — the grid the schedule/constraints are sized to. */
    private static final int STEPS = AeiConfig.defaults().stepCount();

    private final DefaultAeiService service = new DefaultAeiService();

    // Feature: terravow, Property 14: Rescheduling never worsens AEI
    @Property(tries = 100)
    void recommendedAeiNeverExceedsActualAei(@ForAll("scenarios") Scenario scenario) {
        RescheduleResult result = service.reschedule(
                scenario.actual(), scenario.constraints(), scenario.met(), scenario.receptors());

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        double actualAei = result.actualAei().orElseThrow();
        double recommendedAei = result.recommendedAei().orElseThrow();
        assertThat(recommendedAei).isLessThanOrEqualTo(actualAei + EPSILON);

        if (result.recommendationApplied()) {
            assertThat(result.recommendedSchedule()).isNotEqualTo(result.actualSchedule());
        } else {
            assertThat(result.recommendedSchedule()).isEqualTo(result.actualSchedule());
        }
    }

    /**
     * A self-consistent reschedule scenario over the default 24-step grid: a 24-step actual
     * schedule, per-step caps that always cover the actual's mass with extra headroom (so mass can
     * be freely redistributed), an arbitrary meteorological forecast, and a Receptor_Map.
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
