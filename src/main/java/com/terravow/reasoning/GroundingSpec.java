package com.terravow.reasoning;

import java.util.Objects;

/**
 * Specifies the managed retrieval store and mode a reasoning request grounds interpretive values on
 * (Req 6.1, 10.1, 11.1).
 *
 * <p>Pairs a {@link DataStoreRef} with a {@link GroundingMode} (Vertex AI Search or BigQuery vector
 * search). Grounding operates only over already-masked data; the retrieved passages and their
 * citations are folded into the Evidence_Chain so every grounded value resolves to a primary source
 * (Req 10.1).
 *
 * @param store the grounding data store reference; never {@code null}
 * @param mode  the managed retrieval mode; never {@code null}
 *
 * @see GroundingCitation
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 10.1, 11.1</a>
 */
public record GroundingSpec(DataStoreRef store, GroundingMode mode) {

    /**
     * Validates that the store reference and mode are present.
     *
     * @throws NullPointerException if {@code store} or {@code mode} is {@code null}
     */
    public GroundingSpec {
        Objects.requireNonNull(store, "GroundingSpec store must not be null");
        Objects.requireNonNull(mode, "GroundingSpec mode must not be null");
    }
}
