package com.terravow.capability.pollution;

import com.terravow.evidence.EvidenceChain;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of evaluating a reading against its stream's expected profile (Req 3.4, 3.5).
 *
 * <p>Exactly one of three outcomes holds, captured by {@link Status}:
 *
 * <ul>
 *   <li>{@link Status#NORMAL} — the stream had sufficient history and the reading's deviation was
 *       below the configured anomaly-deviation threshold.</li>
 *   <li>{@link Status#ANOMALY} — the stream had sufficient history and the reading deviated from
 *       the expected profile by at least the configured threshold; the {@link #deviation()} and an
 *       {@link #evidence()} chain linking to the reading are present (Req 3.4).</li>
 *   <li>{@link Status#INSUFFICIENT_HISTORY} — the stream had fewer than the configured minimum
 *       readings, so anomaly evaluation is deferred with a {@link #reason()} (Req 3.5).</li>
 * </ul>
 *
 * @param status    the evaluation outcome; never {@code null}
 * @param deviation the measured deviation from the expected profile; present when a profile was
 *                  evaluated ({@link Status#NORMAL} or {@link Status#ANOMALY})
 * @param evidence  the Evidence_Chain linking to the reading when an anomaly is raised; present and
 *                  non-empty for {@link Status#ANOMALY}
 * @param reason    the recorded reason when {@code status == INSUFFICIENT_HISTORY}; otherwise empty
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.4, 3.5</a>
 */
public record AnomalyEvaluation(
        Status status,
        Optional<Double> deviation,
        Optional<EvidenceChain> evidence,
        Optional<String> reason) {

    /** The three mutually-exclusive outcomes of an anomaly evaluation. */
    public enum Status {
        /** Sufficient history; deviation below the threshold. */
        NORMAL,
        /** Sufficient history; deviation at or above the threshold (Req 3.4). */
        ANOMALY,
        /** Fewer than the configured minimum readings; deferred with a reason (Req 3.5). */
        INSUFFICIENT_HISTORY
    }

    /**
     * Validates that the carried fields match the status (Req 3.4, 3.5).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the optionals are inconsistent with {@code status}
     */
    public AnomalyEvaluation {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(deviation, "deviation Optional must not be null");
        Objects.requireNonNull(evidence, "evidence Optional must not be null");
        Objects.requireNonNull(reason, "reason Optional must not be null");
        if (status == Status.ANOMALY) {
            if (deviation.isEmpty()) {
                throw new IllegalArgumentException("ANOMALY status requires a deviation");
            }
            if (evidence.isEmpty() || evidence.get().links().isEmpty()) {
                throw new IllegalArgumentException(
                        "ANOMALY status requires a non-empty Evidence_Chain (Req 3.4)");
            }
        }
        if (status == Status.INSUFFICIENT_HISTORY) {
            if (reason.isEmpty()) {
                throw new IllegalArgumentException(
                        "INSUFFICIENT_HISTORY status requires a reason (Req 3.5)");
            }
            if (deviation.isPresent()) {
                throw new IllegalArgumentException(
                        "deviation must be absent for INSUFFICIENT_HISTORY status");
            }
        }
    }

    /**
     * Builds a normal evaluation (sufficient history, deviation below threshold).
     *
     * @param deviation the measured deviation
     * @return a {@link Status#NORMAL} evaluation
     */
    public static AnomalyEvaluation normal(double deviation) {
        return new AnomalyEvaluation(
                Status.NORMAL, Optional.of(deviation), Optional.empty(), Optional.empty());
    }

    /**
     * Builds an anomaly evaluation (Req 3.4).
     *
     * @param deviation the measured deviation (at or above the threshold)
     * @param evidence  the non-empty Evidence_Chain linking to the reading; never {@code null}
     * @return a {@link Status#ANOMALY} evaluation
     */
    public static AnomalyEvaluation anomaly(double deviation, EvidenceChain evidence) {
        return new AnomalyEvaluation(
                Status.ANOMALY, Optional.of(deviation), Optional.of(evidence), Optional.empty());
    }

    /**
     * Builds an insufficient-history evaluation deferring anomaly detection (Req 3.5).
     *
     * @param reason a non-blank reason; never {@code null}
     * @return a {@link Status#INSUFFICIENT_HISTORY} evaluation
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public static AnomalyEvaluation insufficientHistory(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new AnomalyEvaluation(
                Status.INSUFFICIENT_HISTORY, Optional.empty(), Optional.empty(),
                Optional.of(reason));
    }
}
