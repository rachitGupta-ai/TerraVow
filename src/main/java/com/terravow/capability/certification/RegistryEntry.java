package com.terravow.capability.certification;

import java.time.Instant;
import java.util.Objects;

/**
 * A public global registry entry published for an issued certificate (Req 9.5).
 *
 * <p>When a certificate is issued, TerraVow publishes the certified facility identity, the
 * certificate reference, the verdict, and the certificate validity window to the public global
 * registry (Req 9.5). This record carries exactly those four published fields — it deliberately
 * omits confidence and the Evidence_Chain, which are persisted with the {@link Certificate} but are
 * not part of the public registry projection.
 *
 * @param facility           the certified facility identity; never {@code null}
 * @param certificateReference the published certificate reference; never {@code null}, non-blank
 * @param verdict            the published verdict; never {@code null}
 * @param validFrom          the start of the published validity window; never {@code null}
 * @param validUntil         the end of the published validity window; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.5</a>
 */
public record RegistryEntry(
        FacilityId facility,
        String certificateReference,
        VerdictStatus verdict,
        Instant validFrom,
        Instant validUntil) {

    /**
     * Validates presence of every published field and that the certificate reference is non-blank
     * (Req 9.5).
     *
     * @throws NullPointerException     if any field is {@code null}
     * @throws IllegalArgumentException if {@code certificateReference} is blank
     */
    public RegistryEntry {
        Objects.requireNonNull(facility, "facility must not be null");
        Objects.requireNonNull(certificateReference, "certificateReference must not be null");
        Objects.requireNonNull(verdict, "verdict must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        Objects.requireNonNull(validUntil, "validUntil must not be null");
        if (certificateReference.isBlank()) {
            throw new IllegalArgumentException("certificateReference must not be blank");
        }
    }

    /**
     * Projects an issued {@link Certificate} to its public registry entry (Req 9.5). The verdict is
     * necessarily {@link VerdictStatus#ISSUED} since only issued certificates are published.
     *
     * @param certificate the issued certificate to publish; never {@code null}
     * @return the public registry entry derived from the certificate
     * @throws NullPointerException if {@code certificate} is {@code null}
     */
    public static RegistryEntry from(Certificate certificate) {
        Objects.requireNonNull(certificate, "certificate must not be null");
        return new RegistryEntry(
                certificate.facility(),
                certificate.reference(),
                VerdictStatus.ISSUED,
                certificate.validFrom(),
                certificate.validUntil());
    }
}
