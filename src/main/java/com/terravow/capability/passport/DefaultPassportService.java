package com.terravow.capability.passport;

import com.terravow.domain.BatchId;
import com.terravow.domain.Confidence;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic implementation of {@link PassportService} (Req 7.1–7.7).
 *
 * <p>Every operation is a pure function of its inputs — no wall-clock time, randomness, or
 * iteration-order dependence — so identical inputs always yield an identical result. Graph traversal
 * uses the insertion-ordered, breadth-first {@link SupplyGraph}, so cascade and ripple reports are
 * deterministically ordered.
 *
 * <h2>Create (Req 7.1, 7.3, 7.4)</h2>
 *
 * <p>When a machine-state-conditioned actual footprint is present it is used directly at
 * {@link #ACTUALS_CONFIDENCE} (Req 7.1, 7.3). When it is absent the Fleet_Cohort_Baseline cohort
 * intensity supplies the footprint portion at {@link #FLEET_COHORT_CONFIDENCE} — strictly lower
 * (Req 7.4) — and a {@link SourceType#FALLBACK_PATH} link recording the substitution is appended to
 * the Evidence_Chain. The chain always links to the contributing machine-state readings (Req 7.1).
 *
 * <h2>Propagate (Req 7.2)</h2>
 *
 * <p>The downstream footprint is {@code sum(input.footprint * input.quantity) / sum(quantity)} — the
 * quantity-weighted mean of the consumed inputs' footprints, i.e. each input's footprint weighted by
 * the proportion of its quantity consumed. The downstream confidence is the minimum of the inputs'
 * confidences (the chain is only as trustworthy as its weakest input), and the Evidence_Chain unions
 * the inputs' chains.
 *
 * <h2>Airshed co-scheduling (Req 7.5)</h2>
 *
 * <p>The same total emission mass is redistributed across the airshed in proportion to each
 * facility's assimilative-capacity weight, so the recommended total equals the actual total exactly
 * (within the {@link CoScheduleResult} tolerance).
 *
 * <h2>Cascade-risk (Req 7.6)</h2>
 *
 * <p>Every tier transitively dependent on the degraded upstream tier appears in the report with an
 * attributed risk equal to the source severity (so no dependent tier is ever omitted and each gets a
 * non-zero attribution when the severity is non-zero).
 *
 * <h2>Substitution ripple (Req 7.7)</h2>
 *
 * <p>Every tier connected to the applied tier (in either direction, transitively) appears in the
 * report with its delivery-requirement change set to the substitution's delta.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.1–7.7</a>
 */
public final class DefaultPassportService implements PassportService {

    /** Confidence for a Passport computed from machine-state-conditioned actuals (Req 7.1, 7.3). */
    static final Confidence ACTUALS_CONFIDENCE = new Confidence(0.9);

    /**
     * Confidence for a Passport whose footprint falls back to a Fleet_Cohort_Baseline. Strictly
     * lower than {@link #ACTUALS_CONFIDENCE} to satisfy Req 7.4.
     */
    static final Confidence FLEET_COHORT_CONFIDENCE = new Confidence(0.6);

    /** Confidence attached to a downstream propagation result (Req 7.2). */
    static final Confidence PROPAGATION_CONFIDENCE = new Confidence(0.85);

    /** Confidence attached to an airshed co-scheduling recommendation (Req 7.5). */
    static final Confidence CO_SCHEDULE_CONFIDENCE = new Confidence(0.75);

    /** Confidence attached to a cascade-risk report (Req 7.6). */
    static final Confidence CASCADE_CONFIDENCE = new Confidence(0.7);

    /** Confidence attached to a substitution-ripple report (Req 7.7). */
    static final Confidence RIPPLE_CONFIDENCE = new Confidence(0.7);

    /** Fallback-path marker recorded when the Fleet_Cohort_Baseline is used (Req 7.4). */
    static final String FLEET_COHORT_FALLBACK_PATH = "passport:fleet-cohort-baseline";

    /** {@inheritDoc} */
    @Override
    public Passport create(BatchId batch, MachineStateReadings readings) {
        Objects.requireNonNull(batch, "batch must not be null");
        Objects.requireNonNull(readings, "readings must not be null");

        EvidenceChain evidence = readingEvidence(readings);

        Optional<Double> actual = readings.actualFootprint();
        if (actual.isPresent()) {
            // Req 7.1, 7.3 — footprint from machine-state-conditioned actuals, not industry averages.
            return new Passport(batch, actual.get(), ACTUALS_CONFIDENCE, evidence);
        }

        // Req 7.4 — actual absent: use the Fleet_Cohort_Baseline portion at reduced confidence and
        // record the substitution in the Evidence_Chain.
        Optional<FleetCohortBaseline> cohort = readings.fleetCohortBaseline();
        if (cohort.isPresent()) {
            double cohortFootprint = cohort.get().cohortIntensity();
            EvidenceChain withFallback =
                    evidence.with(
                            new EvidenceLink(
                                    SourceType.FALLBACK_PATH,
                                    new SourceRef(FLEET_COHORT_FALLBACK_PATH)));
            return new Passport(batch, cohortFootprint, FLEET_COHORT_CONFIDENCE, withFallback);
        }

        throw new IllegalArgumentException(
                "cannot create Passport for batch "
                        + batch.value()
                        + ": no machine-state actual and no Fleet_Cohort_Baseline to ground the "
                        + "footprint (Req 7.1, 7.4)");
    }

    /** {@inheritDoc} */
    @Override
    public Passport propagate(BatchId downstream, List<ConsumedInput> inputs) {
        Objects.requireNonNull(downstream, "downstream must not be null");
        Objects.requireNonNull(inputs, "inputs must not be null");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "downstream propagation requires at least one consumed input (Req 7.2)");
        }

        // Req 7.2 — each input's footprint weighted by the proportion of its quantity consumed.
        double totalQuantity = 0.0;
        for (ConsumedInput input : inputs) {
            totalQuantity += input.quantity();
        }

        double weightedFootprint = 0.0;
        double minConfidence = 1.0;
        List<EvidenceLink> links = new ArrayList<>();
        for (ConsumedInput input : inputs) {
            double proportion = input.quantity() / totalQuantity;
            weightedFootprint += input.passport().embeddedFootprint() * proportion;
            minConfidence = Math.min(minConfidence, input.passport().confidence().value());
            links.addAll(input.passport().evidence().links());
        }

        Confidence confidence =
                new Confidence(Math.min(PROPAGATION_CONFIDENCE.value(), minConfidence));
        return new Passport(downstream, weightedFootprint, confidence, new EvidenceChain(links));
    }

    /** {@inheritDoc} */
    @Override
    public CoScheduleResult airshedCoSchedule(List<AirshedFacility> sharedAirshed) {
        Objects.requireNonNull(sharedAirshed, "sharedAirshed must not be null");
        if (sharedAirshed.size() < 2) {
            throw new IllegalArgumentException(
                    "airshed co-scheduling applies to two or more facilities sharing an airshed "
                            + "(Req 7.5), got: " + sharedAirshed.size());
        }

        double totalMass = 0.0;
        double totalWeight = 0.0;
        for (AirshedFacility facility : sharedAirshed) {
            totalMass += facility.actualMass();
            totalWeight += facility.capacityWeight();
        }

        // Req 7.5 — redistribute the same total mass in proportion to assimilative capacity. The
        // last facility absorbs the rounding residual so the recommended total equals the actual
        // total exactly, guaranteeing mass preservation.
        List<CoScheduleEntry> entries = new ArrayList<>(sharedAirshed.size());
        double allocated = 0.0;
        for (int i = 0; i < sharedAirshed.size(); i++) {
            AirshedFacility facility = sharedAirshed.get(i);
            double recommended;
            if (i == sharedAirshed.size() - 1) {
                recommended = totalMass - allocated;
            } else {
                recommended = totalMass * (facility.capacityWeight() / totalWeight);
                allocated += recommended;
            }
            // Guard against a tiny negative residual from floating-point rounding.
            if (recommended < 0.0) {
                recommended = 0.0;
            }
            entries.add(new CoScheduleEntry(facility.facility(), facility.actualMass(), recommended));
        }

        EvidenceChain evidence = airshedEvidence(sharedAirshed);
        return new CoScheduleResult(entries, totalMass, CO_SCHEDULE_CONFIDENCE, evidence);
    }

    /** {@inheritDoc} */
    @Override
    public CascadeRisk propagateCascade(SupplyGraph graph, DegradedCondition condition) {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(condition, "condition must not be null");

        // Req 7.6 — every tier transitively dependent on the degraded upstream tier gets an entry.
        Set<TierId> dependents = graph.dependentDownstreamTiers(condition.upstream());
        List<CascadeRisk.Entry> entries = new ArrayList<>(dependents.size());
        for (TierId tier : dependents) {
            entries.add(new CascadeRisk.Entry(tier, condition.severity()));
        }

        EvidenceChain evidence =
                new EvidenceChain(
                        List.of(
                                new EvidenceLink(
                                        SourceType.OPERATING_LOG,
                                        new SourceRef(
                                                "cascade:" + condition.upstream().value()))));
        return new CascadeRisk(condition.upstream(), entries, CASCADE_CONFIDENCE, evidence);
    }

    /** {@inheritDoc} */
    @Override
    public RippleResult supplyChainRipple(
            SupplyGraph graph, TierId appliedTier, Substitution substitution) {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(appliedTier, "appliedTier must not be null");
        Objects.requireNonNull(substitution, "substitution must not be null");

        // Req 7.7 — every tier connected to the applied tier gets a delivery-requirement change.
        Set<TierId> connected = graph.connectedTiers(appliedTier);
        List<RippleResult.Entry> entries = new ArrayList<>(connected.size());
        for (TierId tier : connected) {
            entries.add(
                    new RippleResult.Entry(tier, substitution.deliveryRequirementDelta()));
        }

        EvidenceChain evidence =
                new EvidenceChain(
                        List.of(
                                new EvidenceLink(
                                        SourceType.OPERATING_LOG,
                                        new SourceRef(
                                                "ripple:" + appliedTier.value()))));
        return new RippleResult(appliedTier, entries, RIPPLE_CONFIDENCE, evidence);
    }

    /**
     * Builds the Evidence_Chain for a created Passport: one {@link SourceType#READING} link per
     * contributing machine-state reading (Req 7.1).
     */
    private static EvidenceChain readingEvidence(MachineStateReadings readings) {
        List<EvidenceLink> links = new ArrayList<>(readings.readingRefs().size());
        for (String ref : readings.readingRefs()) {
            links.add(new EvidenceLink(SourceType.READING, new SourceRef(ref)));
        }
        return new EvidenceChain(links);
    }

    /**
     * Builds the Evidence_Chain for an airshed co-scheduling recommendation: one
     * {@link SourceType#READING} link per participating facility (Req 7.5).
     */
    private static EvidenceChain airshedEvidence(List<AirshedFacility> sharedAirshed) {
        List<EvidenceLink> links = new ArrayList<>(sharedAirshed.size());
        for (AirshedFacility facility : sharedAirshed) {
            links.add(
                    new EvidenceLink(
                            SourceType.READING,
                            new SourceRef("airshed-facility:" + facility.facility().value())));
        }
        return new EvidenceChain(links);
    }
}
