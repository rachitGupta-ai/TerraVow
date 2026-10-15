package com.terravow.evidence;

import java.util.List;
import java.util.Objects;

/**
 * A machine-checkable provenance chain: an ordered, immutable list of {@link EvidenceLink}s, each
 * resolving to a primary source.
 *
 * <p>Every counterfactual, penalty exposure, incentive eligibility, and certification verdict
 * carries an {@code EvidenceChain} whose every link resolves to a primary source (Req 10.1).
 * Grounding citations returned by the reasoning layer and degradation fallback markers are folded
 * in as additional links. The chain is defensively copied into an unmodifiable list so a built
 * chain cannot be mutated after construction.
 *
 * @param links the provenance links; never {@code null}, defensively copied, may be empty
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.1</a>
 */
public record EvidenceChain(List<EvidenceLink> links) {

    /**
     * Validates and defensively copies the links into an unmodifiable list (Req 10.1). A
     * {@code null} element is rejected.
     *
     * @throws NullPointerException if {@code links} is {@code null} or contains a {@code null}
     *                              element
     */
    public EvidenceChain {
        Objects.requireNonNull(links, "links must not be null");
        links = List.copyOf(links); // List.copyOf rejects null elements and yields an immutable list
    }

    /** An evidence chain with no links. */
    public static final EvidenceChain EMPTY = new EvidenceChain(List.of());

    /**
     * Returns a new chain with the given link appended; this chain is left unchanged.
     *
     * @param link the link to append; never {@code null}
     * @return a new {@code EvidenceChain} containing this chain's links followed by {@code link}
     * @throws NullPointerException if {@code link} is {@code null}
     */
    public EvidenceChain with(EvidenceLink link) {
        Objects.requireNonNull(link, "link must not be null");
        var combined = new java.util.ArrayList<EvidenceLink>(links.size() + 1);
        combined.addAll(links);
        combined.add(link);
        return new EvidenceChain(combined);
    }
}
