package com.terravow.capability.certification;

import java.util.Objects;

/**
 * The stable identity of a facility evaluated for Trajectory Certification.
 *
 * <p>A certification verdict is computed per facility, and an issued certificate publishes the
 * certified facility identity to the public global registry (Req 9.5). Wrapping the identity as a
 * value type keeps it type-safe and lets the registry and certificate store key entries by
 * facility.
 *
 * @param value a non-blank facility identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.5</a>
 */
public record FacilityId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 9.5).
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
