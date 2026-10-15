package com.terravow.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.ActorId;
import com.terravow.domain.BatchId;
import com.terravow.domain.Component;
import com.terravow.domain.JobId;
import com.terravow.domain.JobRun;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEvent;
import com.terravow.domain.MaintenanceEventId;
import com.terravow.domain.MaintenanceType;
import com.terravow.domain.OperatingParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.time.api.DateTimes;

/**
 * Property-based tests for the Maintenance_Event / Job_Run store round-trip.
 *
 * <p>Validates Correctness Property 41: for any valid {@link MaintenanceEventSubmission} or
 * {@link JobRunSubmission}, recording it through {@link DefaultMaintenanceEventStore} /
 * {@link DefaultJobRunStore} and reading it back yields every stored field unchanged, and the
 * produced Job_Run is retrievable by the batch it produced (Req 14.3, 14.7). Reads are performed
 * through the stores' own query surface ({@link MaintenanceEventStore#forMachine},
 * {@link JobRunStore#forMachine}, {@link JobRunStore#forBatch}) so the test exercises the stored
 * field set rather than the submission it was built from.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3, 14.7</a>
 */
class MaintenanceJobRoundTripProperties {

    // Feature: terravow, Property 41: Maintenance and job records round-trip their fields.
    @Property(tries = 100)
    void maintenanceEventRoundTripsEveryField(
            @ForAll("maintenanceSubmissions") MaintenanceEventSubmission submission) {
        DefaultMaintenanceEventStore store = new DefaultMaintenanceEventStore();

        StoreResult<MaintenanceEvent> result = store.record(submission);

        assertThat(result.isCreated()).isTrue();
        MaintenanceEvent stored = store.forMachine(submission.machine()).get(0);
        assertThat(stored.id()).isEqualTo(submission.id());
        assertThat(stored.machine()).isEqualTo(submission.machine());
        assertThat(stored.type()).isEqualTo(submission.type());
        assertThat(stored.start()).isEqualTo(submission.start());
        assertThat(stored.end()).isEqualTo(submission.end());
        assertThat(stored.action()).isEqualTo(submission.action());
        assertThat(stored.componentsReplaced())
                .containsExactlyElementsOf(submission.componentsReplaced());
        assertThat(stored.downtime()).isEqualTo(submission.downtime());
        assertThat(stored.actingTechnicianOrSystem())
                .isEqualTo(submission.actingTechnicianOrSystem());
    }

    // Feature: terravow, Property 41: Maintenance and job records round-trip their fields.
    @Property(tries = 100)
    void jobRunRoundTripsEveryFieldAndLinksBatch(
            @ForAll("jobSubmissions") JobRunSubmission submission) {
        DefaultJobRunStore store = new DefaultJobRunStore();

        StoreResult<JobRun> result = store.record(submission);

        assertThat(result.isCreated()).isTrue();
        JobRun created = result.created().orElseThrow();
        assertThat(created.id()).isEqualTo(submission.id());
        assertThat(created.machine()).isEqualTo(submission.machine());
        assertThat(created.start()).isEqualTo(submission.start());
        assertThat(created.end()).isEqualTo(submission.end());
        assertThat(created.batch()).isEqualTo(submission.batch());
        assertThat(created.outputQuantity()).isEqualTo(submission.outputQuantity().getAsDouble());
        assertThat(created.params()).isEqualTo(submission.params());

        // Read back through the store's own query surface — every field survives the round-trip.
        assertThat(store.forMachine(submission.machine())).containsExactly(created);

        // Req 14.7 — the run is retrievable by the batch it produced.
        assertThat(store.forBatch(submission.batch())).contains(created);
    }

    @Provide
    Arbitrary<MaintenanceEventSubmission> maintenanceSubmissions() {
        Arbitrary<MaintenanceEventId> ids =
                identifierStrings().map(MaintenanceEventId::new);
        Arbitrary<MachineId> machines = identifierStrings().map(MachineId::new);
        Arbitrary<MaintenanceType> types = Arbitraries.of(MaintenanceType.class);
        Arbitrary<Instant> starts = instants();
        Arbitrary<Instant> ends = instants();
        Arbitrary<String> actions =
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(40);
        Arbitrary<List<Component>> components =
                identifierStrings().map(Component::new).list().ofMaxSize(5);
        Arbitrary<Duration> downtimes =
                Arbitraries.longs().between(0L, 86_400L).map(Duration::ofSeconds);
        Arbitrary<ActorId> actors = identifierStrings().map(ActorId::new);

        return Combinators.combine(ids, machines, types, starts, ends)
                .as(MaintenanceParts::new)
                .flatMap(
                        parts ->
                                Combinators.combine(actions, components, downtimes, actors)
                                        .as(
                                                (action, comps, downtime, actor) ->
                                                        new MaintenanceEventSubmission(
                                                                parts.id(),
                                                                parts.machine(),
                                                                parts.type(),
                                                                parts.start(),
                                                                parts.end(),
                                                                action,
                                                                comps,
                                                                downtime,
                                                                actor)));
    }

    @Provide
    Arbitrary<JobRunSubmission> jobSubmissions() {
        Arbitrary<JobId> ids = identifierStrings().map(JobId::new);
        Arbitrary<MachineId> machines = identifierStrings().map(MachineId::new);
        Arbitrary<Instant> starts = instants();
        Arbitrary<Instant> ends = instants();
        Arbitrary<BatchId> batches = identifierStrings().map(BatchId::new);
        Arbitrary<OptionalDouble> quantities =
                Arbitraries.doubles()
                        .between(0.0, 1_000_000.0)
                        .filter(Double::isFinite)
                        .map(OptionalDouble::of);
        Arbitrary<OperatingParameters> params = operatingParameters();

        return Combinators.combine(ids, machines, starts, ends, batches)
                .as(JobParts::new)
                .flatMap(
                        parts ->
                                Combinators.combine(quantities, params)
                                        .as(
                                                (quantity, p) ->
                                                        new JobRunSubmission(
                                                                parts.id(),
                                                                parts.machine(),
                                                                parts.start(),
                                                                parts.end(),
                                                                parts.batch(),
                                                                quantity,
                                                                p)));
    }

    private Arbitrary<OperatingParameters> operatingParameters() {
        Arbitrary<Double> values =
                Arbitraries.doubles().between(0.0, 100_000.0).filter(Double::isFinite);
        return Combinators.combine(values, values, values).as(OperatingParameters::new);
    }

    /** Non-blank identifier strings accepted by the value-type compact constructors. */
    private Arbitrary<String> identifierStrings() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(24);
    }

    private Arbitrary<Instant> instants() {
        return DateTimes.instants()
                .between(
                        Instant.parse("2000-01-01T00:00:00Z"),
                        Instant.parse("2030-12-31T23:59:59Z"));
    }

    /** Carrier for the first five Maintenance_Event parts (jqwik combines at most eight at once). */
    private record MaintenanceParts(
            MaintenanceEventId id,
            MachineId machine,
            MaintenanceType type,
            Instant start,
            Instant end) {}

    /** Carrier for the first five Job_Run parts (jqwik combines at most eight at once). */
    private record JobParts(
            JobId id, MachineId machine, Instant start, Instant end, BatchId batch) {}
}
