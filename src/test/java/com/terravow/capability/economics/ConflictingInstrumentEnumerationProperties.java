package com.terravow.capability.economics;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.economics.grounding.DefaultRegulatoryGrounding;
import com.terravow.capability.economics.grounding.InMemoryRegulatoryCorpus;
import com.terravow.capability.economics.grounding.RegulatoryGrounding;
import com.terravow.capability.economics.grounding.ViolationFacts;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.tool.EnvironmentalCompensationTool;
import com.terravow.reasoning.tool.NetPositionTool;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the Economics Engine's conflicting-instrument reporting (Req 6.3).
 *
 * <p>Validates <b>Property 21: Conflicting instruments are fully enumerated</b>: for an arbitrary set
 * of two or more regulatory instruments bearing on a fact pattern, the engine reports a conflict and
 * <em>every</em> supplied instrument appears in the conflict report's Evidence_Chain — one
 * {@link SourceType#REGULATORY_TEXT} link per instrument, resolving to that instrument's primary
 * source, in the order the instruments were supplied (Req 6.3). The "every conflicting instrument is
 * identified in the Evidence_Chain" guarantee holds across any instrument set the retrieval seam
 * returns.
 *
 * <p><b>Validates: Requirements 6.3</b>
 *
 * @see DefaultEconomicsEngine
 * @see InstrumentConflict
 */
// Feature: terravow, Property 21: Conflicting instruments are fully enumerated
class ConflictingInstrumentEnumerationProperties {

    private static final ViolationFacts FACTS =
            new ViolationFacts("PM10", "air", "air-particulate-exceedance", 30L);

    /**
     * For any set of two or more instruments, the conflict is reported and the Evidence_Chain
     * enumerates every one of them exactly, in order, as REGULATORY_TEXT links resolving to each
     * instrument's primary source (Req 6.3).
     *
     * @param instruments an arbitrary list of 2..6 distinct-source instruments
     */
    @Property(tries = 100)
    void everyConflictingInstrumentAppearsInEvidenceChain(
            @ForAll("instrumentSets") List<RegulatoryInstrument> instruments) {

        DefaultEconomicsEngine engine =
                new DefaultEconomicsEngine(
                        emptyGrounding(),
                        new EnvironmentalCompensationTool(),
                        new NetPositionTool(),
                        f -> instruments,
                        allEligible());

        InstrumentConflict conflict = engine.reportInstrumentConflicts(FACTS);

        assertThat(conflict.conflicting()).isTrue();
        // Every supplied instrument is retained, in order.
        assertThat(conflict.instruments()).containsExactlyElementsOf(instruments);
        // Every conflicting instrument is identified in the Evidence_Chain: one link each.
        assertThat(conflict.evidence().links()).hasSameSizeAs(instruments);
        assertThat(conflict.evidence().links())
                .allMatch(link -> link.type() == SourceType.REGULATORY_TEXT);
        assertThat(conflict.evidence().links())
                .extracting(EvidenceLink::ref)
                .containsExactlyElementsOf(instruments.stream().map(RegulatoryInstrument::source).toList());
        // Structural restatement: the set of enumerated sources covers every instrument's source.
        assertThat(conflict.evidence().links().stream().map(EvidenceLink::ref).toList())
                .containsAll(instruments.stream().map(RegulatoryInstrument::source).toList());
    }

    /**
     * Arbitrary sets of 2..6 regulatory instruments. Each instrument is given a unique source id
     * (derived from its index) so the "one Evidence_Chain link per instrument" count is unambiguous
     * even when authorities and citations repeat.
     */
    @Provide
    Arbitrary<List<RegulatoryInstrument>> instrumentSets() {
        Arbitrary<InstrumentAuthority> authority = Arbitraries.of(InstrumentAuthority.class);
        Arbitrary<String> citation = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12);

        Arbitrary<RegulatoryInstrument> withPlaceholderSource =
                Combinators.combine(authority, citation)
                        .as((auth, cite) ->
                                new RegulatoryInstrument(
                                        auth, cite, new SourceRef("placeholder")));

        return withPlaceholderSource.list().ofMinSize(2).ofMaxSize(6).map(list -> {
            // Rewrite each instrument with a per-index unique, resolvable source.
            java.util.List<RegulatoryInstrument> out = new java.util.ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                RegulatoryInstrument base = list.get(i);
                out.add(
                        new RegulatoryInstrument(
                                base.authority(),
                                base.citation(),
                                new SourceRef("gcs://corpus/instrument-" + i + ".pdf")));
            }
            return java.util.List.copyOf(out);
        });
    }

    private static RegulatoryGrounding emptyGrounding() {
        return new DefaultRegulatoryGrounding(InMemoryRegulatoryCorpus.builder().build());
    }

    private static IncentivePort allEligible() {
        return (kind, context) ->
                IncentiveItem.eligible(kind, new com.terravow.domain.Money(1L, context.currency()));
    }
}
