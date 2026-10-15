package com.terravow.capability.passport;

import java.util.Objects;

/**
 * The stable identifier of a facility that participates in airshed co-scheduling (Req 7.5).
 *
 * <p>When two or more facilities share an airshed, co-scheduling is evaluated across those
 * facilities (Req 7.5). Wrapping the facility reference in a dedicated value type keeps it type-safe
 * and distinct from the other opaque identifiers used by the Passport service (batch, tier).
 *
 * @param value a non-blank, stable facility identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.5</a>
 */
public record FacilityId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 7.5).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public FacilityId {
        Objects.requireNonNull(value, "FacilityId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("FacilityId value must not be blank");
        }
    }
}
