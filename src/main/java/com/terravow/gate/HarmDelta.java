package com.terravow.gate;

/**
 * The change in environmental harm a recommendation would cause, used by the Decision_Gate hard
 * veto (Req 8.3, 8.4).
 *
 * <p>If either delta is strictly greater than zero — the recommendation would increase the total
 * emission mass or relocate load across media — the gate applies a hard veto and classifies the
 * recommendation {@link GateDecision#BLOCK} <em>before</em> any confidence classification
 * (Req 8.4). A value of zero or below (a mass-preserving or harm-reducing recommendation) does not
 * trip the veto. Both deltas must be finite so the veto comparison is well-defined; a non-finite
 * delta is a programming error and is rejected.
 *
 * @param totalEmissionMassDelta the change in total emission mass; {@code > 0.0} trips the veto
 * @param crossMediaLoadDelta    the change in cross-media load; {@code > 0.0} trips the veto
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.3, 8.4</a>
 */
public record HarmDelta(double totalEmissionMassDelta, double crossMediaLoadDelta) {

    /**
     * Validates that both deltas are finite (Req 8.4). A {@code NaN} or infinite delta would make
     * the veto comparison undefined and is rejected.
     *
     * @throws IllegalArgumentException if either delta is non-finite
     */
    public HarmDelta {
        requireFinite(totalEmissionMassDelta, "totalEmissionMassDelta");
        requireFinite(crossMediaLoadDelta, "crossMediaLoadDelta");
    }

    /** A harm delta of zero on both axes — mass-preserving, no cross-media relocation. */
    public static final HarmDelta NONE = new HarmDelta(0.0, 0.0);

    /**
     * Whether this delta trips the hard veto: {@code true} iff either axis increases by any amount
     * above zero (Req 8.3, 8.4).
     *
     * @return {@code true} if {@code totalEmissionMassDelta > 0} or {@code crossMediaLoadDelta > 0}
     */
    public boolean increasesHarm() {
        return totalEmissionMassDelta > 0.0 || crossMediaLoadDelta > 0.0;
    }

    private static void requireFinite(double v, String name) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be a finite number, got: " + v);
        }
    }
}
