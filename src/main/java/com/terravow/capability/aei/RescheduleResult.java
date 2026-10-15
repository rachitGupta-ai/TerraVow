package com.terravow.capability.aei;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of a harm-aware rescheduling evaluation (Req 4.4, 4.5, 4.6, 4.7).
 *
 * <p>Rescheduling proposes a revised emission schedule whose AEI is no greater than the actual
 * schedule's (Req 4.4). The {@link #recommendedSchedule()} is the schedule the system recommends:
 * either a strictly-or-equally-better revised schedule, or — when no admissible improvement exists,
 * or a candidate was rejected for violating mass preservation or increasing cross-media load
 * (Req 4.6) — the retained actual schedule. {@link #recommendationApplied()} is {@code true} only
 * when the recommended schedule differs from the actual. {@link #rejectionReason()} records why a
 * candidate was rejected and the actual retained, when applicable.
 *
 * <p>Because a rejected or absent improvement retains the actual schedule, the invariant
 * {@code recommendedAei <= actualAei} always holds (Req 4.4): retaining the actual yields equal
 * AEIs. When the recommended schedule is the actual, {@link #recommendationApplied()} is
 * {@code false}. Every result reports {@link #affectedReceptors()} and an {@link #evidence()} chain
 * (Req 4.7).
 *
 * @param status             the AEI status of the underlying evaluation; never {@code null}
 * @param recommendationApplied whether a revised schedule (differing from the actual) is recommended
 * @param actualSchedule     the actual schedule evaluated; never {@code null}
 * @param recommendedSchedule the recommended schedule (revised or retained actual); never
 *                           {@code null}
 * @param actualAei          the actual schedule's AEI, present iff {@code status == COMPUTED}
 * @param recommendedAei     the recommended schedule's AEI, present iff {@code status == COMPUTED}
 * @param affectedReceptors  the receptors drawn from the Receptor_Map; never {@code null},
 *                           defensively copied
 * @param confidence         the confidence in {@code [0, 1]}; never {@code null}
 * @param evidence           the provenance chain; never {@code null}
 * @param rejectionReason    why a candidate was rejected (and the actual retained), if applicable
 * @param undeterminedReason the undetermined reason, present iff {@code status == UNDETERMINED}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.4, 4.5, 4.6,
 *      4.7</a>
 */
public record RescheduleResult(
        AeiStatus status,
        boolean recommendationApplied,
        EmissionSchedule actualSchedule,
        EmissionSchedule recommendedSchedule,
        Optional<Double> actualAei,
        Optional<Double> recommendedAei,
        List<Receptor> affectedReceptors,
        Confidence confidence,
        EvidenceChain evidence,
        Optional<String> rejectionReason,
        Optional<String> undeterminedReason) {

    /**
     * Validates presence, status consistency, and the {@code recommendedAei <= actualAei}
     * invariant (Req 4.4).
     *
     * @throws NullPointerException     if any argument is {@code null} or {@code affectedReceptors}
     *                                  contains a {@code null} element
     * @throws IllegalArgumentException if the status/value consistency is violated, or a COMPUTED
     *                                  recommendation worsens the AEI
     */
    public RescheduleResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(actualSchedule, "actualSchedule must not be null");
        Objects.requireNonNull(recommendedSchedule, "recommendedSchedule must not be null");
        Objects.requireNonNull(actualAei, "actualAei must not be null");
        Objects.requireNonNull(recommendedAei, "recommendedAei must not be null");
        Objects.requireNonNull(affectedReceptors, "affectedReceptors must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
        Objects.requireNonNull(undeterminedReason, "undeterminedReason must not be null");
        affectedReceptors = List.copyOf(affectedReceptors);
        switch (status) {
            case COMPUTED -> {
                if (actualAei.isEmpty() || recommendedAei.isEmpty()) {
                    throw new IllegalArgumentException("COMPUTED reschedule must carry both AEIs");
                }
                if (undeterminedReason.isPresent()) {
                    throw new IllegalArgumentException(
                            "COMPUTED reschedule must not carry an undetermined reason");
                }
                double epsilon = 1e-9;
                if (recommendedAei.get() > actualAei.get() + epsilon) {
                    throw new IllegalArgumentException(
                            "recommended AEI must not exceed actual AEI (Req 4.4): recommended="
                                    + recommendedAei.get() + ", actual=" + actualAei.get());
                }
                if (!recommendationApplied && !recommendedSchedule.equals(actualSchedule)) {
                    throw new IllegalArgumentException(
                            "recommendationApplied is false but recommended schedule differs from "
                                    + "actual");
                }
            }
            case UNDETERMINED -> {
                if (undeterminedReason.isEmpty()) {
                    throw new IllegalArgumentException(
                            "UNDETERMINED reschedule must carry an undetermined reason");
                }
                if (actualAei.isPresent() || recommendedAei.isPresent()) {
                    throw new IllegalArgumentException(
                            "UNDETERMINED reschedule must not carry AEI values");
                }
                if (recommendationApplied) {
                    throw new IllegalArgumentException(
                            "UNDETERMINED reschedule cannot apply a recommendation");
                }
            }
            default -> throw new IllegalArgumentException("unexpected AEI status: " + status);
        }
    }
}
