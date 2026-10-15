package com.terravow.capability.certification;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.time.Instant;
import java.util.Objects;

/**
 * An issued Trajectory Certification certificate for a facility.
 *
 * <p>When a verdict is issued, TerraVow persists the certificate, its confidence value, and its
 * Evidence_Chain (Req 9.4), and publishes the certified facility identity, the certificate
 * reference, the verdict, and the certificate validity window to the public global registry
 * (Req 9.5). This record carries exactly those fields so a single value can be both stored and
 * published.
 *
 * @param reference    a stable, non-blank certificate reference; never {@code null} (Req 9.5)
 * @param facility     the certified facility identity; never {@code null} (Req 9.5)
 * @param validFrom    the start of the certificate validity window; never {@code null} (Req 9.5)
 * @param validUntil   the end of the certificate validity window; never {@code null}, must be
 *                     strictly after {@code validFrom} (Req 9.5)
 * @param confidence   the confidence attached to the issued verdict; never {@code null} (Req 9.4)
 * @param evidence     the Evidence_Chain backing the verdict; never {@code null} (Req 9.4)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.4, 9.5</a>
 */
public record Certificate(
        String reference,
        FacilityId facility,
        Instant validFrom,
        Instant validUntil,
        Confidence confidence,
        EvidenceChain evidence) {

    /**
     * Validates that every field is present, the reference is non-blank, and the validity window is
     * well-formed (end strictly after start) (Req 9.4, 9.5).
     *
     * @throws NullPointerException     if any field is {@code null}
     * @throws IllegalArgumentException if {@code reference} is blank or {@code validUntil} is not
     *                                  strictly after {@code validFrom}
     */
    public Certificate {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(facility, "facility must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        Objects.requireNonNull(validUntil, "validUntil must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        if (reference.isBlank()) {
            throw new IllegalArgumentException("certificate reference must not be blank");
        }
        if (!validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException(
                    "validUntil must be strictly after validFrom, got validFrom="
                            + validFrom + ", validUntil=" + validUntil);
        }
    }
}
