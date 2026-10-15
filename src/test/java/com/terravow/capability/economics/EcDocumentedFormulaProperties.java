package com.terravow.capability.economics;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.economics.grounding.DefaultRegulatoryGrounding;
import com.terravow.capability.economics.grounding.FigureKind;
import com.terravow.capability.economics.grounding.InMemoryRegulatoryCorpus;
import com.terravow.capability.economics.grounding.RegulatoryGrounding;
import com.terravow.capability.economics.grounding.ViolationFacts;
import com.terravow.domain.Currency;
import com.terravow.domain.Money;
import com.terravow.reasoning.GroundingMode;
import com.terravow.reasoning.tool.EnvironmentalCompensationTool;
import com.terravow.reasoning.tool.NetPositionTool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based test for the Economics Engine's Environmental_Compensation (EC) computation
 * (Req 6.2).
 *
 * <p>Validates <b>Property 20: Environmental Compensation follows the documented formula</b>: for an
 * arbitrary grounded base rate, number of days taken from the violation fact pattern, and deterrence
 * factor, the EC exposure the engine computes equals {@code base rate × days × deterrence} — the
 * documented formula, carried out via the verified function-calling tool, never free-generated
 * (Req 6.2). The engine resolves the base rate and deterrence factor from the regulatory corpus and
 * draws the day count from {@link ViolationFacts#daysOfViolation()}; this property pins the arithmetic
 * identity across the whole grounded input space.
 *
 * <p><b>Validates: Requirements 6.2</b>
 *
 * @see DefaultEconomicsEngine
 */
// Feature: terravow, Property 20: Environmental Compensation follows the documented formula
class EcDocumentedFormulaProperties {

    private static final String CHAPTER_SRC = "gcs://corpus/ec-formula-ch.pdf#p1";
    private static final String BASE_RATE_SRC = "gcs://corpus/ec-formula-base.pdf#p2";
    private static final String DAYS_SRC = "gcs://corpus/ec-formula-days.pdf#p3";
    private static final String DETERRENCE_SRC = "gcs://corpus/ec-formula-det.pdf#p4";

    /**
     * For arbitrary grounded figures and a fact-pattern day count, the engine's EC exposure equals
     * {@code base rate × days × deterrence} rounded half-up to whole minor units — the documented
     * formula applied by the verified tool (Req 6.2).
     *
     * <p>Bounds are chosen to span a wide, realistic input space while staying clear of
     * {@code long} overflow so the arithmetic is always representable: base rate up to 10,000,000
     * paise, up to 3,650 days (~10 years), deterrence up to 5.0.
     *
     * @param baseRate   the grounded EC base rate in minor units; {@code [0, 10_000_000]}
     * @param days       the days of violation drawn from the fact pattern; {@code [0, 3650]}
     * @param deterrence the grounded deterrence factor; {@code [0.0, 5.0]}
     */
    @Property(tries = 100)
    void ecEqualsBaseTimesDaysTimesDeterrence(
            @ForAll @LongRange(min = 0L, max = 10_000_000L) long baseRate,
            @ForAll @LongRange(min = 0L, max = 3_650L) long days,
            @ForAll @DoubleRange(min = 0.0, max = 5.0) double deterrence) {

        ViolationFacts facts =
                new ViolationFacts("PM10", "air", "air-particulate-exceedance", days);

        DefaultEconomicsEngine engine =
                new DefaultEconomicsEngine(
                        groundingFor(facts, baseRate, days, deterrence),
                        new EnvironmentalCompensationTool(),
                        new NetPositionTool(),
                        f -> List.of(),
                        allEligible());

        EcExposure exposure = engine.computeEnvironmentalCompensation(facts, Currency.INR);

        // The documented formula, computed exactly the way the verified tool does: an exact
        // BigDecimal product rounded half-up to whole minor units (Req 6.2).
        long expected =
                BigDecimal.valueOf(baseRate)
                        .multiply(BigDecimal.valueOf(days))
                        .multiply(BigDecimal.valueOf(deterrence))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();

        assertThat(exposure.isComputed()).isTrue();
        assertThat(exposure.amount()).contains(new Money(expected, Currency.INR));
        assertThat(exposure.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    /** A corpus grounding all four figures for {@code facts} with the supplied numeric values. */
    private static RegulatoryGrounding groundingFor(
            ViolationFacts facts, long baseRate, long days, double deterrence) {
        InMemoryRegulatoryCorpus corpus =
                InMemoryRegulatoryCorpus.builder()
                        .withTextualFigure(
                                facts, FigureKind.GUIDELINE_CHAPTER, "Chapter",
                                CHAPTER_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                facts, FigureKind.BASE_RATE, (double) baseRate, "Base rate",
                                BASE_RATE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .withNumericFigure(
                                facts, FigureKind.DAYS_OF_VIOLATION, (double) days, "Days",
                                DAYS_SRC, GroundingMode.BIGQUERY_VECTOR)
                        .withNumericFigure(
                                facts, FigureKind.DETERRENCE_FACTOR, deterrence, "Deterrence",
                                DETERRENCE_SRC, GroundingMode.VERTEX_AI_SEARCH)
                        .build();
        return new DefaultRegulatoryGrounding(corpus);
    }

    /** Every incentive category eligible with a fixed value (irrelevant to the EC formula). */
    private static IncentivePort allEligible() {
        return (kind, context) -> IncentiveItem.eligible(kind, new Money(1L, context.currency()));
    }
}
