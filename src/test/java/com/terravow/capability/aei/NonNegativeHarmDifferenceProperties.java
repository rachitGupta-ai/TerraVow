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
 * Property-based test for the non-negativity of the AEI harm difference computed by
 * {@link DefaultAeiService} (Req 4.3).
 *
 * <p>Validates Correctness Property 13: the AEI is the population-exposure harm of the actual
 * schedule minus the harm of the harm-minimising schedule of the same total mass over the horizon
 * (Req 4.3). Because the harm-minimising schedule is, by construction, no worse than the actual,
 * that difference is always non-negative. For any valid actual schedule, facility constraints,
 * meteorological forecast, and Receptor_Map, a {@link AeiStatus#COMPUTED} result must therefore
 * carry an AEI value {@code >= 0} (the {@link AeiResult} record also enforces this invariant
 * structurally, so the property additionally confirms a COMPUTED result is produced). The result
 * also reports the Receptor_Map's receptors and attaches a non-empty Evidence_Chain (Req 4.7).
 *
 * <p><strong>Validates: Requirements 4.3</strong>
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.3</a>
 */
class NonNegativeHarmDifferenceProperties {

    private static final FacilityLocation FACILITY = new FacilityLocation("plant-A");

    /** The default forecast step count — the grid the schedule/constraints are sized to. */
    private static final int STEPS = AeiConfig.defaults().stepCount();

    private final DefaultAeiService service = new DefaultAeiService();

    // Feature: terravow, Property 13: AEI is the non-negative harm difference
    @Property(tries = 100)
    void computedAeiIsAlwaysNonNegative(
            @ForAll("scenarios") Scenario scenario) {

        AeiResult result = service.aei(
                scenario.actual(), scenario.constraints(), scenario.met(), scenario.receptors());

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        double aei = result.aeiPersonUgM3Hours().orElseThrow();
        assertThat(aei).isGreaterThanOrEqualTo(0.0);
        assertThat(Double.isFinite(aei)).isTrue();
        assertThat(result.affectedReceptors()).hasSize(scenario.receptors().receptors().size());
        assertThat(result.evidence().links()).isNotEmpty();
    }

    /**
     * A self-consistent AEI scenario: a 24-step actual schedule, per-step caps that always cover
     * the actual's mass (cap = actualMass + headroom, so the actual is feasible and mass can be
     * freely redistributed), an arbitrary 24-step meteorological forecast, and a Receptor_Map.
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

    /** A bundle of self-consistent AEI inputs over the default 24-step grid. */
    record Scenario(
            EmissionSchedule actual,
            FacilityConstraints constraints,
            MeteorologicalForecast met,
            ReceptorMap receptors) {}
}
