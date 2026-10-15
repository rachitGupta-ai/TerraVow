package com.terravow.capability.economics;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.economics.grounding.DefaultRegulatoryGrounding;
import com.terravow.capability.economics.grounding.InMemoryRegulatoryCorpus;
import com.terravow.capability.economics.grounding.RegulatoryGrounding;
import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.tool.EnvironmentalCompensationTool;
import com.terravow.reasoning.tool.NetPositionTool;

import java.util.List;
import java.util.Optional;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based test for the Economics Engine's net-position identity (Req 6.5).
 *
 * <p>Validates <b>Property 22: Net position is the economics identity</b>: for arbitrary incentive
 * totals, avoided-EC values, and a computed EC penalty, the net position the engine returns equals
 * {@code total incentives + avoided EC − penalty} — the economics identity computed by the verified
 * function-calling tool, never free-generated (Req 6.5). The property holds across the whole input
 * space, including the net-cost case where the penalty exceeds incentives (a negative net position).
 *
 * <p><b>Validates: Requirements 6.5</b>
 *
 * @see DefaultEconomicsEngine
 * @see NetPosition
 */
// Feature: terravow, Property 22: Net position is the economics identity
class NetPositionIdentityProperties {

    private final DefaultEconomicsEngine engine =
            new DefaultEconomicsEngine(
                    emptyGrounding(),
                    new EnvironmentalCompensationTool(),
                    new NetPositionTool(),
                    f -> List.of(),
                    allEligibleWithNoValue());

    /**
     * For an arbitrary pair of eligible incentive values (PAT/ESCerts and depreciation), an eligible
     * avoided-EC value, and a computed EC penalty, the net position equals
     * {@code (pat + depreciation + statePolicy) + avoidedEc − penalty} (Req 6.5).
     *
     * <p>Bounds stay well within {@code long} so the identity never overflows; the ranges span zero
     * and large values so both net-benefit and net-cost outcomes are generated.
     *
     * @param pat         the PAT/ESCerts incentive value in minor units; {@code [0, 5_000_000]}
     * @param deprec      the depreciation incentive value in minor units; {@code [0, 5_000_000]}
     * @param statePolicy the state-policy incentive value in minor units; {@code [0, 5_000_000]}
     * @param avoidedEc   the avoided-EC value in minor units; {@code [0, 5_000_000]}
     * @param penalty     the computed EC penalty in minor units; {@code [0, 5_000_000]}
     */
    @Property(tries = 100)
    void netPositionEqualsIncentivesPlusAvoidedEcMinusPenalty(
            @ForAll @LongRange(min = 0L, max = 5_000_000L) long pat,
            @ForAll @LongRange(min = 0L, max = 5_000_000L) long deprec,
            @ForAll @LongRange(min = 0L, max = 5_000_000L) long statePolicy,
            @ForAll @LongRange(min = 0L, max = 5_000_000L) long avoidedEc,
            @ForAll @LongRange(min = 0L, max = 5_000_000L) long penalty) {

        IncentiveAssessment incentives =
                IncentiveAssessment.of(
                        Currency.INR,
                        List.of(
                                IncentiveItem.eligible(
                                        IncentiveKind.PAT_ESCERTS, inr(pat)),
                                IncentiveItem.eligible(
                                        IncentiveKind.IT_ACT_SECTION_32_DEPRECIATION, inr(deprec)),
                                IncentiveItem.eligible(
                                        IncentiveKind.STATE_INDUSTRIAL_POLICY, inr(statePolicy)),
                                IncentiveItem.eligible(
                                        IncentiveKind.AVOIDED_EC, inr(avoidedEc))),
                        EvidenceChain.EMPTY);

        EcExposure penaltyExposure = computedPenalty(penalty);

        NetPosition net = engine.netPosition(penaltyExposure, incentives);

        // The identity: total incentives (excluding avoided EC) + avoided EC − penalty.
        long expected = (pat + deprec + statePolicy) + avoidedEc - penalty;

        assertThat(net.status()).isEqualTo(DeterminationStatus.COMPUTED);
        assertThat(net.value()).contains(inr(expected));
        assertThat(net.label()).isEqualTo(AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    /** A COMPUTED EC exposure carrying {@code penalty} minor units of INR with a cited source. */
    private static EcExposure computedPenalty(long penalty) {
        EvidenceChain evidence =
                EvidenceChain.EMPTY.with(
                        new EvidenceLink(
                                SourceType.REGULATORY_TEXT, new SourceRef("gcs://corpus/ec.pdf")));
        return new EcExposure(
                DeterminationStatus.COMPUTED,
                Optional.of(inr(penalty)),
                List.of(),
                Optional.empty(),
                evidence,
                AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    private static Money inr(long minorUnits) {
        return new Money(minorUnits, Currency.INR);
    }

    private static RegulatoryGrounding emptyGrounding() {
        return new DefaultRegulatoryGrounding(InMemoryRegulatoryCorpus.builder().build());
    }

    /** An incentive port that is never consulted in these tests (assessment is built directly). */
    private static IncentivePort allEligibleWithNoValue() {
        return (kind, context) -> IncentiveItem.eligible(kind, new Money(0L, context.currency()));
    }
}
