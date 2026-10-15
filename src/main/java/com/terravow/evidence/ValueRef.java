package com.terravow.evidence;

import java.util.Objects;

/**
 * A reference to an identified produced value, used to look up that value's provenance.
 *
 * <p>A user requests the provenance of a produced value by its {@code ValueRef}; the Evidence
 * service returns the complete {@link EvidenceChain} stored for it (Req 10.5), or a not-found
 * result that leaves the audit trail unchanged when no chain exists (Req 10.6). The reference is a
 * non-blank, opaque identifier.
 *
 * @param id a non-blank identifier of the produced value
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.5, 10.6</a>
 */
public record ValueRef(String id) {

    /**
     * Validates that the identifier is present and non-blank (Req 10.5).
     *
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public ValueRef {
        Objects.requireNonNull(id, "ValueRef id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ValueRef id must not be blank");
        }
    }
}
