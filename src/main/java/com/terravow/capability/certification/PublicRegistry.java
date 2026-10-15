package com.terravow.capability.certification;

import java.util.Optional;

/**
 * The public global registry port for Trajectory Certifications (Req 9.5, 9.6).
 *
 * <p>Only issued certificates are published to the public global registry (Req 9.5); facilities
 * whose evidence is contradictory, unverifiable, or consistent with metric-gaming are withheld and
 * omitted from the registry entirely (Req 9.6). The certification logic therefore publishes only on
 * an issued verdict and never on a withheld one. This interface is the seam to the concrete
 * registry; the design routes it to Firestore (Req 12.2), wired in a later storage task. An
 * in-memory adapter ({@link InMemoryPublicRegistry}) is provided for local-first operation and
 * tests.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.5, 9.6, 12.2</a>
 */
public interface PublicRegistry {

    /**
     * Publishes a registry entry for an issued certificate (Req 9.5), keyed by its
     * {@link RegistryEntry#certificateReference()}. An existing entry with the same reference is
     * replaced.
     *
     * @param entry the entry to publish; never {@code null}
     * @throws NullPointerException if {@code entry} is {@code null}
     */
    void publish(RegistryEntry entry);

    /**
     * Looks up a published registry entry by its certificate reference.
     *
     * @param certificateReference the certificate reference; never {@code null}
     * @return the published entry, or {@link Optional#empty()} if the facility is not in the
     *         registry (e.g. a withheld certification, Req 9.6)
     * @throws NullPointerException if {@code certificateReference} is {@code null}
     */
    Optional<RegistryEntry> findByReference(String certificateReference);
}
