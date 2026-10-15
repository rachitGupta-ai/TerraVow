package com.terravow.domain;

import java.util.Objects;

/**
 * A machine component affected by a {@link MaintenanceEvent}.
 *
 * <p>A Maintenance_Event stores the components replaced during the maintenance action (Req 14.3).
 * Each component is identified by a non-blank name/part reference.
 *
 * @param identifier a non-blank component name or part reference
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3</a>
 */
public record Component(String identifier) {

    /**
     * Validates that the component identifier is present and non-blank (Req 14.3).
     *
     * @throws NullPointerException     if {@code identifier} is {@code null}
     * @throws IllegalArgumentException if {@code identifier} is blank
     */
    public Component {
        Objects.requireNonNull(identifier, "Component identifier must not be null");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("Component identifier must not be blank");
        }
    }
}
