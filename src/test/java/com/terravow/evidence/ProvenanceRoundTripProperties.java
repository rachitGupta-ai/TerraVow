package com.terravow.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.ProvenanceResult.ProvenanceStatus;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the provenance round-trip of {@link DefaultEvidenceService}.
 *
 * <p>Validates Correctness Property 33: for any produced value whose fully-resolved
 * {@link EvidenceChain} has been recorded, {@link EvidenceService#provenanceOf(ValueRef)} returns a
 * {@code FOUND} result carrying exactly the stored chain — same links, same order (Req 10.5).
 *
 * <p>The round-trip is checked by recording an arbitrary {@code (ValueRef, EvidenceChain)} pair and
 * then resolving the provenance of that reference: the result must be {@code FOUND}, must carry a
 * chain, and that chain must equal the one that was stored. {@code EvidenceChain} is a record of a
 * defensively-copied immutable list, so value equality compares the links element-by-element.
 *
 * <p><strong>Validates: Requirements 10.5</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.5</a>
 */
class ProvenanceRoundTripProperties {

    // Feature: terravow, Property 33: Provenance round-trip returns the stored chain
    @Property(tries = 100)
    void provenanceOfReturnsFoundWithExactlyTheStoredChain(
            @ForAll("valueRefs") ValueRef ref, @ForAll("resolvedChains") EvidenceChain stored) {
        EvidenceService service = new DefaultEvidenceService();

        service.record(ref, stored);

        ProvenanceResult result = service.provenanceOf(ref);

        // A recorded value resolves to FOUND carrying exactly the stored chain (Req 10.5).
        assertThat(result.status()).isEqualTo(ProvenanceStatus.FOUND);
        assertThat(result.chain()).isPresent();
        assertThat(result.chain()).contains(stored);
        assertThat(result.chain().orElseThrow().links()).isEqualTo(stored.links());
    }

    /** An arbitrary non-blank {@link ValueRef}. */
    @Provide
    Arbitrary<ValueRef> valueRefs() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(24)
                .map(ValueRef::new);
    }

    /**
     * An arbitrary fully-resolved {@link EvidenceChain}: at least one link, each with a non-null
     * {@link SourceType} and a non-blank {@link SourceRef}, so {@code record} accepts it (Req 10.1,
     * 10.5).
     */
    @Provide
    Arbitrary<EvidenceChain> resolvedChains() {
        Arbitrary<EvidenceLink> links =
                Combinators.combine(
                                Arbitraries.of(SourceType.class),
                                Arbitraries.strings()
                                        .alpha()
                                        .numeric()
                                        .ofMinLength(1)
                                        .ofMaxLength(24)
                                        .map(SourceRef::new))
                        .as(EvidenceLink::new);
        return links.list().ofMinSize(1).ofMaxSize(8).map((List<EvidenceLink> l) -> new EvidenceChain(l));
    }
}
