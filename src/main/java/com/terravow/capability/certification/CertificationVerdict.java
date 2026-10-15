package com.terravow.capability.certification;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of a Trajectory Certification evaluation (Req 9.1–9.6).
 *
 * <p>Mirrors the design's {@code CertificationVerdict}: a {@link VerdictStatus}, an optional
 * {@link Certificate} present only when {@link VerdictStatus#ISSUED}, a confidence value, a flag
 * recording whether the evaluation was routed to human review, and the Evidence_Chain backing the
 * outcome. The compact constructor enforces the structural coupling between status and certificate
 * so an issued verdict always carries a certificate and a withheld verdict never does.
 *
 * @param status               the outcome category; never {@code null}
 * @param certificate          the issued certificate; present iff {@code status == ISSUED}
 * @param confidence           the confidence in the verdict; never {@code null}
 * @param routedToHumanReview  whether the evaluation was routed to human review (Req 9.3)
 * @param evidence             the Evidence_Chain for the verdict; never {@code null} (Req 9.4, 9.6)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.1, 9.3, 9.4,
 *      9.6</a>
 */
public record CertificationVerdict(
        VerdictStatus status,
        Optional<Certificate> certificate,
        Confidence confidence,
        boolean routedToHumanReview,
        EvidenceChain evidence) {

    /**
     * Validates presence of required fields and the status/certificate coupling: an
     * {@link VerdictStatus#ISSUED} verdict must carry a certificate, and any withheld verdict must
     * not (Req 9.4, 9.6).
     *
     * @throws NullPointerException     if {@code status}, {@code certificate}, {@code confidence},
     *                                  or {@code evidence} is {@code null}
     * @throws IllegalArgumentException if the certificate presence does not match the status
     */
    public CertificationVerdict {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(certificate, "certificate must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        boolean issued = status == VerdictStatus.ISSUED;
        if (issued && certificate.isEmpty()) {
            throw new IllegalArgumentException("an ISSUED verdict must carry a certificate");
        }
        if (!issued && certificate.isPresent()) {
            throw new IllegalArgumentException("a withheld verdict must not carry a certificate");
        }
    }

    /**
     * Factory for an issued verdict (Req 9.1, 9.4).
     *
     * @param certificate the issued certificate; never {@code null}
     * @return an {@link VerdictStatus#ISSUED} verdict carrying the certificate, its confidence, and
     *         its Evidence_Chain, not routed to human review
     */
    public static CertificationVerdict issued(Certificate certificate) {
        Objects.requireNonNull(certificate, "certificate must not be null");
        return new CertificationVerdict(
                VerdictStatus.ISSUED,
                Optional.of(certificate),
                certificate.confidence(),
                false,
                certificate.evidence());
    }

    /**
     * Factory for a verdict withheld because the verified history was shorter than the minimum
     * observation period; the evaluation is routed to human review (Req 9.3).
     *
     * @param confidence the confidence in the withholding decision; never {@code null}
     * @param evidence   the Evidence_Chain recording the insufficient-history finding; never
     *                   {@code null}
     * @return a {@link VerdictStatus#WITHHELD_INSUFFICIENT_HISTORY} verdict routed to human review
     */
    public static CertificationVerdict insufficientHistory(
            Confidence confidence, EvidenceChain evidence) {
        return new CertificationVerdict(
                VerdictStatus.WITHHELD_INSUFFICIENT_HISTORY,
                Optional.empty(),
                confidence,
                true,
                evidence);
    }

    /**
     * Factory for a verdict withheld because the evidence was contradictory, unverifiable, or
     * consistent with metric-gaming; the facility is omitted from the registry and the findings are
     * recorded in the Evidence_Chain (Req 9.6). Such a verdict is routed to human review.
     *
     * @param confidence the confidence in the withholding decision; never {@code null}
     * @param evidence   the Evidence_Chain recording the gaming / contradiction findings; never
     *                   {@code null}
     * @return a {@link VerdictStatus#WITHHELD_GAMING} verdict routed to human review
     */
    public static CertificationVerdict gaming(Confidence confidence, EvidenceChain evidence) {
        return new CertificationVerdict(
                VerdictStatus.WITHHELD_GAMING,
                Optional.empty(),
                confidence,
                true,
                evidence);
    }
}
