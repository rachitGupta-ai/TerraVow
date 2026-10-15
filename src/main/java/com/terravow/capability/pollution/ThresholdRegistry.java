package com.terravow.capability.pollution;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The applicable regulatory thresholds, keyed by {@link Pollutant} and {@link EnvironmentalMedium}
 * (Req 3.1).
 *
 * <p>A reading is evaluated against the threshold registered for its pollutant and medium (Req
 * 3.1). When no threshold is registered for that pair, the reading is recorded as
 * unevaluated-for-threshold with a reason (Req 3.2). The registry is configurable: thresholds are
 * supplied at construction, so different environments / regulatory regimes can register different
 * limits without code changes. Pollutant keys are matched case-insensitively via
 * {@link Pollutant#key()}.
 *
 * <p>Instances are immutable; thresholds are copied defensively at construction.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.2</a>
 */
public final class ThresholdRegistry {

    private final Map<Key, Double> thresholds;

    private ThresholdRegistry(Map<Key, Double> thresholds) {
        this.thresholds = Map.copyOf(thresholds);
    }

    /** An empty registry — every lookup returns {@link Optional#empty()} (Req 3.2). */
    public static ThresholdRegistry empty() {
        return new ThresholdRegistry(Map.of());
    }

    /**
     * Starts building a registry.
     *
     * @return a new mutable {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Looks up the applicable regulatory threshold for a pollutant in an environmental medium
     * (Req 3.1).
     *
     * @param pollutant the pollutant; never {@code null}
     * @param medium    the environmental medium; never {@code null}
     * @return the registered threshold, or {@link Optional#empty()} when none applies (Req 3.2)
     * @throws NullPointerException if {@code pollutant} or {@code medium} is {@code null}
     */
    public Optional<Double> thresholdFor(Pollutant pollutant, EnvironmentalMedium medium) {
        Objects.requireNonNull(pollutant, "pollutant must not be null");
        Objects.requireNonNull(medium, "medium must not be null");
        return Optional.ofNullable(thresholds.get(new Key(pollutant.key(), medium)));
    }

    /** The composite lookup key: normalised pollutant key + medium. */
    private record Key(String pollutantKey, EnvironmentalMedium medium) {
    }

    /** Mutable builder for a {@link ThresholdRegistry}. */
    public static final class Builder {

        private final Map<Key, Double> thresholds = new HashMap<>();

        private Builder() {
        }

        /**
         * Registers a finite, non-negative threshold for a pollutant in a medium.
         *
         * @param pollutant the pollutant; never {@code null}
         * @param medium    the environmental medium; never {@code null}
         * @param threshold the regulatory limit; must be finite and {@code >= 0}
         * @return this builder
         * @throws NullPointerException     if {@code pollutant} or {@code medium} is {@code null}
         * @throws IllegalArgumentException if {@code threshold} is non-finite or negative
         */
        public Builder with(Pollutant pollutant, EnvironmentalMedium medium, double threshold) {
            Objects.requireNonNull(pollutant, "pollutant must not be null");
            Objects.requireNonNull(medium, "medium must not be null");
            if (Double.isNaN(threshold) || Double.isInfinite(threshold) || threshold < 0.0) {
                throw new IllegalArgumentException(
                        "threshold must be a finite non-negative number, got: " + threshold);
            }
            thresholds.put(new Key(pollutant.key(), medium), threshold);
            return this;
        }

        /**
         * Builds the immutable registry.
         *
         * @return a new {@link ThresholdRegistry}
         */
        public ThresholdRegistry build() {
            return new ThresholdRegistry(thresholds);
        }
    }
}
