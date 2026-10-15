package com.terravow.reasoning;

/**
 * The terminal outcome of a reasoning request (Req 11.3, 11.5).
 *
 * <p>A request either yields a schema-valid determination that is {@link #ACCEPTED} (Req 11.3), or
 * exhausts its bounded retry limit without a valid output and is {@link #ESCALATED_HUMAN_REVIEW}
 * (Req 11.5).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.3, 11.5</a>
 */
public enum ReasoningStatus {
    /** A schema-valid determination was obtained and accepted (Req 11.3). */
    ACCEPTED,
    /** Every attempt failed schema validation; the request was routed to human review (Req 11.5). */
    ESCALATED_HUMAN_REVIEW
}
