package com.terravow.evidence;

import com.terravow.gate.GateDecision;
import java.time.Instant;
import java.util.Objects;

/**
 * An immutable audit-trail record of a Decision_Gate classification.
 *
 * <p>When the Decision_Gate classifies a recommendation, the system records the recommendation
 * identifier, the ALLOW / ASK / BLOCK classification, the confidence value, and the supporting
 * {@link EvidenceChain} (Req 10.2). The {@code at} timestamp marks when the decision was recorded.
 * Audit entries are persisted synchronously, before any decision-oriented output is returned
 * (Req 10.3), and retained for the configured retention period (Req 10.4).
 *
 * <p>The {@code confidence} is stored as a primitive {@code double} to match the design's audit
 * record shape; it is validated to the closed unit interval {@code [0,1]} so a persisted entry can
 * never carry an out-of-range confidence.
 *
 * @param id         the identifier of the classified recommendation; never {@code null}
 * @param decision   the gate classification; never {@code null}
 * @param confidence the confidence value, {@code 0.0 <= confidence <= 1.0}
 * @param evidence   the Evidence_Chain supporting the recommendation; never {@code null}
 * @param at         the instant the decision was recorded; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.2, 10.3, 10.4</a>
 */
public record AuditEntry(
        RecommendationId id,
        GateDecision decision,
        double confidence,
        EvidenceChain evidence,
        Instant at) {

    /**
     * Validates required fields and the confidence bound (Req 10.2).
     *
     * @throws NullPointerException     if any reference field is {@code null}
     * @throws IllegalArgumentException if {@code confidence} is non-finite or outside {@code [0,1]}
     */
    public AuditEntry {
        Objects.requireNonNull(id, "AuditEntry id must not be null");
        Objects.requireNonNull(decision, "AuditEntry decision must not be null");
        Objects.requireNonNull(evidence, "AuditEntry evidence must not be null");
        Objects.requireNonNull(at, "AuditEntry at must not be null");
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)
                || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "AuditEntry confidence must be within [0.0, 1.0], got: " + confidence);
        }
    }
}
