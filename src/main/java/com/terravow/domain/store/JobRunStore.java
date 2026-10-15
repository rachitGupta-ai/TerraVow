package com.terravow.domain.store;

import com.terravow.domain.BatchId;
import com.terravow.domain.JobRun;
import com.terravow.domain.MachineId;

import java.util.List;
import java.util.Optional;

/**
 * The store of {@link JobRun}s, validating runs at the ingestion boundary and linking each run to
 * the batch it produced.
 *
 * <p>A recorded Job_Run stores its full field set — job identifier, machine reference, start and end
 * timestamps, produced batch, output quantity, and operating parameters — and is linked to the
 * batch it produced (Req 14.7). A run submitted with a missing required field is rejected and the
 * missing field recorded, consistent with the registration/event boundary rule (Req 14.4). The
 * batch link makes {@link #forBatch(BatchId)} the lookup the Embedded Pollution Passport uses to
 * find the run behind a batch (Req 7.1, 14.8).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.4, 14.7,
 *      14.8</a>
 */
public interface JobRunStore {

    /**
     * Validates and, if complete, stores a Job_Run, linking it to its produced batch (Req 14.7).
     *
     * <p>If every required field is present, a {@link JobRun} is constructed storing the full field
     * set, indexed by both machine and produced batch, and returned in a
     * {@link StoreResult.Status#CREATED} result. If a required field is missing, nothing is created
     * and a {@link StoreResult.Status#REJECTED} result naming the first missing field is returned.
     *
     * @param submission the run submission; never {@code null}
     * @return a created-or-rejected result
     * @throws NullPointerException if {@code submission} is {@code null}
     */
    StoreResult<JobRun> record(JobRunSubmission submission);

    /**
     * Returns the Job_Run that produced the given batch, if one is stored (Req 14.7).
     *
     * <p>This is the batch → run link used downstream to assemble a batch's EID and Embedded
     * Pollution Passport inputs (Req 14.8). The latest run recorded for a batch wins if more than
     * one is recorded under the same batch id.
     *
     * @param batch the produced-batch identifier; never {@code null}
     * @return the run that produced {@code batch}, or {@link Optional#empty()} if none
     * @throws NullPointerException if {@code batch} is {@code null}
     */
    Optional<JobRun> forBatch(BatchId batch);

    /**
     * Returns the Job_Runs recorded for a machine, in insertion order.
     *
     * @param machine the machine; never {@code null}
     * @return an unmodifiable list of runs for {@code machine}; empty if none
     * @throws NullPointerException if {@code machine} is {@code null}
     */
    List<JobRun> forMachine(MachineId machine);
}
