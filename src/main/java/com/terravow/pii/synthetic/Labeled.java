package com.terravow.pii.synthetic;

import com.terravow.domain.SyntheticLabel;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A value paired with the {@link SyntheticLabel} that must travel with it, so the synthetic label
 * propagates to every output derived — directly or transitively — from synthetic data (Req 13.2,
 * 14.15).
 *
 * <p>This is the propagation carrier. Rather than relying on callers to remember to re-stamp a
 * label after each transformation, a {@code Labeled<T>} carries the label alongside the value and
 * every derivation method ({@link #map}, {@link #deriveWith}, the static {@link #derive} combinators)
 * computes the derived label via {@link SyntheticLabel#combine}. Because {@code combine} is
 * commutative and associative and {@link SyntheticLabel#REAL} is its identity, folding over any
 * number of inputs in any order yields {@link SyntheticLabel#SYNTHETIC} iff <em>any</em> input was
 * synthetic — which is exactly the propagation rule: an output derived from any synthetic input is
 * itself synthetic (Req 13.2, 14.15). Transitivity follows because each derived {@code Labeled}
 * carries the folded label forward into the next derivation.
 *
 * @param <T>   the carried value type
 * @param value the carried value; never {@code null}
 * @param label the label that must propagate with {@code value}; never {@code null}
 *
 * @see SyntheticLabel#combine
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.2, 14.15</a>
 */
public record Labeled<T>(T value, SyntheticLabel label) {

    /**
     * @throws NullPointerException if {@code value} or {@code label} is {@code null}
     */
    public Labeled {
        Objects.requireNonNull(value, "Labeled value must not be null");
        Objects.requireNonNull(label, "Labeled label must not be null");
    }

    /**
     * Wraps a value derived solely from real records, labelled {@link SyntheticLabel#REAL}.
     *
     * @param value the real-derived value; never {@code null}
     * @param <T>   the value type
     * @return a {@code Labeled} marked {@link SyntheticLabel#REAL}
     */
    public static <T> Labeled<T> real(T value) {
        return new Labeled<>(value, SyntheticLabel.REAL);
    }

    /**
     * Wraps a value known to be synthetic, labelled {@link SyntheticLabel#SYNTHETIC}.
     *
     * @param value the synthetic value; never {@code null}
     * @param <T>   the value type
     * @return a {@code Labeled} marked {@link SyntheticLabel#SYNTHETIC}
     */
    public static <T> Labeled<T> synthetic(T value) {
        return new Labeled<>(value, SyntheticLabel.SYNTHETIC);
    }

    /**
     * @return {@code true} iff this value is (directly or transitively) derived from synthetic data
     */
    public boolean isSynthetic() {
        return label == SyntheticLabel.SYNTHETIC;
    }

    /**
     * Derives a new value from this one, carrying this value's label forward unchanged (Req 13.2).
     *
     * <p>Use when a derivation has exactly one labelled input: the derived output inherits this
     * input's label, so a synthetic input yields a synthetic output.
     *
     * @param mapper the pure transformation from the carried value to the derived value; never
     *               {@code null}, must not return {@code null}
     * @param <R>    the derived value type
     * @return the derived value carrying this value's label
     * @throws NullPointerException if {@code mapper} is {@code null} or returns {@code null}
     */
    public <R> Labeled<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new Labeled<>(mapper.apply(value), label);
    }

    /**
     * Derives a new value from this value and one other labelled input, propagating the combined
     * label via {@link SyntheticLabel#combine} (Req 13.2).
     *
     * @param other  the second labelled input; never {@code null}
     * @param mapper the pure combination of the two carried values; never {@code null}, must not
     *               return {@code null}
     * @param <U>    the other input's value type
     * @param <R>    the derived value type
     * @return the derived value whose label is {@code this.label.combine(other.label)}
     * @throws NullPointerException if any argument is {@code null} or {@code mapper} returns
     *                              {@code null}
     */
    public <U, R> Labeled<R> deriveWith(
            Labeled<U> other, java.util.function.BiFunction<? super T, ? super U, ? extends R> mapper) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new Labeled<>(mapper.apply(value, other.value), label.combine(other.label));
    }

    /**
     * Folds the labels of any number of labelled inputs into the label a value derived from all of
     * them must carry (Req 13.2, 14.15).
     *
     * <p>The fold starts from {@link SyntheticLabel#REAL} (the identity of {@link
     * SyntheticLabel#combine}) and combines every input's label, so the result is {@link
     * SyntheticLabel#SYNTHETIC} iff <em>any</em> input is synthetic. An empty input collection
     * therefore yields {@link SyntheticLabel#REAL}: a value derived from no synthetic input is real.
     *
     * @param inputs the labelled inputs a value is derived from; never {@code null}, no {@code null}
     *               elements
     * @return the propagated label for an output derived from all of {@code inputs}
     * @throws NullPointerException if {@code inputs} or any element is {@code null}
     */
    public static SyntheticLabel propagatedLabel(Collection<? extends Labeled<?>> inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        SyntheticLabel folded = SyntheticLabel.REAL;
        for (Labeled<?> input : inputs) {
            Objects.requireNonNull(input, "inputs must not contain null");
            folded = folded.combine(input.label());
        }
        return folded;
    }

    /**
     * Derives a value from a collection of labelled inputs, attaching the folded propagated label
     * (Req 13.2, 14.15).
     *
     * @param inputs      the labelled inputs; never {@code null}, no {@code null} elements
     * @param derivedValue the value derived from those inputs; never {@code null}
     * @param <R>         the derived value type
     * @return {@code derivedValue} labelled with {@link #propagatedLabel(Collection)} of {@code
     *         inputs}
     * @throws NullPointerException if any argument or element is {@code null}
     */
    public static <R> Labeled<R> derive(Collection<? extends Labeled<?>> inputs, R derivedValue) {
        Objects.requireNonNull(derivedValue, "derivedValue must not be null");
        return new Labeled<>(derivedValue, propagatedLabel(inputs));
    }

    /**
     * Convenience over {@link #derive(Collection, Object)} for a varargs list of inputs.
     *
     * @param derivedValue the value derived from {@code inputs}; never {@code null}
     * @param inputs       the labelled inputs; never {@code null}, no {@code null} elements
     * @param <R>          the derived value type
     * @return {@code derivedValue} labelled with the folded propagated label of {@code inputs}
     */
    @SafeVarargs
    public static <R> Labeled<R> deriveFrom(R derivedValue, Labeled<?>... inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        return derive(List.of(inputs), derivedValue);
    }
}
