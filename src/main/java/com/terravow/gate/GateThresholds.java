package com.terravow.gate;

/**
 * The confidence thresholds that partition the Decision_Gate confidence classification (Req 8.1).
 *
 * <p>A recommendation whose confidence is at or above {@code allowThreshold} is classified
 * {@link GateDecision#ALLOW}; at or above {@code askThreshold} but below {@code allowThreshold} is
 * {@link GateDecision#ASK}; below {@code askThreshold} is {@link GateDecision#BLOCK}. The compact
 * constructor enforces the structural invariant {@code 0 <= askThreshold <= allowThreshold <= 1}
 * so the three bands are well-formed and non-overlapping. Both thresholds must be finite.
 *
 * @param allowThreshold the confidence at or above which a recommendation is allowed, in
 *                       {@code [askThreshold, 1.0]}
 * @param askThreshold   the confidence at or above which a recommendation requires human review
 *                       rather than a block, in {@code [0.0, allowThreshold]}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1</a>
 */
public record GateThresholds(double allowThreshold, double askThreshold) {

    /**
     * Validates that both thresholds are finite, within {@code [0.0, 1.0]}, and ordered
     * {@code askThreshold <= allowThreshold} (Req 8.1).
     *
     * @throws IllegalArgumentException if either threshold is non-finite, out of {@code [0.0, 1.0]},
     *                                  or {@code askThreshold > allowThreshold}
     */
    public GateThresholds {
        requireUnitInterval(allowThreshold, "allowThreshold");
        requireUnitInterval(askThreshold, "askThreshold");
        if (askThreshold > allowThreshold) {
            throw new IllegalArgumentException(
                    "askThreshold (" + askThreshold + ") must not exceed allowThreshold ("
                            + allowThreshold + ")");
        }
    }

    private static void requireUnitInterval(double v, String name) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be a finite number, got: " + v);
        }
        if (v < 0.0 || v > 1.0) {
            throw new IllegalArgumentException(name + " must be within [0.0, 1.0], got: " + v);
        }
    }
}
