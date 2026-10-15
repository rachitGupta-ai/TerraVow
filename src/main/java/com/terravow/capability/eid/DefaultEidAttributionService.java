package com.terravow.capability.eid;

import com.terravow.domain.BaselineKind;
import com.terravow.domain.CommissioningBaseline;
import com.terravow.domain.Confidence;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic implementation of {@link EidAttributionService} (Req 1.1–1.7).
 *
 * <p>Both operations are pure functions of their inputs — no wall-clock time, randomness, or
 * iteration-order dependence — so identical inputs always yield an identical result.
 *
 * <h2>Emissions intensity (Req 1.1, 1.2)</h2>
 *
 * <p>Intensity is {@code emissions / output}. A missing emissions or output value, a non-finite or
 * negative value, or a zero output (which would make the ratio undefined) yields an
 * {@link IntensityStatus#UNAVAILABLE} result with a specific recorded reason (Req 1.2) rather than
 * an exception.
 *
 * <h2>EID (Req 1.3–1.6)</h2>
 *
 * <p>EID is {@code currentIntensity − baselineIntensity}. Baseline selection is ordered: a
 * {@link CommissioningBaseline} is used when present at full baseline confidence (Req 1.3); when it
 * is absent a {@link FleetCohortBaseline} is used at a confidence strictly lower than the
 * commissioning path (Req 1.5); when both are absent — or the current intensity is itself
 * unavailable — the EID is {@link EidStatus#UNDETERMINED} with a recorded reason (Req 1.6).
 *
 * <h2>Additive attribution (Req 1.4)</h2>
 *
 * <p>A computed EID reports two additive attributed quantities whose sum equals the EID: the
 * emissions attributable to the machine health state, and the emissions attributable to unavoidable
 * process necessity. By the EID definition the delta against the commissioning baseline <em>is</em>
 * the maintenance-decision excess (process necessity is already captured in the baseline), so the
 * whole EID is attributed to the health state and the process-necessity component is zero. This
 * keeps the attribution deterministic and makes {@code health + process == eid} hold exactly.
 *
 * <h2>Confidence and Evidence_Chain (Req 1.7)</h2>
 *
 * <p>Every result carries a {@link Confidence} in {@code [0,1]} and an {@link EvidenceChain}. The
 * commissioning path carries {@link #COMMISSIONING_CONFIDENCE}; the fleet-cohort fallback carries
 * {@link #FLEET_COHORT_CONFIDENCE}, which is strictly lower (Req 1.5), and adds a
 * {@link SourceType#FALLBACK_PATH} link recording the substitution.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.1–1.7</a>
 */
public final class DefaultEidAttributionService implements EidAttributionService {

    /** Confidence assigned to an EID computed against a Commissioning_Baseline (Req 1.3). */
    static final Confidence COMMISSIONING_CONFIDENCE = new Confidence(0.9);

    /**
     * Confidence assigned to an EID computed against a Fleet_Cohort_Baseline fallback. Strictly
     * lower than {@link #COMMISSIONING_CONFIDENCE} to satisfy Req 1.5.
     */
    static final Confidence FLEET_COHORT_CONFIDENCE = new Confidence(0.6);

    /** Confidence assigned to an available emissions-intensity measurement (Req 1.1, 1.7). */
    static final Confidence INTENSITY_CONFIDENCE = new Confidence(0.9);

    /** Confidence carried by an undetermined/unavailable result (Req 1.2, 1.6, 1.7). */
    static final Confidence NO_VALUE_CONFIDENCE = Confidence.NONE;

    /** Fallback-path marker recorded when the Fleet_Cohort_Baseline is used (Req 1.5, 12.5). */
    static final String FLEET_COHORT_FALLBACK_PATH = "eid:fleet-cohort-baseline";

    /** {@inheritDoc} */
    @Override
    public EmissionsIntensityResult intensity(
            MachineId machine, TelemetryWindow window, ProductionOutput output) {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(output, "output must not be null");

        EvidenceChain evidence = operatingLogEvidence(machine);

        // Req 1.2 — emissions must be present and valid.
        Optional<Double> emissions = window.emissions();
        if (emissions.isEmpty()) {
            return EmissionsIntensityResult.unavailable(
                    machine, "emissions telemetry absent", NO_VALUE_CONFIDENCE, evidence);
        }
        double e = emissions.get();
        if (Double.isNaN(e) || Double.isInfinite(e)) {
            return EmissionsIntensityResult.unavailable(
                    machine, "emissions telemetry invalid (non-finite)", NO_VALUE_CONFIDENCE,
                    evidence);
        }
        if (e < 0.0) {
            return EmissionsIntensityResult.unavailable(
                    machine, "emissions telemetry invalid (negative)", NO_VALUE_CONFIDENCE,
                    evidence);
        }

        // Req 1.2 — output must be present, valid, and non-zero (ratio otherwise undefined).
        Optional<Double> quantity = output.quantity();
        if (quantity.isEmpty()) {
            return EmissionsIntensityResult.unavailable(
                    machine, "production output absent", NO_VALUE_CONFIDENCE, evidence);
        }
        double o = quantity.get();
        if (Double.isNaN(o) || Double.isInfinite(o)) {
            return EmissionsIntensityResult.unavailable(
                    machine, "production output invalid (non-finite)", NO_VALUE_CONFIDENCE,
                    evidence);
        }
        if (o < 0.0) {
            return EmissionsIntensityResult.unavailable(
                    machine, "production output invalid (negative)", NO_VALUE_CONFIDENCE, evidence);
        }
        if (o == 0.0) {
            return EmissionsIntensityResult.unavailable(
                    machine, "production output is zero (intensity undefined)", NO_VALUE_CONFIDENCE,
                    evidence);
        }

        // Req 1.1 — emissions per unit of output.
        double intensity = e / o;
        return EmissionsIntensityResult.available(
                machine, intensity, INTENSITY_CONFIDENCE, evidence);
    }

    /** {@inheritDoc} */
    @Override
    public EidResult eid(EidRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        MachineId machine = request.machine();
        EmissionsIntensityResult current = request.currentIntensity();

        // Current intensity must be available to compute a delta against a baseline (Req 1.3).
        if (current.status() == IntensityStatus.UNAVAILABLE || current.intensity().isEmpty()) {
            return EidResult.undetermined(
                    machine,
                    "current emissions intensity unavailable: "
                            + current.reason().orElse("unknown"),
                    NO_VALUE_CONFIDENCE,
                    operatingLogEvidence(machine));
        }
        double currentIntensity = current.intensity().get();

        // Req 1.3 — Commissioning_Baseline is preferred.
        Optional<CommissioningBaseline> commissioning = request.commissioningBaseline();
        if (commissioning.isPresent()) {
            double baseline = commissioning.get().baselineIntensity();
            return computeEid(
                    machine,
                    currentIntensity,
                    baseline,
                    BaselineKind.COMMISSIONING,
                    COMMISSIONING_CONFIDENCE,
                    operatingLogEvidence(machine));
        }

        // Req 1.5 — Fleet_Cohort_Baseline fallback at strictly lower confidence.
        Optional<FleetCohortBaseline> cohort = request.fleetCohortBaseline();
        if (cohort.isPresent()) {
            double baseline = cohort.get().cohortIntensity();
            EvidenceChain evidence =
                    operatingLogEvidence(machine)
                            .with(
                                    new EvidenceLink(
                                            SourceType.FALLBACK_PATH,
                                            new SourceRef(FLEET_COHORT_FALLBACK_PATH)));
            return computeEid(
                    machine,
                    currentIntensity,
                    baseline,
                    BaselineKind.FLEET_COHORT,
                    FLEET_COHORT_CONFIDENCE,
                    evidence);
        }

        // Req 1.6 — both baselines absent.
        return EidResult.undetermined(
                machine,
                "no Commissioning_Baseline or Fleet_Cohort_Baseline available",
                NO_VALUE_CONFIDENCE,
                operatingLogEvidence(machine));
    }

    /**
     * Computes an EID against the given baseline and splits it into the two additive attributed
     * quantities (Req 1.3, 1.4). The whole delta is attributed to the machine health state and the
     * process-necessity component is zero, so the two sum exactly to the EID.
     */
    private static EidResult computeEid(
            MachineId machine,
            double currentIntensity,
            double baselineIntensity,
            BaselineKind baselineUsed,
            Confidence confidence,
            EvidenceChain evidence) {
        double eid = currentIntensity - baselineIntensity; // Req 1.3
        double healthAttributed = eid; // Req 1.4 — the delta is the maintenance-decision excess
        double processNecessityAttributed = 0.0; // already captured in the baseline
        return EidResult.computed(
                machine,
                eid,
                healthAttributed,
                processNecessityAttributed,
                baselineUsed,
                confidence,
                evidence);
    }

    /**
     * Builds the base Evidence_Chain for a machine's EID determination: a single
     * {@link SourceType#OPERATING_LOG} link to the machine's append-only log, the provenance spine
     * every EID value traces to (Req 1.7, 14.12).
     */
    private static EvidenceChain operatingLogEvidence(MachineId machine) {
        return new EvidenceChain(
                List.of(
                        new EvidenceLink(
                                SourceType.OPERATING_LOG,
                                new SourceRef("operating-log:" + machine.value()))));
    }
}
