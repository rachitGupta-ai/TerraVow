package com.terravow.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultDecisionGate}, exercising the ordered hard-veto-before-confidence
 * algorithm with specific examples and boundary values (Req 8.1–8.6).
 */
class DefaultDecisionGateTest {

    private final DecisionGate gate = new DefaultDecisionGate();
    private final GateThresholds thresholds = new GateThresholds(0.8, 0.5);

    private static EvidenceChain readingChain() {
        return EvidenceChain.EMPTY.with(
                new EvidenceLink(SourceType.READING, new SourceRef("reading-1")));
    }

    private static Recommendation rec(double confidence) {
        return Recommendation.of("rec-1", new Confidence(confidence), readingChain());
    }

    // Req 8.1 — confidence at/above the allow threshold yields ALLOW; ALLOW is not routed to review.
    @Test
    void confidenceAtOrAboveAllowThresholdYieldsAllow() {
        GateVerdict verdict = gate.classify(rec(0.8), thresholds, HarmDelta.NONE);

        assertThat(verdict.decision()).isEqualTo(GateDecision.ALLOW);
        assertThat(verdict.vetoApplied()).isFalse();
        assertThat(verdict.routedToHumanReview()).isFalse();
    }

    // Req 8.1 — confidence in [ask, allow) yields ASK and routes to human review (Req 8.2).
    @Test
    void confidenceInAskBandYieldsAskAndRoutesToReview() {
        GateVerdict verdict = gate.classify(rec(0.5), thresholds, HarmDelta.NONE);

        assertThat(verdict.decision()).isEqualTo(GateDecision.ASK);
        assertThat(verdict.routedToHumanReview()).isTrue();
        assertThat(verdict.vetoApplied()).isFalse();
    }

    // Req 8.1 — confidence below the ask threshold yields BLOCK and routes to review (Req 8.2).
    @Test
    void confidenceBelowAskThresholdYieldsBlock() {
        GateVerdict verdict = gate.classify(rec(0.49), thresholds, HarmDelta.NONE);

        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.routedToHumanReview()).isTrue();
        assertThat(verdict.vetoApplied()).isFalse();
    }

    // Req 8.3, 8.4 — a mass-increasing recommendation is BLOCK with veto even at full confidence.
    @Test
    void hardVetoOnMassIncreaseForcesBlockRegardlessOfConfidence() {
        GateVerdict verdict =
                gate.classify(rec(1.0), thresholds, new HarmDelta(0.0001, 0.0));

        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.vetoApplied()).isTrue();
        assertThat(verdict.routedToHumanReview()).isTrue();
    }

    // Req 8.3, 8.4 — a cross-media-increasing recommendation is vetoed before confidence is read.
    @Test
    void hardVetoOnCrossMediaIncreaseForcesBlock() {
        GateVerdict verdict =
                gate.classify(rec(1.0), thresholds, new HarmDelta(0.0, 0.0001));

        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.vetoApplied()).isTrue();
    }

    // Req 8.3 — the veto attaches the supporting readings carried on the recommendation.
    @Test
    void hardVetoAttachesSupportingReadings() {
        Recommendation r = rec(0.1);
        GateVerdict verdict = gate.classify(r, thresholds, new HarmDelta(1.0, 0.0));

        assertThat(verdict.evidence()).isEqualTo(r.evidence());
        assertThat(verdict.evidence().links())
                .anyMatch(link -> link.type() == SourceType.READING);
    }

    // Req 8.4 — the veto takes precedence over an absent confidence too (veto flag set, not just
    // the invalid-confidence BLOCK path).
    @Test
    void hardVetoPrecedesConfidenceValidityCheck() {
        Recommendation noConfidence = Recommendation.withoutConfidence("rec-1", readingChain());

        GateVerdict verdict =
                gate.classify(noConfidence, thresholds, new HarmDelta(2.0, 0.0));

        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.vetoApplied()).isTrue();
    }

    // Req 8.5 — absent confidence (no veto) forces BLOCK and human review, veto flag not set.
    @Test
    void absentConfidenceFailsClosedToBlockAndReview() {
        Recommendation noConfidence = Recommendation.withoutConfidence("rec-1", readingChain());

        GateVerdict verdict = gate.classify(noConfidence, thresholds, HarmDelta.NONE);

        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.vetoApplied()).isFalse();
        assertThat(verdict.routedToHumanReview()).isTrue();
    }

    // Req 8.1 — zero harm with zero deltas does not trip the veto; classification proceeds normally.
    @Test
    void zeroHarmDeltaDoesNotTripVeto() {
        GateVerdict verdict = gate.classify(rec(0.9), thresholds, HarmDelta.NONE);

        assertThat(verdict.decision()).isEqualTo(GateDecision.ALLOW);
        assertThat(verdict.vetoApplied()).isFalse();
    }

    // Req 8.4 — a negative (harm-reducing) delta must not trip the veto.
    @Test
    void negativeHarmDeltaDoesNotTripVeto() {
        GateVerdict verdict =
                gate.classify(rec(0.95), thresholds, new HarmDelta(-5.0, -2.0));

        assertThat(verdict.decision()).isEqualTo(GateDecision.ALLOW);
        assertThat(verdict.vetoApplied()).isFalse();
    }
}
