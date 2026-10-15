package com.terravow.domain;

import java.util.Objects;

/**
 * The identifier of the technician or system that performed a {@link MaintenanceEvent}.
 *
 * <p>A Maintenance_Event records the acting technician or system identifier (Req 14.3), preserving
 * accountability for who or what carried out the maintenance action. Supplier/technician
 * identifiers that are PII or commercially confidential are masked before any value derived from
 * this field reaches reasoning or persistence (Req 13.3 — handled by the masking gate in task 20).
 *
 * @param value a non-blank actor identifier (technician or system)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3</a>
 */
public record ActorId(String value) {

    /**
     * Validates that the actor identifier is present and non-blank (Req 14.3).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public ActorId {
        Objects.requireNonNull(value, "ActorId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ActorId value must not be blank");
        }
    }
}
