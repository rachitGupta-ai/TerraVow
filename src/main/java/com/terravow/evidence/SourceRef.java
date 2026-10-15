package com.terravow.evidence;

import java.util.Objects;

/**
 * An opaque, resolvable reference to a primary source.
 *
 * <p>An {@link EvidenceLink} pairs a {@link SourceType} with a {@code SourceRef} that locates the
 * concrete primary source — e.g. a Cloud Storage object for a regulatory text, a reading id, or an
 * Operating_Log entry id. The reference is a non-blank identifier; resolution to the stored source
 * is performed by the Evidence/Audit service.
 *
 * @param id a non-blank, resolvable identifier of the primary source
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.1, 10.5</a>
 */
public record SourceRef(String id) {

    /**
     * Validates that the reference identifier is present and non-blank (Req 10.1).
     *
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public SourceRef {
        Objects.requireNonNull(id, "SourceRef id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("SourceRef id must not be blank");
        }
    }
}
