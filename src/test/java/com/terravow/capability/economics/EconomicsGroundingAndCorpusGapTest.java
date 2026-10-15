package com.terravow.capability.economics;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.economics.grounding.DefaultRegulatoryGrounding;
import com.terravow.capability.economics.grounding.FigureKind;
import com.terravow.capability.economics.grounding.InMemoryRegulatoryCorpus;
import com.terravow.capability.economics.grounding.RegulatoryGrounding;
import com.terravow.capability.economics.grounding.ViolationFacts;
import com.terravow.domain.AssetClass;
import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.FacilityLocation;
import com.terravow.domain.Money;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.GroundingMode;
import com.terravow.reasoning.tool.EnvironmentalCompensationTool;
import com.terravow.reasoning.tool.NetPositionTool;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Supplementary unit/integration tests for the Economics Engine's grounding integration and
 * corpus-gap handling (Req 6.1, 6.4, 6.9).
 *
 * <p>These cases complement {@code DefaultEconomicsEngineTest} (engine behaviour) and
 * {@code DefaultRegulatoryGroundingTest} (grounding-layer behaviour) by exercising cases those do
 * not: the engine driving the real {@link DefaultRegulatoryGrounding} over a controlled in-memory
 * corpus end to end so the resolved EC figures feed the computed exposure's Evidence_Chain
 * (Req 6.1); incentive-category coverage with a <em>mix</em> of eligible and ineligible categories so
 * the total correctly excludes the ineligible ones and avoided EC (Req 6.4); and the engine-level
 * undetermined path when the <em>base rate</em> (rather than the deterrence factor) is the missing
 * corpus figure (Req 6.9).
 */
class EconomicsGroundingAndCorpusGapTest {

    private static final ViolationFacts FACTS =
            new ViolationFacts("SO2", "air", "sulphur-dioxide-exceedance", 20L);

    private static final AssetContext CONTEXT =
            new AssetContext(
                    new AssetClass("furnace-class-C"),
                    new FacilityLocation("Gujarat-Surat-plant-2"),
                    Currency.INR);

    private static final String CHAPTER_SRC = "gcs://corpus/so2/ec-ch7.pdf#p1";
    private static final String BASE_RATE_SRC = "gcs://corpus/so2/ec-ch7.pdf#p2";
    private static final String DAYS_SRC = "gcs://corpus/so2/ec-method.pdf#p3";
    private static final String DETERRENCE_SRC = "gcs://corpus/so2/ec-ch7.pdf#p4";

    private DefaultEconomicsEngine engine(
            RegulatoryGrounding grounding, IncentivePort incentivePort) {
        return new DefaultEconomicsEngine(
                grounding,
                new EnvironmentalCompensationTool(),
                new NetPositionTool(),
                f -> List.of(),
                incentivePort);
    }

    // ----- Req 6.1: EC corpus resolution against a controlled corpus, end to end -----

