package com.terravow.capability.eid;

import com.terravow.domain.BaselineKind;
import com.terravow.domain.Confidence;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of an EID (Emissions-Intensity Degradation) determination for a machine.
 *
 * <p>When a baseline is available, the EID is computed as the delta between the current emissions
 * intensity and the baseline intensity (Req 1.3, 1.5) and the result is
 * {@link EidStatus#COMPUTED}. In that case it also carries two additive attributed quantities — the
 * portion attributable to machine health state and the portion attributable to unavoidable process
 * necessity — whose sum equals the EID (Req 1.4). The baseline actually used is recorded in
 * {@link #baselineUsed()}: a Fleet_Cohort_Baseline fallback carries strictly lower confidence than a
 * Commissioning_Baseline (Req 1.5). When neither baseline (nor the current intensity) is available,
 * the result is {@link EidStatus#UNDETERMINED} with a recorded reason (Req 1.6). Every result
 * carries a confidence in {@code [0,1]} and an {@link EvidenceChain} (Req 1.7).
 *
 * <p>The record enforces its invariants in the compact constructor: a COMPUTED result must carry an
 * EID value with both additive components summing to it (within a small floating-point tolerance),
 * and an UNDETERMINED result must carry a non-blank reason and {@link BaselineKind#NONE}. This makes
 * an inconsistent result unrepresentable.
 *
 * @param machine                    the machine the EID was computed for; never {@code null}
 * @param status                     whether the EID is computed or undetermined; never
 *                                   {@code null}
 * @param eid                        the EID value; present iff {@code status == COMPUTED} (Req 1.3)
 * @param healthAttributed           emissions attributable to machine health state; present iff
 *                                   {@code status == COMPUTED} (Req 1.4)
 * @param processNecessityAttributed emissions attributable to unavoidable process necessity;
 *                                   present iff {@code status == COMPUTED}; sums with
 *                                   {@code healthAttributed} to {@code eid} (Req 1.4)
 * @param baselineUsed               the baseline the EID was computed against; never {@code null}
 *                                   (Req 1.5)
 * @param confidence                 the confidence attached to the result; never {@code null}
 *                                   (Req 1.7)
 * @param evidence                   the Evidence_Chain for the result; never {@code null} (Req 1.7)
 * @param reason                     the reason the EID is undetermined; present iff
 *                                   {@code status == UNDETERMINED} (Req 1.6)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.3, 1.4, 1.5, 1.6,
 *      1.7</a>
 */
public record EidResult(
        MachineId machine,
        EidStatus status,
        Optional<Double> eid,
        Optional<Double> healthAttributed,
        Optional<Double> processNecessityAttributed,
        BaselineKind baselineUsed,
        Confidence confidence,
        EvidenceChain evidence,
        Optional<String> reason) {

    /** Tolerance for the additive-attribution sum invariant (Req 1.4), to absorb float rounding. */
    private static final double SUM_TOLERANCE = 1.0e-9;

    /**
     * Validates presence of all required references and the status-dependent invariants (Req 1.3,
     * 1.4, 1.5, 1.6, 1.7).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the status is inconsistent with the value/components/
     *                                  reason, if any present component is non-finite, or if the
     *                                  two additive components do not sum to the EID
     */
    public EidResult {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(eid, "eid must not be null");
        Objects.requireNonNull(healthAttributed, "healthAttributed must not be null");
        Objects.requireNonNull(
                processNecessityAttributed, "processNecessityAttributed must not be null");
        Objects.requireNonNull(baselineUsed, "baselineUsed must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        if (status == EidStatus.COMPUTED) {
            if (eid.isEmpty()
                    || healthAttributed.isEmpty()
                    || processNecessityAttributed.isEmpty()) {
                throw new IllegalArgumentException(
                        "COMPUTED EID result must carry eid and both additive components (Req 1.4)");
            }
            requireFinite(eid.get(), "eid");
            requireFinite(healthAttributed.get(), "healthAttributed");
            requireFinite(processNecessityAttributed.get(), "processNecessityAttributed");

            double sum = healthAttributed.get() + processNecessityAttributed.get();
            if (Math.abs(sum - eid.get()) > SUM_TOLERANCE) {
                throw new IllegalArgumentException(
                        "additive attribution must sum to the EID (Req 1.4): "
                                + healthAttributed.get()
                                + " + "
                                + processNecessityAttributed.get()
                                + " != "
                                + eid.get());
            }
            if (baselineUsed == BaselineKind.NONE) {
                throw new IllegalArgumentException(
                        "COMPUTED EID result must use a COMMISSIONING or FLEET_COHORT baseline");
            }
            if (reason.isPresent()) {
                throw new IllegalArgumentException(
                        "COMPUTED EID result must not carry an undetermined reason");
            }
        } else { // UNDETERMINED
            if (eid.isPresent()
                    || healthAttributed.isPresent()
                    || processNecessityAttributed.isPresent()) {
                throw new IllegalArgumentException(
                        "UNDETERMINED EID result must not carry an eid or attribution components");
            }
            if (baselineUsed != BaselineKind.NONE) {
                throw new IllegalArgumentException(
                        "UNDETERMINED EID result must use BaselineKind.NONE");
            }
            if (reason.isEmpty() || reason.get().isBlank()) {
                throw new IllegalArgumentException(
                        "UNDETERMINED EID result must carry a non-blank reason (Req 1.6)");
            }
        }
    }

    private static void requireFinite(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(
                    "EID " + field + " must be a finite number, got: " + value);
        }
    }

    /**
     * Builds a {@link EidStatus#COMPUTED} result (Req 1.3, 1.4, 1.5, 1.7).
     *
     * @param machine                    the machine; never {@code null}
     * @param eid                        the computed EID (current − baseline intensity)
     * @param healthAttributed           the health-attributed component (Req 1.4)
     * @param processNecessityAttributed the process-necessity component; must sum with
     *                                   {@code healthAttributed} to {@code eid}
     * @param baselineUsed               the baseline used; must not be {@link BaselineKind#NONE}
     * @param confidence                 the confidence; never {@code null}
     * @param evidence                   the Evidence_Chain; never {@code null}
     * @return a computed EID result
     */
    public static EidResult computed(
            MachineId machine,
            double eid,
            double healthAttributed,
            double processNecessityAttributed,
            BaselineKind baselineUsed,
            Confidence confidence,
            EvidenceChain evidence) {
        return new EidResult(
                machine,
                EidStatus.COMPUTED,
                Optional.of(eid),
                Optional.of(healthAttributed),
                Optional.of(processNecessityAttributed),
                baselineUsed,
                confidence,
                evidence,
                Optional.empty());
    }

    /**
     * Builds an {@link EidStatus#UNDETERMINED} result with a recorded reason (Req 1.6).
     *
     * @param machine    the machine; never {@code null}
     * @param reason     the non-blank reason the EID is undetermined
     * @param confidence the confidence; never {@code null}
     * @param evidence   the Evidence_Chain; never {@code null}
     * @return an undetermined EID result using {@link BaselineKind#NONE}
     */
    public static EidResult undetermined(
            MachineId machine, String reason, Confidence confidence, EvidenceChain evidence) {
        return new EidResult(
                machine,
                EidStatus.UNDETERMINED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                BaselineKind.NONE,
                confidence,
                evidence,
                Optional.of(reason));
    }
}
