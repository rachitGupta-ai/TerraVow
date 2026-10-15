package com.terravow.gcp.storage;

import com.terravow.evidence.SourceRef;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A deterministic in-memory {@link DocumentStore} for local-first operation and tests (Req 12.2).
 *
 * <p>Backs the Evidence_Chain primary-source store with a thread-safe map keyed by
 * {@link SourceRef}, so tests can store representative corpus/filing/primary-source documents and
 * resolve them back by the same reference an Evidence_Chain link carries. It holds no live Cloud
 * Storage connection and performs no network I/O; the design routes this seam to real Cloud Storage
 * in a later wiring task. The store is self-contained and references no other capability package.
 *
 * @see DocumentStore
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2</a>
 */
public final class InMemoryDocumentStore implements DocumentStore {

    private final ConcurrentMap<SourceRef, StoredDocument> byRef = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void put(StoredDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        byRef.put(document.ref(), document);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<StoredDocument> get(SourceRef ref) {
        Objects.requireNonNull(ref, "ref must not be null");
        return Optional.ofNullable(byRef.get(ref));
    }

    /**
     * Returns the number of documents currently stored. Intended for tests.
     *
     * @return the count of stored documents
     */
    public int size() {
        return byRef.size();
    }
}
