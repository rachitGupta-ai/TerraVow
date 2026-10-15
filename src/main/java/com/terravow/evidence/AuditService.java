package com.terravow.evidence;

import java.util.List;
import java.util.Optional;

/**
 * Persists and reads the decision audit trail (Req 10.2, 10.3, 10.4).
 *
 * <p>When a Decision_Gate classifies a recommendation, the classification is recorded with its
 * identifier, decision, confidence, and Evidence_Chain (Req 10.2). The defining safety property of
 * this service is <em>synchronous</em> persistence: {@link #persistDecision(AuditEntry)} must
 * complete — the entry durably recorded — before any decision-oriented output is returned to a
 * caller (Req 10.3). The trail is retained for a configured period of at least 7 years (Req 10.4).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.2, 10.3, 10.4</a>
 */
public interface AuditService {

    /**
     * Persists a decision audit entry synchronously (Req 10.2, 10.3).
     *
     * <p>This call must return only after the entry has been durably recorded. Callers that produce
     * a decision-oriented output must invoke this and let it complete <em>before</em> returning the
     * output, so no decision is surfaced without an audit record behind it.
     *
     * @param entry the audit entry to persist; never {@code null}
     * @throws NullPointerException if {@code entry} is {@code null}
     */
    void persistDecision(AuditEntry entry);

    /**
     * Returns the persisted audit entry for a recommendation, if one exists.
     *
     * @param id the recommendation identifier; never {@code null}
     * @return the stored entry, or empty if none was persisted
     * @throws NullPointerException if {@code id} is {@code null}
     */
    Optional<AuditEntry> findByRecommendation(RecommendationId id);

    /**
     * Returns all persisted audit entries, in insertion order.
     *
     * @return an immutable snapshot of the audit trail
     */
    List<AuditEntry> entries();
}
