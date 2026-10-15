package com.terravow.domain.store;

import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link MaintenanceEventStore} that validates every required field before creating a
 * {@link MaintenanceEvent} and storing its full field set (Req 14.3, 14.4).
 *
 * <p>Validation runs in a fixed order — {@code id}, {@code machine}, {@code type}, {@code start},
 * {@code end}, {@code action} (a blank action counts as missing), {@code componentsReplaced},
 * {@code downtime}, {@code actingTechnicianOrSystem} — so the first missing field is reported
 * deterministically. Only when all are present is the record constructed (the record's own compact
 * constructor remains a structural backstop) and appended to its machine's event list. An empty
 * (non-null) components list is accepted and stored as-is: it records that no components were
 * replaced.
 *
 * <p>Events are held per machine in insertion order; the Firestore/BigQuery persistence split is
 * task 23. A {@link ConcurrentHashMap} of {@link CopyOnWriteArrayList} keeps concurrent recording
 * and reads safe across callers. No wall-clock time or randomness is read.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3, 14.4,
 *      14.14</a>
 */
public final class DefaultMaintenanceEventStore implements MaintenanceEventStore {

    /** Field name recorded when the event identifier is missing (Req 14.4). */
    static final String FIELD_ID = "id";

    /** Field name recorded when the machine reference is missing (Req 14.4). */
    static final String FIELD_MACHINE = "machine";

    /** Field name recorded when the event type is missing (Req 14.4). */
    static final String FIELD_TYPE = "type";

    /** Field name recorded when the start timestamp is missing (Req 14.4). */
    static final String FIELD_START = "start";

    /** Field name recorded when the end timestamp is missing (Req 14.4). */
    static final String FIELD_END = "end";

    /** Field name recorded when the action is missing or blank (Req 14.4). */
    static final String FIELD_ACTION = "action";

    /** Field name recorded when the components-replaced list is missing (Req 14.4). */
    static final String FIELD_COMPONENTS_REPLACED = "componentsReplaced";

    /** Field name recorded when the downtime is missing (Req 14.4). */
    static final String FIELD_DOWNTIME = "downtime";

    /** Field name recorded when the acting technician/system is missing (Req 14.4). */
    static final String FIELD_ACTING_TECHNICIAN_OR_SYSTEM = "actingTechnicianOrSystem";

    private final Map<MachineId, List<MaintenanceEvent>> eventsByMachine = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     *
     * <p>Rejects the first absent required field (Req 14.4); otherwise constructs and stores the
     * event with its full field set (Req 14.3).
     */
    @Override
    public StoreResult<MaintenanceEvent> record(MaintenanceEventSubmission submission) {
        Objects.requireNonNull(submission, "submission must not be null");

        if (submission.id() == null) {
            return StoreResult.rejected(FIELD_ID);
        }
        if (submission.machine() == null) {
            return StoreResult.rejected(FIELD_MACHINE);
        }
        if (submission.type() == null) {
            return StoreResult.rejected(FIELD_TYPE);
        }
        if (submission.start() == null) {
            return StoreResult.rejected(FIELD_START);
        }
        if (submission.end() == null) {
            return StoreResult.rejected(FIELD_END);
        }
        if (submission.action() == null || submission.action().isBlank()) {
            return StoreResult.rejected(FIELD_ACTION);
        }
        if (submission.componentsReplaced() == null) {
            return StoreResult.rejected(FIELD_COMPONENTS_REPLACED);
        }
        if (submission.downtime() == null) {
            return StoreResult.rejected(FIELD_DOWNTIME);
        }
        if (submission.actingTechnicianOrSystem() == null) {
            return StoreResult.rejected(FIELD_ACTING_TECHNICIAN_OR_SYSTEM);
        }

        MaintenanceEvent event =
                new MaintenanceEvent(
                        submission.id(),
                        submission.machine(),
                        submission.type(),
                        submission.start(),
                        submission.end(),
                        submission.action(),
                        submission.componentsReplaced(),
                        submission.downtime(),
                        submission.actingTechnicianOrSystem());
        eventsByMachine
                .computeIfAbsent(event.machine(), m -> new CopyOnWriteArrayList<>())
                .add(event);
        return StoreResult.created(event);
    }

    /** {@inheritDoc} */
    @Override
    public List<MaintenanceEvent> forMachine(MachineId machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        List<MaintenanceEvent> events = eventsByMachine.get(machine);
        return events == null ? List.of() : List.copyOf(events);
    }
}
