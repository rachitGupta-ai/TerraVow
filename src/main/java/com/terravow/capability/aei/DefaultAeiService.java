package com.terravow.capability.aei;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.tool.MassPreservationRequest;
import com.terravow.reasoning.tool.MassPreservationResult;
import com.terravow.reasoning.tool.MassPreservationTool;
import com.terravow.reasoning.tool.ToolResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic AEI capability and harm-aware rescheduler (Req 4).
 *
 * <p>The harm model is explicit and deterministic (same inputs → same output): the
 * population-exposure harm of emitting mass {@code m} in forecast step {@code i} is
 * {@code m × harmCoefficient[i]}, where
 * {@code harmCoefficient[i] = totalPopulationWeight / dispersionCapacity[i]}. A step whose airshed
 * disperses mass readily (high capacity) earns a low coefficient; a stagnant step over a dense
 * receptor population earns a high one. Total harm is the per-step sum, in person-µg/m³-hours
 * (Req 4.3).
 *
 * <p>The <em>harm-minimising schedule of the same total mass</em> is computed by a greedy fill
 * (Req 4.3): allocate the total mass into steps in ascending harm-coefficient order, each step
 * taking up to its production-constraint cap, until the mass is exhausted. Because harm is linear
 * in per-step mass and the caps are independent, filling the cheapest steps first is optimal, so
 * {@code AEI = harm(actual) − harm(minimising) >= 0} (Req 4.3).
 *
 * <p>Rescheduling (Req 4.4–4.6) recommends that harm-minimising schedule when it is feasible and
 * preserves total mass within tolerance — verified through the shared {@link MassPreservationTool}
 * (Req 4.5). A candidate that fails mass preservation (would change total mass beyond tolerance) or
 * increases cross-media load is rejected and the actual schedule retained (Req 4.6). Redistributing
 * the same air-emission mass across time never moves mass to another medium, so the cross-media
 * load delta of a time-shift candidate is structurally zero; the check is applied explicitly so the
 * invariant is auditable.
 *
 * <p>Confidence: the AEI/forecast is a counterfactual over forecast inputs; this service attaches a
 * fixed base confidence to a computed result. Richer confidence derivation (e.g. propagating met
 * forecast confidence or degradation fallbacks) is layered by the orchestration/degradation paths,
 * which are out of scope for this capability.
 *
 * @see AeiService
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.1–4.7</a>
 */
public final class DefaultAeiService implements AeiService {

    /** Base confidence attached to a computed AEI/reschedule result on the primary path. */
    static final Confidence BASE_CONFIDENCE = new Confidence(0.75);

    /** Confidence attached to an undetermined result (no forecast inputs). */
    static final Confidence UNDETERMINED_CONFIDENCE = new Confidence(0.1);

    /** Absolute epsilon for floating-point comparisons of harm and mass. */
    private static final double EPSILON = 1e-9;

    private final AeiConfig config;
    private final MassPreservationTool massPreservationTool;

    /**
     * Creates the service with the given configuration and mass-preservation tool.
     *
     * @param config               the forecast/tolerance configuration; never {@code null}
     * @param massPreservationTool the shared mass-preservation verified tool; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultAeiService(AeiConfig config, MassPreservationTool massPreservationTool) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.massPreservationTool =
                Objects.requireNonNull(massPreservationTool, "massPreservationTool must not be null");
    }

    /**
     * Creates the service with {@link AeiConfig#defaults() default configuration} and a fresh
     * {@link MassPreservationTool}.
     */
    public DefaultAeiService() {
        this(AeiConfig.defaults(), new MassPreservationTool());
    }

