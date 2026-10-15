package com.terravow.domain;

import com.terravow.evidence.SourceRef;
import java.time.Instant;
import java.util.Objects;

/**
 * A single append-only entry in a {@link MachineAsset}'s Operating_Log.
 *
 * <p>The Operating_Log is an append-only per-machine record that ties together telemetry,
 * Maintenance_Events, Job_Runs, and Machine_Health_State transitions (Req 14.11). Each entry pairs
 * the machine, the time of the record, the {@link LogRecordType}, and a {@link SourceRef} resolving
 * to the underlying primary-source record. Entries supply the Operating_Log records that EID, ERD,
 * and Embedded Pollution Passport values cite in their Evidence_Chains (Req 14.12). This record is
 * an immutable value; the append-only guarantee — never modifying or deleting prior entries — is
 * enforced by the Operating_Log store (task 2.3).
 *
 * @param machine the machine the entry belongs to; never {@code null}
 * @param at      the time of the recorded event; never {@code null}
 * @param type    the kind of record this entry points at; never {@code null}
 * @param record  a resolvable reference to the underlying primary-source record; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.11, 14.12</a>
 */
public record OperatingLogEntry(
        MachineId machine, Instant at, LogRecordType type, SourceRef record) {

    /**
     * Validates that every field is present (Req 14.11, 14.12).
     *
     * @throws NullPointerException if any field is {@code null}
     */
    public OperatingLogEntry {
        Objects.requireNonNull(machine, "OperatingLogEntry machine must not be null");
        Objects.requireNonNull(at, "OperatingLogEntry at must not be null");
        Objects.requireNonNull(type, "OperatingLogEntry type must not be null");
        Objects.requireNonNull(record, "OperatingLogEntry record must not be null");
    }
}
