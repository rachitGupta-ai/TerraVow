package com.terravow.capability.certification;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory {@link CertificateStore} for local-first operation and tests (Req 9.4).
 *
 * <p>Backs certificate persistence with a thread-safe map keyed by certificate reference. The
 * design routes certificates to Firestore in a later storage task (Req 12.2); this adapter lets the
 * certification logic be implemented and tested now without that dependency. It is deliberately
 * self-contained and holds no reference to other capability packages.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.4, 12.2</a>
 */
public final class InMemoryCertificateStore implements CertificateStore {

    private final ConcurrentMap<String, Certificate> byReference = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void save(Certificate certificate) {
        Objects.requireNonNull(certificate, "certificate must not be null");
        byReference.put(certificate.reference(), certificate);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Certificate> findByReference(String reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        return Optional.ofNullable(byReference.get(reference));
    }

    /**
     * Returns the number of certificates currently persisted. Intended for tests.
     *
     * @return the count of stored certificates
     */
    public int size() {
        return byReference.size();
    }
}
