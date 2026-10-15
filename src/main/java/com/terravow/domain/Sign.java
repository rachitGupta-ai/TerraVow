package com.terravow.domain;

/**
 * The explicit sign of a {@link SignedValue}.
 *
 * <p>Substitution counterfactuals report signed deltas (Δemissions, Δenergy, Δcost) where the
 * direction of change is itself meaningful — a reduction versus an increase (Req 5.2). Carrying the
 * sign as an explicit value, rather than folding it into a magnitude's numeric sign, keeps a
 * zero-magnitude delta's intended direction unambiguous.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 5.2</a>
 */
public enum Sign {
    /** The value represents an increase (positive direction). */
    POSITIVE,
    /** The value represents a decrease (negative direction). */
    NEGATIVE
}
