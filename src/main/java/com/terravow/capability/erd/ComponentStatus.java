package com.terravow.capability.erd;

/**
 * Whether an {@link ErdComponent} could be computed and therefore contributes to the ERD sum.
 *
 * <p>ERD equals the sum of its <em>available</em> components only (Req 2.2). A component that cannot
 * be computed from the available data is excluded from the sum and reported unavailable with a
 * recorded reason (Req 2.6).
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 2.6</a>
 */
public enum ComponentStatus {
    /** The component was computed to a monetary amount and contributes to the ERD sum (Req 2.2). */
    AVAILABLE,
    /** The component could not be computed; excluded from the sum, reason recorded (Req 2.6). */
    UNAVAILABLE
}
