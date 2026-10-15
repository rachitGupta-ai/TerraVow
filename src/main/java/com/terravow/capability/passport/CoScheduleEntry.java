package com.terravow.capability.passport;

import java.util.Objects;

/**
 * One facility's line in an airshed co-scheduling recommendation: its actual emission mass and the
 * recommended mass after mass-preserving redistribution (Req 7.5).
 *
 * @param facility        the facility; never {@code null}
 * @param actualMass      the facility's actual scheduled emission mass; finite and non-negative
 * @param recommendedMass the recommended emission mass after co-scheduling; finite and non-negative
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.5</a>
 */
public record CoScheduleEntry(FacilityId facility, double actualMass, double recommendedMass) {

    /**
     * Validates the facility reference and finite, non-negative masses (Req 7.5).
     *
     * @throws NullPointerException     if {@code facility} is {@code null}
     * @throws IllegalArgumentException if either mass is non-finite or negative
     */
    public CoScheduleEntry {
        Objects.requireNonNull(facility, "facility must not be null");
        requireFiniteNonNegative(actualMass, "actualMass");
        requireFiniteNonNegative(recommendedMass, "recommendedMass");
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be a finite number, got: " + value);
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(field + " must be non-negative, got: " + value);
        }
    }
}
