package com.terravow.capability.lifecycle;

import com.terravow.capability.erd.ErdComponentRate;
import com.terravow.domain.AssetClass;
import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.MachineId;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.RecommendationId;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.gate.DecisionGate;
import com.terravow.gate.GateVerdict;
import com.terravow.gate.HarmDelta;
import com.terravow.gate.Recommendation;
import com.terravow.reasoning.tool.ToolResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic implementation of {@link MaintenanceLifecycle} (Req 15.1–15.10).
 *
 * <p>Collaborators are injected (constructor injection): the {@link EidTrajectoryForecaster} seam
 * over the Reasoning_Agent (Req 15.1), the {@link DeferralCostPricer} that prices the trajectory
 * through the ERD components via a verified tool (Req 15.2), the {@link DecisionGate} the
 * recommendation is routed through (Req 15.9), the {@link MaintenanceEffectivenessStore} the
 * effectiveness records accumulate in and are surfaced from (Req 15.7, 15.8), and the
 * {@link MaintenanceLifecycleConfig} holding the configurable horizon, currency, and gate thresholds.
 *
 * <p>Every method is a pure function of its inputs and the injected collaborators — no wall clock,
 * no randomness, and iteration-order-stable ranking — so the lifecycle is reproducible.
 *
 * @see MaintenanceLifecycle
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.1–15.10</a>
 */
public final class DefaultMaintenanceLifecycle implements MaintenanceLifecycle {

    private final EidTrajectoryForecaster forecaster;
    private final DeferralCostPricer pricer;
    private final DecisionGate decisionGate;
    private final MaintenanceEffectivenessStore effectivenessStore;
    private final MaintenanceLifecycleConfig config;

    /**
     * Creates a lifecycle with its injected collaborators (Req 15.1, 15.2, 15.7, 15.8, 15.9).
     *
     * @param forecaster         the Reasoning_Agent trajectory forecaster (Req 15.1); never
     *                           {@code null}
     * @param pricer             the ERD-component trajectory pricer (Req 15.2); never {@code null}
     * @param decisionGate       the Decision_Gate the recommendation routes through (Req 15.9);
     *                           never {@code null}
     * @param effectivenessStore the effectiveness knowledge base (Req 15.7, 15.8); never
     *                           {@code null}
     * @param config             the configurable horizon/currency/thresholds; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultMaintenanceLifecycle(
            EidTrajectoryForecaster forecaster,
            DeferralCostPricer pricer,
            DecisionGate decisionGate,
            MaintenanceEffectivenessStore effectivenessStore,
            MaintenanceLifecycleConfig config) {
        this.forecaster = Objects.requireNonNull(forecaster, "forecaster must not be null");
        this.pricer = Objects.requireNonNull(pricer, "pricer must not be null");
        this.decisionGate = Objects.requireNonNull(decisionGate, "decisionGate must not be null");
        this.effectivenessStore =
                Objects.requireNonNull(effectivenessStore, "effectivenessStore must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    // ----- Req 15.1: forecast EID trajectory via the Reasoning_Agent over a configured horizon -----

    /** {@inheritDoc} */
    @Override
    public EidTrajectoryForecast forecastTrajectory(MachineId machine, Horizon horizon) {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(horizon, "horizon must not be null");
        // Req 15.1 — the forecast is produced by a Reasoning_Agent, modelled by the injected seam.
        EidTrajectoryForecast forecast = forecaster.forecast(machine, horizon);
        Objects.requireNonNull(forecast, "forecaster returned a null forecast");
        return forecast;
    }

    // ----- Req 15.2 / 15.10: price the trajectory through ERD; insufficient data → undetermined ----

    /** {@inheritDoc} */
    @Override
    public DeferralCostResult forecastDeferralCost(
            EidTrajectoryForecast forecast, List<ErdComponentRate> componentRates) {
        Objects.requireNonNull(forecast, "forecast must not be null");
        Objects.requireNonNull(componentRates, "componentRates must not be null");

        MachineId machine = forecast.machine();
        Currency currency = config.currency();
        Confidence confidence = forecast.confidence();

        // Req 15.10 — insufficient trajectory data → deferral cost undetermined with the reason.
        if (!forecast.isComputed()) {
            String reason =
                    "EID trajectory insufficient: "
                            + forecast.reason().orElse("no trajectory forecast available");
            return DeferralCostResult.undetermined(
                    machine, currency, reason, confidence, forecast.evidence());
        }

        // Req 15.2 — price the forecast trajectory through the ERD components via the verified tool.
        ToolResult<Money> priced = pricer.price(currency, forecast.points(), componentRates);
        if (!priced.isComputed()) {
            // A verified-tool failure or no priceable components → undetermined with the reason.
            String reason =
                    "deferral-cost pricing unavailable: " + priced.reason().orElse("unknown");
            return DeferralCostResult.undetermined(
                    machine, currency, reason, confidence, forecast.evidence());
        }

        EvidenceChain evidence = forecast.evidence().with(deferralPricingLink(machine));
        return DeferralCostResult.computed(
                machine, priced.value().orElseThrow(), currency, confidence, evidence);
    }

    // ----- Req 15.3 / 15.4 / 15.5: Emissions_Aware_Priority ranking with Evidence_Chain -----

