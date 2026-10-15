package com.terravow.capability.economics.grounding;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.GroundingMode;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultRegulatoryGrounding} over a controlled in-memory regulatory corpus.
 *
 * <p>Exercises resolution of the four Environmental_Compensation figures against a seeded corpus
 * (Req 6.1), that the returned citations feed the Evidence_Chain resolving to primary regulatory
 * texts (Req 6.7, 10.1), and that a figure the corpus cannot return is reported undetermined with a
 * recorded gap (Req 6.9).
 */
class DefaultRegulatoryGroundingTest {

    private static final ViolationFacts FACTS =
            new ViolationFacts("PM10", "air", "air-particulate-exceedance", 30L);

    private static final String CHAPTER_SRC = "gcs://corpus/cpcb/ec-guideline-ch4.pdf#p12";
    private static final String BASE_RATE_SRC = "gcs://corpus/cpcb/ec-guideline-ch4.pdf#p13";
    private static final String DAYS_SRC = "gcs://corpus/cpcb/ec-methodology.pdf#p3";
    private static final String DETERRENCE_SRC = "gcs://corpus/cpcb/ec-guideline-ch4.pdf#p14";

    /** A corpus seeded with all four figures for {@link #FACTS}. */
    private InMemoryRegulatoryCorpus fullyGroundedCorpus() {
        return InMemoryRegulatoryCorpus.builder()
                .withTextualFigure(
                        FACTS,
                        FigureKind.GUIDELINE_CHAPTER,
                        "Chapter 4: Particulate matter exceedance in the air medium",
                        CHAPTER_SRC,
                        GroundingMode.VERTEX_AI_SEARCH)
                .withNumericFigure(
                        FACTS,
                        FigureKind.BASE_RATE,
                        50_000.0,
                        "Base rate: INR 50,000 per day for Chapter 4 violations",
                        BASE_RATE_SRC,
                        GroundingMode.VERTEX_AI_SEARCH)
                .withNumericFigure(
                        FACTS,
                        FigureKind.DAYS_OF_VIOLATION,
                        30.0,
                        "Days of violation: 30",
                        DAYS_SRC,
                        GroundingMode.BIGQUERY_VECTOR)
                .withNumericFigure(
                        FACTS,
                        FigureKind.DETERRENCE_FACTOR,
                        1.5,
                        "Deterrence factor: 1.5 for repeat exceedance",
                        DETERRENCE_SRC,
                        GroundingMode.VERTEX_AI_SEARCH)
                .build();
    }

    // ----- Resolve figures against a controlled corpus (Req 6.1) -----

    // Req 6.1 — all four EC figures resolve from the seeded corpus with their numeric values.
    @Test
    void resolvesAllFourFiguresFromCorpus() {
        RegulatoryGrounding grounding = new DefaultRegulatoryGrounding(fullyGroundedCorpus());

        GroundedFigures figures = grounding.resolve(FACTS);

        assertThat(figures.fullyResolved()).isTrue();
        assertThat(figures.hasGaps()).isFalse();
        assertThat(figures.figure(FigureKind.GUIDELINE_CHAPTER)).isPresent();
        assertThat(figures.figure(FigureKind.BASE_RATE).flatMap(GroundedFigure::numericValue))
                .contains(50_000.0);
        assertThat(figures.figure(FigureKind.DAYS_OF_VIOLATION).flatMap(GroundedFigure::numericValue))
                .contains(30.0);
        assertThat(figures.figure(FigureKind.DETERRENCE_FACTOR).flatMap(GroundedFigure::numericValue))
                .contains(1.5);
    }

    // Req 6.1 — the guideline chapter is a textual figure (no numeric value) carrying its snippet.
    @Test
    void guidelineChapterIsTextual() {
        RegulatoryGrounding grounding = new DefaultRegulatoryGrounding(fullyGroundedCorpus());

        GroundedFigures figures = grounding.resolve(FACTS);
        GroundedFigure chapter = figures.figure(FigureKind.GUIDELINE_CHAPTER).orElseThrow();

        assertThat(chapter.numericValue()).isEqualTo(Optional.empty());
        assertThat(chapter.citation().snippet()).contains("Chapter 4");
        assertThat(chapter.retrievedVia()).isEqualTo(GroundingMode.VERTEX_AI_SEARCH);
    }

    // Req 6.1 — the resolved figure records the managed-retrieval substrate it came from.
    @Test
    void resolvedFigureRecordsRetrievalSubstrate() {
        RegulatoryGrounding grounding = new DefaultRegulatoryGrounding(fullyGroundedCorpus());

        GroundedFigures figures = grounding.resolve(FACTS);

        assertThat(figures.figure(FigureKind.DAYS_OF_VIOLATION).orElseThrow().retrievedVia())
                .isEqualTo(GroundingMode.BIGQUERY_VECTOR);
    }

    // ----- Citations feed the Evidence_Chain (Req 6.7, 10.1) -----

