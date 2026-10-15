package com.terravow.capability.pollution;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of evaluating a reading against its applicable regulatory threshold (Req 3.1–3.3).
 *
 * <p>Exactly one of three outcomes holds, captured by {@link Status}:
 *
 * <ul>
 *   <li>{@link Status#WITHIN_LIMIT} — a threshold applied and the reading did not exceed it.</li>
 *   <li>{@link Status#VIOLATION} — a threshold applied and the reading strictly exceeded it; the
 *       {@link #violation()} is present carrying the full violation context and Evidence_Chain
 *       (Req 3.3).</li>
 *   <li>{@link Status#UNEVALUATED} — no applicable threshold existed, so the reading is recorded as
 *       unevaluated-for-threshold with a {@link #reason()} (Req 3.2).</li>
 * </ul>
 *
 * @param status    the evaluation outcome; never {@code null}
 * @param violation the raised violation when {@code status == VIOLATION}; otherwise empty
 * @param reason    the recorded reason when {@code status == UNEVALUATED}; otherwise empty
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.2, 3.3</a>
 */
public record ThresholdEvaluation(
        Status status,
        Optional<ThresholdViolation> violation,
        Optional<String> reason) {

    /** The three mutually-exclusive outcomes of a threshold evaluation. */
    public enum Status {
        /** A threshold applied and the reading was within it. */
        WITHIN_LIMIT,
        /** A threshold applied and the reading exceeded it (Req 3.3). */
        VIOLATION,
        /** No applicable threshold existed; recorded with a reason (Req 3.2). */
        UNEVALUATED
    }

    /**
     * Validates that the carried fields match the status (Req 3.1–3.3).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the optionals are inconsistent with {@code status}
     */
    public ThresholdEvaluation {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(violation, "violation Optional must not be null");
        Objects.requireNonNull(reason, "reason Optional must not be null");
        if (status == Status.VIOLATION && violation.isEmpty()) {
            throw new IllegalArgumentException("VIOLATION status requires a violation");
        }
        if (status != Status.VIOLATION && violation.isPresent()) {
            throw new IllegalArgumentException(
                    "violation must be present only for VIOLATION status");
        }
        if (status == Status.UNEVALUATED && reason.isEmpty()) {
            throw new IllegalArgumentException("UNEVALUATED status requires a reason (Req 3.2)");
        }
        if (status != Status.UNEVALUATED && reason.isPresent()) {
            throw new IllegalArgumentException("reason must be present only for UNEVALUATED status");
        }
    }

    /**
     * Builds a within-limit evaluation (a threshold applied and was not exceeded).
     *
     * @return a {@link Status#WITHIN_LIMIT} evaluation
     */
    public static ThresholdEvaluation withinLimit() {
        return new ThresholdEvaluation(Status.WITHIN_LIMIT, Optional.empty(), Optional.empty());
    }

    /**
     * Builds a violation evaluation (Req 3.3).
     *
     * @param violation the raised violation; never {@code null}
     * @return a {@link Status#VIOLATION} evaluation carrying the violation
     */
    public static ThresholdEvaluation violation(ThresholdViolation violation) {
        return new ThresholdEvaluation(
                Status.VIOLATION, Optional.of(violation), Optional.empty());
    }

    /**
     * Builds an unevaluated-for-threshold evaluation recording the reason no threshold applied
     * (Req 3.2).
     *
     * @param reason a non-blank reason; never {@code null}
     * @return a {@link Status#UNEVALUATED} evaluation carrying the reason
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public static ThresholdEvaluation unevaluated(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new ThresholdEvaluation(Status.UNEVALUATED, Optional.empty(), Optional.of(reason));
    }
}
