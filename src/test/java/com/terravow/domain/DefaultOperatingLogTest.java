package com.terravow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultOperatingLog}: the append-only, per-machine Operating_Log.
 *
 * <p>Covers appending each of the four record kinds — telemetry, {@link MaintenanceEvent},
 * {@link JobRun}, and {@link MachineHealthState} transitions (Req 14.11) — the append-only
 * guarantee that prior entries are never modified or removed (Req 14.11), the immutability of
 * returned snapshots, and the surfacing of logged records as Evidence_Chain links of type
 * {@link SourceType#OPERATING_LOG} (Req 14.12).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.11, 14.12</a>
 */
class DefaultOperatingLogTest {

    private static final MachineId MACHINE = new MachineId("machine-1");
    private final Clock clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final DefaultOperatingLog log = new DefaultOperatingLog(clock);

    @Test
    void appendTelemetryRecordsATelemetryEntryForTheMachine() {
        OperatingLogEntry entry = log.appendTelemetry(MACHINE, new SourceRef("reading-7"));

        assertThat(entry.machine()).isEqualTo(MACHINE);
        assertThat(entry.type()).isEqualTo(LogRecordType.TELEMETRY);
        assertThat(entry.at()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(entry.record().id()).contains("reading-7");
        assertThat(log.entriesFor(MACHINE)).containsExactly(entry);
    }

    @Test
    void appendMaintenanceEventRecordsAMaintenanceEntryKeyedByEventId() {
        MaintenanceEvent event = maintenanceEvent("evt-1");

        OperatingLogEntry entry = log.appendMaintenanceEvent(event);

        assertThat(entry.type()).isEqualTo(LogRecordType.MAINTENANCE_EVENT);
        assertThat(entry.machine()).isEqualTo(MACHINE);
        assertThat(entry.record().id()).contains("evt-1");
    }

    @Test
    void appendJobRunRecordsAJobRunEntryKeyedByJobId() {
        JobRun jobRun = jobRun("job-1");

        OperatingLogEntry entry = log.appendJobRun(jobRun);

        assertThat(entry.type()).isEqualTo(LogRecordType.JOB_RUN);
        assertThat(entry.machine()).isEqualTo(MACHINE);
        assertThat(entry.record().id()).contains("job-1");
    }

    @Test
    void appendHealthStateTransitionRecordsAHealthTransitionEntry() {
        MachineHealthState state = healthState();

        OperatingLogEntry entry = log.appendHealthStateTransition(state);

        assertThat(entry.type()).isEqualTo(LogRecordType.HEALTH_STATE_TRANSITION);
        assertThat(entry.machine()).isEqualTo(MACHINE);
        assertThat(entry.record().id()).contains(MACHINE.value());
    }

    @Test
    void appendsAccumulateInOrderAndNeverModifyOrDropPriorEntries() {
        OperatingLogEntry first = log.appendTelemetry(MACHINE, new SourceRef("r1"));
        OperatingLogEntry second = log.appendMaintenanceEvent(maintenanceEvent("evt-1"));
        OperatingLogEntry third = log.appendJobRun(jobRun("job-1"));

        List<OperatingLogEntry> entries = log.entriesFor(MACHINE);

        // Every prior entry remains present and unchanged after subsequent appends (Req 14.11).
        assertThat(entries).containsExactly(first, second, third);
    }

    @Test
    void entriesAreSegregatedPerMachine() {
        MachineId other = new MachineId("machine-2");
        log.appendTelemetry(MACHINE, new SourceRef("r1"));
        log.appendTelemetry(other, new SourceRef("r2"));

        assertThat(log.entriesFor(MACHINE)).hasSize(1);
        assertThat(log.entriesFor(other)).hasSize(1);
        assertThat(log.entriesFor(MACHINE).get(0).record())
                .isNotEqualTo(log.entriesFor(other).get(0).record());
    }

    @Test
    void entriesForReturnsImmutableSnapshotThatCannotMutateTheLog() {
        log.appendTelemetry(MACHINE, new SourceRef("r1"));

        List<OperatingLogEntry> snapshot = log.entriesFor(MACHINE);

        assertThatThrownBy(() -> snapshot.add(snapshot.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        // A later append does not retroactively appear in the earlier snapshot.
        log.appendTelemetry(MACHINE, new SourceRef("r2"));
        assertThat(snapshot).hasSize(1);
        assertThat(log.entriesFor(MACHINE)).hasSize(2);
    }

    @Test
    void entriesForUnknownMachineIsEmpty() {
        assertThat(log.entriesFor(new MachineId("never-logged"))).isEmpty();
    }

    @Test
    void evidenceLinksSurfaceEveryEntryAsAnOperatingLogLinkInOrder() {
        OperatingLogEntry first = log.appendTelemetry(MACHINE, new SourceRef("r1"));
        OperatingLogEntry second = log.appendJobRun(jobRun("job-1"));

        List<EvidenceLink> links = log.evidenceLinksFor(MACHINE);

        assertThat(links)
                .containsExactly(
                        new EvidenceLink(SourceType.OPERATING_LOG, first.record()),
                        new EvidenceLink(SourceType.OPERATING_LOG, second.record()));
        assertThat(links).allSatisfy(l -> assertThat(l.type()).isEqualTo(SourceType.OPERATING_LOG));
    }

    @Test
    void evidenceLinksForUnknownMachineIsEmpty() {
        assertThat(log.evidenceLinksFor(new MachineId("never-logged"))).isEmpty();
    }

    @Test
    void lastEntryTimeReflectsTheMostRecentAppendAndIsEmptyWhenNoEntries() {
        assertThat(log.lastEntryTime(MACHINE)).isEmpty();

        log.appendTelemetry(MACHINE, new SourceRef("r1"));

        assertThat(log.lastEntryTime(MACHINE)).contains(Instant.parse("2025-01-01T00:00:00Z"));
    }

    @Test
    void appendRejectsNullArguments() {
        assertThatThrownBy(() -> log.appendTelemetry(null, new SourceRef("r1")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> log.appendTelemetry(MACHINE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> log.appendMaintenanceEvent(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> log.appendJobRun(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> log.appendHealthStateTransition(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNullClock() {
        assertThatThrownBy(() -> new DefaultOperatingLog(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static MaintenanceEvent maintenanceEvent(String id) {
        return new MaintenanceEvent(
                new MaintenanceEventId(id),
                MACHINE,
                MaintenanceType.PREVENTIVE,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T01:00:00Z"),
                "replace filter",
                List.of(),
                Duration.ofHours(1),
                new ActorId("tech-1"));
    }

    private static JobRun jobRun(String id) {
        return new JobRun(
                new JobId(id),
                MACHINE,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T02:00:00Z"),
                new BatchId("batch-1"),
                100.0,
                sampleParams());
    }

    private static MachineHealthState healthState() {
        return new MachineHealthState(
                MACHINE,
                Instant.parse("2025-01-01T00:00:00Z"),
                0.8,
                List.of(),
                Instant.parse("2025-01-01T00:00:00Z"),
                new Confidence(0.9));
    }

    private static OperatingParameters sampleParams() {
        return new OperatingParameters(0.75, 50.0, 120.0);
    }
}
