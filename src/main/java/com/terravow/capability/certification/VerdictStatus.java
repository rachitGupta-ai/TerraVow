package com.terravow.capability.certification;

/**
 * The outcome category of a Trajectory Certification evaluation (Req 9.1, 9.3, 9.6).
 *
 * <p>Certification is withheld rather than guessed whenever the evidence is too thin or looks
 * manipulated. Only an {@link #ISSUED} verdict produces a persisted certificate and a public
 * registry publication (Req 9.4, 9.5); both withheld statuses keep the facility out of the registry
 * (Req 9.6) and the insufficient-history case additionally routes to human review (Req 9.3).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.1, 9.3, 9.6</a>
 */
public enum VerdictStatus {
    /**
     * A certificate was issued: the verified effort history spanned at least the minimum
     * observation period and showed a sustained improvement trajectory with no gaming signal
     * (Req 9.1).
     */
    ISSUED,

    /**
     * Certification was withheld because the verified effort history spanned less than the
     * configured minimum trajectory observation period; the evaluation is routed to human review
     * (Req 9.3).
     */
    WITHHELD_INSUFFICIENT_HISTORY,

    /**
     * Certification was withheld because the supporting evidence contained contradictory
     * measurement points, unverifiable readings, or a pattern consistent with metric-gaming; the
     * facility is omitted from the public registry and the findings are recorded in the
     * Evidence_Chain (Req 9.6).
     */
    WITHHELD_GAMING
}
