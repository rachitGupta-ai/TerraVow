package com.terravow.capability.passport;

import java.util.Objects;

/**
 * A facility participating in airshed co-scheduling, with its actual scheduled emission mass and a
 * relative assimilative-capacity weight (Req 7.5).
 *
 * <p>When two or more facilities share an airshed, TerraVow evaluates a co-scheduling recommendation
 * that preserves the total emission mass of the affected facilities (Req 7.5). The co-scheduler
 * redistributes the <em>same total mass</em> across the facilities in proportion to their
 * assimilative-capacity {@code weight} — a facility with more headroom receives a larger share —
 * without creating or destroying any mass.
 *
 * @param facility      the facility; never {@code null}
 * @param actualMass    the facility's actual scheduled emission mass; finite and non-negative
 * @param capacityWeight the facility's relative assimilative-capacity weight; finite and strictly
 *                      positive
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.5</a>
 */
public record AirshedFacility(FacilityId facility, double actualMass, double capacityWeight) {

    /**
     * Validates the facility reference, a finite non-negative actual mass, and a finite
     * strictly-positive capacity weight (Req 7.5).
     *
     * @throws NullPointerException     if {@code facility} is {@code null}
     * @throws IllegalArgumentException if {@code actualMass} is non-finite or negative, or if
     *                                  {@code capacityWeight} is non-finite or not strictly positive
     */
    public AirshedFacility {
        Objects.requireNonNull(facility, "facility must not be null");
        if (Double.isNaN(actualMass) || Double.isInfinite(actualMass)) {
            throw new IllegalArgumentException(
                    "actualMass must be a finite number, got: " + actualMass);
        }
        if (actualMass < 0.0) {
            throw new IllegalArgumentException(
                    "actualMass must be non-negative, got: " + actualMass);
        }
        if (Double.isNaN(capacityWeight) || Double.isInfinite(capacityWeight)) {
            throw new IllegalArgumentException(
                    "capacityWeight must be a finite number, got: " + capacityWeight);
        }
        if (capacityWeight <= 0.0) {
            throw new IllegalArgumentException(
                    "capacityWeight must be strictly positive, got: " + capacityWeight);
        }
    }
}
