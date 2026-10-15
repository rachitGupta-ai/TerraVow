package com.terravow.domain;

import java.util.Objects;

/**
 * A substitute reference profile derived from comparable machines of the same {@link AssetClass}.
 *
 * <p>A Fleet_Cohort_Baseline is used when a machine's {@link CommissioningBaseline} data is absent;
 * it supplies a cohort emissions-intensity and energy-consumption profile and always carries lower
 * confidence than a commissioning-based result (Req 1.5, 14.10). It is keyed by asset class so the
 * cohort of comparable machines is explicit. Both profile values are required to be finite and
 * non-negative.
 *
 * @param assetClass      the asset class this cohort profile applies to; never {@code null}
 * @param cohortIntensity the cohort emissions intensity; finite and non-negative
 * @param cohortEnergy    the cohort energy consumption; finite and non-negative
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.5, 14.10</a>
 */
public record FleetCohortBaseline(
        AssetClass assetClass, double cohortIntensity, double cohortEnergy) {

    /**
     * Validates that the asset class is present and both profile values are finite and non-negative
     * (Req 1.5, 14.10).
     *
     * @throws NullPointerException     if {@code assetClass} is {@code null}
     * @throws IllegalArgumentException if {@code cohortIntensity} or {@code cohortEnergy} is
     *                                  non-finite or negative
     */
    public FleetCohortBaseline {
        Objects.requireNonNull(assetClass, "FleetCohortBaseline assetClass must not be null");
        requireFiniteNonNegative(cohortIntensity, "cohortIntensity");
        requireFiniteNonNegative(cohortEnergy, "cohortEnergy");
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(
                    "FleetCohortBaseline " + field + " must be a finite number, got: " + value);
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(
                    "FleetCohortBaseline " + field + " must be non-negative, got: " + value);
        }
    }
}
