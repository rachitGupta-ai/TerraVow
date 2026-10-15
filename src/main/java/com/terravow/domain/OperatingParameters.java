package com.terravow.domain;

/**
 * The operating parameters captured for a {@link JobRun}.
 *
 * <p>A Job_Run carries operating parameters such as load, throughput, and energy consumed
 * (Req 14.7). Together with the Job_Run output quantity, these parameters are the per-run basis for
 * computing the EID and the Embedded Pollution Passport of the produced batch (Req 14.8). All three
 * are required to be finite and non-negative so downstream intensity ratios (emissions per unit of
 * output) are well-defined.
 *
 * @param load          the machine load during the run; finite and non-negative
 * @param throughput    the throughput during the run; finite and non-negative
 * @param energyConsumed the energy consumed during the run; finite and non-negative
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.7, 14.8</a>
 */
public record OperatingParameters(double load, double throughput, double energyConsumed) {

    /**
     * Validates that each parameter is a finite, non-negative number (Req 14.7).
     *
     * @throws IllegalArgumentException if any parameter is non-finite or negative
     */
    public OperatingParameters {
        requireFiniteNonNegative(load, "load");
        requireFiniteNonNegative(throughput, "throughput");
        requireFiniteNonNegative(energyConsumed, "energyConsumed");
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(
                    "OperatingParameters " + field + " must be a finite number, got: " + value);
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(
                    "OperatingParameters " + field + " must be non-negative, got: " + value);
        }
    }
}
