package com.terravow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.SourceRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the append-only guarantee of the {@link OperatingLog}.
 *
 * <p>Validates Correctness Property 44: the Operating_Log is append-only — for an arbitrary
 * sequence of appends, every append adds exactly one new entry to the tail and leaves every
 * earlier entry present and unchanged, so prior entries are never modified or dropped (Req 14.11).
 *
 * <p>The invariant is checked by snapshotting {@link OperatingLog#entriesFor(MachineId)} after each
 * append: each snapshot must be an exact prefix of the next (same entries, same order, growing by
 * one), and the final snapshot must contain exactly as many entries as there were appends, in
 * append order. A fixed {@link Clock} keeps the test deterministic.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.11</a>
 */
class OperatingLogAppendOnlyProperties {

    private static final MachineId MACHINE = new MachineId("machine-1");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

    /**
     * The four kinds of append the Operating_Log supports. Each knows how to perform its append
     * against a given log and return the resulting entry (Req 14.11).
     */
    private enum AppendKind {
        TELEMETRY,
        MAINTENANCE_EVENT,
        JOB_RUN,
        HEALTH_STATE_TRANSITION;

        OperatingLogEntry appendTo(OperatingLog log, int seq) {
            return switch (this) {
                case TELEMETRY -> log.appendTelemetry(MACHINE, new SourceRef("reading-" + seq));
                case MAINTENANCE_EVENT -> log.appendMaintenanceEvent(maintenanceEvent("evt-" + seq));
                case JOB_RUN -> log.appendJobRun(jobRun("job-" + seq));
                case HEALTH_STATE_TRANSITION -> log.appendHealthStateTransition(healthState(seq));
            };
        }
    }

    // Feature: terravow, Property 44: Operating_Log is append-only
    @Property(tries = 100)
    void appendingNeverModifiesOrDropsAnyPriorEntry(
            @ForAll("appendSequences") List<AppendKind> appends) {
        DefaultOperatingLog log = new DefaultOperatingLog(CLOCK);

        List<OperatingLogEntry> appendedInOrder = new ArrayList<>(appends.size());
        List<List<OperatingLogEntry>> snapshots = new ArrayList<>(appends.size());

        for (int k = 0; k < appends.size(); k++) {
            OperatingLogEntry appended = appends.get(k).appendTo(log, k);
            appendedInOrder.add(appended);
            snapshots.add(log.entriesFor(MACHINE));
        }

        // (b) Each snapshot is an exact prefix of the next: append k+1 grows the log by exactly one
        // entry at the tail and leaves entries 0..k present and unchanged (Req 14.11).
        for (int k = 0; k < snapshots.size() - 1; k++) {
            List<OperatingLogEntry> before = snapshots.get(k);
            List<OperatingLogEntry> after = snapshots.get(k + 1);

            assertThat(after).hasSize(before.size() + 1);
            assertThat(after.subList(0, before.size())).isEqualTo(before);
        }

        // (a) After all appends the log holds exactly N entries, in append order, matching the
        // entries each append returned (Req 14.11).
        List<OperatingLogEntry> finalEntries = log.entriesFor(MACHINE);
        assertThat(finalEntries).hasSize(appends.size());
        assertThat(finalEntries).containsExactlyElementsOf(appendedInOrder);
    }

    /** An arbitrary non-empty sequence of appends drawn from the four supported record kinds. */
    @Provide
    Arbitrary<List<AppendKind>> appendSequences() {
        return Arbitraries.of(AppendKind.class).list().ofMinSize(1).ofMaxSize(40);
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
                new OperatingParameters(0.75, 50.0, 120.0));
    }

    private static MachineHealthState healthState(int seq) {
        // Vary assessment time per append so each health-state reference is unique and resolvable.
        Instant assessedAt = Instant.parse("2025-01-01T00:00:00Z").plusSeconds(seq);
        return new MachineHealthState(
                MACHINE, assessedAt, 0.8, List.of(), assessedAt, new Confidence(0.9));
    }
}
