package com.terravow.capability.eid;

import com.terravow.domain.Confidence;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of computing a machine's emissions intensity (emissions per unit of output).
 *
 * <p>When telemetry and production output are present and valid, the intensity is computed as
 * {@code emissions / output} and the result is {@link IntensityStatus#AVAILABLE} with the value
 * populated (Req 1.1). When a required input is absent or fails validation, the result is
 * {@link IntensityStatus#UNAVAILABLE} with a recorded reason and no value (Req 1.2). Every result
 * carries a confidence in {@code [0,1]} and an {@link EvidenceChain} linking it to its inputs
 * (Req 1.7).
 *
 * <p>The record enforces its status/value invariants in the compact constructor so an inconsistent
 * result (e.g. AVAILABLE with no value, or UNAVAILABLE without a reason) is unrepresentable.
 *
 * @param machine   the machine the intensity was computed for; never {@code null}
 * @param status    whether the intensity is available or unavailable; never {@code null}
 * @param intensity the emissions intensity; present iff {@code status == AVAILABLE}
 * @param confidence the confidence attached to the result; never {@code null} (Req 1.7)
 * @param evidence  the Evidence_Chain for the result; never {@code null} (Req 1.7)
 * @param reason    the reason the intensity is unavailable; present iff {@code status ==
 *                  UNAVAILABLE}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.1, 1.2, 1.7</a>
 */
public record EmissionsIntensityResult(
        MachineId machine,
        IntensityStatus status,
        Optional<Double> intensity,
        Confidence confidence,
        EvidenceChain evidence,
        Optional<String> reason) {

    /**
     * Validates presence of all required references and the status/value invariants (Req 1.1, 1.2,
     * 1.7).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the status is inconsistent with the value/reason, or if a
     *                                  present intensity is non-finite or negative
     */
    public EmissionsIntensityResult {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(intensity, "intensity must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        if (status == IntensityStatus.AVAILABLE) {
            if (intensity.isEmpty()) {
                throw new IllegalArgumentException(
                        "AVAILABLE intensity result must carry an intensity value");
            }
            double value = intensity.get();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException(
                        "emissions intensity must be a finite number, got: " + value);
            }
            if (value < 0.0) {
                throw new IllegalArgumentException(
                        "emissions intensity must be non-negative, got: " + value);
            }
            if (reason.isPresent()) {
                throw new IllegalArgumentException(
                        "AVAILABLE intensity result must not carry an unavailability reason");
            }
        } else { // UNAVAILABLE
            if (intensity.isPresent()) {
                throw new IllegalArgumentException(
                        "UNAVAILABLE intensity result must not carry an intensity value");
            }
            if (reason.isEmpty() || reason.get().isBlank()) {
                throw new IllegalArgumentException(
                        "UNAVAILABLE intensity result must carry a non-blank reason (Req 1.2)");
            }
        }
    }

    /**
     * Builds an {@link IntensityStatus#AVAILABLE} result (Req 1.1).
     *
     * @param machine    the machine; never {@code null}
     * @param intensity  the computed emissions intensity; finite and non-negative
     * @param confidence the confidence; never {@code null}
     * @param evidence   the Evidence_Chain; never {@code null}
     * @return an available intensity result
     */
    public static EmissionsIntensityResult available(
            MachineId machine, double intensity, Confidence confidence, EvidenceChain evidence) {
        return new EmissionsIntensityResult(
                machine,
                IntensityStatus.AVAILABLE,
                Optional.of(intensity),
                confidence,
                evidence,
                Optional.empty());
    }

    /**
     * Builds an {@link IntensityStatus#UNAVAILABLE} result with a recorded reason (Req 1.2).
     *
     * @param machine    the machine; never {@code null}
     * @param reason     the non-blank reason the intensity is unavailable
     * @param confidence the confidence; never {@code null}
     * @param evidence   the Evidence_Chain; never {@code null}
     * @return an unavailable intensity result
     */
    public static EmissionsIntensityResult unavailable(
            MachineId machine, String reason, Confidence confidence, EvidenceChain evidence) {
        return new EmissionsIntensityResult(
                machine,
                IntensityStatus.UNAVAILABLE,
                Optional.empty(),
                confidence,
                evidence,
                Optional.of(reason));
    }
}
