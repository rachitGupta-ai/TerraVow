package com.terravow.domain.store;

import com.terravow.domain.ActorId;
import com.terravow.domain.Component;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEvent;
import com.terravow.domain.MaintenanceEventId;
import com.terravow.domain.MaintenanceType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A raw, possibly-incomplete Maintenance_Event submission validated by the
 * {@link MaintenanceEventStore}.
 *
 * <p>Unlike {@link MaintenanceEvent} — whose compact constructor rejects any {@code null} required
 * field — this submission permits {@code null} fields so the store can detect a missing required
 * field, reject the event, and record the rejection with the missing field (Req 14.4). All of
 * {@code id}, {@code machine}, {@code type}, {@code start}, {@code end}, {@code action},
 * {@code componentsReplaced}, {@code downtime}, and {@code actingTechnicianOrSystem} are required
 * (Req 14.3); a blank {@code action} counts as missing. An empty (but non-null) components list is
 * valid — it means no components were replaced.
 *
 * <p>This is an inert data carrier: construction never throws on a missing field; validation is the
 * store's responsibility.
 *
 * @param id                       the maintenance-event identifier; may be {@code null} (missing)
 * @param machine                  the machine the event was recorded against; may be {@code null}
 * @param type                     the preventive/corrective classification; may be {@code null}
 * @param start                    the start timestamp; may be {@code null}
 * @param end                      the end timestamp; may be {@code null}
 * @param action                   the maintenance action performed; may be {@code null} or blank
 * @param componentsReplaced       the components replaced; may be {@code null} (missing), may be
 *                                 empty (none replaced)
 * @param downtime                 the downtime duration; may be {@code null}
 * @param actingTechnicianOrSystem the acting technician or system identifier; may be {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3, 14.4</a>
 */
public record MaintenanceEventSubmission(
        MaintenanceEventId id,
        MachineId machine,
        MaintenanceType type,
        Instant start,
        Instant end,
        String action,
        List<Component> componentsReplaced,
        Duration downtime,
        ActorId actingTechnicianOrSystem) {
}
