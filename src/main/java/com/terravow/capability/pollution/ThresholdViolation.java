package com.terravow.capability.pollution;

import com.terravow.evidence.EvidenceChain;

import java.util.Objects;

/**
 * A raised threshold-violation condition (Req 3.3).
 *
 * <p>When a reading strictly exceeds its applicable regulatory threshold, a threshold-violation is
 * raised carrying every piece of context the requirement mandates: the {@link Pollutant}, the
 * {@link EnvironmentalMedium}, the measured value, the exceeded threshold value, and an
 * {@link EvidenceChain} linking to the reading (Req 3.3). The chain is required to be non-empty so
 * the violation is always traceable to its primary-source reading (Req 10.1, Property 8/9).
 *
 * @param pollutant         the pollutant that exceeded its limit; never {@code null}
 * @param medium            the environmental medium; never {@code null}
 * @param measuredValue     the measured value that exceeded the threshold
 * @param exceededThreshold the regulatory threshold that was exceeded
 * @param evidence          the Evidence_Chain linking to the reading; never {@code null} or empty
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 10.1</a>
 */
public record ThresholdViolation(
        Pollutant pollutant,
        EnvironmentalMedium medium,
        double measuredValue,
        double exceededThreshold,
        EvidenceChain evidence) {

    /**
     * Validates that all context is present and the Evidence_Chain is non-empty (Req 3.3).
     *
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code evidence} has no links
     */
    public ThresholdViolation {
        Objects.requireNonNull(pollutant, "ThresholdViolation pollutant must not be null");
        Objects.requireNonNull(medium, "ThresholdViolation medium must not be null");
        Objects.requireNonNull(evidence, "ThresholdViolation evidence must not be null");
        if (evidence.links().isEmpty()) {
            throw new IllegalArgumentException(
                    "ThresholdViolation evidence must link to the reading (Req 3.3)");
        }
    }
}
