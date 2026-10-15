package com.terravow.capability.pollution;

import java.util.List;

/**
 * The Cross_Media_Guardrail: detects when a remediation reduces pollutant load in one environmental
 * medium while increasing it in another (Req 3.6, 3.7).
 *
 * <p>Given the readings observed before and after a remediation, the guardrail aggregates the total
 * pollutant load per {@link EnvironmentalMedium}, compares before to after, and — if the load
 * decreased in at least one medium while increasing in at least one other — flags the remediation
 * as cross-media displacement, attaching the supporting readings and an Evidence_Chain (Req 3.7).
 * This stops a fix that merely relocates harm from being treated as an improvement.
 *
 * @see DefaultCrossMediaGuardrail
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.6, 3.7</a>
 */
public interface CrossMediaGuardrail {

    /**
     * Assesses whether a remediation displaced pollutant load across environmental media
     * (Req 3.6, 3.7).
     *
     * @param remediation the remediation to assess; never {@code null}
     * @param before      the readings observed before the remediation; never {@code null}, may be
     *                    empty
     * @param after       the readings observed after the remediation; never {@code null}, may be
     *                    empty
     * @return a {@link CrossMediaResult} flagging displacement (with supporting readings and
     *         Evidence_Chain) or confirming none
     * @throws NullPointerException if any argument is {@code null} or contains a {@code null}
     */
    CrossMediaResult assess(Remediation remediation, List<Reading> before, List<Reading> after);
}
