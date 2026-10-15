package com.terravow.capability.passport;

import com.terravow.domain.BatchId;
import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;

/**
 * An Embedded Pollution Passport: the machine-health-adjusted emissions footprint carried by a
 * single produced batch, together with its confidence and provenance (Req 7.1).
 *
 * <p>The footprint is computed from machine-state-conditioned actual values rather than
 * industry-average emission factors (Req 7.3). A downstream batch's Passport is the proportional
 * sum of the embedded footprints of the input batches it consumed (Req 7.2). Every Passport carries
 * a {@link Confidence} in {@code [0,1]} and a non-empty {@link EvidenceChain} that links the
 * footprint to its contributing machine-state readings (Req 7.1); when a required actual is absent a
 * Fleet_Cohort_Baseline portion is used, the confidence is reduced, and the substitution is recorded
 * as an extra link in the chain (Req 7.4).
 *
 * <p>The record enforces its invariants in the compact constructor so an inconsistent Passport (a
 * non-finite or negative footprint) is unrepresentable.
 *
 * @param batch            the batch this Passport belongs to; never {@code null}
 * @param embeddedFootprint the machine-state-conditioned embedded emissions footprint; finite and
 *                          non-negative
 * @param confidence       the confidence attached to the footprint; never {@code null} (Req 7.1)
 * @param evidence         the Evidence_Chain linking the footprint to its contributing readings;
 *                         never {@code null} and never empty (Req 7.1)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.1, 7.2, 7.3, 7.4</a>
 */
public record Passport(
        BatchId batch, double embeddedFootprint, Confidence confidence, EvidenceChain evidence) {

    /**
     * Validates presence of all references and the finite, non-negative footprint invariant
     * (Req 7.1).
     *
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code embeddedFootprint} is non-finite or negative, or if
     *                                  the Evidence_Chain is empty
     */
    public Passport {
        Objects.requireNonNull(batch, "batch must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        if (Double.isNaN(embeddedFootprint) || Double.isInfinite(embeddedFootprint)) {
            throw new IllegalArgumentException(
                    "embeddedFootprint must be a finite number, got: " + embeddedFootprint);
        }
        if (embeddedFootprint < 0.0) {
            throw new IllegalArgumentException(
                    "embeddedFootprint must be non-negative, got: " + embeddedFootprint);
        }
        if (evidence.links().isEmpty()) {
            throw new IllegalArgumentException(
                    "Passport Evidence_Chain must link the footprint to its contributing readings "
                            + "(Req 7.1)");
        }
    }
}
