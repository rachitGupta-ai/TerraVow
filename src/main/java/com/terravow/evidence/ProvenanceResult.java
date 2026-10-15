package com.terravow.evidence;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a provenance lookup for an identified produced value.
 *
 * <p>A lookup either {@link ProvenanceStatus#FOUND FOUND}s the complete {@link EvidenceChain}
 * stored for the value (Req 10.5), or returns {@link ProvenanceStatus#NOT_FOUND NOT_FOUND} when no
 * chain exists for the reference. A {@code NOT_FOUND} result must leave the audit trail unchanged
 * (Req 10.6): it is a pure read that neither creates nor mutates any stored provenance.
 *
 * <p>The chain is present if and only if the status is {@code FOUND}; this invariant is enforced in
 * the compact constructor so a caller can rely on {@code chain().isPresent() == (status == FOUND)}.
 *
 * @param status the lookup outcome; never {@code null}
 * @param chain  the resolved chain, present iff {@code status == FOUND}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.5, 10.6</a>
 */
public record ProvenanceResult(ProvenanceStatus status, Optional<EvidenceChain> chain) {

    /** Whether a stored Evidence_Chain was found for the requested value. */
    public enum ProvenanceStatus {
        /** A complete Evidence_Chain was found for the value (Req 10.5). */
        FOUND,
        /** No Evidence_Chain exists for the value; the audit trail is left unchanged (Req 10.6). */
        NOT_FOUND
    }

    /**
     * Validates the status/chain invariant (Req 10.5, 10.6).
     *
     * @throws NullPointerException     if {@code status} or {@code chain} is {@code null}
     * @throws IllegalArgumentException if a {@code FOUND} result lacks a chain, or a
     *                                  {@code NOT_FOUND} result carries one
     */
    public ProvenanceResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(chain, "chain must not be null");
        if (status == ProvenanceStatus.FOUND && chain.isEmpty()) {
            throw new IllegalArgumentException("A FOUND provenance result must carry an EvidenceChain");
        }
        if (status == ProvenanceStatus.NOT_FOUND && chain.isPresent()) {
            throw new IllegalArgumentException("A NOT_FOUND provenance result must not carry a chain");
        }
    }

    /**
     * Builds a {@code FOUND} result wrapping the resolved chain (Req 10.5).
     *
     * @param chain the resolved Evidence_Chain; never {@code null}
     * @return a {@code FOUND} provenance result
     * @throws NullPointerException if {@code chain} is {@code null}
     */
    public static ProvenanceResult found(EvidenceChain chain) {
        Objects.requireNonNull(chain, "chain must not be null");
        return new ProvenanceResult(ProvenanceStatus.FOUND, Optional.of(chain));
    }

    /**
     * The canonical not-found result (Req 10.6).
     *
     * @return a {@code NOT_FOUND} provenance result with no chain
     */
    public static ProvenanceResult notFound() {
        return new ProvenanceResult(ProvenanceStatus.NOT_FOUND, Optional.empty());
    }
}
