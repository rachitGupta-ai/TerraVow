package com.terravow.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A single production job executed on a {@link MachineAsset}.
 *
 * <p>A Job_Run stores the job identifier, the machine reference, the start and end timestamps, the
 * batch produced, the output quantity, and the operating parameters, and links the run to the batch
 * it produced (Req 14.7). The output quantity and operating parameters are the per-run basis for
 * computing the EID and the Embedded Pollution Passport of the produced batch (Req 14.8). This
 * record holds the full field set; the job store additionally links the run to its produced batch
 * and feeds those per-run inputs forward (task 2.2). The output quantity must be finite and
 * non-negative so emissions-intensity ratios are well-defined.
 *
 * @param id             the job identifier; never {@code null}
 * @param machine        the machine the run executed on; never {@code null}
 * @param start          the start timestamp; never {@code null}
 * @param end            the end timestamp; never {@code null}
 * @param batch          the batch produced by the run; never {@code null}
 * @param outputQuantity the output quantity produced; finite and non-negative
 * @param params         the operating parameters (load, throughput, energy consumed); never
 *                       {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.7, 14.8</a>
 */
public record JobRun(
        JobId id,
        MachineId machine,
        Instant start,
        Instant end,
        BatchId batch,
        double outputQuantity,
        OperatingParameters params) {

    /**
     * Validates that every required reference and timestamp is present (Req 14.7) and that the
     * output quantity is a finite, non-negative number.
     *
     * @throws NullPointerException     if any required reference or timestamp is {@code null}
     * @throws IllegalArgumentException if {@code outputQuantity} is non-finite or negative
     */
    public JobRun {
        Objects.requireNonNull(id, "JobRun id must not be null");
        Objects.requireNonNull(machine, "JobRun machine must not be null");
        Objects.requireNonNull(start, "JobRun start must not be null");
        Objects.requireNonNull(end, "JobRun end must not be null");
        Objects.requireNonNull(batch, "JobRun batch must not be null");
        Objects.requireNonNull(params, "JobRun params must not be null");
        if (Double.isNaN(outputQuantity) || Double.isInfinite(outputQuantity)) {
            throw new IllegalArgumentException(
                    "JobRun outputQuantity must be a finite number, got: " + outputQuantity);
        }
        if (outputQuantity < 0.0) {
            throw new IllegalArgumentException(
                    "JobRun outputQuantity must be non-negative, got: " + outputQuantity);
        }
    }
}
