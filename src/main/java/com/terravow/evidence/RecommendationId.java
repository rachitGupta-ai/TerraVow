package com.terravow.evidence;

import java.util.Objects;

/**
 * A stable identifier for a recommendation whose Decision_Gate classification is recorded in the
 * audit trail.
 *
 * <p>An {@link AuditEntry} is keyed by a {@code RecommendationId} so a classification (ALLOW / ASK /
 * BLOCK), its confidence, and its supporting {@link EvidenceChain} can be persisted and later
 * retrieved (Req 10.2). The identifier is a non-blank, opaque string.
 *
 * @param value a non-blank identifier of the recommendation
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.2</a>
 */
public record RecommendationId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 10.2).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public RecommendationId {
        Objects.requireNonNull(value, "RecommendationId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RecommendationId value must not be blank");
        }
    }
}
