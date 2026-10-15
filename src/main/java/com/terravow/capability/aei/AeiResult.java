package com.terravow.capability.aei;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of an AEI (Avoidable Exposure Index) evaluation (Req 4.2, 4.3, 4.7).
 *
 * <p>When {@link AeiStatus#COMPUTED}, {@link #aeiPersonUgM3Hours()} carries the AEI value — the
 * difference between the population-exposure harm of the actual schedule and the harm of the
 * harm-minimising schedule of the same total mass over the horizon (Req 4.3), in
 * person-µg/m³-hours. That difference is non-negative because the harm-minimising schedule is, by
 * construction, no worse than the actual. When {@link AeiStatus#UNDETERMINED}, the value is absent
 * and {@link #reason()} records why (missing meteorological forecast or Receptor_Map, Req 4.2).
 *
 * <p>Every result reports the {@link #affectedReceptors()} drawn from the Receptor_Map and attaches
 * an {@link #evidence()} chain linking the result to the meteorological forecast and receptor
 * inputs (Req 4.7). A {@link #confidence()} in {@code [0, 1]} is always attached. The compact
 * constructor enforces the status/value/reason consistency so an invalid result is unrepresentable.
 *
 * @param status            the outcome status; never {@code null}
 * @param aeiPersonUgM3Hours the AEI value, present iff {@code status == COMPUTED}, {@code >= 0}
 * @param affectedReceptors the receptors drawn from the Receptor_Map; never {@code null},
 *                          defensively copied, may be empty
 * @param confidence        the confidence in {@code [0, 1]}; never {@code null}
 * @param evidence          the provenance chain; never {@code null}
 * @param reason            the undetermined reason, present iff {@code status == UNDETERMINED}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.2, 4.3, 4.7</a>
 */
public record AeiResult(
        AeiStatus status,
        Optional<Double> aeiPersonUgM3Hours,
        List<Receptor> affectedReceptors,
        Confidence confidence,
        EvidenceChain evidence,
        Optional<String> reason) {

    /**
     * Validates presence and the status/value/reason consistency invariant (Req 4.2, 4.3).
     *
     * @throws NullPointerException     if any argument is {@code null} or {@code affectedReceptors}
     *                                  contains a {@code null} element
     * @throws IllegalArgumentException if a COMPUTED result lacks a value or carries a reason or a
     *                                  negative/non-finite AEI, or an UNDETERMINED result carries a
     *                                  value or lacks a reason
     */
    public AeiResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(aeiPersonUgM3Hours, "aeiPersonUgM3Hours must not be null");
        Objects.requireNonNull(affectedReceptors, "affectedReceptors must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        affectedReceptors = List.copyOf(affectedReceptors);
        switch (status) {
            case COMPUTED -> {
                if (aeiPersonUgM3Hours.isEmpty()) {
                    throw new IllegalArgumentException("COMPUTED AEI must carry a value");
                }
                if (reason.isPresent()) {
                    throw new IllegalArgumentException("COMPUTED AEI must not carry a reason");
                }
                double aei = aeiPersonUgM3Hours.get();
                if (Double.isNaN(aei) || Double.isInfinite(aei)) {
                    throw new IllegalArgumentException("AEI value must be finite, got: " + aei);
                }
                if (aei < 0.0) {
                    throw new IllegalArgumentException(
                            "AEI value must be non-negative (Req 4.3), got: " + aei);
                }
            }
            case UNDETERMINED -> {
                if (reason.isEmpty()) {
                    throw new IllegalArgumentException("UNDETERMINED AEI must carry a reason");
                }
                if (aeiPersonUgM3Hours.isPresent()) {
                    throw new IllegalArgumentException("UNDETERMINED AEI must not carry a value");
                }
            }
            default -> throw new IllegalArgumentException("unexpected AEI status: " + status);
        }
    }

    /**
     * Builds a COMPUTED AEI result (Req 4.3, 4.7).
     *
     * @param aeiPersonUgM3Hours the non-negative AEI value
     * @param affectedReceptors  the receptors drawn from the Receptor_Map; never {@code null}
     * @param confidence         the confidence; never {@code null}
     * @param evidence           the provenance chain; never {@code null}
     * @return a COMPUTED {@code AeiResult}
     */
    public static AeiResult computed(
            double aeiPersonUgM3Hours,
            List<Receptor> affectedReceptors,
            Confidence confidence,
            EvidenceChain evidence) {
        return new AeiResult(
                AeiStatus.COMPUTED,
                Optional.of(aeiPersonUgM3Hours),
                affectedReceptors,
                confidence,
                evidence,
                Optional.empty());
    }

    /**
     * Builds an UNDETERMINED AEI result with a recorded reason (Req 4.2).
     *
     * @param reason     the non-blank reason the AEI could not be determined
     * @param confidence the confidence; never {@code null}
     * @param evidence   the provenance chain; never {@code null}
     * @return an UNDETERMINED {@code AeiResult}
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public static AeiResult undetermined(
            String reason, Confidence confidence, EvidenceChain evidence) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("UNDETERMINED reason must not be blank");
        }
        return new AeiResult(
                AeiStatus.UNDETERMINED,
                Optional.empty(),
                List.of(),
                confidence,
                evidence,
                Optional.of(reason));
    }
}
