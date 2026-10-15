package com.terravow.reasoning;

import java.util.Objects;

/**
 * An opaque reference to a managed grounding data store (Req 6.1, 11.1).
 *
 * <p>Paired with a {@link GroundingMode} in a {@link GroundingSpec}, this locates the Vertex AI
 * Search data store or BigQuery vector index a reasoning request grounds on. The reference is a
 * non-blank identifier; it always designates a store holding already-masked data.
 *
 * @param id a non-blank identifier of the grounding data store
 *
 * @see GroundingSpec
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 11.1</a>
 */
public record DataStoreRef(String id) {

    /**
     * Validates that the identifier is present and non-blank.
     *
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public DataStoreRef {
        Objects.requireNonNull(id, "DataStoreRef id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("DataStoreRef id must not be blank");
        }
    }
}
