package com.terravow.capability.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.terravow.capability.erd.ErdComponentRate;
import com.terravow.capability.erd.ErdComponentType;
import com.terravow.domain.ActionId;
import com.terravow.domain.AssetClass;
import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEventId;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.RecommendationId;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.gate.DefaultDecisionGate;
import com.terravow.gate.GateDecision;
import com.terravow.reasoning.tool.ErdSummationTool;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Example-based and edge-case unit tests for {@link DefaultMaintenanceLifecycle} covering the
 * Emissions-Aware Maintenance Lifecycle acceptance criteria:
 *
 * <ul>
 *   <li>agent routing — the trajectory is forecast through the injected Reasoning_Agent seam
 *       (Req 15.1);</li>
 *   <li>deferral-cost pricing — the trajectory is priced through the ERD components via the verified
 *       tool (Req 15.2);</li>
 *   <li>AEI-weighted priority — the pollution-cost term is weighted where a Receptor_Map and met
 *       forecast exist (Req 15.4);</li>
 *   <li>harm outranks failure probability — a higher-environmental-harm action ranks above a
 *       higher-failure-probability but lower-harm action, with an Evidence_Chain (Req 15.3,
 *       15.5);</li>
 *   <li>effectiveness delta — before minus after intensity (Req 15.6);</li>
 *   <li>effectiveness-record completeness and surfacing to substitution/certification (Req 15.7,
 *       15.8);</li>
 *   <li>Decision_Gate routing of the maintenance recommendation (Req 15.9);</li>
 *   <li>insufficient trajectory data → deferral cost undetermined with a reason (Req 15.10).</li>
 * </ul>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 15.1–15.10</a>
 */
class DefaultMaintenanceLifecycleTest {

    private static final Currency INR = Currency.INR;
    private final MachineId machine = new MachineId("machine-1");
    private final AssetClass assetClass = new AssetClass("boiler-class-A");

    private final MaintenanceEffectivenessStore store =
            new InMemoryMaintenanceEffectivenessStore();

    private final DeferralCostPricer pricer =
            new VerifiedToolDeferralCostPricer(new ErdSummationTool());

    private final EvidenceLink readingLink =
            new EvidenceLink(SourceType.READING, new SourceRef("reading-1"));

    /** A deterministic forecaster stub returning a fixed, flat trajectory over the horizon. */
    private EidTrajectoryForecaster flatForecaster(double perStepEid) {
        return (m, h) -> {
            java.util.List<EidTrajectoryPoint> points = new java.util.ArrayList<>();
            for (int i = 0; i < h.stepCount(); i++) {
                points.add(EidTrajectoryPoint.hourly(i, perStepEid));
            }
            return EidTrajectoryForecast.computed(
                    m, h, points, new Confidence(0.9),
                    new EvidenceChain(List.of(readingLink)));
        };
    }

    /** A forecaster stub that reports the trajectory undetermined (insufficient data). */
    private EidTrajectoryForecaster undeterminedForecaster(String reason) {
        return (m, h) ->
                EidTrajectoryForecast.undetermined(
                        m, h, reason, Confidence.NONE, EvidenceChain.EMPTY);
    }

    private DefaultMaintenanceLifecycle lifecycle(EidTrajectoryForecaster forecaster) {
        return new DefaultMaintenanceLifecycle(
                forecaster,
                pricer,
                new DefaultDecisionGate(),
                store,
                MaintenanceLifecycleConfig.defaults());
    }

    private List<ErdComponentRate> ratesPerHour(long perHourMinorUnits) {
        return List.of(
                ErdComponentRate.available(
                        ErdComponentType.EXCESS_ENERGY, new Money(perHourMinorUnits, INR), "src"));
    }

    // ----- Req 15.1: agent routing -----

