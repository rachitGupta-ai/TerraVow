package com.terravow.capability.certification;

import java.util.Optional;

/**
 * A persistence port for issued Trajectory Certification certificates (Req 9.4).
 *
 * <p>When a verdict is issued, TerraVow persists the certificate, its confidence value, and its
 * Evidence_Chain (Req 9.4). All of those fields live on {@link Certificate}, so persisting the
 * certificate persists the full set. This interface is the seam between the deterministic
 * certification logic and the concrete store; the design routes certificates to Firestore
 * (Req 12.2), wired in a later storage task. An in-memory adapter ({@link InMemoryCertificateStore})
 * is provided for local-first operation and tests.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.4, 12.2</a>
 */
public interface CertificateStore {

    /**
     * Persists an issued certificate, keyed by its {@link Certificate#reference()} (Req 9.4). An
     * existing certificate with the same reference is replaced.
     *
     * @param certificate the certificate to persist; never {@code null}
     * @throws NullPointerException if {@code certificate} is {@code null}
     */
    void save(Certificate certificate);

    /**
     * Looks up a persisted certificate by its reference.
     *
     * @param reference the certificate reference; never {@code null}
     * @return the stored certificate, or {@link Optional#empty()} if none is stored under that
     *         reference
     * @throws NullPointerException if {@code reference} is {@code null}
     */
    Optional<Certificate> findByReference(String reference);
}
