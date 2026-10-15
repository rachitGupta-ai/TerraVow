package com.terravow.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory implementation of {@link EvidenceService} (Req 10).
 *
 * <p>Stored Evidence_Chains are held in a thread-safe in-memory map keyed by {@link ValueRef}.
 * Firestore-backed persistence of provenance is deferred to a later storage task; this
 * implementation preserves the Req 10 behaviours independently of the backing store:
 *
 * <ul>
 *   <li>{@link #build(List)} assembles a chain whose links each resolve to a primary source (Req
 *       10.1).</li>
 *   <li>{@link #isFullyResolved(EvidenceChain)} treats an empty chain as unresolved and every
 *       non-empty chain of resolvable links as resolved (Req 10.1).</li>
 *   <li>{@link #provenanceOf(ValueRef)} is a pure read: a {@code NOT_FOUND} lookup touches no
 *       stored state and therefore leaves the audit trail unchanged (Req 10.6); a hit returns the
 *       complete stored chain (Req 10.5).</li>
 *   <li>{@link #markUnsupported(ValueRef, String)} records the reason and routes the value to human
 *       review without storing a chain (Req 10.7).</li>
 * </ul>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.1, 10.5, 10.6, 10.7</a>
 */
@Service
public class DefaultEvidenceService implements EvidenceService {

    private final Map<ValueRef, EvidenceChain> chainsByValue = new ConcurrentHashMap<>();

    @Override
    public EvidenceChain build(List<EvidenceLink> links) {
        Objects.requireNonNull(links, "links must not be null");
        // EvidenceChain's compact constructor defensively copies and rejects null elements.
        return new EvidenceChain(links);
    }

    @Override
    public boolean isFullyResolved(EvidenceChain chain) {
        Objects.requireNonNull(chain, "chain must not be null");
        // A value with no supporting links cannot be traced to a primary source (Req 10.7).
        if (chain.links().isEmpty()) {
            return false;
        }
        // Each link carries a non-blank, resolvable SourceRef (enforced by SourceRef's constructor)
        // and a non-null SourceType (enforced by EvidenceLink's constructor), so every link in a
        // constructed chain resolves to an identified primary source (Req 10.1).
        return chain.links().stream().allMatch(link -> link != null && link.ref() != null);
    }

    @Override
    public ProvenanceResult provenanceOf(ValueRef ref) {
        Objects.requireNonNull(ref, "ref must not be null");
        // Pure read: a miss returns NOT_FOUND and mutates nothing, leaving the audit trail
        // unchanged (Req 10.6). A hit returns the complete stored chain (Req 10.5).
        EvidenceChain stored = chainsByValue.get(ref);
        if (stored == null) {
            return ProvenanceResult.notFound();
        }
        return ProvenanceResult.found(stored);
    }

    @Override
    public void record(ValueRef ref, EvidenceChain chain) {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(chain, "chain must not be null");
        if (!isFullyResolved(chain)) {
            throw new IllegalArgumentException(
                    "Cannot record an unresolved Evidence_Chain for " + ref
                            + "; mark the value unsupported instead (Req 10.7)");
        }
        chainsByValue.put(ref, chain);
    }

    @Override
    public UnsupportedValueReview markUnsupported(ValueRef ref, String reason) {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        // An unsupported value has no primary-source chain; do not store one. Record the reason and
        // route to human review (Req 10.7).
        return UnsupportedValueReview.of(ref, reason);
    }
}
