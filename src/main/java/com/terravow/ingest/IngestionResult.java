package com.terravow.ingest;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of streaming one event through the ingestion pipeline (Req 3.1, 3.3, 3.4, 13.3,
 * 13.4, 15.3).
 *
 * <p>Exactly one of two outcomes holds, captured by {@link Status}:
 *
 * <ul>
 *   <li>{@link Status#TRIAGED} — the event was masked, landed, and triaged in near-real-time; the
 *       {@link #triage()} outcome is present carrying the Pollution Monitor evaluations and/or the
 *       Maintenance Lifecycle triage trigger (Req 3.1, 3.3, 3.4, 15.3).</li>
 *   <li>{@link Status#REJECTED} — masking could not complete, so the event was <em>not</em> landed
 *       and <em>not</em> triaged; the {@link #rejectionReason()} records why (Req 13.4).</li>
 *   <li>{@link Status#DEFERRED} — the continuous query was unavailable, so the masked event was
 *       landed but near-real-time triage was deferred to the batch-micro-poll fallback (Req 12.5,
 *       task 22.2); no triage outcome is present yet.</li>
 * </ul>
 *
 * @param recordId        the correlation id of the ingested event; never {@code null} or blank
 * @param status          the ingestion outcome; never {@code null}
 * @param triage          the near-real-time triage outcome when {@code status == TRIAGED};
 *                        otherwise empty
 * @param rejectionReason the recorded masking-rejection reason when {@code status == REJECTED};
 *                        otherwise empty
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4, 13.4,
 *      15.3</a>
 */
public record IngestionResult(
        String recordId,
        Status status,
        Optional<TriageOutcome> triage,
        Optional<String> rejectionReason) {

    /** The mutually-exclusive outcomes of streaming one event through ingestion. */
    public enum Status {
        /** Masked, landed, and triaged in near-real-time (Req 3.1, 15.3). */
        TRIAGED,
        /** Masking could not complete; nothing landed or triaged (Req 13.4). */
        REJECTED,
        /** Landed, but near-real-time triage deferred to the batch fallback (Req 12.5). */
        DEFERRED
    }

    /**
     * Validates identity and the carried optionals against the status (Req 3.1, 13.4).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code recordId} is blank or the optionals are
     *                                  inconsistent with {@code status}
     */
    public IngestionResult {
        Objects.requireNonNull(recordId, "IngestionResult recordId must not be null");
        Objects.requireNonNull(status, "IngestionResult status must not be null");
        Objects.requireNonNull(triage, "triage Optional must not be null");
        Objects.requireNonNull(rejectionReason, "rejectionReason Optional must not be null");
        if (recordId.isBlank()) {
            throw new IllegalArgumentException("IngestionResult recordId must not be blank");
        }
        if (status == Status.TRIAGED && triage.isEmpty()) {
            throw new IllegalArgumentException("TRIAGED status requires a triage outcome");
        }
        if (status != Status.TRIAGED && triage.isPresent()) {
            throw new IllegalArgumentException("triage must be present only for TRIAGED status");
        }
        if (status == Status.REJECTED && rejectionReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "REJECTED status requires a rejection reason (Req 13.4)");
        }
        if (status != Status.REJECTED && rejectionReason.isPresent()) {
            throw new IllegalArgumentException(
                    "rejectionReason must be present only for REJECTED status");
        }
    }

    /**
     * Builds a triaged result carrying the near-real-time triage outcome (Req 3.1, 15.3).
     *
     * @param triage the triage outcome; never {@code null}
     * @return a {@link Status#TRIAGED} result
     */
    public static IngestionResult triaged(TriageOutcome triage) {
        Objects.requireNonNull(triage, "triage must not be null");
        return new IngestionResult(
                triage.recordId(), Status.TRIAGED, Optional.of(triage), Optional.empty());
    }

    /**
     * Builds a rejected result recording why masking could not complete (Req 13.4).
     *
     * @param recordId the correlation id; never {@code null} or blank
     * @param reason   the non-blank masking-rejection reason; never {@code null}
     * @return a {@link Status#REJECTED} result
     */
    public static IngestionResult rejected(String recordId, String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return new IngestionResult(
                recordId, Status.REJECTED, Optional.empty(), Optional.of(reason));
    }

    /**
     * Builds a deferred result: the event was masked and landed but the continuous query was
     * unavailable, so triage is handed to the batch-micro-poll fallback (Req 12.5, task 22.2).
     *
     * @param recordId the correlation id; never {@code null} or blank
     * @return a {@link Status#DEFERRED} result
     */
    public static IngestionResult deferred(String recordId) {
        return new IngestionResult(recordId, Status.DEFERRED, Optional.empty(), Optional.empty());
    }
}
