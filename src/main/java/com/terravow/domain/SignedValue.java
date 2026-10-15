package com.terravow.domain;

import java.util.Objects;

/**
 * A non-negative magnitude paired with an explicit {@link Sign}.
 *
 * <p>Used for signed deltas in substitution dossiers (Δemissions, Δenergy, Δcost) where the
 * direction of change carries meaning independent of the magnitude (Req 5.2). The magnitude is
 * constrained to be finite and non-negative; direction is expressed solely through {@code sign}, so
 * a zero-magnitude value still records its intended direction.
 *
 * @param magnitude the non-negative, finite magnitude of the change
 * @param sign      the direction of the change; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 5.2</a>
 */
public record SignedValue(double magnitude, Sign sign) {

    /**
     * Validates the magnitude is finite and non-negative and the sign is present (Req 5.2).
     *
     * @throws NullPointerException     if {@code sign} is {@code null}
     * @throws IllegalArgumentException if {@code magnitude} is non-finite or negative
     */
    public SignedValue {
        Objects.requireNonNull(sign, "sign must not be null");
        if (Double.isNaN(magnitude) || Double.isInfinite(magnitude)) {
            throw new IllegalArgumentException(
                    "SignedValue magnitude must be a finite number, got: " + magnitude);
        }
        if (magnitude < 0.0) {
            throw new IllegalArgumentException(
                    "SignedValue magnitude must be non-negative; use sign for direction, got: "
                            + magnitude);
        }
    }

    /**
     * Returns the signed scalar value — the magnitude negated when the sign is
     * {@link Sign#NEGATIVE}.
     *
     * @return {@code -magnitude} for a negative sign, otherwise {@code magnitude}
     */
    public double signedMagnitude() {
        return sign == Sign.NEGATIVE ? -magnitude : magnitude;
    }
}
