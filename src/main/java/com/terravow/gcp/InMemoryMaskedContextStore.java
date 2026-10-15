package com.terravow.gcp;

import com.terravow.reasoning.CapabilityId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A deterministic in-memory {@link MaskedContextStore} for tests and local development (Req 13.3).
 *
 * <p>Stands in for Agent Engine's Sessions / Memory Bank. It retains, per {@link CapabilityId}, the
 * ordered sequence of masked contexts written to it, so tests can assert that only masked context
 * reaches the store (Req 13.3) and that the correct capability's session received it (Req 11.1). It
 * holds no live Google Cloud connection and performs no network I/O.
 *
 * <p>The store is safe for concurrent use (a {@link ConcurrentHashMap} of
 * {@link CopyOnWriteArrayList}s) but is intentionally minimal — it exists to make the adapter
 * testable, not to replicate Memory Bank semantics.
 *
 * @see MaskedContextStore
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
public final class InMemoryMaskedContextStore implements MaskedContextStore {

    private final Map<CapabilityId, List<Map<String, Object>>> contextsByCapability =
            new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     *
     * <p>Appends an immutable copy of the masked context to the capability's session history.
     *
     * @throws NullPointerException if {@code capability} or {@code maskedContext} is {@code null},
     *                              or {@code maskedContext} contains a {@code null} key/value
     */
    @Override
    public void storeMaskedContext(CapabilityId capability, Map<String, Object> maskedContext) {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(maskedContext, "maskedContext must not be null");
        Map<String, Object> snapshot = Map.copyOf(maskedContext); // rejects null keys/values
        contextsByCapability
                .computeIfAbsent(capability, c -> new CopyOnWriteArrayList<>())
                .add(snapshot);
    }

    /**
     * Returns the ordered masked contexts stored for the given capability.
     *
     * @param capability the capability whose session history to read; never {@code null}
     * @return an immutable list of the stored masked contexts, oldest first; never {@code null}
     */
    public List<Map<String, Object>> contextsFor(CapabilityId capability) {
        Objects.requireNonNull(capability, "capability must not be null");
        return List.copyOf(contextsByCapability.getOrDefault(capability, List.of()));
    }
}
