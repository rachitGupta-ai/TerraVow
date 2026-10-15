package com.terravow.domain;

import java.util.Objects;

/**
 * The facility location a {@link MachineAsset} is sited at.
 *
 * <p>A Machine_Asset carries a required facility location (Req 14.1). The location identifies the
 * site to which the machine's emissions, airshed membership, and receptor exposure are attributed.
 * It is kept as an opaque, non-blank identifier here; richer geographic structure (e.g. a
 * Receptor_Map) is layered on by the capability services.
 *
 * @param value a non-blank facility-location identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1</a>
 */
public record FacilityLocation(String value) {

    /**
     * Validates that the location identifier is present and non-blank (Req 14.1).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public FacilityLocation {
        Objects.requireNonNull(value, "FacilityLocation value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("FacilityLocation value must not be blank");
        }
    }
}
