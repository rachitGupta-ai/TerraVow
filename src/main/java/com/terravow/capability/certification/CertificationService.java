package com.terravow.capability.certification;

import java.time.Period;

/**
 * The Trajectory Certification capability (Req 9).
 *
 * <p>Certifies facilities on their <em>verified improvement trajectory and effort history over
 * time</em> rather than a single gameable audit-day snapshot. An evaluation:
 *
 * <ul>
 *   <li>computes the verdict from the facility rate of improvement and verified effort history
 *       measured across at least the configured minimum trajectory observation period (Req 9.1),
 *       derived from at least two verified measurement points separated across the period rather
 *       than a single snapshot (Req 9.2);</li>
 *   <li>withholds the verdict, reports insufficient history, and routes to human review when the
 *       verified history spans less than the minimum observation period (Req 9.3);</li>
 *   <li>on issue, persists the certificate, its confidence, and its Evidence_Chain (Req 9.4) and
 *       publishes the facility identity, certificate reference, verdict, and validity window to the
 *       public global registry (Req 9.5);</li>
 *   <li>withholds certification, omits the facility from the registry, and records the supporting
 *       findings in the Evidence_Chain when the evidence is contradictory, unverifiable, or
 *       consistent with metric-gaming (Req 9.6).</li>
 * </ul>
 *
 * <p>Implementations are deterministic: given the same facility, effort history, and minimum
 * observation period they produce the same verdict, reading no wall-clock time and using no
 * randomness.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.1, 9.2, 9.3, 9.4,
 *      9.5, 9.6</a>
 */
public interface CertificationService {

    /**
     * Evaluates a facility for Trajectory Certification over its verified effort history (Req 9).
     *
     * <p>Side effects occur only on an {@link VerdictStatus#ISSUED} verdict: the certificate is
     * persisted to the {@link CertificateStore} (Req 9.4) and an entry is published to the
     * {@link PublicRegistry} (Req 9.5). Withheld verdicts persist and publish nothing (Req 9.6).
     *
     * @param facility       the facility to evaluate; never {@code null}
     * @param history        the facility's verified effort history; never {@code null}
     * @param minObservation the configured minimum trajectory observation period; never
     *                       {@code null}, must be positive (Req 9.1, 9.3)
     * @return the certification verdict (issued or withheld)
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code minObservation} is not positive, or {@code history}
     *                                  belongs to a different facility
     */
    CertificationVerdict evaluate(FacilityId facility, EffortHistory history, Period minObservation);
}