    @Test
    void forecastTrajectoryRoutesThroughTheReasoningAgentSeamOverTheHorizon() {
        boolean[] invoked = {false};
        EidTrajectoryForecaster recording =
                (m, h) -> {
                    invoked[0] = true;
                    assertThat(m).isEqualTo(machine);
                    assertThat(h.stepCount()).isEqualTo(6);
                    return EidTrajectoryForecast.computed(
                            m,
                            h,
                            List.of(EidTrajectoryPoint.hourly(0, 0.2)),
                            new Confidence(0.8),
                            new EvidenceChain(List.of(readingLink)));
                };

        Horizon horizon = Horizon.ofHourlySteps(6);
        EidTrajectoryForecast forecast = lifecycle(recording).forecastTrajectory(machine, horizon);

        // Req 15.1 — the forecast is produced by the Reasoning_Agent seam, over the configured
        // horizon.
        assertThat(invoked[0]).isTrue();
        assertThat(forecast.isComputed()).isTrue();
        assertThat(forecast.horizon()).isEqualTo(horizon);
        assertThat(forecast.machine()).isEqualTo(machine);
    }

    // ----- Req 15.2: deferral-cost pricing through the ERD components (verified tool) -----

    @Test
    void deferralCostPricesTheTrajectoryThroughErdComponents() {
        Horizon horizon = Horizon.ofHourlySteps(4); // 4 one-hour steps
        DefaultMaintenanceLifecycle lifecycle = lifecycle(flatForecaster(2.0)); // forecastEid = 2.0
        EidTrajectoryForecast forecast = lifecycle.forecastTrajectory(machine, horizon);

        DeferralCostResult cost =
                lifecycle.forecastDeferralCost(forecast, ratesPerHour(100));

        // Req 15.2 — each step costs perHour(100) × operatingHours(1) × forecastEid(2.0) = 200;
        // summed over 4 steps = 800, in the declared currency.
        assertThat(cost.isComputed()).isTrue();
        assertThat(cost.currency()).isEqualTo(INR);
        assertThat(cost.cost()).isPresent();
        assertThat(cost.cost().orElseThrow().minorUnits()).isEqualTo(800L);
        // Confidence derives from the forecast confidence.
        assertThat(cost.confidence().value()).isEqualTo(0.9);
    }

    @Test
    void deferralCostIsUndeterminedWhenNoErdComponentIsAvailable() {
        Horizon horizon = Horizon.ofHourlySteps(2);
        DefaultMaintenanceLifecycle lifecycle = lifecycle(flatForecaster(1.0));
        EidTrajectoryForecast forecast = lifecycle.forecastTrajectory(machine, horizon);

        DeferralCostResult cost =
                lifecycle.forecastDeferralCost(
                        forecast,
                        List.of(
                                ErdComponentRate.unavailable(
                                        ErdComponentType.EXCESS_ENERGY, "rate missing")));

        // No priceable component → the verified pricer is unavailable → undetermined with a reason.
        assertThat(cost.status()).isEqualTo(DeterminationStatus.UNDETERMINED);
        assertThat(cost.cost()).isEmpty();
        assertThat(cost.reason()).isPresent();
    }

    // ----- Req 15.10: insufficient trajectory data → deferral cost undetermined with reason -----

    @Test
    void insufficientTrajectoryDataYieldsUndeterminedDeferralCostWithReason() {
        DefaultMaintenanceLifecycle lifecycle =
                lifecycle(undeterminedForecaster("telemetry gap: no recent readings"));
        EidTrajectoryForecast forecast =
                lifecycle.forecastTrajectory(machine, Horizon.ofHourlySteps(3));

        assertThat(forecast.isComputed()).isFalse();

        DeferralCostResult cost =
                lifecycle.forecastDeferralCost(forecast, ratesPerHour(100));

        // Req 15.10 — undetermined with the recorded reason; no cost produced.
        assertThat(cost.status()).isEqualTo(DeterminationStatus.UNDETERMINED);
        assertThat(cost.cost()).isEmpty();
        assertThat(cost.reason()).isPresent();
        assertThat(cost.reason().orElseThrow()).contains("telemetry gap");
    }

    // ----- Req 15.4: AEI-weighted priority -----

