package com.terravow.domain;

/**
 * A confidence score bounded to the closed unit interval {@code [0.0, 1.0]}.
 *
 * <p>Every determination TerraVow produces carries a {@code Confidence} (Req 1.7). The invariant is
 * enforced in the compact constructor: a value below {@code 0.0} or above {@code 1.0}, or a
 * non-finite value ({@code NaN}/infinity), is rejected. This makes an out-of-range confidence
 * unrepresentable, so downstream safety logic (the Decision_Gate confidence classification) can
 * rely on the bound structurally rather than re-checking it.
 *
 * @param value the confidence, {@code 0.0 <= value <= 1.0}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.7, 14.9</a>
 */
public record Confidence(double value) {

    /**
     * Validates the unit-interval invariant (Req 1.7).
     *
     * @throws IllegalArgumentException if {@code value} is non-finite or outside {@code [0.0, 1.0]}
     */
    public Confidence {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Confidence must be a finite number, got: " + value);
        }
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    "Confidence must be within [0.0, 1.0], got: " + value);
        }
    }

    /** Lowest possible confidence. */
    public static final Confidence NONE = new Confidence(0.0);

    /** Highest possible confidence. */
    public static final Confidence FULL = new Confidence(1.0);

    /**
     * Returns a confidence strictly lower than this one, clamped to {@code 0.0}.
     *
     * <p>Supports the uniform degradation rule (Req 1.5, 12.4): a fallback or reduced-evidence path
     * must carry strictly lower confidence than its primary path. The returned value is never
     * negative.
     *
     * @param delta the positive amount to subtract; must be {@code > 0.0} and finite
     * @return a new {@code Confidence} at {@code max(0.0, value - delta)}
     * @throws IllegalArgumentException if {@code delta} is not a positive finite number
     */
    public Confidence loweredBy(double delta) {
        if (Double.isNaN(delta) || Double.isInfinite(delta) || delta <= 0.0) {
            throw new IllegalArgumentException(
                    "Confidence reduction delta must be a positive finite number, got: " + delta);
        }
        return new Confidence(Math.max(0.0, value - delta));
    }
}
