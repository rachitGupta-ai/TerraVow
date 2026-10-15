package com.terravow.domain;

/**
 * The classification of a {@link MaintenanceEvent}.
 *
 * <p>Every Maintenance_Event is classified as preventive (scheduled) or corrective
 * (unscheduled/breakdown) (Req 14.3). The classification distinguishes planned maintenance from
 * reactive repair when deriving health state and maintenance effectiveness.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3</a>
 */
public enum MaintenanceType {
    /** Scheduled, planned maintenance. */
    PREVENTIVE,
    /** Unscheduled maintenance in response to a breakdown or fault. */
    CORRECTIVE
}
