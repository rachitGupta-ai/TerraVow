package com.terravow.gcp.storage;

import com.terravow.evidence.SourceRef;

import java.util.Optional;

/**
 * The Cloud Storage repository seam for the regulatory corpus, filings, and document primary
 * sources — the Evidence_Chain primary-source store (Req 12.2).
 *
 * <p>The design routes to Cloud Storage every document an Evidence_Chain can cite: the regulatory
 * corpus indexed by Vertex AI Search, submitted filings, and other primary sources. This interface
 * is the seam between the application and that object store; documents are addressed by their
 * {@link SourceRef} so an Evidence_Chain link resolves directly to its stored object. The design
 * routes this to live Cloud Storage in a later wiring task (Req 12.2); an in-memory adapter
 * ({@link InMemoryDocumentStore}) backs local-first operation and tests.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2, 10.1</a>
 */
public interface DocumentStore {

    /**
     * Stores a document, keyed by its {@link StoredDocument#ref()} (Req 12.2). An existing object
     * stored under the same reference is replaced.
     *
     * @param document the document to store; never {@code null}
     * @throws NullPointerException if {@code document} is {@code null}
     */
    void put(StoredDocument document);

    /**
     * Resolves a stored document by its primary-source reference.
     *
     * @param ref the primary-source reference; never {@code null}
     * @return the stored document, or {@link Optional#empty()} if none is stored under that
     *         reference
     * @throws NullPointerException if {@code ref} is {@code null}
     */
    Optional<StoredDocument> get(SourceRef ref);
}
