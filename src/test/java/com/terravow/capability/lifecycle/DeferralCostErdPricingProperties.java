package com.terravow.capability.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.erd.ErdComponentRate;
import com.terravow.capability.erd.ErdComponentType;
import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.MachineId;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.gate.DefaultDecisionGate;
import com.terravow.reasoning.tool.ErdSummationTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for Correctness Property 46 of the Emissions-Aware Maintenance Lifecycle
 * (Req 15.2): the forecast pollution cost of continued deferral equals the ERD-component pricing of
 * the forecast EID trajectory summed over the horizon.
 *
 * <p>For an arbitrary computed EID-trajectory forecast and an arbitrary set of ERD-component
 * per-operating-hour rates, {@link DefaultMaintenanceLifecycle#forecastDeferralCost} prices each
 * step as {@code round(sum(available per-hour rates) × operatingHours × forecastEid)} and sums the
 * per-step costs through the verified {@link ErdSummationTool}. The test independently re-derives
 * that expected total from the generated trajectory and rates and asserts the deferral cost equals
 * it, in the single declared currency, so the assertion checks the pricing rather than restating the
 * implementation. When no ERD component is available the pricing has nothing to sum and the result is
 * undetermined with a reason (Req 15.10) — the complementary edge of the same property.
 *
 * <p>Validates: Requirements 15.2
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 15.2, 15.10</a>
 */
class DeferralCostErdPricingProperties {

    private static final Currency INR = Currency.INR;
    private final MachineId machine = new MachineId("machine-1");

    private final EvidenceLink readingLink =
            new EvidenceLink(SourceType.READING, new SourceRef("reading-1"));

    private DefaultMaintenanceLifecycle lifecycle() {
        return new DefaultMaintenanceLifecycle(
                (m, h) -> {
                    throw new UnsupportedOperationException("forecaster unused in pricing");
                },
                new VerifiedToolDeferralCostPricer(new ErdSummationTool()),
                new DefaultDecisionGate(),
                new InMemoryMaintenanceEffectivenessStore(),
                MaintenanceLifecycleConfig.defaults());
    }

    private EidTrajectoryForecast forecastOf(List<EidTrajectoryPoint> points) {
        return EidTrajectoryForecast.computed(
                machine,
                Horizon.ofHourlySteps(Math.max(1, points.size())),
                points,
                new Confidence(0.9),
                new EvidenceChain(List.of(readingLink)));
    }

    // Feature: terravow, Property 46: Forecast deferral cost prices the trajectory through ERD
    @Property(tries = 100)
    void deferralCostEqualsErdPricedTrajectorySummedOverHorizon(
            @ForAll("trajectoryPoints") List<EidTrajectoryPoint> points,
            @ForAll("componentRates") List<ErdComponentRate> rates) {
        DeferralCostResult cost = lifecycle().forecastDeferralCost(forecastOf(points), rates);

        // Independently re-derive the available per-hour rate sum (Req 15.2).
        long perHourMinorUnits = 0L;
        int availableComponents = 0;
        for (ErdComponentRate rate : rates) {
            if (rate.isAvailable()) {
                perHourMinorUnits += rate.amountPerHour().orElseThrow().minorUnits();
                availableComponents++;
            }
        }

        if (availableComponents == 0) {
            // No priceable component → nothing to price → undetermined with a recorded reason.
            assertThat(cost.isComputed()).isFalse();
            assertThat(cost.cost()).isEmpty();
            assertThat(cost.reason()).isPresent();
            return;
        }

        // Price each step the same way the verified pricer does, then sum over the horizon.
        long expectedTotal = 0L;
        for (EidTrajectoryPoint point : points) {
            double raw = (double) perHourMinorUnits * point.operatingHours() * point.forecastEid();
            expectedTotal += Math.round(raw);
        }

        assertThat(cost.isComputed()).isTrue();
        assertThat(cost.currency()).isEqualTo(INR);
        assertThat(cost.cost()).isPresent();
        assertThat(cost.cost().orElseThrow().currency()).isEqualTo(INR);
        assertThat(cost.cost().orElseThrow().minorUnits()).isEqualTo(expectedTotal);
        assertThat(cost.costMinorUnitsOrZero()).isEqualTo(expectedTotal);
    }

    /**
     * A non-empty forecast trajectory. Each step carries a bounded, non-negative forecast EID and a
     * one-operating-hour span (the default hourly step), so the step costs stay within long range
     * for the independent re-derivation.
     */
    @Provide
    Arbitrary<List<EidTrajectoryPoint>> trajectoryPoints() {
        Arbitrary<Double> eid = Arbitraries.doubles().between(0.0, 50.0);
        return eid.list()
                .ofMinSize(1)
                .ofMaxSize(24)
                .map(
                        eids -> {
                            List<EidTrajectoryPoint> points = new ArrayList<>(eids.size());
                            for (int i = 0; i < eids.size(); i++) {
                                points.add(EidTrajectoryPoint.hourly(i, eids.get(i)));
                            }
                            return points;
                        });
    }

    /**
     * An arbitrary set of ERD-component rates mixing available INR per-hour rates and unavailable
     * rates (each of the five types at most once), so the property exercises both the priced path
     * and the no-available-component edge.
     */
    @Provide
    Arbitrary<List<ErdComponentRate>> componentRates() {
        List<Arbitrary<Optional<ErdComponentRate>>> perType = new ArrayList<>();
        for (ErdComponentType type : ErdComponentType.values()) {
            Arbitrary<ErdComponentRate> available =
                    Arbitraries.longs()
                            .between(0L, 10_000L)
                            .map(
                                    units ->
                                            ErdComponentRate.available(
                                                    type, new Money(units, INR), type.name() + "-src"));
            Arbitrary<ErdComponentRate> unavailable =
                    Arbitraries.just(ErdComponentRate.unavailable(type, type.name() + " missing"));
            Arbitrary<ErdComponentRate> present = Arbitraries.oneOf(List.of(available, unavailable));
            perType.add(
                    Arbitraries.oneOf(
                            List.of(Arbitraries.just(Optional.empty()), present.map(Optional::of))));
        }
        return Combinators.combine(perType)
                .as(optionals -> optionals.stream().flatMap(Optional::stream).toList());
    }
}
