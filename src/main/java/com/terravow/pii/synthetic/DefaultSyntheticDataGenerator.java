package com.terravow.pii.synthetic;

import com.terravow.domain.SyntheticLabel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic implementation of {@link SyntheticDataGenerator} that places each value inside its
 * published {@link ParameterRange} and labels the result {@link SyntheticLabel#SYNTHETIC} (Req 13.1,
 * 13.2, 14.15).
 *
 * <h2>Grounding</h2>
 * For each supplied range the generator derives a reproducible fraction in {@code [0.0, 1.0]} from
 * the {@code seed} and the parameter name, then maps it onto the range via {@link
 * ParameterRange#at(double)}. The resulting value therefore always lies within the published
 * {@code [min, max]} bound — the synthetic figure is grounded in a published regulatory parameter
 * range and real instrument structure, never free-invented (Req 13.1, 14.15).
 *
 * <h2>Determinism</h2>
 * The fraction is a pure function of {@code seed} and the parameter name (mixed with a fixed
 * hashing constant), so the same {@code (structure, ranges, seed)} always yields the same record.
 * The generator reads no wall-clock time and uses no ungoverned randomness, matching the project's
 * determinism rule.
 *
 * <h2>Labelling</h2>
 * Every produced {@link SyntheticRecord} is labelled {@link SyntheticLabel#SYNTHETIC}. Downstream
 * code must carry that label forward through {@link Labeled} so it propagates to every derived
 * output (Req 13.2, 14.15).
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.1, 13.2, 14.15</a>
 */
public final class DefaultSyntheticDataGenerator implements SyntheticDataGenerator {

    /** Odd mixing constant (golden-ratio derived) that decorrelates per-parameter fractions. */
    private static final long MIX = 0x9E3779B97F4A7C15L;

    /** Divisor mapping a 53-bit mantissa-sized long onto {@code [0.0, 1.0)}. */
    private static final double UNIT_SCALE = (double) (1L << 53);

    /**
     * {@inheritDoc}
     *
     * <p>Validates the structure and ranges (unique parameter names), draws a deterministic grounded
     * value for each range, and assembles a {@link SyntheticLabel#SYNTHETIC} record.
     */
    @Override
    public SyntheticRecord generate(String structure, List<ParameterRange> ranges, long seed) {
        Objects.requireNonNull(structure, "structure must not be null");
        Objects.requireNonNull(ranges, "ranges must not be null");
        if (structure.isBlank()) {
            throw new IllegalArgumentException("structure must not be blank");
        }
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("ranges must not be empty");
        }

        Map<String, SyntheticParameter> parameters = new LinkedHashMap<>();
        for (ParameterRange range : ranges) {
            Objects.requireNonNull(range, "ranges must not contain null");
            if (parameters.containsKey(range.parameter())) {
                throw new IllegalArgumentException(
                        "Duplicate parameter name in ranges: " + range.parameter());
            }
            double fraction = fractionFor(seed, range.parameter());
            double value = range.at(fraction); // guaranteed within [min, max] — grounded
            parameters.put(
                    range.parameter(),
                    new SyntheticParameter(range.parameter(), value, range.unit()));
        }

        return new SyntheticRecord(structure, parameters, SyntheticLabel.SYNTHETIC);
    }

    /**
     * Derives a reproducible fraction in {@code [0.0, 1.0]} from the seed and parameter name.
     *
     * <p>Pure function: combines the seed with the parameter name's hash through a bit-mixing step
     * (SplitMix64-style finalizer), then scales the top 53 bits onto the unit interval. Different
     * parameter names decorrelate, and the same inputs always produce the same fraction.
     *
     * @param seed      the generation seed
     * @param parameter the parameter name; never {@code null}
     * @return a deterministic fraction in {@code [0.0, 1.0]}
     */
    private static double fractionFor(long seed, String parameter) {
        long z = seed + MIX * (0x100000001B3L + parameter.hashCode());
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        double fraction = (z >>> 11) / UNIT_SCALE; // [0.0, 1.0)
        return Math.min(1.0, Math.max(0.0, fraction));
    }
}
