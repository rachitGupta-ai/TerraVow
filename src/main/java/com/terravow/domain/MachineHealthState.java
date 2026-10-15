package com.terravow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A time-stamped assessment of a {@link MachineAsset}'s condition.
 *
 * <p>A Machine_Health_State is derived from telemetry and maintenance history and carries a health
 * score, the contributing factors, the onset timestamp of the current degraded-health-state period,
 * and a confidence value in {@code [0, 1]} (Req 14.9). When telemetry or maintenance history is
 * insufficient, the state is derived from a {@link FleetCohortBaseline} estimate at strictly lower
 * confidence (Req 14.10 — the derivation and fallback live in the store, task 2.2). The confidence
 * is held as a {@link Confidence}, which enforces the unit-interval invariant structurally. The
 * contributing factors are defensively copied into an unmodifiable list.
 *
 * @param machine             the assessed machine; never {@code null}
 * @param assessedAt          the time the assessment was made; never {@code null}
 * @param healthScore         the health score for the assessment
 * @param contributingFactors the factors behind the score; never {@code null}, defensively copied,
 *                            may be empty
 * @param degradedOnset       the onset of the current degraded period; never {@code null}
 * @param confidence          the confidence in {@code [0, 1]}; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.9, 14.10</a>
 */
public record MachineHealthState(
        MachineId machine,
        Instant assessedAt,
        double healthScore,
        List<Factor> contributingFactors,
        Instant degradedOnset,
        Confidence confidence) {

    /**
     * Validates that every required field is present and that the health score is finite (Req
     * 14.9), and defensively copies the contributing factors into an unmodifiable list. The
     * confidence invariant is enforced by {@link Confidence} itself.
     *
     * @throws NullPointerException     if any required field is {@code null}, or
     *                                  {@code contributingFactors} contains a {@code null} element
     * @throws IllegalArgumentException if {@code healthScore} is non-finite
     */
    public MachineHealthState {
        Objects.requireNonNull(machine, "MachineHealthState machine must not be null");
        Objects.requireNonNull(assessedAt, "MachineHealthState assessedAt must not be null");
        Objects.requireNonNull(
                contributingFactors, "MachineHealthState contributingFactors must not be null");
        Objects.requireNonNull(degradedOnset, "MachineHealthState degradedOnset must not be null");
        Objects.requireNonNull(confidence, "MachineHealthState confidence must not be null");
        if (Double.isNaN(healthScore) || Double.isInfinite(healthScore)) {
            throw new IllegalArgumentException(
                    "MachineHealthState healthScore must be a finite number, got: " + healthScore);
        }
        // List.copyOf rejects null elements and yields an immutable list.
        contributingFactors = List.copyOf(contributingFactors);
    }
}
