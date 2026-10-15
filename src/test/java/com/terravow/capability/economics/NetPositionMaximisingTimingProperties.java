package com.terravow.capability.economics;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.economics.grounding.DefaultRegulatoryGrounding;
import com.terravow.capability.economics.grounding.InMemoryRegulatoryCorpus;
import com.terravow.capability.economics.grounding.RegulatoryGrounding;
import com.terravow.domain.Currency;
import com.terravow.domain.Money;
import com.terravow.reasoning.tool.EnvironmentalCompensationTool;
import com.terravow.reasoning.tool.NetPositionTool;

import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the Economics Engine's replacement-timing recommendation (Req 6.6).
 *
 * <p>Validates <b>Property 23: Recommended timing maximises net position</b>: for an arbitrary
 * evaluated horizon of candidate timings, each with its net-position value, the engine recommends a
 * candidate whose net position is greater than or equal to that of <em>every</em> other candidate in
 * the horizon (Req 6.6). The recommendation is therefore a true maximiser over the evaluated set, and
 * it carries a recorded, non-blank basis and the cited-draft advisory label (Req 6.6, 6.8).
 *
 * <p><b>Validates: Requirements 6.6</b>
 *
 * @see DefaultEconomicsEngine
 * @see TimingRecommendation
 */
// Feature: terravow, Property 23: Recommended timing maximises net position
class NetPositionMaximisingTimingProperties {

    private final DefaultEconomicsEngine engine =
            new DefaultEconomicsEngine(
                    emptyGrounding(),
                    new EnvironmentalCompensationTool(),
                    new NetPositionTool(),
                    f -> List.of(),
                    (kind, context) ->
                            IncentiveItem.eligible(kind, new Money(1L, context.currency())));

    /**
     * The recommended candidate's net position is {@code >=} every candidate's net position in the
     * horizon (Req 6.6), and the recommendation carries a non-blank basis and the advisory label
     * (Req 6.6, 6.8).
     *
     * @param horizon an arbitrary non-empty single-currency horizon of candidate timings
     */
    @Property(tries = 100)
    void recommendedNetPositionIsGreaterOrEqualToEveryCandidate(
            @ForAll("horizons") TimingHorizon horizon) {

        TimingRecommendation rec = engine.optimalReplacementTiming(horizon);

        long recommended = rec.recommended().netPosition().minorUnits();
        for (TimingCandidate candidate : horizon.candidates()) {
            assertThat(recommended)
                    .as("recommended net position must be >= candidate at offset %d",
                            candidate.offsetDays())
                    .isGreaterThanOrEqualTo(candidate.netPosition().minorUnits());
        }

        // The recommended candidate is one of the evaluated candidates.
        assertThat(horizon.candidates()).contains(rec.recommended());
        assertThat(rec.basis()).isNotBlank();
        assertThat(rec.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    /**
     * Arbitrary timing horizons: 1..8 candidates in INR, each with a distinct non-negative offset
     * (so {@link TimingHorizon} accepts them) and an arbitrary signed net position spanning net cost
     * and net benefit.
     */
    @Provide
    Arbitrary<TimingHorizon> horizons() {
        Arbitrary<Long> netPositions =
                Arbitraries.longs().between(-5_000_000L, 5_000_000L);

        return netPositions.list().ofMinSize(1).ofMaxSize(8).map(values -> {
            List<TimingCandidate> candidates = new ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++) {
                // Distinct offsets keep each candidate well-formed and the horizon unambiguous.
                candidates.add(
                        new TimingCandidate((long) i * 10L, new Money(values.get(i), Currency.INR)));
            }
            return new TimingHorizon(Currency.INR, candidates);
        });
    }

    private static RegulatoryGrounding emptyGrounding() {
        return new DefaultRegulatoryGrounding(InMemoryRegulatoryCorpus.builder().build());
    }
}
