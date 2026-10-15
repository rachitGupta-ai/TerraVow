package com.terravow.evidence;

import java.util.List;

/**
 * Builds and resolves machine-checkable provenance for produced values (Req 10).
 *
 * <p>Every counterfactual, penalty exposure, incentive eligibility, and certification verdict
 * carries an {@link EvidenceChain} whose every link resolves to a primary source (Req 10.1). This
 * service assembles those chains, checks whether a chain is fully resolvable to primary sources,
 * resolves the provenance of a produced value by reference (Req 10.5 / 10.6), and marks a value
 * that cannot be linked to any primary source as unsupported, routing it to human review (Req
 * 10.7).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.1, 10.5, 10.6, 10.7</a>
 */
public interface EvidenceService {

    /**
     * Assembles an {@link EvidenceChain} from the given links; each link must resolve to a primary
     * source (Req 10.1).
     *
     * @param links the provenance links; never {@code null}, must not contain {@code null}
     * @return the assembled chain
     * @throws NullPointerException if {@code links} is {@code null} or contains a {@code null}
     */
    EvidenceChain build(List<EvidenceLink> links);

    /**
     * Returns whether every link in the chain resolves to an identified primary source (Req 10.1).
     *
     * <p>An empty chain is <em>not</em> fully resolved: a produced value with no supporting links
     * cannot be traced to any primary source, which is the condition that marks it unsupported
     * (Req 10.7).
     *
     * @param chain the chain to check; never {@code null}
     * @return {@code true} iff the chain is non-empty and every link resolves to a primary source
     * @throws NullPointerException if {@code chain} is {@code null}
     */
    boolean isFullyResolved(EvidenceChain chain);

    /**
     * Resolves the provenance of an identified produced value.
     *
     * <p>Returns the complete stored {@link EvidenceChain} when one exists (Req 10.5), or a
     * not-found result that leaves the audit trail unchanged (Req 10.6). This is a pure read: it
     * never creates or mutates stored provenance.
     *
     * @param ref the reference to the produced value; never {@code null}
     * @return a {@link ProvenanceResult} that is {@code FOUND} with the chain, or {@code NOT_FOUND}
     * @throws NullPointerException if {@code ref} is {@code null}
     */
    ProvenanceResult provenanceOf(ValueRef ref);

    /**
     * Records a stored Evidence_Chain for a produced value so its provenance can later be resolved
     * (Req 10.5).
     *
     * <p>If the chain is not fully resolvable to primary sources, the value is instead marked
     * unsupported and routed to human review (Req 10.7); callers should use
     * {@link #markUnsupported(ValueRef, String)} for that path. A chain stored here must be fully
     * resolved.
     *
     * @param ref   the reference to the produced value; never {@code null}
     * @param chain the fully-resolved chain to store; never {@code null}
     * @throws NullPointerException     if {@code ref} or {@code chain} is {@code null}
     * @throws IllegalArgumentException if {@code chain} is not fully resolved (Req 10.1)
     */
    void record(ValueRef ref, EvidenceChain chain);

    /**
     * Marks a produced value that cannot be linked to a primary source as unsupported and routes it
     * to human review, recording the reason (Req 10.7).
     *
     * @param ref    the reference to the unsupported value; never {@code null}
     * @param reason a non-blank human-readable reason; never {@code null}
     * @return the review record, routed to human review
     * @throws NullPointerException     if {@code ref} or {@code reason} is {@code null}
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    UnsupportedValueReview markUnsupported(ValueRef ref, String reason);
}
