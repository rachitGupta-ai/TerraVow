package com.terravow.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based tests for the {@link DefaultDecisionGate} confidence classification partition.
 *
 * <p>Validates Correctness Property 28: for any non-vetoed recommendation (hard veto cleared via
 * {@link HarmDelta#NONE}) carrying a present confidence in {@code [0.0, 1.0]} and any thresholds
 * where {@code askThreshold <= allowThreshold}, the gate assigns exactly one decision — ALLOW when
 * {@code confidence >= allowThreshold}, ASK when {@code askThreshold <= confidence < allowThreshold},
 * and BLOCK when {@code confidence < askThreshold}. The three bands are total and disjoint over the
 * whole unit interval, and every below-allow decision (ASK or BLOCK) withholds automatic
 * application and routes to human review (Req 8.1, 8.2, 15.9).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 8.2, 15.9</a>
 */
class ConfidenceClassificationPartitionProperties {

    private final DecisionGate gate = new DefaultDecisionGate();

    private static EvidenceChain readingChain() {
        return EvidenceChain.EMPTY.with(
                new EvidenceLink(SourceType.READING, new SourceRef("reading-1")));
    }

    // Feature: terravow, Property 28: Confidence classification partitions the decision space.
    @Property(tries = 100)
    void classificationMatchesTheConfidenceBand(
            @ForAll("thresholds") GateThresholds thresholds,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double confidence) {
        Recommendation rec =
                Recommendation.of("rec-1", new Confidence(confidence), readingChain());

        GateVerdict verdict = gate.classify(rec, thresholds, HarmDelta.NONE);

        GateDecision expected;
        if (confidence >= thresholds.allowThreshold()) {
            expected = GateDecision.ALLOW;
        } else if (confidence >= thresholds.askThreshold()) {
            expected = GateDecision.ASK;
        } else {
            expected = GateDecision.BLOCK;
        }

        assertThat(verdict.decision()).isEqualTo(expected);
        // No veto on the NONE harm delta.
        assertThat(verdict.vetoApplied()).isFalse();
    }

    // Feature: terravow, Property 28: Confidence classification partitions the decision space.
    @Property(tries = 100)
    void exactlyOneBandAppliesForEveryConfidence(
            @ForAll("thresholds") GateThresholds thresholds,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double confidence) {
        Recommendation rec =
                Recommendation.of("rec-1", new Confidence(confidence), readingChain());

        GateVerdict verdict = gate.classify(rec, thresholds, HarmDelta.NONE);

        // The partition is total: the decision is always exactly one of the three bands.
        assertThat(verdict.decision())
                .isIn(GateDecision.ALLOW, GateDecision.ASK, GateDecision.BLOCK);

        // The partition is disjoint: exactly one of the mutually exclusive band predicates holds,
        // and it is the one matching the assigned decision.
        boolean inAllow = confidence >= thresholds.allowThreshold();
        boolean inAsk =
                confidence >= thresholds.askThreshold()
                        && confidence < thresholds.allowThreshold();
        boolean inBlock = confidence < thresholds.askThreshold();

        int bandsSatisfied = (inAllow ? 1 : 0) + (inAsk ? 1 : 0) + (inBlock ? 1 : 0);
        assertThat(bandsSatisfied).isEqualTo(1);

        switch (verdict.decision()) {
            case ALLOW -> assertThat(inAllow).isTrue();
            case ASK -> assertThat(inAsk).isTrue();
            case BLOCK -> assertThat(inBlock).isTrue();
        }
    }

    // Feature: terravow, Property 28: Confidence classification partitions the decision space.
    @Property(tries = 100)
    void belowAllowDecisionsRouteToHumanReviewAndAllowDoesNot(
            @ForAll("thresholds") GateThresholds thresholds,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double confidence) {
        Recommendation rec =
                Recommendation.of("rec-1", new Confidence(confidence), readingChain());

        GateVerdict verdict = gate.classify(rec, thresholds, HarmDelta.NONE);

        // Any below-allow decision (ASK or BLOCK) withholds automatic application and routes to
        // human review; ALLOW is applied automatically and is never routed (Req 8.2, 15.9).
        boolean belowAllow = verdict.decision() != GateDecision.ALLOW;
        assertThat(verdict.routedToHumanReview()).isEqualTo(belowAllow);
    }

    /**
     * Well-formed thresholds with {@code 0.0 <= askThreshold <= allowThreshold <= 1.0}. Generated by
     * drawing two values in the unit interval and ordering them so the ask threshold never exceeds
     * the allow threshold, matching the {@link GateThresholds} structural invariant (Req 8.1).
     */
    @Provide
    Arbitrary<GateThresholds> thresholds() {
        Arbitrary<Double> a = Arbitraries.doubles().between(0.0, 1.0);
        Arbitrary<Double> b = Arbitraries.doubles().between(0.0, 1.0);
        return Combinators.combine(a, b).as((x, y) -> {
            double allow = Math.max(x, y);
            double ask = Math.min(x, y);
            return new GateThresholds(allow, ask);
        });
    }
}
