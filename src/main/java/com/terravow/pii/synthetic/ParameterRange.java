package com.terravow.pii.synthetic;

import java.util.Objects;

/**
 * A published regulatory parameter range that grounds synthetic data generation (Req 13.1, 14.15).
 *
 * <p>Where real facility, machine, maintenance, or job/run data is unavailable, TerraVow fills the
 * gap with <em>synthetic</em> data that is nonetheless grounded in <em>published regulatory
 * parameter ranges and real instrument structures</em> (Req 13.1, 14.15). A {@code ParameterRange}
 * models one such published range for a single named parameter of a real instrument field: for
 * example a stack SO₂ reading bounded by its regulatory threshold, or a commissioning-baseline
 * emissions intensity bounded by the instrument's documented operating envelope.
 *
 * <p>The range is a closed interval {@code [min, max]} expressed in a declared {@code unit}. A
 * generator draws values from inside this interval so every synthetic value is traceable to the
 * published bound that justifies it — synthetic data is never free-invented, it is grounded.
 *
 * @param parameter the name of the real instrument parameter this range bounds; never {@code null},
 *                  non-blank (e.g. {@code "stack.so2"}, {@code "commissioning.emissionsIntensity"})
 * @param min       the published lower bound, inclusive; finite and {@code <= max}
 * @param max       the published upper bound, inclusive; finite and {@code >= min}
 * @param unit      the declared unit of the bounds; never {@code null}, non-blank (e.g.
 *                  {@code "mg/Nm3"}, {@code "kgCO2e/t"})
 *
 * @see SyntheticDataGenerator
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.1, 14.15</a>
 */
public record ParameterRange(String parameter, double min, double max, String unit) {

    /**
     * Validates that the range names a real parameter, carries a declared unit, and is a finite
     * closed interval (Req 13.1).
     *
     * @throws NullPointerException     if {@code parameter} or {@code unit} is {@code null}
     * @throws IllegalArgumentException if {@code parameter}/{@code unit} is blank, a bound is
     *                                  non-finite, or {@code min > max}
     */
    public ParameterRange {
        Objects.requireNonNull(parameter, "ParameterRange parameter must not be null");
        Objects.requireNonNull(unit, "ParameterRange unit must not be null");
        if (parameter.isBlank()) {
            throw new IllegalArgumentException("ParameterRange parameter must not be blank");
        }
        if (unit.isBlank()) {
            throw new IllegalArgumentException("ParameterRange unit must not be blank");
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException(
                    "ParameterRange bounds must be finite, got [" + min + ", " + max + "]");
        }
        if (min > max) {
            throw new IllegalArgumentException(
                    "ParameterRange min must not exceed max, got [" + min + ", " + max + "]");
        }
    }

    /**
     * @param value a candidate value
     * @return {@code true} iff {@code value} lies within this closed interval {@code [min, max]}
     */
    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    /**
     * Maps a unit-interval fraction onto this range by linear interpolation, so a deterministic
     * generator can place a grounded value inside the published bounds (Req 13.1).
     *
     * @param fraction a position in {@code [0.0, 1.0]} within the range (0 → {@link #min}, 1 →
     *                 {@link #max})
     * @return the grounded value {@code min + fraction * (max - min)}, always within {@code [min,
     *         max]}
     * @throws IllegalArgumentException if {@code fraction} is non-finite or outside {@code [0, 1]}
     */
    public double at(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException(
                    "fraction must be within [0.0, 1.0], got: " + fraction);
        }
        double value = min + fraction * (max - min);
        // Guard against floating-point drift pushing the result a hair outside the closed interval.
        return Math.min(max, Math.max(min, value));
    }
}
