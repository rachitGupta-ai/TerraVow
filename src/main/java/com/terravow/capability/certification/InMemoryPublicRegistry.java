package com.terravow.capability.certification;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory {@link PublicRegistry} for local-first operation and tests (Req 9.5, 9.6).
 *
 * <p>Backs public registry publication with a thread-safe map keyed by certificate reference. Only
 * issued certificates are ever published through this adapter (Req 9.5); the certification logic
 * does not publish withheld facilities, so a withheld facility is simply absent from the map
 * (Req 9.6). The design routes the registry to Firestore in a later storage task (Req 12.2); this
 * adapter lets the certification logic be implemented and tested now. It is self-contained and
 * holds no reference to other capability packages.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.5, 9.6, 12.2</a>
 */
public final class InMemoryPublicRegistry implements PublicRegistry {

    private final ConcurrentMap<String, RegistryEntry> byReference = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void publish(RegistryEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        byReference.put(entry.certificateReference(), entry);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<RegistryEntry> findByReference(String certificateReference) {
        Objects.requireNonNull(certificateReference, "certificateReference must not be null");
        return Optional.ofNullable(byReference.get(certificateReference));
    }

    /**
     * Returns the number of entries currently published. Intended for tests.
     *
     * @return the count of published registry entries
     */
    public int size() {
        return byReference.size();
    }
}
