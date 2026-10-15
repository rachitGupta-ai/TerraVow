package com.terravow.domain;

import java.util.Objects;

/**
 * The identifier of a {@link ScheduledAction} within a {@link MaintenanceSchedule}.
 *
 * <p>A Maintenance_Schedule is the planned set of due maintenance actions for a
 * {@link MachineAsset}, against which deferral and overdue status are detected (Req 14.5). Each
 * scheduled action carries a dedicated identifier so a specific action can be tracked from due
 * through completion.
 *
 * @param value a non-blank scheduled-action identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.5</a>
 */
public record ActionId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 14.5).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public ActionId {
        Objects.requireNonNull(value, "ActionId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ActionId value must not be blank");
        }
    }
}
