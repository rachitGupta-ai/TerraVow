package com.terravow.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.BatchId;
import com.terravow.domain.JobId;
import com.terravow.domain.JobRun;
import com.terravow.domain.MachineId;
import com.terravow.domain.OperatingParameters;

import java.time.Instant;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultJobRunStore}: a complete run stores its full field set and is linked
 * to its produced batch, a run missing a required field is rejected with the field recorded, and a
 * missing output quantity is distinguished from a zero quantity (Req 14.4, 14.7, 14.8).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.4, 14.7, 14.8</a>
 */
class DefaultJobRunStoreTest {

    private final DefaultJobRunStore store = new DefaultJobRunStore();

    private static final MachineId MACHINE = new MachineId("machine-1");
    private static final BatchId BATCH = new BatchId("batch-1");
    private static final Instant START = Instant.parse("2024-02-01T06:00:00Z");
    private static final Instant END = Instant.parse("2024-02-01T07:00:00Z");
    private static final OperatingParameters PARAMS = new OperatingParameters(0.8, 120.0, 45.0);

    private static JobRunSubmission complete() {
        return new JobRunSubmission(
                new JobId("job-1"), MACHINE, START, END, BATCH, OptionalDouble.of(100.0), PARAMS);
    }

    // Req 14.7 — a complete run stores the full field set and links to its produced batch.
    @Test
    void completeRunStoresFullFieldSetAndLinksBatch() {
        StoreResult<JobRun> result = store.record(complete());

        assertThat(result.isCreated()).isTrue();
        JobRun run = result.created().orElseThrow();
        assertThat(run.id()).isEqualTo(new JobId("job-1"));
        assertThat(run.machine()).isEqualTo(MACHINE);
        assertThat(run.start()).isEqualTo(START);
        assertThat(run.end()).isEqualTo(END);
        assertThat(run.batch()).isEqualTo(BATCH);
        assertThat(run.outputQuantity()).isEqualTo(100.0);
        assertThat(run.params()).isEqualTo(PARAMS);
        // Req 14.7 — the batch → run link resolves to the produced run.
        assertThat(store.forBatch(BATCH)).contains(run);
        assertThat(store.forMachine(MACHINE)).containsExactly(run);
    }

    // Req 14.7 — a zero output quantity is a valid, stored value (distinct from "missing").
    @Test
    void zeroOutputQuantityIsStored() {
        StoreResult<JobRun> result =
                store.record(
                        new JobRunSubmission(
                                new JobId("job-2"),
                                MACHINE,
                                START,
                                END,
                                new BatchId("batch-2"),
                                OptionalDouble.of(0.0),
                                PARAMS));

        assertThat(result.isCreated()).isTrue();
        assertThat(result.created().orElseThrow().outputQuantity()).isEqualTo(0.0);
    }

    // Req 14.4 — a missing output quantity is rejected (not treated as zero).
    @Test
    void missingOutputQuantityIsRejected() {
        StoreResult<JobRun> result =
                store.record(
                        new JobRunSubmission(
                                new JobId("job-3"),
                                MACHINE,
                                START,
                                END,
                                BATCH,
                                OptionalDouble.empty(),
                                PARAMS));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.missingField())
                .contains(DefaultJobRunStore.FIELD_OUTPUT_QUANTITY);
        assertThat(store.forBatch(BATCH)).isEmpty();
    }

    // Req 14.4 — a null components (here: null batch) field is rejected by name.
    @Test
    void missingBatchIsRejected() {
        StoreResult<JobRun> result =
                store.record(
                        new JobRunSubmission(
                                new JobId("job-4"),
                                MACHINE,
                                START,
                                END,
                                null,
                                OptionalDouble.of(5.0),
                                PARAMS));

        assertThat(result.missingField()).contains(DefaultJobRunStore.FIELD_BATCH);
    }

    // A null outputQuantity wrapper is normalised to empty by the submission and rejected.
    @Test
    void nullOutputQuantityWrapperIsTreatedAsMissing() {
        JobRunSubmission submission =
                new JobRunSubmission(new JobId("job-5"), MACHINE, START, END, BATCH, null, PARAMS);

        assertThat(submission.outputQuantity()).isEmpty();
        assertThat(store.record(submission).missingField())
                .contains(DefaultJobRunStore.FIELD_OUTPUT_QUANTITY);
    }

    @Test
    void forUnknownBatchIsEmpty() {
        assertThat(store.forBatch(new BatchId("absent"))).isEmpty();
    }
}
