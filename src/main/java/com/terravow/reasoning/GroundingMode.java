package com.terravow.reasoning;

/**
 * The managed retrieval substrate a {@link GroundingSpec} grounds an interpretive value on (Req
 * 6.1, 11.1).
 *
 * <p>Both modes operate only over already-masked data; retrieved passages and their citations feed
 * the Evidence_Chain so every grounded value resolves to a primary source (Req 10.1).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 10.1, 11.1</a>
 */
public enum GroundingMode {
    /** Grounding with Vertex AI Search: semantic + keyword RAG over the regulatory corpus. */
    VERTEX_AI_SEARCH,
    /** BigQuery embeddings + approximate vector search co-located with analytics data. */
    BIGQUERY_VECTOR
}
