package com.terravow.capability.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.ActionId;
import com.terravow.domain.AssetClass;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;
import com.terravow.gate.DefaultDecisionGate;
import com.terravow.reasoning.tool.ErdSummationTool;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for Correctness Property 48 of the Emissions-Aware Maintenance Lifecycle
 * (Req 15.3, 15.5): environmental harm can outrank failure probability.
 *
 * <p>For a pair of queued maintenance actions where the first has a higher forecast environmental
 * harm (a higher weighted pollution cost) and the second has a higher failure probability but a
 * lower environmental harm, the lifecycle's {@link DefaultMaintenanceLifecycle#prioritise ranking}
 * places the higher-harm action above the higher-failure-probability action, and every ranking
 * carries a non-empty Evidence_Chain. The generator constructs the pair so the harm advantage of the
 * first strictly dominates the failure-probability advantage of the second in the additive
 * Emissions_Aware_Priority score, then feeds them to the ranker in both input orders to show the
 * outcome is independent of queue order.
 *
 * <p>Validates: Requirements 15.3, 15.5
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 15.3, 15.5</a>
 */
class HarmOutranksFailureProbabilityProperties {

    private final AssetClass assetClass = new AssetClass("boiler-class-A");

    private DefaultMaintenanceLifecycle lifecycle() {
        return new DefaultMaintenanceLifecycle(
                (m, h) -> {
                    throw new UnsupportedOperationException("forecaster unused in ranking");
                },
                new VerifiedToolDeferralCostPricer(new ErdSummationTool()),
                new DefaultDecisionGate(),
                new InMemoryMaintenanceEffectivenessStore(),
                MaintenanceLifecycleConfig.defaults());
    }

    // Feature: terravow, Property 48: Environmental harm can outrank failure probability
    @Property(tries = 100)
    void higherEnvironmentalHarmOutranksHigherFailureProbability(
            @ForAll("scenario") Scenario scenario) {
        // highHarm: lower failure risk, much higher pollution cost → higher overall priority.
        QueuedAction highHarm =
                new QueuedAction(
                        new MachineId("m-harm"),
                        new ActionId("high-harm"),
                        assetClass,
                        scenario.lowFailureRisk(),
                        scenario.downtimeCost(),
                        scenario.highPollutionCost(),
                        AeiWeighting.none());
        // highFailure: higher failure risk but lower pollution cost → lower overall priority.
        QueuedAction highFailure =
                new QueuedAction(
                        new MachineId("m-fail"),
                        new ActionId("high-failure"),
                        assetClass,
                        scenario.highFailureRisk(),
                        scenario.downtimeCost(),
                        scenario.lowPollutionCost(),
                        AeiWeighting.none());

        // Precondition from Property 48: the first has higher harm, the second higher failure prob.
        assertThat(highHarm.forecastPollutionCost())
                .isGreaterThan(highFailure.forecastPollutionCost());
        assertThat(highFailure.failureRisk()).isGreaterThan(highHarm.failureRisk());
        // And harm strictly dominates the overall priority (what the generator guarantees).
        assertThat(highHarm.priority().score()).isGreaterThan(highFailure.priority().score());

        // Req 15.5 — ranked first regardless of input order.
        assertRankedFirst(lifecycle().prioritise(List.of(highFailure, highHarm)), highHarm);
        assertRankedFirst(lifecycle().prioritise(List.of(highHarm, highFailure)), highHarm);
    }

    private static void assertRankedFirst(List<RankedAction> ranked, QueuedAction expectedTop) {
        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).action()).isEqualTo(expectedTop);
        assertThat(ranked.get(0).rank()).isZero();
        assertThat(ranked.get(1).rank()).isEqualTo(1);
        // Req 15.5 — every ranking carries a non-empty Evidence_Chain.
        assertThat(ranked).allSatisfy(r -> assertThat(r.evidence().links()).isNotEmpty());
        assertThat(ranked).allSatisfy(r -> assertThat(r.evidence()).isNotNull());
        assertThat(ranked)
                .allSatisfy(r -> assertThat(r.evidence()).isInstanceOf(EvidenceChain.class));
    }

    /**
     * A generated scenario where the first action's pollution-cost advantage strictly dominates the
     * second action's failure-probability advantage in the additive priority score. Shared downtime
     * cost cancels, so the deciding margin is {@code (highPollution − lowPollution)} against
     * {@code (highFailureRisk − lowFailureRisk)}; failure risk is a probability in {@code [0,1]}, so
     * a pollution gap {@code > 1} guarantees harm dominance.
     */
    @Provide
    Arbitrary<Scenario> scenario() {
        Arbitrary<Double> lowFailureRisk = Arbitraries.doubles().between(0.0, 0.4);
        Arbitrary<Double> highFailureRisk = Arbitraries.doubles().between(0.6, 1.0);
        Arbitrary<Double> downtimeCost = Arbitraries.doubles().between(0.0, 1_000.0);
        Arbitrary<Double> lowPollutionCost = Arbitraries.doubles().between(0.0, 100.0);
        // The high pollution cost exceeds the low by a margin strictly greater than the maximum
        // possible failure-probability advantage (1.0), guaranteeing harm dominance.
        Arbitrary<Double> pollutionGap = Arbitraries.doubles().between(2.0, 10_000.0);
        return Combinators.combine(
                        lowFailureRisk,
                        highFailureRisk,
                        downtimeCost,
                        lowPollutionCost,
                        pollutionGap)
                .as(
                        (lowRisk, highRisk, downtime, lowPollution, gap) ->
                                new Scenario(
                                        lowRisk,
                                        highRisk,
                                        downtime,
                                        lowPollution,
                                        lowPollution + gap));
    }

    /** The fixed inputs for a harm-vs-failure-probability ranking scenario. */
    record Scenario(
            double lowFailureRisk,
            double highFailureRisk,
            double downtimeCost,
            double lowPollutionCost,
            double highPollutionCost) {}
}
