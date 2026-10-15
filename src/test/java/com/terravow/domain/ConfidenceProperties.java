package com.terravow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based tests for the {@link Confidence} unit-interval invariant.
 *
 * <p>Validates Correctness Property 5: every {@code Confidence} value object carries a value within
 * the closed interval {@code [0.0, 1.0]}, and any attempt to construct one from an out-of-range or
 * non-finite value is rejected. This is the structural guarantee the Decision_Gate confidence
 * classification relies on (Req 1.7, 14.9).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.7, 14.9</a>
 */
class ConfidenceProperties {

    // Feature: terravow, Property 5: Confidence stays within the unit interval.
    @Property(tries = 100)
    void anyConstructedConfidenceIsWithinUnitInterval(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double value) {
        Confidence confidence = new Confidence(value);

        assertThat(confidence.value()).isBetween(0.0, 1.0);
    }

    // Feature: terravow, Property 5: Confidence stays within the unit interval.
    @Property(tries = 100)
    void outOfRangeValuesAreRejected(@ForAll("outOfRange") double value) {
        assertThatThrownBy(() -> new Confidence(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Feature: terravow, Property 5: Confidence stays within the unit interval.
    @Property(tries = 100)
    void nonFiniteValuesAreRejected(@ForAll("nonFinite") double value) {
        assertThatThrownBy(() -> new Confidence(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Finite values strictly outside the closed unit interval: anything below {@code 0.0} or above
     * {@code 1.0}.
     */
    @Provide
    Arbitrary<Double> outOfRange() {
        Arbitrary<Double> belowZero =
                Arbitraries.doubles().lessThan(0.0).filter(Double::isFinite);
        Arbitrary<Double> aboveOne =
                Arbitraries.doubles().greaterThan(1.0).filter(Double::isFinite);
        return Arbitraries.oneOf(belowZero, aboveOne);
    }

    /** Non-finite values: {@code NaN}, positive infinity, and negative infinity. */
    @Provide
    Arbitrary<Double> nonFinite() {
        return Arbitraries.of(
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }
}
