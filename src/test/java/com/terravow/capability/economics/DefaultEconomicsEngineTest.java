package com.terravow.capability.economics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.GroundingMode;
import com.terravow.reasoning.tool.EnvironmentalCompensationTool;
import com.terravow.reasoning.tool.NetPositionTool;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultEconomicsEngine} over controlled grounding and deterministic stub
 * ports.
 *
 * <p>Covers the EC formula via the verified tool (Req 6.2), the undetermined path on a grounding gap
 * (Req 6.9), conflicting-instrument enumeration in the Evidence_Chain (Req 6.3), incentive-category
 * coverage (Req 6.4), the net-position identity via the verified tool (Req 6.5), the
 * net-position-maximising timing recommendation with a recorded basis (Req 6.6), and the cited-draft
 * advisory label on every output (Req 6.8).
 */
class DefaultEconomicsEngineTest {

    private static final ViolationFacts FACTS =
            new ViolationFacts("PM10", "air", "air-particulate-exceedance", 30L);

    private static final AssetContext CONTEXT =
            new AssetContext(
                    new AssetClass("boiler-class-A"),
                    new FacilityLocation("Maharashtra-Pune-plant-1"),
                    Currency.INR);

    private static final String CHAPTER_SRC = "gcs://corpus/ec-ch4.pdf#p12";
    private static final String BASE_RATE_SRC = "gcs://corpus/ec-ch4.pdf#p13";
    private static final String DAYS_SRC = "gcs://corpus/ec-method.pdf#p3";
    private static final String DETERRENCE_SRC = "gcs://corpus/ec-ch4.pdf#p14";

    // ----- fixtures -----