    @Override
    public Optional<AssimilativeForecast> forecast(
            MeteorologicalForecast meteorologicalForecast, ReceptorMap receptorMap) {
        if (meteorologicalForecast == null || receptorMap == null) {
            return Optional.empty();
        }
        if (meteorologicalForecast.stepCount() == 0) {
            return Optional.empty();
        }
        int steps = config.stepCount();
        double totalPopulationWeight = receptorMap.totalPopulationWeight();
        List<Double> capacities = meteorologicalForecast.dispersionCapacityPerStep();
        List<Double> harmCoefficients = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            // Map the forecast's provided capacities onto the configured step grid; if the met
            // forecast is shorter than the horizon, reuse its last available step (persistence
            // assumption) so the whole horizon is covered deterministically.
            int sourceIndex = Math.min(i, capacities.size() - 1);
            double capacity = capacities.get(sourceIndex);
            double coefficient = totalPopulationWeight / capacity;
            harmCoefficients.add(coefficient);
        }
        return Optional.of(new AssimilativeForecast(
                receptorMap.facility(), config.horizon(), config.step(), harmCoefficients));
    }

    @Override
    public AeiResult aei(
            EmissionSchedule actual,
            FacilityConstraints constraints,
            MeteorologicalForecast meteorologicalForecast,
            ReceptorMap receptorMap) {
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");

        Optional<AssimilativeForecast> maybeForecast =
                forecast(meteorologicalForecast, receptorMap);
        if (maybeForecast.isEmpty()) {
            return AeiResult.undetermined(
                    undeterminedReason(meteorologicalForecast, receptorMap),
                    UNDETERMINED_CONFIDENCE,
                    EvidenceChain.EMPTY);
        }
        AssimilativeForecast forecast = maybeForecast.get();

        double actualHarm = harmOf(actual, forecast);
        EmissionSchedule minimising = harmMinimisingSchedule(actual, constraints, forecast);
        double minimisingHarm = harmOf(minimising, forecast);
        double aei = Math.max(0.0, actualHarm - minimisingHarm);

        EvidenceChain evidence = buildEvidence(meteorologicalForecast, receptorMap);
        return AeiResult.computed(aei, receptorMap.receptors(), BASE_CONFIDENCE, evidence);
    }

    @Override
    public RescheduleResult reschedule(
            EmissionSchedule actual,
            FacilityConstraints constraints,
            MeteorologicalForecast meteorologicalForecast,
            ReceptorMap receptorMap) {
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");

        Optional<AssimilativeForecast> maybeForecast =
                forecast(meteorologicalForecast, receptorMap);
        if (maybeForecast.isEmpty()) {
            return undeterminedReschedule(
                    actual, undeterminedReason(meteorologicalForecast, receptorMap));
        }
        AssimilativeForecast forecast = maybeForecast.get();
        EvidenceChain evidence = buildEvidence(meteorologicalForecast, receptorMap);
        List<Receptor> receptors = receptorMap.receptors();

        double actualHarm = harmOf(actual, forecast);
        EmissionSchedule candidate = harmMinimisingSchedule(actual, constraints, forecast);
        double candidateHarm = harmOf(candidate, forecast);

        // The candidate IS the harm-minimising same-mass schedule, so:
        //   actualAei    = harm(actual) − harm(minimising)  >= 0
        //   candidateAei = harm(minimising) − harm(minimising) = 0
        double actualAei = Math.max(0.0, actualHarm - candidateHarm);
        double candidateAei = 0.0;

        // Req 4.6: reject a candidate that is infeasible, fails mass preservation, or increases
        // cross-media load; retain the actual schedule.
        Optional<String> rejection =
                rejectionReason(actual, candidate, constraints);
        if (rejection.isPresent()) {
            return retainActual(actual, actualAei, receptors, evidence, rejection.get());
        }

        // Req 4.4: only recommend a candidate whose AEI is no greater than the actual's. The
        // minimising schedule's AEI (candidateAei = 0) is <= actualAei by construction.
        boolean improves = candidateAei <= actualAei + EPSILON
                && !candidate.equals(actual)
                && candidateHarm < actualHarm - EPSILON;
        if (!improves) {
            // No admissible improvement (actual is already harm-minimising, or ties): retain actual.
            return new RescheduleResult(
                    AeiStatus.COMPUTED,
                    false,
                    actual,
                    actual,
                    Optional.of(actualAei),
                    Optional.of(actualAei),
                    receptors,
                    BASE_CONFIDENCE,
                    evidence,
                    Optional.empty(),
                    Optional.empty());
        }

        return new RescheduleResult(
                AeiStatus.COMPUTED,
                true,
                actual,
                candidate,
                Optional.of(actualAei),
                Optional.of(candidateAei),
                receptors,
                BASE_CONFIDENCE,
                evidence,
                Optional.empty(),
                Optional.empty());
    }

    /**
     * The population-exposure harm of a schedule under a forecast: the per-step sum of
     * {@code mass × harmCoefficient} (Req 4.3). Steps beyond the forecast horizon (if a schedule is
     * longer than the forecast) reuse the last forecast coefficient so no mass is unaccounted.
     *
     * @param schedule the emission schedule
     * @param forecast the assimilative-capacity forecast
     * @return the total harm in person-µg/m³-hours, {@code >= 0}
     */
    private double harmOf(EmissionSchedule schedule, AssimilativeForecast forecast) {
        List<Double> masses = schedule.massPerStep();
        List<Double> coefficients = forecast.harmCoefficientPerStep();
        double harm = 0.0;
        for (int i = 0; i < masses.size(); i++) {
            int coeffIndex = Math.min(i, coefficients.size() - 1);
            harm += masses.get(i) * coefficients.get(coeffIndex);
        }
        return harm;
    }

    /**
     * Computes the harm-minimising schedule of the same total mass as {@code actual}, feasible
     * under the production constraints, by a greedy ascending-coefficient fill (Req 4.3).
     *
     * <p>Steps are ordered by ascending harm coefficient (ties broken by step index for
     * determinism) and filled up to each step's cap until the total mass is exhausted. The result
     * has one entry per constrained step. When total capacity is below the total mass (the actual
     * would itself be infeasible), the fill allocates all available capacity; the resulting
     * schedule is still returned, and the reschedule path's feasibility/mass checks (Req 4.6) catch
     * the shortfall.
     *
     * @param actual      the actual schedule, whose total mass is preserved
     * @param constraints the production constraints (per-step caps)
     * @param forecast    the forecast providing per-step harm coefficients
     * @return the harm-minimising {@link EmissionSchedule}
     */
    private EmissionSchedule harmMinimisingSchedule(
            EmissionSchedule actual, FacilityConstraints constraints, AssimilativeForecast forecast) {
        int steps = constraints.stepCount();
        List<Double> coefficients = forecast.harmCoefficientPerStep();

        List<Integer> order = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparingDouble((Integer i) -> coefficients.get(Math.min(i, coefficients.size() - 1)))
                .thenComparingInt(i -> i));

        double[] allocation = new double[steps];
        double remaining = actual.totalMass();
        for (int step : order) {
            if (remaining <= EPSILON) {
                break;
            }
            double cap = constraints.maxMassPerStep().get(step);
            double take = Math.min(cap, remaining);
            allocation[step] = take;
            remaining -= take;
        }

        List<Double> massPerStep = new ArrayList<>(steps);
        for (double mass : allocation) {
            massPerStep.add(mass);
        }
        return new EmissionSchedule(massPerStep);
    }

    /**
     * Determines whether a candidate must be rejected (Req 4.6): infeasible under the constraints,
     * total mass not preserved within tolerance (verified via {@link MassPreservationTool}), or an
     * increase in cross-media load.
     *
     * @param actual      the actual schedule (retained on rejection)
     * @param candidate   the candidate schedule
     * @param constraints the production constraints
     * @return a rejection reason if the candidate must be rejected, else empty
     */
    private Optional<String> rejectionReason(
            EmissionSchedule actual, EmissionSchedule candidate, FacilityConstraints constraints) {
        if (!constraints.isFeasible(candidate)) {
            return Optional.of(
                    "candidate schedule is infeasible under the facility production constraints");
        }
        ToolResult<MassPreservationResult> massResult = massPreservationTool.execute(
                new MassPreservationRequest(
                        actual.totalMass(), candidate.totalMass(), config.toleranceFraction()));
        if (!massResult.isComputed()) {
            return Optional.of("mass-preservation check unavailable: "
                    + massResult.reason().orElse("unknown"));
        }
        if (!massResult.value().get().preserved()) {
            return Optional.of("candidate would change total emission mass beyond the "
                    + config.toleranceFraction() + " tolerance (Req 4.5/4.6)");
        }
        // A pure time-shift of air-emission mass does not move load to another medium, so the
        // cross-media load delta is zero and never positive. The guard is explicit for auditability.
        if (crossMediaLoadDelta(actual, candidate) > EPSILON) {
            return Optional.of("candidate would increase cross-media load (Req 4.6)");
        }
        return Optional.empty();
    }

    /**
     * The change in cross-media load a candidate would cause relative to the actual (Req 4.6).
     *
     * <p>Rescheduling only redistributes air-emission mass across time within the same medium, so
     * no mass is displaced to water or solid waste; the delta is structurally zero. This method is
     * the explicit seam where a future cross-media model would plug in.
     *
     * @param actual    the actual schedule
     * @param candidate the candidate schedule
     * @return the cross-media load delta; {@code 0.0} for a time-shift candidate
     */
    private double crossMediaLoadDelta(EmissionSchedule actual, EmissionSchedule candidate) {
        return 0.0;
    }

    /**
     * Builds the Evidence_Chain linking an AEI result to its meteorological forecast and receptor
     * inputs (Req 4.7).
     *
     * @param met       the meteorological forecast input
     * @param receptors the Receptor_Map input
     * @return the provenance chain
     */
    private EvidenceChain buildEvidence(MeteorologicalForecast met, ReceptorMap receptors) {
        EvidenceLink metLink = new EvidenceLink(
                SourceType.READING,
                new SourceRef("met-forecast:" + met.facility().value()));
        EvidenceLink receptorLink = new EvidenceLink(
                SourceType.READING,
                new SourceRef("receptor-map:" + receptors.facility().value()));
        return EvidenceChain.EMPTY.with(metLink).with(receptorLink);
    }

    /**
     * The reason an AEI is undetermined, naming the missing input (Req 4.2).
     *
     * @param met       the meteorological forecast (possibly {@code null} or empty)
     * @param receptors the Receptor_Map (possibly {@code null})
     * @return a non-blank reason
     */
    private String undeterminedReason(MeteorologicalForecast met, ReceptorMap receptors) {
        boolean metMissing = (met == null || met.stepCount() == 0);
        boolean receptorMissing = (receptors == null);
        if (metMissing && receptorMissing) {
            return "AEI undetermined: meteorological forecast and Receptor_Map are both unavailable";
        }
        if (metMissing) {
            return "AEI undetermined: meteorological forecast is unavailable";
        }
        return "AEI undetermined: Receptor_Map is unavailable";
    }

    /**
     * Builds a COMPUTED reschedule result that retains the actual schedule after rejecting a
     * candidate (Req 4.6).
     */
    private RescheduleResult retainActual(
            EmissionSchedule actual,
            double actualAei,
            List<Receptor> receptors,
            EvidenceChain evidence,
            String rejectionReason) {
        return new RescheduleResult(
                AeiStatus.COMPUTED,
                false,
                actual,
                actual,
                Optional.of(actualAei),
                Optional.of(actualAei),
                receptors,
                BASE_CONFIDENCE,
                evidence,
                Optional.of(rejectionReason),
                Optional.empty());
    }

    /**
     * Builds an UNDETERMINED reschedule result retaining the actual schedule (Req 4.2).
     */
    private RescheduleResult undeterminedReschedule(EmissionSchedule actual, String reason) {
        return new RescheduleResult(
                AeiStatus.UNDETERMINED,
                false,
                actual,
                actual,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                UNDETERMINED_CONFIDENCE,
                EvidenceChain.EMPTY,
                Optional.empty(),
                Optional.of(reason));
    }
}
