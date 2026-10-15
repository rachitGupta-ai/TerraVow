package com.terravow.domain;

import java.util.List;
import java.util.Objects;

/**
 * The planned set of due maintenance actions for a {@link MachineAsset}.
 *
 * <p>A Maintenance_Schedule is the collection of {@link ScheduledAction}s against which deferral and
 * overdue status are detected (Req 14.5). The actions list is defensively copied into an
 * unmodifiable list so a stored schedule cannot be mutated after construction; it may be empty when
 * no actions are planned.
 *
 * @param machine the machine the schedule belongs to; never {@code null}
 * @param actions the planned actions; never {@code null}, defensively copied, may be empty
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.5</a>
 */
public record MaintenanceSchedule(MachineId machine, List<ScheduledAction> actions) {

    /**
     * Validates that the machine reference is present and defensively copies the actions list into
     * an unmodifiable list (Req 14.5).
     *
     * @throws NullPointerException if {@code machine} is {@code null}, {@code actions} is
     *                              {@code null}, or {@code actions} contains a {@code null} element
     */
    public MaintenanceSchedule {
        Objects.requireNonNull(machine, "MaintenanceSchedule machine must not be null");
        Objects.requireNonNull(actions, "MaintenanceSchedule actions must not be null");
        // List.copyOf rejects null elements and yields an immutable list.
        actions = List.copyOf(actions);
    }
}