    // Req 6.1 — the engine resolves the EC figures from a controlled corpus and the resolved
    // figures' citations feed the computed exposure's Evidence_Chain (every figure a REGULATORY_TEXT
    // link resolving to its primary source).
    @Test
    void ecExposureEvidenceChainIsFedByCorpusResolution() {
        InMemoryRegulatoryCorpus corpus =
                InMemoryRegulatoryCorpus.builder()
                        .withTextualFigure(
                                FACTS, FigureKind.GUIDELINE_CHAPTER, "Chapter 7",
                                CHAPTER_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.BASE_RATE, 40_000.0, "Base rate",
                                BASE_RATE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.DAYS_OF_VIOLATION, 20.0, "Days",
                                DAYS_SRC, GroundingMode.BIGQUERY_VECTOR)
                        .withNumericFigure(
                                FACTS, FigureKind.DETERRENCE_FACTOR, 2.0, "Deterrence",
                                DETERRENCE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .build();
        DefaultEconomicsEngine engine =
                engine(new DefaultRegulatoryGrounding(corpus), allEligible());

        EcExposure exposure = engine.computeEnvironmentalCompensation(FACTS, Currency.INR);

        assertThat(exposure.isComputed()).isTrue();
        // 40,000 × 20 × 2.0 = 1,600,000
        assertThat(exposure.amount()).contains(new Money(1_600_000L, Currency.INR));
        // All four resolved figures contribute a primary-source link to the Evidence_Chain.
        assertThat(exposure.evidence().links()).hasSize(4);
        assertThat(exposure.evidence().links())
                .allMatch(link -> link.type() == SourceType.REGULATORY_TEXT);
        assertThat(exposure.evidence().links())
                .extracting(link -> link.ref())
                .containsExactlyInAnyOrder(
                        new SourceRef(CHAPTER_SRC),
                        new SourceRef(BASE_RATE_SRC),
                        new SourceRef(DAYS_SRC),
                        new SourceRef(DETERRENCE_SRC));
    }

    // ----- Req 6.4: incentive-category coverage with a mix of eligible/ineligible -----

    // Req 6.4 — every category is still assessed when some are ineligible; the incentive subtotal
    // sums only eligible categories and always excludes avoided EC (so it is not double-counted).
    @Test
    void incentiveAssessmentCoversEveryCategoryWithMixedEligibility() {
        IncentivePort mixed =
                (kind, context) -> switch (kind) {
                    case PAT_ESCERTS ->
                            IncentiveItem.eligible(kind, new Money(300_000L, context.currency()));
                    case IT_ACT_SECTION_32_DEPRECIATION ->
                            IncentiveItem.ineligible(kind, "asset not newly commissioned");
                    case STATE_INDUSTRIAL_POLICY ->
                            IncentiveItem.eligible(kind, new Money(150_000L, context.currency()));
                    case AVOIDED_EC ->
                            IncentiveItem.eligible(kind, new Money(100_000L, context.currency()));
                };
        DefaultEconomicsEngine engine = engine(emptyGrounding(), mixed);

        IncentiveAssessment assessment = engine.assessIncentives(CONTEXT);

        // Coverage: an item for every category.
        for (IncentiveKind kind : IncentiveKind.values()) {
            assertThat(assessment.item(kind)).isNotNull();
            assertThat(assessment.item(kind).kind()).isEqualTo(kind);
        }
        assertThat(assessment.item(IncentiveKind.IT_ACT_SECTION_32_DEPRECIATION).eligible())
                .isFalse();
        // Subtotal: eligible non-avoided-EC categories only: 300,000 + 150,000 = 450,000.
        assertThat(assessment.totalIncentives()).isEqualTo(new Money(450_000L, Currency.INR));
        // Avoided EC is reported separately, not folded into the subtotal.
        assertThat(assessment.avoidedEc()).contains(new Money(100_000L, Currency.INR));
    }

    // ----- Req 6.9: engine-level undetermined when a required figure is a corpus gap -----

    // Req 6.9 — when the base rate (a formula input) is a corpus gap, the engine reports the exposure
    // undetermined naming that figure, never inventing a value.
    @Test
    void ecExposureUndeterminedWhenBaseRateIsAGap() {
        // Seed everything except the base rate.
        InMemoryRegulatoryCorpus corpus =
                InMemoryRegulatoryCorpus.builder()
                        .withTextualFigure(
                                FACTS, FigureKind.GUIDELINE_CHAPTER, "Chapter 7",
                                CHAPTER_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.DAYS_OF_VIOLATION, 20.0, "Days",
                                DAYS_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.DETERRENCE_FACTOR, 2.0, "Deterrence",
                                DETERRENCE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .build();
        DefaultEconomicsEngine engine = engine(new DefaultRegulatoryGrounding(corpus), allEligible());

        EcExposure exposure = engine.computeEnvironmentalCompensation(FACTS, Currency.INR);

        assertThat(exposure.status()).isEqualTo(DeterminationStatus.UNDETERMINED);
        assertThat(exposure.amount()).isEmpty();
        assertThat(exposure.gaps()).extracting(g -> g.kind()).contains(FigureKind.BASE_RATE);
        assertThat(exposure.reason()).isPresent();
        assertThat(exposure.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    // Req 6.9 — the guideline chapter being the only gap does NOT block the arithmetic: the chapter
    // is textual context, not a formula input, so the exposure still computes.
    @Test
    void ecExposureComputesWhenOnlyGuidelineChapterIsAGap() {
        InMemoryRegulatoryCorpus corpus =
                InMemoryRegulatoryCorpus.builder()
                        .withNumericFigure(
                                FACTS, FigureKind.BASE_RATE, 40_000.0, "Base rate",
                                BASE_RATE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.DAYS_OF_VIOLATION, 20.0, "Days",
                                DAYS_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.DETERRENCE_FACTOR, 2.0, "Deterrence",
                                DETERRENCE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .build();
        DefaultEconomicsEngine engine = engine(new DefaultRegulatoryGrounding(corpus), allEligible());

        EcExposure exposure = engine.computeEnvironmentalCompensation(FACTS, Currency.INR);

        assertThat(exposure.isComputed()).isTrue();
        assertThat(exposure.amount()).contains(new Money(1_600_000L, Currency.INR));
    }

    private static RegulatoryGrounding emptyGrounding() {
        return new DefaultRegulatoryGrounding(InMemoryRegulatoryCorpus.builder().build());
    }

    private static IncentivePort allEligible() {
        return (kind, context) -> IncentiveItem.eligible(kind, new Money(1L, context.currency()));
    }
}
