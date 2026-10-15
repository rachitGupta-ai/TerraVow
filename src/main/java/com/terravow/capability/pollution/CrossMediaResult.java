package com.terravow.capability.pollution;

import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a Cross_Media_Guardrail assessment of a remediation (Req 3.6, 3.7).
 *
 * <p>When {@link #displacement()} is {@code true}, the remediation reduced pollutant load in at
 * least one environmental medium while increasing it in at least one other, and is flagged as
 * cross-media displacement (Req 3.7). In that case the {@link #supportingReadings()} — the before
 * and after readings that evidence the trade-off — are non-empty and the {@link #evidence()} chain
 * linking to those readings is non-empty, so the flag is always traceable to primary sources
 * (Req 3.7, Property 11). When {@code displacement} is {@code false} the remediation did not
 * relocate harm across media.
 *
 * @param remediation       the assessed remediation; never {@code null}
 * @param displacement      whether cross-media displacement was detected (Req 3.7)
 * @param supportingReadings the before/after readings evidencing the trade-off; defensively copied,
 *                          non-empty when {@code displacement} is {@code true}
 * @param evidence          the Evidence_Chain linking to the supporting readings; non-empty when
 *                          {@code displacement} is {@code true}; never {@code null}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.6, 3.7, 10.1</a>
 */
public record CrossMediaResult(
        Remediation remediation,
        boolean displacement,
        List<Reading> supportingReadings,
        EvidenceChain evidence) {

    /**
     * Validates consistency between {@code displacement} and the supporting evidence (Req 3.7).
     *
     * @throws NullPointerException     if any reference argument is {@code null} or a reading is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code displacement} is {@code true} but the supporting
     *                                  readings or Evidence_Chain are empty
     */
    public CrossMediaResult {
        Objects.requireNonNull(remediation, "remediation must not be null");
        Objects.requireNonNull(supportingReadings, "supportingReadings must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        supportingReadings = List.copyOf(supportingReadings); // rejects nulls, immutable copy
        if (displacement) {
            if (supportingReadings.isEmpty()) {
                throw new IllegalArgumentException(
                        "cross-media displacement requires supporting readings (Req 3.7)");
            }
            if (evidence.links().isEmpty()) {
                throw new IllegalArgumentException(
                        "cross-media displacement requires a non-empty Evidence_Chain (Req 3.7)");
            }
        }
    }
}