    // Req 6.7, 10.1 — every resolved figure contributes a REGULATORY_TEXT link resolving to its
    // cited primary source.
    @Test
    void citationsFeedEvidenceChain() {
        RegulatoryGrounding grounding = new DefaultRegulatoryGrounding(fullyGroundedCorpus());

        GroundedFigures figures = grounding.resolve(FACTS);

        assertThat(figures.evidence().links()).hasSize(4);
        assertThat(figures.evidence().links())
                .allMatch(link -> link.type() == SourceType.REGULATORY_TEXT);
        assertThat(figures.evidence().links())
                .extracting(EvidenceLink::ref)
                .containsExactlyInAnyOrder(
                        new SourceRef(CHAPTER_SRC),
                        new SourceRef(BASE_RATE_SRC),
                        new SourceRef(DAYS_SRC),
                        new SourceRef(DETERRENCE_SRC));
    }

    // Req 6.7 — each grounded figure's citation ties back to its own primary source.
    @Test
    void eachFigureCitationResolvesToItsPrimarySource() {
        RegulatoryGrounding grounding = new DefaultRegulatoryGrounding(fullyGroundedCorpus());

        GroundedFigures figures = grounding.resolve(FACTS);

        assertThat(figures.figure(FigureKind.BASE_RATE).orElseThrow().citation().source())
                .isEqualTo(new SourceRef(BASE_RATE_SRC));
        assertThat(figures.figure(FigureKind.DETERRENCE_FACTOR).orElseThrow().citation().source())
                .isEqualTo(new SourceRef(DETERRENCE_SRC));
    }

    // Req 6.7, 10.1 — the Evidence_Chain is deterministic in enum-declaration order.
    @Test
    void evidenceChainOrderIsDeterministic() {
        RegulatoryGrounding grounding = new DefaultRegulatoryGrounding(fullyGroundedCorpus());

        GroundedFigures figures = grounding.resolve(FACTS);

        assertThat(figures.evidence().links())
                .extracting(link -> link.ref().id())
                .containsExactly(CHAPTER_SRC, BASE_RATE_SRC, DAYS_SRC, DETERRENCE_SRC);
    }

    // ----- Missing figure → undetermined with recorded gap (Req 6.9) -----

    // Req 6.9 — a figure the corpus cannot return is reported as a recorded gap, not invented.
    @Test
    void missingFigureIsReportedAsUndeterminedGap() {
        // Seed everything except the deterrence factor.
        InMemoryRegulatoryCorpus corpus =
                InMemoryRegulatoryCorpus.builder()
                        .withTextualFigure(
                                FACTS,
                                FigureKind.GUIDELINE_CHAPTER,
                                "Chapter 4",
                                CHAPTER_SRC,
                                GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS,
                                FigureKind.BASE_RATE,
                                50_000.0,
                                "Base rate",
                                BASE_RATE_SRC,
                                GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS,
                                FigureKind.DAYS_OF_VIOLATION,
                                30.0,
                                "Days",
                                DAYS_SRC,
                                GroundingMode.VERTEX_AI_SEARCH)
                        .build();
        RegulatoryGrounding grounding = new DefaultRegulatoryGrounding(corpus);

        GroundedFigures figures = grounding.resolve(FACTS);

        assertThat(figures.fullyResolved()).isFalse();
        assertThat(figures.hasGaps()).isTrue();
        assertThat(figures.figure(FigureKind.DETERRENCE_FACTOR)).isEmpty();
        assertThat(figures.gaps())
                .singleElement()
                .satisfies(
                        gap -> {
                            assertThat(gap.kind()).isEqualTo(FigureKind.DETERRENCE_FACTOR);
                            assertThat(gap.reason()).isNotBlank();
                        });
    }

    // Req 6.9 — an entirely empty corpus reports all four figures as gaps and an empty chain.
    @Test
    void emptyCorpusReportsAllFiguresAsGaps() {
        RegulatoryGrounding grounding =
                new DefaultRegulatoryGrounding(InMemoryRegulatoryCorpus.builder().build());

        GroundedFigures figures = grounding.resolve(FACTS);

        assertThat(figures.hasGaps()).isTrue();
        assertThat(figures.gaps()).hasSize(4);
        assertThat(figures.gaps()).extracting(CorpusGap::kind)
                .containsExactlyInAnyOrder(FigureKind.values());
        assertThat(figures.evidence().links()).isEmpty();
    }

    // Req 6.9 — a gapped figure never appears in the resolved set (no fabricated value).
    @Test
    void gappedFigureIsNotFabricated() {
        RegulatoryGrounding grounding =
                new DefaultRegulatoryGrounding(InMemoryRegulatoryCorpus.builder().build());

        GroundedFigures figures = grounding.resolve(FACTS);

        for (FigureKind kind : FigureKind.values()) {
            assertThat(figures.figure(kind)).isEmpty();
        }
    }
}
