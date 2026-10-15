package com.terravow.evidence;

import java.util.Objects;

/**
 * The record produced when a value cannot be linked to a primary source.
 *
 * <p>A produced value whose Evidence_Chain cannot be fully resolved to primary sources is marked
 * unsupported, routed to human review, and the reason is recorded (Req 10.7). This record captures
 * that outcome: the reference to the offending value and a human-readable reason. {@code
 * routedToHumanReview} is always {@code true} — the type exists precisely to represent the
 * route-to-review outcome.
 *
 * @param value              the reference to the unsupported value; never {@code null}
 * @param reason             a non-blank human-readable reason; never {@code null}
 * @param routedToHumanReview always {@code true}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.7</a>
 */
public record UnsupportedValueReview(ValueRef value, String reason, boolean routedToHumanReview) {

    /**
     * Validates required fields (Req 10.7).
     *
     * @throws NullPointerException     if {@code value} or {@code reason} is {@code null}
     * @throws IllegalArgumentException if {@code reason} is blank or {@code routedToHumanReview} is
     *                                  {@code false}
     */
    public UnsupportedValueReview {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (!routedToHumanReview) {
            throw new IllegalArgumentException(
                    "An unsupported value must be routed to human review (Req 10.7)");
        }
    }

    /**
     * Builds a review record for an unsupported value, routed to human review (Req 10.7).
     *
     * @param value  the reference to the unsupported value; never {@code null}
     * @param reason a non-blank human-readable reason; never {@code null}
     * @return the review record with {@code routedToHumanReview = true}
     */
    public static UnsupportedValueReview of(ValueRef value, String reason) {
        return new UnsupportedValueReview(value, reason, true);
    }
}
