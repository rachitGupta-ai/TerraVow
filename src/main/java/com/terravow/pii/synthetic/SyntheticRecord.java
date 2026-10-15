package com.terravow.pii.synthetic;

import com.terravow.domain.SyntheticLabel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A synthetic record modelled on a real instrument structure, labelled {@link
 * SyntheticLabel#SYNTHETIC} (Req 13.1, 13.2, 14.15).
 *
 * <p>Where real external machine, maintenance, job/run, or facility records are unavailable,
 * TerraVow fills the gap with a synthetic record whose <em>shape</em> mirrors the real instrument
 * (the same named parameters) and whose <em>values</em> are each grounded in a published
 * {@link ParameterRange} (Req 13.1, 14.15). Such a record is always labelled {@link
 * SyntheticLabel#SYNTHETIC}, and that label must propagate to every output derived from it
 * (Req 13.2) — see {@link Labeled} and {@link SyntheticLabel#combine}.
 *
 * @param structure  a name identifying the real instrument structure this record mirrors; never
 *                   {@code null}, non-blank (e.g. {@code "stack-cems"}, {@code "job-run"})
 * @param parameters the generated parameters keyed by name; never {@code null}, defensively copied,
 *                   non-empty, grounded in their ranges by the generator
 * @param label      the synthetic label; a synthetic record is always {@link
 *                   SyntheticLabel#SYNTHETIC}
 *
 * @see SyntheticDataGenerator
 * @see Labeled
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.1, 13.2, 14.15</a>
 */
public record SyntheticRecord(
        String structure, Map<String, SyntheticParameter> parameters, SyntheticLabel label) {

    /**
     * Validates the record mirrors a named structure, carries at least one grounded parameter, and
     * is labelled {@link SyntheticLabel#SYNTHETIC} (Req 13.1, 13.2, 14.15).
     *
     * @throws NullPointerException     if any argument, key, or value is {@code null}
     * @throws IllegalArgumentException if {@code structure} is blank, {@code parameters} is empty,
     *                                  or {@code label} is not {@link SyntheticLabel#SYNTHETIC}
     */
    public SyntheticRecord {
        Objects.requireNonNull(structure, "SyntheticRecord structure must not be null");
        Objects.requireNonNull(parameters, "SyntheticRecord parameters must not be null");
        Objects.requireNonNull(label, "SyntheticRecord label must not be null");
        if (structure.isBlank()) {
            throw new IllegalArgumentException("SyntheticRecord structure must not be blank");
        }
        parameters = Map.copyOf(parameters); // immutable copy; rejects null keys/values
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException(
                    "SyntheticRecord must carry at least one grounded parameter");
        }
        if (label != SyntheticLabel.SYNTHETIC) {
            throw new IllegalArgumentException(
                    "A synthetic record must be labelled SYNTHETIC (Req 13.2, 14.15), got: " + label);
        }
    }

    /**
     * Wraps this record as a {@link Labeled} value carrying its {@link SyntheticLabel#SYNTHETIC}
     * label, so downstream derivations propagate the label by construction (Req 13.2, 14.15).
     *
     * @return a {@code Labeled<SyntheticRecord>} labelled {@link SyntheticLabel#SYNTHETIC}
     */
    public Labeled<SyntheticRecord> asLabeled() {
        return new Labeled<>(this, label);
    }

    /**
     * @return the generated numeric values keyed by parameter name, in a defensive insertion-ordered
     *         copy (convenience for callers that only need the figures)
     */
    public Map<String, Double> values() {
        Map<String, Double> values = new LinkedHashMap<>();
        parameters.forEach((name, parameter) -> values.put(name, parameter.value()));
        return values;
    }
}
