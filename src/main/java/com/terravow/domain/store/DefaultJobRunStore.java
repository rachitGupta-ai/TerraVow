package com.terravow.domain.store;

import com.terravow.domain.BatchId;
import com.terravow.domain.JobRun;
import com.terravow.domain.MachineId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link JobRunStore} that validates every required field before creating a
 * {@link JobRun}, stores its full field set, and links the run to its produced batch (Req 14.4,
 * 14.7).
 *
 * <p>Validation runs in a fixed order — {@code id}, {@code machine}, {@code start}, {@code end},
 * {@code batch}, {@code outputQuantity}, {@code params} — so the first missing field is reported
 * deterministically. The output quantity is required: a submission whose
 * {@link JobRunSubmission#outputQuantity()} is empty is rejected on that field. When all fields are
 * present the run is constructed (the record's compact constructor additionally enforces a finite,
 * non-negative quantity) and indexed two ways: by machine (insertion-ordered list) and by produced
 * batch (the batch → run link of Req 14.7). The latest run recorded under a batch id wins the
 * batch link.
 *
 * <p>Runs are held in memory; the Firestore/BigQuery persistence split is task 23. Concurrent maps
 * and copy-on-write lists keep concurrent recording and reads safe. No wall-clock time or
 * randomness is read.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.4, 14.7,
 *      14.8</a>
 */
public final class DefaultJobRunStore implements JobRunStore {

    /** Field name recorded when the job identifier is missing (Req 14.4). */
    static final String FIELD_ID = "id";

    /** Field name recorded when the machine reference is missing (Req 14.4). */
    static final String FIELD_MACHINE = "machine";

    /** Field name recorded when the start timestamp is missing (Req 14.4). */
    static final String FIELD_START = "start";

    /** Field name recorded when the end timestamp is missing (Req 14.4). */
    static final String FIELD_END = "end";

    /** Field name recorded when the produced batch is missing (Req 14.4). */
    static final String FIELD_BATCH = "batch";

    /** Field name recorded when the output quantity is missing (Req 14.4). */
    static final String FIELD_OUTPUT_QUANTITY = "outputQuantity";

    /** Field name recorded when the operating parameters are missing (Req 14.4). */
    static final String FIELD_PARAMS = "params";

    private final Map<MachineId, List<JobRun>> runsByMachine = new ConcurrentHashMap<>();
    private final Map<BatchId, JobRun> runByBatch = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     *
     * <p>Rejects the first absent required field (Req 14.4); otherwise constructs the run, indexes
     * it by machine and by produced batch, and returns it (Req 14.7).
     */
    @Override
    public StoreResult<JobRun> record(JobRunSubmission submission) {
        Objects.requireNonNull(submission, "submission must not be null");

        if (submission.id() == null) {
            return StoreResult.rejected(FIELD_ID);
        }
        if (submission.machine() == null) {
            return StoreResult.rejected(FIELD_MACHINE);
        }
        if (submission.start() == null) {
            return StoreResult.rejected(FIELD_START);
        }
        if (submission.end() == null) {
            return StoreResult.rejected(FIELD_END);
        }
        if (submission.batch() == null) {
            return StoreResult.rejected(FIELD_BATCH);
        }
        if (submission.outputQuantity().isEmpty()) {
            return StoreResult.rejected(FIELD_OUTPUT_QUANTITY);
        }
        if (submission.params() == null) {
            return StoreResult.rejected(FIELD_PARAMS);
        }

        JobRun run =
                new JobRun(
                        submission.id(),
                        submission.machine(),
                        submission.start(),
                        submission.end(),
                        submission.batch(),
                        submission.outputQuantity().getAsDouble(),
                        submission.params());
        runsByMachine
                .computeIfAbsent(run.machine(), m -> new CopyOnWriteArrayList<>())
                .add(run);
        // Link the run to the batch it produced (Req 14.7).
        runByBatch.put(run.batch(), run);
        return StoreResult.created(run);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<JobRun> forBatch(BatchId batch) {
        Objects.requireNonNull(batch, "batch must not be null");
        return Optional.ofNullable(runByBatch.get(batch));
    }

    /** {@inheritDoc} */
    @Override
    public List<JobRun> forMachine(MachineId machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        List<JobRun> runs = runsByMachine.get(machine);
        return runs == null ? List.of() : List.copyOf(runs);
    }
}
