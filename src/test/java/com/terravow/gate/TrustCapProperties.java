package com.terravow.gate;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the Decision_Gate trust-score cap (Req 16.15, 16.16).
 *
 * <p>Feature: terravow, Property 58: Sub-threshold trust caps the Decision_Gate at ASK.
 * <p>Feature: terravow, Property 59: Trust never raises a classification and never lifts the hard veto.
 */
class TrustCapProperties {

    @Property(tries = 100)
    @Label("Feature: terravow, Property 58: Sub-threshold trust caps the Decision_Gate at ASK")
    void subThresholdTrustCapsAtAsk(
            @ForAll @DoubleRange(min = 0.75, max = 1.0) double highConfidence,
            @ForAll @DoubleRange(min = 0.0, max = 0.59) double lowTrust,
            @ForAll @DoubleRange(min = 0.60, max = 1.0) double trustThreshold) {
        // Given: a recommendation with confidence >= allow threshold (would normally be ALLOW)
        // and a governing trust < trust threshold
        DecisionGate gate = new DefaultDecisionGate();
        Recommendation rec = Recommendation.of("test", new Confidence(highConfidence), EvidenceChain.EMPTY);
        GateThresholds thresholds = new GateThresholds(0.75, 0.40);
        HarmDelta noHarm = new HarmDelta(0.0, 0.0);
        TrustContext trustContext = new TrustContext(lowTrust, trustThreshold);

        // When: the gate classifies the recommendation
        GateVerdict verdict = gate.classify(rec, thresholds, noHarm, trustContext);

        // Then: the classification is capped at ASK (not ALLOW), trust-capped flag is set,
        // and the recommendation is routed to human review
        assertThat(verdict.decision()).isEqualTo(GateDecision.ASK);
        assertThat(verdict.trustCapped()).isTrue();
        assertThat(verdict.routedToHumanReview()).isTrue();
        assertThat(verdict.vetoApplied()).isFalse();
    }

    @Property(tries = 100)
    @Label("Feature: terravow, Property 59 part 1: Trust never raises a classification")
    void trustNeverRaisesClassification(
            @ForAll @DoubleRange(min = 0.0, max = 0.39) double lowConfidence,
            @ForAll @DoubleRange(min = 0.8, max = 1.0) double highTrust,
            @ForAll @DoubleRange(min = 0.0, max = 0.79) double trustThreshold) {
        // Given: a recommendation with confidence < ask threshold (would be BLOCK)
        // and a governing trust > trust threshold (high trust, no cap applied)
        DecisionGate gate = new DefaultDecisionGate();
        Recommendation rec = Recommendation.of("test", new Confidence(lowConfidence), EvidenceChain.EMPTY);
        GateThresholds thresholds = new GateThresholds(0.75, 0.40);
        HarmDelta noHarm = new HarmDelta(0.0, 0.0);
        TrustContext trustContext = new TrustContext(highTrust, trustThreshold);

        // When: the gate classifies the recommendation
        GateVerdict verdict = gate.classify(rec, thresholds, noHarm, trustContext);

        // Then: the classification remains BLOCK (trust doesn't raise it to ASK or ALLOW)
        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.trustCapped()).isFalse();
        assertThat(verdict.routedToHumanReview()).isTrue();
    }

    @Property(tries = 100)
    @Label("Feature: terravow, Property 59 part 2: Trust never lifts the hard veto")
    void trustNeverLiftsHardVeto(
            @ForAll @DoubleRange(min = 0.75, max = 1.0) double highConfidence,
            @ForAll @DoubleRange(min = 0.8, max = 1.0) double highTrust,
            @ForAll @DoubleRange(min = 0.0, max = 0.79) double trustThreshold,
            @ForAll @DoubleRange(min = 0.01, max = 10.0) double harmDelta) {
        // Given: a recommendation with high confidence and high trust (both above thresholds)
        // but the recommendation would increase harm (hard veto applies)
        DecisionGate gate = new DefaultDecisionGate();
        Recommendation rec = Recommendation.of("test", new Confidence(highConfidence), EvidenceChain.EMPTY);
        GateThresholds thresholds = new GateThresholds(0.75, 0.40);
        HarmDelta harm = new HarmDelta(harmDelta, 0.0);
        TrustContext trustContext = new TrustContext(highTrust, trustThreshold);

        // When: the gate classifies the recommendation
        GateVerdict verdict = gate.classify(rec, thresholds, harm, trustContext);

        // Then: the hard veto still forces BLOCK (trust doesn't lift it)
        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.vetoApplied()).isTrue();
        assertThat(verdict.trustCapped()).isFalse();
        assertThat(verdict.routedToHumanReview()).isTrue();
    }

    @Property(tries = 100)
    @Label("Feature: terravow, Property 59 part 3: Trust cap does not affect ASK or BLOCK")
    void trustCapDoesNotAffectAskOrBlock(
            @ForAll @DoubleRange(min = 0.40, max = 0.74) double middleConfidence,
            @ForAll @DoubleRange(min = 0.0, max = 0.59) double lowTrust,
            @ForAll @DoubleRange(min = 0.60, max = 1.0) double trustThreshold) {
        // Given: a recommendation with confidence in the ASK range (not ALLOW)
        // and a governing trust < trust threshold
        DecisionGate gate = new DefaultDecisionGate();
        Recommendation rec = Recommendation.of("test", new Confidence(middleConfidence), EvidenceChain.EMPTY);
        GateThresholds thresholds = new GateThresholds(0.75, 0.40);
        HarmDelta noHarm = new HarmDelta(0.0, 0.0);
        TrustContext trustContext = new TrustContext(lowTrust, trustThreshold);

        // When: the gate classifies the recommendation
        GateVerdict verdict = gate.classify(rec, thresholds, noHarm, trustContext);

        // Then: the classification remains ASK (trust cap doesn't change it)
        assertThat(verdict.decision()).isEqualTo(GateDecision.ASK);
        assertThat(verdict.trustCapped()).isFalse();
        assertThat(verdict.routedToHumanReview()).isTrue();
    }
}