    @Test
    void aeiWeightingRaisesThePollutionCostTermInThePriority() {
        // Two identical actions except one has AEI weighting applied; the weighted one has the
        // higher priority (Req 15.4).
        QueuedAction unweighted =
                new QueuedAction(
                        machine, new ActionId("a-unweighted"), assetClass,
                        0.2, 10.0, 50.0, AeiWeighting.none());
        QueuedAction weighted =
                new QueuedAction(
                        machine, new ActionId("a-weighted"), assetClass,
                        0.2, 10.0, 50.0, AeiWeighting.ofAei(2.0)); // multiplier = 3.0

        // Unweighted score: 0.2 + 10 + 50 = 60.2; weighted score: 0.2 + 10 + 150 = 160.2.
        assertThat(unweighted.priority().score()).isEqualTo(60.2);
        assertThat(weighted.priority().score()).isEqualTo(160.2);
        assertThat(weighted.priority().weightedPollutionCost()).isEqualTo(150.0);

        List<RankedAction> ranked = lifecycle(flatForecaster(1.0)).prioritise(List.of(unweighted, weighted));
        assertThat(ranked.get(0).action()).isEqualTo(weighted);
        assertThat(ranked.get(0).rank()).isZero();
        assertThat(ranked.get(1).action()).isEqualTo(unweighted);
    }

    // ----- Req 15.3, 15.5: environmental harm outranks higher failure probability, with evidence --

    @Test
    void higherEnvironmentalHarmOutranksHigherFailureProbabilityWithEvidenceChain() {
        // highHarm: lower failure risk but much higher pollution cost.
        QueuedAction highHarm =
                new QueuedAction(
                        machine, new ActionId("high-harm"), assetClass,
                        0.1, 5.0, 500.0, AeiWeighting.none());
        // highFailure: higher failure risk but lower pollution cost → lower overall priority.
        QueuedAction highFailure =
                new QueuedAction(
                        new MachineId("machine-2"), new ActionId("high-failure"), assetClass,
                        0.9, 5.0, 50.0, AeiWeighting.none());

        List<RankedAction> ranked =
                lifecycle(flatForecaster(1.0)).prioritise(List.of(highFailure, highHarm));

        // Req 15.5 — the higher-environmental-harm action is ranked first despite the lower failure
        // probability.
        assertThat(ranked.get(0).action()).isEqualTo(highHarm);
        assertThat(ranked.get(1).action()).isEqualTo(highFailure);
        // Req 15.5 — every ranking carries a (non-empty) Evidence_Chain.
        assertThat(ranked).allSatisfy(r -> assertThat(r.evidence().links()).isNotEmpty());
    }

    @Test
    void rankingIsStableForEqualPriorities() {
        QueuedAction a =
                new QueuedAction(machine, new ActionId("a"), assetClass, 0.5, 1.0, 1.0, AeiWeighting.none());
        QueuedAction b =
                new QueuedAction(machine, new ActionId("b"), assetClass, 0.5, 1.0, 1.0, AeiWeighting.none());

        List<RankedAction> ranked = lifecycle(flatForecaster(1.0)).prioritise(List.of(a, b));
        // Equal scores keep original queue order (deterministic tie-break).
        assertThat(ranked.get(0).action()).isEqualTo(a);
        assertThat(ranked.get(1).action()).isEqualTo(b);
    }

    // ----- Req 15.6, 15.7: effectiveness delta and record completeness -----

    @Test
    void recordEffectivenessComputesTheIntensityDeltaAndRecordsACompleteRecord() {
        IntensityObservation observation =
                new IntensityObservation(
                        new MaintenanceEventId("event-1"),
                        machine,
                        assetClass,
                        "replace-filter",
                        /* before */ 0.80,
                        /* after */ 0.50,
                        new EvidenceLink(SourceType.READING, new SourceRef("before-reading")),
                        new EvidenceLink(SourceType.READING, new SourceRef("after-reading")));

        MaintenanceEffectivenessRecord record =
                lifecycle(flatForecaster(1.0)).recordEffectiveness(observation);

        // Req 15.6 — attributable change = before − after = 0.30.
        assertThat(record.intensityDelta()).isCloseTo(0.30, org.assertj.core.data.Offset.offset(1e-9));
        // Req 15.7 — the record carries asset class, action, measured change, confidence, evidence.
        assertThat(record.assetClass()).isEqualTo(assetClass);
        assertThat(record.action()).isEqualTo("replace-filter");
        assertThat(record.confidence()).isNotNull();
        assertThat(record.evidence().links()).isNotEmpty();
        assertThat(record.event().value()).isEqualTo("event-1");
    }

    // ----- Req 15.8: surfacing effectiveness records to substitution/certification -----

