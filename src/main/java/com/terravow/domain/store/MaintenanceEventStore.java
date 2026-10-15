package com.terravow.domain.store;

import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEvent;

import java.util.List;

/**
 * The store of {@link MaintenanceEvent}s, validating events at the ingestion boundary.
 *
 * <p>A recorded Maintenance_Event stores its full field set: event type (preventive/corrective),
 * start and end timestamps, the action performed, the components replaced, the downtime, and the
 * acting technician or system (Req 14.3). An event submitted with a missing required field is
 * rejected and the rejection recorded with the missing field (Req 14.4). As with registrations, the
 * outcome is a {@link StoreResult} so ingestion can record the failure and continue (Req 14.14).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3, 14.4</a>
 */
public interface MaintenanceEventStore {

    /**
     * Validates and, if complete, stores a Maintenance_Event (Req 14.3, 14.4).
     *
     * <p>If every required field is present (and {@code action} is non-blank), a
     * {@link MaintenanceEvent} is constructed storing the full field set, recorded against its
     * machine, and returned in a {@link StoreResult.Status#CREATED} result. If a required field is
     * missing, nothing is created and a {@link StoreResult.Status#REJECTED} result naming the first
     * missing field is returned.
     *
     * @param submission the event submission; never {@code null}
     * @return a created-or-rejected result
     * @throws NullPointerException if {@code submission} is {@code null}
     */
    StoreResult<MaintenanceEvent> record(MaintenanceEventSubmission submission);

    /**
     * Returns the Maintenance_Events recorded for a machine, in insertion order.
     *
     * @param machine the machine; never {@code null}
     * @return an unmodifiable list of events for {@code machine}; empty if none
     * @throws NullPointerException if {@code machine} is {@code null}
     */
    List<MaintenanceEvent> forMachine(MachineId machine);
}