    private static RegulatoryGrounding fullyGroundedGrounding() {
        InMemoryRegulatoryCorpus corpus =
                InMemoryRegulatoryCorpus.builder()
                        .withTextualFigure(
                                FACTS, FigureKind.GUIDELINE_CHAPTER, "Chapter 4",
                                CHAPTER_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.BASE_RATE, 50_000.0, "Base rate",
                                BASE_RATE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.DAYS_OF_VIOLATION, 30.0, "Days",
                                DAYS_SRC, GroundingMode.BIGQUERY_VECTOR)
                        .withNumericFigure(
                                FACTS, FigureKind.DETERRENCE_FACTOR, 1.5, "Deterrence",
                                DETERRENCE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .build();
        return new DefaultRegulatoryGrounding(corpus);
    }

    /** A grounding missing the deterrence factor (a blocking gap). */
    private static RegulatoryGrounding missingDeterrenceGrounding() {
        InMemoryRegulatoryCorpus corpus =
                InMemoryRegulatoryCorpus.builder()
                        .withTextualFigure(
                                FACTS, FigureKind.GUIDELINE_CHAPTER, "Chapter 4",
                                CHAPTER_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.BASE_RATE, 50_000.0, "Base rate",
                                BASE_RATE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                FACTS, FigureKind.DAYS_OF_VIOLATION, 30.0, "Days",
                                DAYS_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .build();
        return new DefaultRegulatoryGrounding(corpus);
    }

    /** Every category eligible with a fixed value; avoided-EC = 100000 paise. */
    private static final IncentivePort ALL_ELIGIBLE_INCENTIVES =
            (kind, context) -> switch (kind) {
                case PAT_ESCERTS ->
                        IncentiveItem.eligible(kind, new Money(300_000L, context.currency()));
                case IT_ACT_SECTION_32_DEPRECIATION ->
                        IncentiveItem.eligible(kind, new Money(200_000L, context.currency()));
                case STATE_INDUSTRIAL_POLICY ->
                        IncentiveItem.eligible(kind, new Money(150_000L, context.currency()));
                case AVOIDED_EC ->
                        IncentiveItem.eligible(kind, new Money(100_000L, context.currency()));
            };

    private DefaultEconomicsEngine engine(
            RegulatoryGrounding grounding,
            InstrumentConflictPort instrumentPort,
            IncentivePort incentivePort) {
        return new DefaultEconomicsEngine(
                grounding,
                new EnvironmentalCompensationTool(),
                new NetPositionTool(),
                instrumentPort,
                incentivePort);
    }

    // ----- EC formula (Req 6.2) -----

    // Req 6.2 — EC exposure = base × days × deterrence via the verified tool.
    @Test
    void ecExposureAppliesDocumentedFormula() {
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        EcExposure exposure = engine.computeEnvironmentalCompensation(FACTS, Currency.INR);

        assertThat(exposure.isComputed()).isTrue();
        // 50,000 × 30 × 1.5 = 2,250,000
        assertThat(exposure.amount()).contains(new Money(2_250_000L, Currency.INR));
        assertThat(exposure.evidence().links()).isNotEmpty();
        assertThat(exposure.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    // Req 6.2 — days of violation comes from the fact pattern, not an invented value.
    @Test
    void ecExposureUsesFactPatternDays() {
        ViolationFacts tenDays = new ViolationFacts("PM10", "air", "air-particulate-exceedance", 10L);
        InMemoryRegulatoryCorpus corpus =
                InMemoryRegulatoryCorpus.builder()
                        .withNumericFigure(
                                tenDays, FigureKind.BASE_RATE, 1_000.0, "Base rate",
                                BASE_RATE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                tenDays, FigureKind.DAYS_OF_VIOLATION, 10.0, "Days",
                                DAYS_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                tenDays, FigureKind.DETERRENCE_FACTOR, 2.0, "Deterrence",
                                DETERRENCE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withTextualFigure(
                                tenDays, FigureKind.GUIDELINE_CHAPTER, "Ch", CHAPTER_SRC,
                                GroundingMode.VERTEX_AI_SEARCH)
                        .build();
        DefaultEconomicsEngine engine =
                engine(new DefaultRegulatoryGrounding(corpus), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        EcExposure exposure = engine.computeEnvironmentalCompensation(tenDays, Currency.INR);

        // 1,000 × 10 × 2.0 = 20,000
        assertThat(exposure.amount()).contains(new Money(20_000L, Currency.INR));
    }

    // Req 6.9 — a required figure that is a grounding gap → exposure undetermined, not invented.
    @Test
    void ecExposureUndeterminedWhenRequiredFigureIsAGap() {
        DefaultEconomicsEngine engine =
                engine(missingDeterrenceGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        EcExposure exposure = engine.computeEnvironmentalCompensation(FACTS, Currency.INR);

        assertThat(exposure.status()).isEqualTo(DeterminationStatus.UNDETERMINED);
        assertThat(exposure.amount()).isEmpty();
        assertThat(exposure.gaps()).extracting(g -> g.kind())
                .contains(FigureKind.DETERRENCE_FACTOR);
        assertThat(exposure.reason()).isPresent();
        assertThat(exposure.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    // ----- Conflicting-instrument enumeration (Req 6.3) -----

    // Req 6.3 — two or more conflicting instruments → every one enumerated in the Evidence_Chain.
    @Test
    void conflictEnumeratesEveryInstrumentInEvidenceChain() {
        RegulatoryInstrument central =
                new RegulatoryInstrument(
                        InstrumentAuthority.CENTRAL, "CPCB directive 2021",
                        new SourceRef("gcs://corpus/central.pdf"));
        RegulatoryInstrument state =
                new RegulatoryInstrument(
                        InstrumentAuthority.STATE, "MPCB rule 12",
                        new SourceRef("gcs://corpus/state.pdf"));
        RegulatoryInstrument ngt =
                new RegulatoryInstrument(
                        InstrumentAuthority.NGT, "NGT OA 55/2019",
                        new SourceRef("gcs://corpus/ngt.pdf"));
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(),
                        f -> List.of(central, state, ngt),
                        ALL_ELIGIBLE_INCENTIVES);

        InstrumentConflict conflict = engine.reportInstrumentConflicts(FACTS);

        assertThat(conflict.conflicting()).isTrue();
        assertThat(conflict.instruments()).containsExactly(central, state, ngt);
        // Every conflicting instrument is identified in the Evidence_Chain (one link each).
        assertThat(conflict.evidence().links()).hasSize(3);
        assertThat(conflict.evidence().links())
                .allMatch(link -> link.type() == SourceType.REGULATORY_TEXT);
        assertThat(conflict.evidence().links())
                .extracting(link -> link.ref())
                .containsExactly(central.source(), state.source(), ngt.source());
    }

    // Req 6.3 — a single applicable instrument is not a conflict.
    @Test
    void singleInstrumentIsNotAConflict() {
        RegulatoryInstrument only =
                new RegulatoryInstrument(
                        InstrumentAuthority.CENTRAL, "only one",
                        new SourceRef("gcs://corpus/only.pdf"));
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(), f -> List.of(only), ALL_ELIGIBLE_INCENTIVES);

        InstrumentConflict conflict = engine.reportInstrumentConflicts(FACTS);

        assertThat(conflict.conflicting()).isFalse();
        assertThat(conflict.instruments()).isEmpty();
        assertThat(conflict.evidence().links()).isEmpty();
    }

    // ----- Incentive-category coverage (Req 6.4) -----

    // Req 6.4 — the assessment covers every incentive category.
    @Test
    void incentiveAssessmentCoversEveryCategory() {
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        IncentiveAssessment assessment = engine.assessIncentives(CONTEXT);

        for (IncentiveKind kind : IncentiveKind.values()) {
            assertThat(assessment.item(kind)).isNotNull();
            assertThat(assessment.item(kind).kind()).isEqualTo(kind);
        }
        // total incentives excludes avoided EC: 300,000 + 200,000 + 150,000 = 650,000
        assertThat(assessment.totalIncentives()).isEqualTo(new Money(650_000L, Currency.INR));
        assertThat(assessment.avoidedEc()).contains(new Money(100_000L, Currency.INR));
        assertThat(assessment.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    // ----- Net-position identity (Req 6.5) -----

    // Req 6.5 — net position = total incentives + avoided EC − penalty, via the verified tool.
    @Test
    void netPositionIsTheEconomicsIdentity() {
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        EcExposure penalty = engine.computeEnvironmentalCompensation(FACTS, Currency.INR);
        IncentiveAssessment incentives = engine.assessIncentives(CONTEXT);

        NetPosition net = engine.netPosition(penalty, incentives);

        assertThat(net.isComputed()).isTrue();
        // (650,000 + 100,000) − 2,250,000 = −1,500,000
        assertThat(net.value()).contains(new Money(-1_500_000L, Currency.INR));
        assertThat(net.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        // The net-position Evidence_Chain folds the penalty figure citations.
        assertThat(net.evidence().links()).isNotEmpty();
    }

    // Req 6.5 — an undetermined penalty leaves net position unavailable (dependent value).
    @Test
    void netPositionUnavailableWhenPenaltyUndetermined() {
        DefaultEconomicsEngine engine =
                engine(missingDeterrenceGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        EcExposure penalty = engine.computeEnvironmentalCompensation(FACTS, Currency.INR);
        IncentiveAssessment incentives = engine.assessIncentives(CONTEXT);

        NetPosition net = engine.netPosition(penalty, incentives);

        assertThat(net.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(net.value()).isEmpty();
        assertThat(net.reason()).isPresent();
    }

    // ----- Timing recommendation (Req 6.6) -----

    // Req 6.6 — the recommended timing maximises net position and records the basis.
    @Test
    void timingRecommendationMaximisesNetPosition() {
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        TimingHorizon horizon =
                new TimingHorizon(
                        Currency.INR,
                        List.of(
                                new TimingCandidate(0L, new Money(-500_000L, Currency.INR)),
                                new TimingCandidate(30L, new Money(250_000L, Currency.INR)),
                                new TimingCandidate(60L, new Money(100_000L, Currency.INR))));

        TimingRecommendation rec = engine.optimalReplacementTiming(horizon);

        assertThat(rec.recommended().offsetDays()).isEqualTo(30L);
        assertThat(rec.recommended().netPosition()).isEqualTo(new Money(250_000L, Currency.INR));
        assertThat(rec.basis()).isNotBlank();
        assertThat(rec.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        // The recommended value is >= every other candidate (Req 6.6).
        assertThat(rec.recommended().netPosition().minorUnits())
                .isGreaterThanOrEqualTo(
                        horizon.candidates().stream()
                                .mapToLong(c -> c.netPosition().minorUnits())
                                .max()
                                .orElseThrow());
    }

    // Req 6.6 — ties are broken deterministically by the earliest offset.
    @Test
    void timingRecommendationBreaksTiesByEarliestOffset() {
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        TimingHorizon horizon =
                new TimingHorizon(
                        Currency.INR,
                        List.of(
                                new TimingCandidate(30L, new Money(250_000L, Currency.INR)),
                                new TimingCandidate(10L, new Money(250_000L, Currency.INR)),
                                new TimingCandidate(60L, new Money(100_000L, Currency.INR))));

        TimingRecommendation rec = engine.optimalReplacementTiming(horizon);

        assertThat(rec.recommended().offsetDays()).isEqualTo(10L);
    }

    // ----- Advisory label (Req 6.8) -----

    // Req 6.8 — every economics output is labelled a cited draft, not advice.
    @Test
    void everyOutputCarriesCitedDraftLabel() {
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        EcExposure penalty = engine.computeEnvironmentalCompensation(FACTS, Currency.INR);
        IncentiveAssessment incentives = engine.assessIncentives(CONTEXT);
        NetPosition net = engine.netPosition(penalty, incentives);
        TimingRecommendation rec =
                engine.optimalReplacementTiming(
                        new TimingHorizon(
                                Currency.INR,
                                List.of(new TimingCandidate(0L, new Money(1L, Currency.INR)))));

        assertThat(penalty.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        assertThat(incentives.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        assertThat(net.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        assertThat(rec.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        assertThat(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE.text())
                .containsIgnoringCase("not legal or tax advice");
    }

    // ----- construction / null-safety -----

    @Test
    void rejectsNullCollaborators() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new DefaultEconomicsEngine(
                                        null,
                                        new EnvironmentalCompensationTool(),
                                        new NetPositionTool(),
                                        f -> List.of(),
                                        ALL_ELIGIBLE_INCENTIVES));
    }

    @Test
    void defaultCurrencyIsInr() {
        DefaultEconomicsEngine engine =
                engine(fullyGroundedGrounding(), f -> List.of(), ALL_ELIGIBLE_INCENTIVES);

        EcExposure exposure = engine.computeEnvironmentalCompensation(FACTS);

        assertThat(exposure.amount().orElseThrow().currency()).isEqualTo(Currency.INR);
    }
}
