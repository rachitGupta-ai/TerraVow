package com.terravow.domain;

import java.util.Objects;

/**
 * The identifier of a {@link MaintenanceEvent}.
 *
 * <p>A Maintenance_Event is a recorded maintenance occurrence on a {@link MachineAsset} (Req 14.3).
 * Its identifier is wrapped as a value type so maintenance-event references stay type-safe and
 * distinct from machine, job, batch, and action identifiers across the domain core.
 *
 * @param value a non-blank maintenance-event identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3</a>
 */
public record MaintenanceEventId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 14.3).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public MaintenanceEventId {
        Objects.requireNonNull(value, "MaintenanceEventId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("MaintenanceEventId value must not be blank");
        }
    }
}
