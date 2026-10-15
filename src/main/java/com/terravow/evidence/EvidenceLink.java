package com.terravow.evidence;

import java.util.Objects;

/**
 * A single machine-checkable provenance link: a {@link SourceType} paired with a resolvable
 * {@link SourceRef} to the primary source.
 *
 * <p>An {@link EvidenceChain} is composed of these links; each must resolve to a primary source
 * (Req 10.1). A {@link SourceType#FALLBACK_PATH} link records that a degradation fallback was
 * substituted for a primary path (Req 12.4).
 *
 * @param type the kind of primary source; never {@code null}
 * @param ref  the resolvable reference to that primary source; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.1, 12.4</a>
 */
public record EvidenceLink(SourceType type, SourceRef ref) {

    /**
     * Validates that both the source type and reference are present (Req 10.1).
     *
     * @throws NullPointerException if {@code type} or {@code ref} is {@code null}
     */
    public EvidenceLink {
        Objects.requireNonNull(type, "EvidenceLink type must not be null");
        Objects.requireNonNull(ref, "EvidenceLink ref must not be null");
    }
}
