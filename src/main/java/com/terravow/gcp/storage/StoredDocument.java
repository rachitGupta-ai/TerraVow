package com.terravow.gcp.storage;

import com.terravow.evidence.SourceRef;

import java.util.Objects;

/**
 * A document object stored in Cloud Storage as an Evidence_Chain primary source (Req 12.2).
 *
 * <p>Cloud Storage is the design's primary-source store for the regulatory corpus, filings, and
 * other documents that Evidence_Chains cite. Each object is addressed by its {@link SourceRef} —
 * the same opaque, resolvable reference an {@link com.terravow.evidence.EvidenceLink} carries — so a
 * value's provenance link resolves directly to the stored object. The {@code contentType} and
 * {@code content} bytes carry the object payload.
 *
 * <p>The {@code content} is copied defensively so the record is an immutable value; mutating the
 * caller's array after construction does not change the stored document.
 *
 * @param ref         the resolvable primary-source reference addressing this object; never
 *                    {@code null}
 * @param kind        the document family; never {@code null}
 * @param contentType a non-blank MIME type for the object; never {@code null}
 * @param content     the object bytes; never {@code null} (may be empty)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2, 10.1</a>
 */
public record StoredDocument(
        SourceRef ref,
        DocumentKind kind,
        String contentType,
        byte[] content) {

    /**
     * Validates presence of every field, a non-blank content type, and defensively copies the
     * content bytes (Req 12.2).
     *
     * @throws NullPointerException     if any field is {@code null}
     * @throws IllegalArgumentException if {@code contentType} is blank
     */
    public StoredDocument {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        content = content.clone();
    }

    /**
     * Returns a defensive copy of the object bytes so callers cannot mutate the stored payload.
     *
     * @return a copy of the content bytes; never {@code null}
     */
    @Override
    public byte[] content() {
        return content.clone();
    }
}