    @Test
    void effectivenessRecordsAreSurfacedByAssetClass() {
        DefaultMaintenanceLifecycle lifecycle = lifecycle(flatForecaster(1.0));
        AssetClass other = new AssetClass("pump-class-B");

        lifecycle.recordEffectiveness(obs("event-1", machine, assetClass, 0.9, 0.6));
        lifecycle.recordEffectiveness(obs("event-2", machine, assetClass, 0.7, 0.5));
        lifecycle.recordEffectiveness(
                obs("event-3", new MachineId("machine-3"), other, 0.4, 0.4));

        List<MaintenanceEffectivenessRecord> forClass =
                lifecycle.effectivenessRecordsForAssetClass(assetClass);

        // Req 15.8 — exactly the records for the asset class are surfaced, in insertion order.
        assertThat(forClass).hasSize(2);
        assertThat(forClass.get(0).event().value()).isEqualTo("event-1");
        assertThat(forClass.get(1).event().value()).isEqualTo("event-2");
        assertThat(lifecycle.effectivenessRecordsForAssetClass(other)).hasSize(1);
    }

    private IntensityObservation obs(
            String eventId, MachineId m, AssetClass ac, double before, double after) {
        return new IntensityObservation(
                new MaintenanceEventId(eventId),
                m,
                ac,
                "action",
                before,
                after,
                new EvidenceLink(SourceType.READING, new SourceRef(eventId + "-before")),
                new EvidenceLink(SourceType.READING, new SourceRef(eventId + "-after")));
    }

    // ----- Req 15.9: route the recommendation through the Decision_Gate -----

    @Test
    void recommendRoutesAHighConfidenceRecommendationToAllow() {
        Horizon horizon = Horizon.ofHourlySteps(2);
        DefaultMaintenanceLifecycle lifecycle = lifecycle(flatForecaster(1.0)); // confidence 0.9
        EidTrajectoryForecast forecast = lifecycle.forecastTrajectory(machine, horizon);
        DeferralCostResult cost = lifecycle.forecastDeferralCost(forecast, ratesPerHour(100));

        QueuedAction action =
                new QueuedAction(machine, new ActionId("a"), assetClass, 0.5, 1.0, 2.0, AeiWeighting.none());

        MaintenanceRecommendation rec =
                lifecycle.recommend(
                        new RecommendationId("rec-1"), List.of(action), cost);

        // Req 15.9 — confidence 0.9 >= allow threshold (0.8) → ALLOW, not routed to review.
        assertThat(rec.verdict().decision()).isEqualTo(GateDecision.ALLOW);
        assertThat(rec.routedToHumanReview()).isFalse();
        assertThat(rec.ranking()).hasSize(1);
    }

    @Test
    void recommendRoutesABelowAllowRecommendationToHumanReview() {
        // A forecaster with low confidence (0.3) → below the allow and ask thresholds → BLOCK.
        EidTrajectoryForecaster lowConfidence =
                (m, h) ->
                        EidTrajectoryForecast.computed(
                                m,
                                h,
                                List.of(EidTrajectoryPoint.hourly(0, 1.0)),
                                new Confidence(0.3),
                                new EvidenceChain(List.of(readingLink)));
        DefaultMaintenanceLifecycle lifecycle = lifecycle(lowConfidence);
        EidTrajectoryForecast forecast =
                lifecycle.forecastTrajectory(machine, Horizon.ofHourlySteps(1));
        DeferralCostResult cost = lifecycle.forecastDeferralCost(forecast, ratesPerHour(100));

        QueuedAction action =
                new QueuedAction(machine, new ActionId("a"), assetClass, 0.5, 1.0, 2.0, AeiWeighting.none());

        MaintenanceRecommendation rec =
                lifecycle.recommend(new RecommendationId("rec-2"), List.of(action), cost);

        // Req 15.9 — a below-allow recommendation is routed to human review.
        assertThat(rec.verdict().decision()).isNotEqualTo(GateDecision.ALLOW);
        assertThat(rec.routedToHumanReview()).isTrue();
    }

    // ----- construction / argument validation -----

    @Test
    void constructorRejectsNullCollaborators() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new DefaultMaintenanceLifecycle(
                                        null,
                                        pricer,
                                        new DefaultDecisionGate(),
                                        store,
                                        MaintenanceLifecycleConfig.defaults()));
    }
}
