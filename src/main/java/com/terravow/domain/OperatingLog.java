package com.terravow.domain;

import com.terravow.evidence.EvidenceLink;
import java.time.Instant;
import java.util.List;

/**
 * The append-only, per-machine Operating_Log.
 *
 * <p>The Operating_Log is the domain provenance spine: an append-only per-machine record that ties
 * together telemetry, {@link MaintenanceEvent}s, {@link JobRun}s, and {@link MachineHealthState}
 * transitions so that every EID, ERD, and Embedded Pollution Passport value can be traced to the
 * source records it was derived from (Req 14.11, 14.12).
 *
 * <p><strong>Append-only by construction.</strong> This contract exposes only append and read
 * operations — there is deliberately no update or delete method. Once appended, a prior
 * {@link OperatingLogEntry} is never modified or removed (Req 14.11); each append yields a new entry
 * and leaves every earlier entry present and unchanged. The structural absence of mutators is the
 * safety guarantee, so callers and tests can rely on it rather than re-checking it.
 *
 * <p><strong>Evidence surfacing.</strong> {@link #evidenceLinksFor(MachineId)} exposes the logged
 * records as {@link EvidenceLink}s of type {@link com.terravow.evidence.SourceType#OPERATING_LOG},
 * ready to be folded into the Evidence_Chain of any value derived from them (Req 14.12).
 *
 * <p>Append timestamps are taken from an injected {@link java.time.Clock}, so logging is
 * deterministic under test and free of hidden wall-clock reads.
 *
 * @see OperatingLogEntry
 * @see LogRecordType
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.11, 14.12</a>
 */
public interface OperatingLog {

    /**
     * Appends a telemetry record to the machine's Operating_Log (Req 14.11).
     *
     * <p>The entry is stamped {@link LogRecordType#TELEMETRY} at the current clock time and points
     * at the supplied telemetry primary source. Prior entries are left unchanged.
     *
     * @param machine the machine the telemetry belongs to; never {@code null}
     * @param reading a resolvable reference to the telemetry reading; never {@code null}
     * @return the appended entry
     * @throws NullPointerException if any argument is {@code null}
     */
    OperatingLogEntry appendTelemetry(MachineId machine, com.terravow.evidence.SourceRef reading);

    /**
     * Appends a {@link MaintenanceEvent} record to the machine's Operating_Log (Req 14.11).
     *
     * <p>The entry is stamped {@link LogRecordType#MAINTENANCE_EVENT} at the current clock time and
     * references the event by its identifier. Prior entries are left unchanged.
     *
     * @param event the maintenance event to log; never {@code null}
     * @return the appended entry
     * @throws NullPointerException if {@code event} is {@code null}
     */
    OperatingLogEntry appendMaintenanceEvent(MaintenanceEvent event);

    /**
     * Appends a {@link JobRun} record to the machine's Operating_Log (Req 14.11).
     *
     * <p>The entry is stamped {@link LogRecordType#JOB_RUN} at the current clock time and references
     * the run by its identifier. Prior entries are left unchanged.
     *
     * @param jobRun the job run to log; never {@code null}
     * @return the appended entry
     * @throws NullPointerException if {@code jobRun} is {@code null}
     */
    OperatingLogEntry appendJobRun(JobRun jobRun);

    /**
     * Appends a {@link MachineHealthState} transition record to the machine's Operating_Log (Req
     * 14.11).
     *
     * <p>The entry is stamped {@link LogRecordType#HEALTH_STATE_TRANSITION} at the current clock
     * time and references the transitioned-into health state. Prior entries are left unchanged.
     *
     * @param state the health state transitioned into; never {@code null}
     * @return the appended entry
     * @throws NullPointerException if {@code state} is {@code null}
     */
    OperatingLogEntry appendHealthStateTransition(MachineHealthState state);

    /**
     * Returns the machine's Operating_Log entries in append order (Req 14.12).
     *
     * <p>The returned list is an immutable snapshot: it reflects the entries present at call time
     * and cannot be used to mutate the log. A machine with no appended entries yields an empty list.
     *
     * @param machine the machine whose log to read; never {@code null}
     * @return the entries for the machine, oldest first; never {@code null}, possibly empty
     * @throws NullPointerException if {@code machine} is {@code null}
     */
    List<OperatingLogEntry> entriesFor(MachineId machine);

    /**
     * Returns the machine's Operating_Log records as Evidence_Chain links (Req 14.12).
     *
     * <p>Each logged entry is surfaced as an {@link EvidenceLink} of type
     * {@link com.terravow.evidence.SourceType#OPERATING_LOG} wrapping the entry's primary-source
     * reference, in append order. These are the records an EID, ERD, or Embedded Pollution Passport
     * value includes in its Evidence_Chain (Req 14.12).
     *
     * @param machine the machine whose log to expose; never {@code null}
     * @return the Operating_Log evidence links, oldest first; never {@code null}, possibly empty
     * @throws NullPointerException if {@code machine} is {@code null}
     */
    List<EvidenceLink> evidenceLinksFor(MachineId machine);

    /**
     * Returns the time of the most recently appended entry for the machine, if any.
     *
     * <p>Exposed for diagnostics and ordering checks; it reads existing entries only and never
     * mutates the log.
     *
     * @param machine the machine whose log to inspect; never {@code null}
     * @return the latest append {@link Instant}, or empty if the machine has no entries
     * @throws NullPointerException if {@code machine} is {@code null}
     */
    java.util.Optional<Instant> lastEntryTime(MachineId machine);
}
