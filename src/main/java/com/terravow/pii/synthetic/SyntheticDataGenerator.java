package com.terravow.pii.synthetic;

import java.util.List;

/**
 * Produces synthetic data grounded in published regulatory parameter ranges and real instrument
 * structures, labelled synthetic (Req 13.1, 13.2, 14.15).
 *
 * <p>Where real facility, machine, maintenance, or job/run data is unavailable, an implementation
 * generates values that lie <em>inside</em> the supplied {@link ParameterRange published ranges} —
 * so every synthetic figure is grounded in a published bound rather than free-invented — assembled
 * into a {@link SyntheticRecord} that mirrors a named real instrument structure. Every produced
 * record is labelled {@link com.terravow.domain.SyntheticLabel#SYNTHETIC}, and that label must then
 * propagate to every output derived from it (Req 13.2, 14.15) via {@link Labeled}.
 *
 * <p>Implementations must be deterministic: the same structure, ranges, and seed always produce the
 * same record, so synthetic fixtures are reproducible and the generation reads no wall-clock time
 * and no ungoverned randomness.
 *
 * @see DefaultSyntheticDataGenerator
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.1, 13.2, 14.15</a>
 */
public interface SyntheticDataGenerator {

    /**
     * Generates a synthetic record mirroring {@code structure}, with one grounded value per supplied
     * range and labelled {@link com.terravow.domain.SyntheticLabel#SYNTHETIC} (Req 13.1, 13.2,
     * 14.15).
     *
     * <p>Each produced {@link SyntheticParameter} is guaranteed to lie within its grounding
     * {@link ParameterRange}, so the record is grounded by construction. Generation is deterministic
     * in {@code structure}, {@code ranges}, and {@code seed}.
     *
     * @param structure the name of the real instrument structure to mirror; never {@code null},
     *                  non-blank
     * @param ranges    the published parameter ranges to ground the values in; never {@code null},
     *                  non-empty, no {@code null} elements, parameter names must be unique
     * @param seed      a seed selecting a reproducible grounded point within each range
     * @return a grounded, synthetic-labelled record
     * @throws NullPointerException     if {@code structure} or {@code ranges} (or an element) is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code structure} is blank, {@code ranges} is empty, or
     *                                  two ranges share a parameter name
     */
    SyntheticRecord generate(String structure, List<ParameterRange> ranges, long seed);
}
