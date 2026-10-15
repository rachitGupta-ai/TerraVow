package com.terravow.domain;

import java.util.Objects;

/**
 * The reference emissions-intensity and energy-consumption profile of a machine at its original
 * commissioning.
 *
 * <p>The Commissioning_Baseline is the comparison point for EID: EID is the delta between the
 * current emissions intensity and the Commissioning_Baseline intensity (Req 1.3). A machine links to
 * its Commissioning_Baseline when one is known (Req 14.1); when it is absent, EID falls back to a
 * {@link FleetCohortBaseline} at strictly lower confidence (Req 1.5). Both intensity and energy are
 * required to be finite and non-negative.
 *
 * @param machine          the machine this baseline belongs to; never {@code null}
 * @param baselineIntensity the commissioning emissions intensity; finite and non-negative
 * @param baselineEnergy    the commissioning energy consumption; finite and non-negative
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.3, 14.1</a>
 */
public record CommissioningBaseline(
        MachineId machine, double baselineIntensity, double baselineEnergy) {

    /**
     * Validates that the machine reference is present and both profile values are finite and
     * non-negative (Req 1.3, 14.1).
     *
     * @throws NullPointerException     if {@code machine} is {@code null}
     * @throws IllegalArgumentException if {@code baselineIntensity} or {@code baselineEnergy} is
     *                                  non-finite or negative
     */
    public CommissioningBaseline {
        Objects.requireNonNull(machine, "CommissioningBaseline machine must not be null");
        requireFiniteNonNegative(baselineIntensity, "baselineIntensity");
        requireFiniteNonNegative(baselineEnergy, "baselineEnergy");
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(
                    "CommissioningBaseline " + field + " must be a finite number, got: " + value);
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(
                    "CommissioningBaseline " + field + " must be non-negative, got: " + value);
        }
    }
}
