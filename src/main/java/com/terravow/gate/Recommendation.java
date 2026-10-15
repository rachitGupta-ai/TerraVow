package com.terravow.gate;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;
import java.util.Optional;

/**
 * A recommendation submitted to the {@link DecisionGate} for classification (Req 8).
 *
 * <p>A recommendation carries an identifier, an optional {@link Confidence}, and the
 * {@link EvidenceChain} supporting it. The confidence is modelled as an {@link Optional} precisely
 * because a <em>missing</em> confidence value is a distinct, meaningful input to the gate: a
 * recommendation produced without a confidence value is classified {@link GateDecision#BLOCK} and
 * routed to human review (Req 8.5). When present, the {@link Confidence} record already enforces the
 * {@code [0.0, 1.0]} invariant structurally, so an out-of-range value is unrepresentable here;
 * callers that cannot construct a valid {@code Confidence} pass {@link Optional#empty()} to signal
 * the invalid/absent case.
 *
 * @param id         a non-blank recommendation identifier, recorded in the audit trail (Req 10.2)
 * @param confidence the recommendation's confidence, or {@link Optional#empty()} when absent or
 *                   un-constructable (invalid), driving the fail-closed BLOCK path (Req 8.5)
 * @param evidence   the Evidence_Chain supporting the recommendation; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 8.5, 10.2</a>
 */
public record Recommendation(String id, Optional<Confidence> confidence, EvidenceChain evidence) {

    /**
     * Validates that the identifier is present and non-blank, and that the confidence and evidence
     * references are non-null (Req 8.5, 10.2).
     *
     * @throws NullPointerException     if {@code id}, {@code confidence}, or {@code evidence} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public Recommendation {
        Objects.requireNonNull(id, "Recommendation id must not be null");
        Objects.requireNonNull(confidence, "Recommendation confidence Optional must not be null");
        Objects.requireNonNull(evidence, "Recommendation evidence must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Recommendation id must not be blank");
        }
    }

    /**
     * Convenience factory for a recommendation with a present confidence.
     *
     * @param id         a non-blank recommendation identifier
     * @param confidence the confidence; never {@code null}
     * @param evidence   the supporting Evidence_Chain; never {@code null}
     * @return a recommendation carrying the given confidence
     */
    public static Recommendation of(String id, Confidence confidence, EvidenceChain evidence) {
        return new Recommendation(id, Optional.of(confidence), evidence);
    }

    /**
     * Convenience factory for a recommendation whose confidence is absent/invalid, which the gate
     * classifies BLOCK and routes to human review (Req 8.5).
     *
     * @param id       a non-blank recommendation identifier
     * @param evidence the supporting Evidence_Chain; never {@code null}
     * @return a recommendation with no confidence value
     */
    public static Recommendation withoutConfidence(String id, EvidenceChain evidence) {
        return new Recommendation(id, Optional.empty(), evidence);
    }
}
