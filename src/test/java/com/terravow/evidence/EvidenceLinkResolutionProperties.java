package com.terravow.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for evidence link resolution (Property 8).
 *
 * <p><b>Property 8: Evidence links resolve to primary sources.</b> For any {@link EvidenceChain}
 * built from a non-empty list of {@link EvidenceLink}s — each carrying a non-null {@link SourceType}
 * and a non-blank {@link SourceRef} id — every link resolves to an identified primary source and
 * {@link EvidenceService#isFullyResolved(EvidenceChain)} returns {@code true}. An empty chain is
 * treated as not fully resolved, because a produced value with no supporting links cannot be traced
 * to any primary source (Req 10.1, 10.7).
 *
 * <p>Every produced value that carries evidence — counterfactuals, penalty exposures, incentive
 * eligibility, certification verdicts, and the provenance-first guarantees across the pipeline —
 * depends on this resolution invariant.
 *
 * <p>Validates: Requirements 2.4, 4.7, 5.5, 6.7, 7.1, 10.1, 14.12.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.1</a>
 */
// Feature: terravow, Property 8: Evidence links resolve to primary sources
class EvidenceLinkResolutionProperties {

    private final EvidenceService service = new DefaultEvidenceService();

    /**
     * A chain built from a non-empty list of valid links is fully resolved, and every link carries
     * a non-null {@link SourceType} and a non-blank {@link SourceRef} id — i.e. each link resolves
     * to an identified primary source (Req 10.1).
     *
     * <p>Validates: Requirements 2.4, 4.7, 5.5, 6.7, 7.1, 10.1, 14.12.
     */
    @Property(tries = 100)
    void nonEmptyChainOfValidLinksIsFullyResolved(
            @ForAll("nonEmptyLinks") List<EvidenceLink> links) {
        EvidenceChain chain = service.build(links);

        assertThat(service.isFullyResolved(chain))
                .as("a non-empty chain of resolvable links is fully resolved (Req 10.1)")
                .isTrue();

        assertThat(chain.links())
                .as("every link resolves to an identified primary source (Req 10.1)")
                .isNotEmpty()
                .allSatisfy(link -> {
                    assertThat(link.type())
                            .as("each link carries a non-null SourceType")
                            .isNotNull();
                    assertThat(link.ref())
                            .as("each link carries a non-null SourceRef")
                            .isNotNull();
                    assertThat(link.ref().id())
                            .as("each SourceRef resolves to a non-blank primary-source id")
                            .isNotBlank();
                });
    }

    /**
     * An empty chain is not fully resolved: a produced value with no supporting links cannot be
     * traced to any primary source and is the condition that marks it unsupported (Req 10.1, 10.7).
     *
     * <p>Validates: Requirements 10.1.
     */
    @Example
    void emptyChainIsNotFullyResolved() {
        EvidenceChain empty = service.build(List.of());

        assertThat(service.isFullyResolved(empty))
                .as("an empty chain is not fully resolved (Req 10.1)")
                .isFalse();
        assertThat(service.isFullyResolved(EvidenceChain.EMPTY))
                .as("the canonical EMPTY chain is not fully resolved (Req 10.1)")
                .isFalse();
    }

    /** Generates non-empty lists of valid {@link EvidenceLink}s. */
    @Provide
    Arbitrary<List<EvidenceLink>> nonEmptyLinks() {
        return validLink().list().ofMinSize(1).ofMaxSize(8);
    }

    /**
     * A single valid {@link EvidenceLink}: a non-null {@link SourceType} paired with a
     * {@link SourceRef} whose id is non-blank.
     */
    private Arbitrary<EvidenceLink> validLink() {
        Arbitrary<SourceType> types = Arbitraries.of(SourceType.class);
        Arbitrary<SourceRef> refs =
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(32).map(SourceRef::new);
        return Combinators.combine(types, refs).as(EvidenceLink::new);
    }
}
