package com.terravow.domain;

/**
 * Marks whether a value originates from (or is derived from) synthetic data.
 *
 * <p>Synthetic data is grounded in published regulatory parameter ranges and real instrument
 * structures, labelled {@link #SYNTHETIC}, and the label must propagate to every output derived —
 * directly or transitively — from it (Req 13.2, 14.15). {@link #REAL} marks values derived solely
 * from real records.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.2, 14.15</a>
 */
public enum SyntheticLabel {
    /** Derived solely from real records. */
    REAL,
    /** Derived, directly or transitively, from synthetic data. */
    SYNTHETIC;

    /**
     * Combines this label with another along a derivation edge: a value derived from any synthetic
     * input is itself synthetic (Req 13.2). The combination is commutative and associative, so it
     * propagates correctly across a derivation graph of any shape.
     *
     * @param other the label of another input to the derived value; never {@code null}
     * @return {@link #SYNTHETIC} if either operand is synthetic, otherwise {@link #REAL}
     */
    public SyntheticLabel combine(SyntheticLabel other) {
        return (this == SYNTHETIC || other == SYNTHETIC) ? SYNTHETIC : REAL;
    }
}
