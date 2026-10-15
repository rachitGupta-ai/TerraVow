package com.terravow.reasoning;

import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a reasoning request: a schema-valid determination that was accepted, or an
 * escalation to human review after exhausted retries (Req 11.3, 11.4, 11.5).
 *
 * <p>When a determination passes schema validation it is accepted and its typed value is present
 * with status {@link ReasoningStatus#ACCEPTED} (Req 11.3). When every attempt up to the clamped
 * retry limit fails, the value is absent, the status is
 * {@link ReasoningStatus#ESCALATED_HUMAN_REVIEW}, and a {@code failureReason} is recorded alongside
 * the {@code attemptsPerformed} count (Req 11.5). Grounding citations returned by the specialist
 * agent are surfaced in {@code citations} and reflected in {@code evidence} so the capability can
 * fold them into its Evidence_Chain (Req 10.1).
 *
 * <p>Consistency invariants enforced in the compact constructor:
 * <ul>
 *   <li>{@code ACCEPTED} implies {@code value} is present and {@code failureReason} is absent.</li>
 *   <li>{@code ESCALATED_HUMAN_REVIEW} implies {@code value} is absent and {@code failureReason}
 *       is present (Req 11.5).</li>
 *   <li>{@code attemptsPerformed} is at least 1 — a request always performs at least one
 *       attempt.</li>
 * </ul>
 *
 * @param <T>               the typed determination produced by the capability's {@link OutputSchema}
 * @param value             the accepted determination; present iff status is {@code ACCEPTED}
 * @param attemptsPerformed the number of attempts performed (&ge; 1) (Req 11.5)
 * @param status            the terminal outcome; never {@code null}
 * @param failureReason     the schema-validation failure reason; present iff status is
 *                          {@code ESCALATED_HUMAN_REVIEW} (Req 11.5)
 * @param citations         grounding citations folded into the Evidence_Chain; never {@code null},
 *                          defensively copied
 * @param evidence          the Evidence_Chain for the determination; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.3, 11.4, 11.5,
 *      10.1</a>
 */
public record ReasoningResult<T>(
        Optional<T> value,
        int attemptsPerformed,
        ReasoningStatus status,
        Optional<String> failureReason,
        List<GroundingCitation> citations,
        EvidenceChain evidence) {

    /**
     * Validates the references, attempt count, and status-consistency invariants (Req 11.3, 11.5).
     *
     * @throws NullPointerException     if {@code value}, {@code status}, {@code failureReason},
     *                                  {@code citations}, or {@code evidence} is {@code null}
     * @throws IllegalArgumentException if {@code attemptsPerformed} is less than 1, or the
     *                                  value/failureReason presence does not match {@code status}
     */
    public ReasoningResult {
        Objects.requireNonNull(value, "ReasoningResult value must not be null");
        Objects.requireNonNull(status, "ReasoningResult status must not be null");
        Objects.requireNonNull(failureReason, "ReasoningResult failureReason must not be null");
        Objects.requireNonNull(citations, "ReasoningResult citations must not be null");
        Objects.requireNonNull(evidence, "ReasoningResult evidence must not be null");
        if (attemptsPerformed < 1) {
            throw new IllegalArgumentException(
                    "attemptsPerformed must be at least 1 but was " + attemptsPerformed);
        }
        citations = List.copyOf(citations); // immutable copy; rejects null elements
        switch (status) {
            case ACCEPTED -> {
                if (value.isEmpty()) {
                    throw new IllegalArgumentException(
                            "An ACCEPTED result must carry a value");
                }
                if (failureReason.isPresent()) {
                    throw new IllegalArgumentException(
                            "An ACCEPTED result must not carry a failureReason");
                }
            }
            case ESCALATED_HUMAN_REVIEW -> {
                if (value.isPresent()) {
                    throw new IllegalArgumentException(
                            "An ESCALATED_HUMAN_REVIEW result must not carry a value");
                }
                if (failureReason.isEmpty()) {
                    throw new IllegalArgumentException(
                            "An ESCALATED_HUMAN_REVIEW result must carry a failureReason (Req 11.5)");
                }
            }
        }
    }

    /**
     * Builds an {@code ACCEPTED} result for a schema-valid determination (Req 11.3).
     *
     * @param <T>               the determination type
     * @param value             the accepted determination; never {@code null}
     * @param attemptsPerformed the number of attempts performed (&ge; 1)
     * @param citations         grounding citations to fold into the Evidence_Chain; never
     *                          {@code null}
     * @param evidence          the Evidence_Chain; never {@code null}
     * @return an accepted result
     */
    public static <T> ReasoningResult<T> accepted(
            T value,
            int attemptsPerformed,
            List<GroundingCitation> citations,
            EvidenceChain evidence) {
        Objects.requireNonNull(value, "accepted value must not be null");
        return new ReasoningResult<>(
                Optional.of(value),
                attemptsPerformed,
                ReasoningStatus.ACCEPTED,
                Optional.empty(),
                citations,
                evidence);
    }

    /**
     * Builds an {@code ESCALATED_HUMAN_REVIEW} result after exhausted retries (Req 11.5).
     *
     * @param <T>               the determination type
     * @param attemptsPerformed the number of attempts performed (&ge; 1)
     * @param failureReason     the schema-validation failure reason; never {@code null}, non-blank
     * @param evidence          the Evidence_Chain; never {@code null}
     * @return an escalated result
     */
    public static <T> ReasoningResult<T> escalated(
            int attemptsPerformed,
            String failureReason,
            EvidenceChain evidence) {
        Objects.requireNonNull(failureReason, "failureReason must not be null");
        if (failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason must not be blank");
        }
        return new ReasoningResult<>(
                Optional.empty(),
                attemptsPerformed,
                ReasoningStatus.ESCALATED_HUMAN_REVIEW,
                Optional.of(failureReason),
                List.of(),
                evidence);
    }
}
