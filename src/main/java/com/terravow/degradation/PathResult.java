package com.terravow.degradation;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of running a managed path through the {@link DegradationPolicy} (Req 12.3–12.6).
 *
 * <p>A result is in exactly one of three states:
 *
 * <ul>
 *   <li><b>Primary success</b> — the primary path produced a value; {@code value} is present,
 *       {@code usedFallback} is {@code false}, {@code confidence} is the primary-path confidence,
 *       {@code routedToHumanReview} is {@code false}.</li>
 *   <li><b>Fallback success</b> (Req 12.3–12.5) — the primary was unavailable and the predefined
 *       safe fallback produced a value; {@code value} is present, {@code usedFallback} is
 *       {@code true}, {@code confidence} is <em>strictly lower</em> than the primary-path
 *       confidence, and {@code evidence} carries a {@code FALLBACK_PATH} link recording both the
 *       fallback id and the replaced primary id.</li>
 *   <li><b>Undetermined</b> (Req 12.6) — no safe fallback was available; {@code value} is empty,
 *       {@code confidence} is {@link Confidence#NONE}, {@code routedToHumanReview} is {@code true},
 *       and {@code unavailablePath} records the path that could not be served.</li>
 * </ul>
 *
 * <p>The three states are mutually exclusive and are validated structurally in the compact
 * constructor so an inconsistent result (for example, undetermined yet carrying a value) is
 * unrepresentable.
 *
 * @param value               the produced value, present on success and empty when undetermined
 * @param usedFallback        {@code true} iff a fallback produced the value (Req 12.3)
 * @param determined          {@code true} iff a value was produced; {@code false} when undetermined
 *                            (Req 12.6)
 * @param confidence          the confidence of the result; strictly lower than primary when a
 *                            fallback was used (Req 12.4), {@link Confidence#NONE} when undetermined
 * @param routedToHumanReview {@code true} iff the result is routed to human review (Req 12.6)
 * @param unavailablePath     the unavailable path recorded when undetermined (Req 12.6); empty
 *                            otherwise
 * @param evidence            the Evidence_Chain, carrying the substituted-path link when a fallback
 *                            was used (Req 12.5); never {@code null}
 * @param <T>                 the type of the produced value
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.3, 12.4, 12.5,
 *      12.6</a>
 */
public record PathResult<T>(
        Optional<T> value,
        boolean usedFallback,
        boolean determined,
        Confidence confidence,
        boolean routedToHumanReview,
        Optional<ManagedPath> unavailablePath,
        EvidenceChain evidence) {

    /**
     * Validates the three mutually-exclusive states (Req 12.3–12.6).
     *
     * @throws NullPointerException     if {@code value}, {@code confidence}, {@code unavailablePath}
     *                                  or {@code evidence} is {@code null}
     * @throws IllegalArgumentException if the fields describe an inconsistent state (e.g. a
     *                                  determined result with no value, an undetermined result that
     *                                  carries a value, or an undetermined result not routed to
     *                                  review)
     */
    public PathResult {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(unavailablePath, "unavailablePath must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");

        if (determined) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("A determined result must carry a value");
            }
            if (unavailablePath.isPresent()) {
                throw new IllegalArgumentException(
                        "A determined result must not record an unavailable path");
            }
        } else {
            if (value.isPresent()) {
                throw new IllegalArgumentException("An undetermined result must not carry a value");
            }
            if (usedFallback) {
                throw new IllegalArgumentException(
                        "An undetermined result cannot have used a fallback");
            }
            if (!routedToHumanReview) {
                throw new IllegalArgumentException(
                        "An undetermined result must be routed to human review (Req 12.6)");
            }
            if (unavailablePath.isEmpty()) {
                throw new IllegalArgumentException(
                        "An undetermined result must record the unavailable path (Req 12.6)");
            }
        }
    }

    /**
     * Builds a primary-success result at the primary-path confidence.
     *
     * @param value      the produced value; never {@code null}
     * @param confidence the primary-path confidence; never {@code null}
     * @param evidence   the Evidence_Chain; never {@code null}
     * @param <T>        the value type
     * @return a determined, non-fallback result
     */
    public static <T> PathResult<T> primary(T value, Confidence confidence, EvidenceChain evidence) {
        return new PathResult<>(
                Optional.of(Objects.requireNonNull(value, "value must not be null")),
                false,
                true,
                confidence,
                false,
                Optional.empty(),
                evidence);
    }

    /**
     * Builds a fallback-success result at strictly-lower confidence (Req 12.3–12.5).
     *
     * @param value      the produced value; never {@code null}
     * @param confidence the strictly-lower fallback confidence; never {@code null}
     * @param evidence   the Evidence_Chain carrying the substituted-path link; never {@code null}
     * @param <T>        the value type
     * @return a determined, fallback result
     */
    public static <T> PathResult<T> fallback(
            T value, Confidence confidence, EvidenceChain evidence) {
        return new PathResult<>(
                Optional.of(Objects.requireNonNull(value, "value must not be null")),
                true,
                true,
                confidence,
                false,
                Optional.empty(),
                evidence);
    }

    /**
     * Builds an undetermined result routed to human review, recording the unavailable path
     * (Req 12.6).
     *
     * @param unavailablePath the path that could not be served; never {@code null}
     * @param evidence        the Evidence_Chain recording the unavailable path; never {@code null}
     * @param <T>             the value type
     * @return an undetermined result at {@link Confidence#NONE}
     */
    public static <T> PathResult<T> undetermined(
            ManagedPath unavailablePath, EvidenceChain evidence) {
        return new PathResult<>(
                Optional.empty(),
                false,
                false,
                Confidence.NONE,
                true,
                Optional.of(Objects.requireNonNull(unavailablePath, "unavailablePath must not be null")),
                evidence);
    }
}
