package com.terravow.pii.synthetic;

import java.util.Objects;

/**
 * A single synthetic value generated inside a published {@link ParameterRange} (Req 13.1).
 *
 * <p>The value is guaranteed by construction to lie within the closed interval of its grounding
 * range, so a {@code SyntheticParameter} is self-describing proof that the synthetic figure is
 * <em>grounded</em> in a published regulatory parameter range and real instrument structure rather
 * than free-invented (Req 13.1, 14.15).
 *
 * @param parameter the name of the parameter, copied from the grounding {@link ParameterRange};
 *                  never {@code null}, non-blank
 * @param value     the generated value; finite and within the grounding range's {@code [min, max]}
 * @param unit      the declared unit, copied from the grounding range; never {@code null}, non-blank
 *
 * @see SyntheticDataGenerator
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.1, 14.15</a>
 */
public record SyntheticParameter(String parameter, double value, String unit) {

    /**
     * Validates identity, unit, and the finiteness of the value (the range-containment invariant is
     * enforced at the point of generation in {@link SyntheticDataGenerator}).
     *
     * @throws NullPointerException     if {@code parameter} or {@code unit} is {@code null}
     * @throws IllegalArgumentException if {@code parameter}/{@code unit} is blank or {@code value}
     *                                  is non-finite
     */
    public SyntheticParameter {
        Objects.requireNonNull(parameter, "SyntheticParameter parameter must not be null");
        Objects.requireNonNull(unit, "SyntheticParameter unit must not be null");
        if (parameter.isBlank()) {
            throw new IllegalArgumentException("SyntheticParameter parameter must not be blank");
        }
        if (unit.isBlank()) {
            throw new IllegalArgumentException("SyntheticParameter unit must not be blank");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "SyntheticParameter value must be finite, got: " + value);
        }
    }
}
