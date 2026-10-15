package com.terravow.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A recorded maintenance occurrence on a {@link MachineAsset}.
 *
 * <p>A Maintenance_Event stores the event type (preventive or corrective), the start and end
 * timestamps, the maintenance action performed, the components replaced, the downtime duration, and
 * the acting technician or system identifier (Req 14.3). This record holds that full field set; the
 * event store additionally rejects events submitted with a missing required field, recording the
 * rejection with the missing field (Req 14.4 — implemented by the store in task 2.2). The components
 * list is defensively copied into an unmodifiable list so a stored event cannot be mutated after
 * construction; it may be empty when no components were replaced.
 *
 * @param id                      the maintenance-event identifier; never {@code null}
 * @param machine                 the machine the event was recorded against; never {@code null}
 * @param type                    the preventive/corrective classification; never {@code null}
 * @param start                   the start timestamp; never {@code null}
 * @param end                     the end timestamp; never {@code null}
 * @param action                  the maintenance action performed; never {@code null} or blank
 * @param componentsReplaced      the components replaced; never {@code null}, defensively copied,
 *                                may be empty
 * @param downtime                the downtime duration; never {@code null}
 * @param actingTechnicianOrSystem the acting technician or system identifier; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3, 14.4</a>
 */
public record MaintenanceEvent(
        MaintenanceEventId id,
        MachineId machine,
        MaintenanceType type,
        Instant start,
        Instant end,
        String action,
        List<Component> componentsReplaced,
        Duration downtime,
        ActorId actingTechnicianOrSystem) {

    /**
     * Validates that every required field is present (Req 14.3) and defensively copies the
     * components list into an unmodifiable list. A blank {@code action} is rejected.
     *
     * @throws NullPointerException     if any required field is {@code null}, or
     *                                  {@code componentsReplaced} contains a {@code null} element
     * @throws IllegalArgumentException if {@code action} is blank
     */
    public MaintenanceEvent {
        Objects.requireNonNull(id, "MaintenanceEvent id must not be null");
        Objects.requireNonNull(machine, "MaintenanceEvent machine must not be null");
        Objects.requireNonNull(type, "MaintenanceEvent type must not be null");
        Objects.requireNonNull(start, "MaintenanceEvent start must not be null");
        Objects.requireNonNull(end, "MaintenanceEvent end must not be null");
        Objects.requireNonNull(action, "MaintenanceEvent action must not be null");
        if (action.isBlank()) {
            throw new IllegalArgumentException("MaintenanceEvent action must not be blank");
        }
        Objects.requireNonNull(
                componentsReplaced, "MaintenanceEvent componentsReplaced must not be null");
        Objects.requireNonNull(downtime, "MaintenanceEvent downtime must not be null");
        Objects.requireNonNull(
                actingTechnicianOrSystem,
                "MaintenanceEvent actingTechnicianOrSystem must not be null");
        // List.copyOf rejects null elements and yields an immutable list.
        componentsReplaced = List.copyOf(componentsReplaced);
    }
}