    /** {@inheritDoc} */
    @Override
    public List<RankedAction> prioritise(List<QueuedAction> queue) {
        Objects.requireNonNull(queue, "queue must not be null");

        // Compute each action's Emissions_Aware_Priority (Req 15.3, 15.4), preserving queue order
        // as the stable tie-breaker so the ranking is deterministic.
        List<QueuedAction> ordered = new ArrayList<>(queue.size());
        for (QueuedAction action : queue) {
            ordered.add(Objects.requireNonNull(action, "queue must not contain a null action"));
        }

        List<Indexed> indexed = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            indexed.add(new Indexed(i, ordered.get(i)));
        }

        // Req 15.5 — rank by Emissions_Aware_Priority descending, so a higher-environmental-harm
        // action outranks a lower-harm action that merely has a higher failure probability. Ties
        // keep original queue order (stable) for determinism.
        indexed.sort(
                Comparator.comparingDouble((Indexed e) -> e.action.priority().score())
                        .reversed()
                        .thenComparingInt(e -> e.originalIndex));

        List<RankedAction> ranked = new ArrayList<>(indexed.size());
        for (int rank = 0; rank < indexed.size(); rank++) {
            QueuedAction action = indexed.get(rank).action;
            // Req 15.5 — attach an Evidence_Chain to the ranking recording its basis.
            ranked.add(
                    new RankedAction(action, action.priority(), rank, rankingEvidence(action)));
        }
        return List.copyOf(ranked);
    }

    // ----- Req 15.6 / 15.7 / 15.8: effectiveness delta, record, and surfacing -----

    /** {@inheritDoc} */
    @Override
    public MaintenanceEffectivenessRecord recordEffectiveness(IntensityObservation observation) {
        Objects.requireNonNull(observation, "observation must not be null");

        // Req 15.6 — attributable change = before intensity − after intensity.
        double intensityDelta = observation.attributableChange();

        // Req 15.7 — record carries asset class, action, measured change, confidence, Evidence_Chain.
        EvidenceChain evidence =
                new EvidenceChain(List.of(observation.beforeSource(), observation.afterSource()))
                        .with(operatingLogLink(observation.machine()));
        MaintenanceEffectivenessRecord record =
                new MaintenanceEffectivenessRecord(
                        observation.event(),
                        observation.machine(),
                        observation.assetClass(),
                        observation.action(),
                        intensityDelta,
                        Confidence.FULL,
                        evidence);

        // Req 15.8 — accumulate into the knowledge base surfaced to substitution/certification.
        effectivenessStore.record(record);
        return record;
    }

    // ----- Req 15.9: route the maintenance recommendation through the Decision_Gate -----

    /** {@inheritDoc} */
    @Override
    public MaintenanceRecommendation recommend(
            RecommendationId id, List<QueuedAction> queue, DeferralCostResult deferralCost) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(queue, "queue must not be null");
        Objects.requireNonNull(deferralCost, "deferralCost must not be null");

        List<RankedAction> ranking = prioritise(queue);

        // The recommendation's Evidence_Chain combines the deferral-cost chain with a ranking link.
        EvidenceChain evidence = deferralCost.evidence().with(recommendationLink(id));

        // The recommendation confidence is the deferral-cost confidence (derived from the forecast).
        // A below-allow confidence is routed to human review by the gate (Req 15.9).
        Recommendation rec =
                Recommendation.of(id.value(), deferralCost.confidence(), evidence);

        // Ranking maintenance by harm neither increases emission mass nor relocates load, so no hard
        // veto applies; the gate classifies by confidence (Req 15.9).
        GateVerdict verdict =
                decisionGate.classify(rec, config.gateThresholds(), HarmDelta.NONE);

        return new MaintenanceRecommendation(id, ranking, deferralCost, verdict);
    }

    // ----- Req 15.8: surfacing to substitution/certification -----

    /** {@inheritDoc} */
    @Override
    public List<MaintenanceEffectivenessRecord> effectivenessRecordsForAssetClass(
            AssetClass assetClass) {
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        return effectivenessStore.recordsForAssetClass(assetClass);
    }

    // ----- Evidence_Chain helpers -----

    private static EvidenceChain rankingEvidence(QueuedAction action) {
        EvidenceChain chain =
                new EvidenceChain(List.of(operatingLogLink(action.machine())))
                        .with(
                                new EvidenceLink(
                                        SourceType.OPERATING_LOG,
                                        new SourceRef(
                                                "emissions-aware-priority:"
                                                        + action.action().value())));
        if (action.aeiWeighting().applied()) {
            chain =
                    chain.with(
                            new EvidenceLink(
                                    SourceType.READING,
                                    new SourceRef(
                                            "aei-weighting:" + action.action().value())));
        }
        return chain;
    }

    private static EvidenceLink operatingLogLink(MachineId machine) {
        return new EvidenceLink(
                SourceType.OPERATING_LOG, new SourceRef("operating-log:" + machine.value()));
    }

    private static EvidenceLink deferralPricingLink(MachineId machine) {
        return new EvidenceLink(
                SourceType.OPERATING_LOG,
                new SourceRef("deferral-cost:" + machine.value()));
    }

    private static EvidenceLink recommendationLink(RecommendationId id) {
        return new EvidenceLink(
                SourceType.OPERATING_LOG,
                new SourceRef("maintenance-recommendation:" + id.value()));
    }

    /** Internal pairing of a queued action with its original queue index for a stable sort. */
    private record Indexed(int originalIndex, QueuedAction action) {}
}
