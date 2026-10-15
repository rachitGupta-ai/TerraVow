package com.terravow.gcp.storage;

import java.time.Period;
import java.util.Objects;

/**
 * The Firestore retention policy for the audit trail, certificates, and public registry
 * (Req 10.4, 9.5, 12.2).
 *
 * <p>Firestore holds the public global registry, issued certificates, and the decision audit trail
 * (Req 12.2, 9.5). The audit trail must be retained for at least seven years (Req 10.4). This value
 * object carries the configured retention {@link Period} and enforces the floor <em>as an
 * invariant</em>: a policy that would retain for less than seven years cannot be constructed, so no
 * wiring can accidentally weaken the retention guarantee. The retention window is configurable above
 * the floor via {@link #ofYears(int)}; the {@link #sevenYears()} factory yields the minimum
 * compliant policy and is the safe default.
 *
 * @param retention the retention window; never {@code null}, never shorter than seven years
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.4, 9.5, 12.2</a>
 */
public record FirestoreRetentionPolicy(Period retention) {

    /** The regulatory minimum retention: seven years (Req 10.4). */
    public static final Period MINIMUM_RETENTION = Period.ofYears(7);

    /**
     * Validates that the configured retention meets the seven-year floor (Req 10.4).
     *
     * <p>The comparison normalises both periods to whole days (365 days/year, 30 days/month) so a
     * policy expressed in months or days is still checked against the seven-year minimum. This is a
     * conservative, deterministic comparison intended to reject obviously non-compliant windows, not
     * a calendar-exact duration.
     *
     * @throws NullPointerException     if {@code retention} is {@code null}
     * @throws IllegalArgumentException if {@code retention} is shorter than seven years
     */
    public FirestoreRetentionPolicy {
        Objects.requireNonNull(retention, "retention must not be null");
        if (approximateDays(retention) < approximateDays(MINIMUM_RETENTION)) {
            throw new IllegalArgumentException(
                    "Firestore audit retention must be at least 7 years (Req 10.4), got: "
                            + retention);
        }
    }

    /**
     * The minimum compliant retention policy: exactly seven years. This is the safe default
     * (Req 10.4).
     *
     * @return a seven-year retention policy
     */
    public static FirestoreRetentionPolicy sevenYears() {
        return new FirestoreRetentionPolicy(MINIMUM_RETENTION);
    }

    /**
     * A retention policy of the given whole number of years, which must be at least seven
     * (Req 10.4).
     *
     * @param years the retention in whole years; must be {@code >= 7}
     * @return a retention policy of {@code years} years
     * @throws IllegalArgumentException if {@code years < 7}
     */
    public static FirestoreRetentionPolicy ofYears(int years) {
        return new FirestoreRetentionPolicy(Period.ofYears(years));
    }

    /**
     * Reports whether this policy satisfies the seven-year regulatory floor. By construction this is
     * always {@code true}; it exists so callers and tests can assert the guarantee explicitly
     * (Req 10.4).
     *
     * @return {@code true} if the retention is at least seven years
     */
    public boolean meetsRegulatoryMinimum() {
        return approximateDays(retention) >= approximateDays(MINIMUM_RETENTION);
    }

    private static long approximateDays(Period period) {
        return (long) period.getYears() * 365L
                + (long) period.getMonths() * 30L
                + period.getDays();
    }
}
