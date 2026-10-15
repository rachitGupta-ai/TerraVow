package com.terravow.domain;

/**
 * The kind of record an {@link OperatingLogEntry} points at.
 *
 * <p>The append-only Operating_Log ties together telemetry, Maintenance_Events, Job_Runs, and
 * Machine_Health_State transitions for a machine (Req 14.11); this enum classifies which of those a
 * given log entry records, so Evidence_Chains can cite the correct category of primary source
 * (Req 14.12).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.11, 14.12</a>
 */
public enum LogRecordType {
    /** A telemetry reading. */
    TELEMETRY,
    /** A {@link MaintenanceEvent}. */
    MAINTENANCE_EVENT,
    /** A {@link JobRun}. */
    JOB_RUN,
    /** A {@link MachineHealthState} transition. */
    HEALTH_STATE_TRANSITION
}
