package com.terravow.domain;

import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory, append-only implementation of the {@link OperatingLog}.
 *
 * <p>Maintains one append-ordered list of {@link OperatingLogEntry} per {@link MachineId}. Appends
 * add to the tail of the per-machine list; nothing in this class ever rewrites or removes an
 * existing entry, so the append-only guarantee (Req 14.11) holds by construction. Reads return
 * immutable snapshots, so a caller cannot reach back through the API to mutate logged history.
 *
 * <p>Timestamps come from an injected {@link Clock}, keeping append order deterministic under test
 * and avoiding hidden wall-clock reads.
 *
 * <p>Durable landing (BigQuery continuous-query tables) is a later concern (task 23); this
 * implementation is the in-memory provenance spine the domain core and capability services build
 * on. Backing stores are concurrency-safe ({@link ConcurrentHashMap} of
 * {@link CopyOnWriteArrayList}) so concurrent appends across threads cannot corrupt or drop prior
 * entries.
 *
 * @see OperatingLog
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.11, 14.12</a>
 */
public final class DefaultOperatingLog implements OperatingLog {

    /** Prefix for Operating_Log references derived from a telemetry reading reference. */
    static final String TELEMETRY_REF_PREFIX = "oplog:telemetry:";

    /** Prefix for Operating_Log references derived from a {@link MaintenanceEvent} identifier. */
    static final String MAINTENANCE_REF_PREFIX = "oplog:maintenance:";

    /** Prefix for Operating_Log references derived from a {@link JobRun} identifier. */
    static final String JOB_RUN_REF_PREFIX = "oplog:jobrun:";

    /**
     * Prefix for Operating_Log references derived from a {@link MachineHealthState} transition.
     */
    static final String HEALTH_STATE_REF_PREFIX = "oplog:health:";

    private final Clock clock;
    private final Map<MachineId, List<OperatingLogEntry>> entriesByMachine =
            new ConcurrentHashMap<>();

    /**
     * Creates an Operating_Log that stamps entries from the given clock (Req 14.11).
     *
     * @param clock the time source for append timestamps; never {@code null}
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    public DefaultOperatingLog(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public OperatingLogEntry appendTelemetry(MachineId machine, SourceRef reading) {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(reading, "reading must not be null");
        SourceRef record = new SourceRef(TELEMETRY_REF_PREFIX + reading.id());
        return append(machine, LogRecordType.TELEMETRY, record);
    }

    @Override
    public OperatingLogEntry appendMaintenanceEvent(MaintenanceEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        SourceRef record = new SourceRef(MAINTENANCE_REF_PREFIX + event.id().value());
        return append(event.machine(), LogRecordType.MAINTENANCE_EVENT, record);
    }

    @Override
    public OperatingLogEntry appendJobRun(JobRun jobRun) {
        Objects.requireNonNull(jobRun, "jobRun must not be null");
        SourceRef record = new SourceRef(JOB_RUN_REF_PREFIX + jobRun.id().value());
        return append(jobRun.machine(), LogRecordType.JOB_RUN, record);
    }

    @Override
    public OperatingLogEntry appendHealthStateTransition(MachineHealthState state) {
        Objects.requireNonNull(state, "state must not be null");
        // A health state carries no identifier of its own; key the reference by machine and the
        // assessment time so the transition is uniquely and deterministically resolvable.
        SourceRef record =
                new SourceRef(
                        HEALTH_STATE_REF_PREFIX
                                + state.machine().value()
                                + ":"
                                + state.assessedAt().toString());
        return append(state.machine(), LogRecordType.HEALTH_STATE_TRANSITION, record);
    }

    /**
     * Appends an entry for the machine at the current clock time, leaving all prior entries
     * untouched (Req 14.11).
     */
    private OperatingLogEntry append(MachineId machine, LogRecordType type, SourceRef record) {
        OperatingLogEntry entry =
                new OperatingLogEntry(machine, Instant.now(clock), type, record);
        entriesByMachine
                .computeIfAbsent(machine, m -> new CopyOnWriteArrayList<>())
                .add(entry);
        return entry;
    }

    @Override
    public List<OperatingLogEntry> entriesFor(MachineId machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        List<OperatingLogEntry> entries = entriesByMachine.get(machine);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public List<EvidenceLink> evidenceLinksFor(MachineId machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        List<OperatingLogEntry> entries = entriesByMachine.get(machine);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<EvidenceLink> links = new ArrayList<>(entries.size());
        for (OperatingLogEntry entry : entries) {
            links.add(new EvidenceLink(SourceType.OPERATING_LOG, entry.record()));
        }
        return List.copyOf(links);
    }

    @Override
    public Optional<Instant> lastEntryTime(MachineId machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        List<OperatingLogEntry> entries = entriesByMachine.get(machine);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(entries.size() - 1).at());
    }
}
