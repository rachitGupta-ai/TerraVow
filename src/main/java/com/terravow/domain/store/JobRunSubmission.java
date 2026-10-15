package com.terravow.domain.store;

import com.terravow.domain.BatchId;
import com.terravow.domain.JobId;
import com.terravow.domain.JobRun;
import com.terravow.domain.MachineId;
import com.terravow.domain.OperatingParameters;

import java.time.Instant;
import java.util.OptionalDouble;

/**
 * A raw, possibly-incomplete Job_Run submission validated by the {@link JobRunStore}.
 *
 * <p>Unlike {@link JobRun} — whose compact constructor rejects any {@code null} required reference —
 * this submission permits {@code null} fields so the store can detect a missing required field,
 * reject the run, and record the missing field, consistent with the ingestion-boundary rule applied
 * to registrations and events (Req 14.4, 14.7). The required fields are {@code id}, {@code machine},
 * {@code start}, {@code end}, {@code batch}, {@code outputQuantity}, and {@code params}; the Job_Run
 * is linked to {@code batch} on creation (Req 14.7). The output quantity is an {@link OptionalDouble}
 * so a submission that omits it is distinguishable from one that supplies {@code 0.0}.
 *
 * <p>This is an inert data carrier: construction never throws on a missing field; validation is the
 * store's responsibility.
 *
 * @param id             the job identifier; may be {@code null} (missing)
 * @param machine        the machine the run executed on; may be {@code null}
 * @param start          the start timestamp; may be {@code null}
 * @param end            the end timestamp; may be {@code null}
 * @param batch          the batch produced by the run; may be {@code null}
 * @param outputQuantity the output quantity produced; may be {@link OptionalDouble#empty()}
 *                       (missing)
 * @param params         the operating parameters; may be {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.4, 14.7</a>
 */
public record JobRunSubmission(
        JobId id,
        MachineId machine,
        Instant start,
        Instant end,
        BatchId batch,
        OptionalDouble outputQuantity,
        OperatingParameters params) {

    /**
     * Compact constructor normalising a {@code null} {@code outputQuantity} wrapper to
     * {@link OptionalDouble#empty()} so callers may pass {@code null} to mean "missing".
     */
    public JobRunSubmission {
        if (outputQuantity == null) {
            outputQuantity = OptionalDouble.empty();
        }
    }
}
