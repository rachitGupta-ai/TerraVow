package com.terravow.gate;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Property-based test for the fail-closed handling of invalid/absent confidence by
 * {@link DefaultDecisionGate} (Req 8.5).
 *
 * <p>Validates Correctness Property 29: a recommendation produced <em>without</em> a confidence
 * value is the canonical invalid-confidence case — the {@link com.terravow.domain.Confidence} record
 * enforces the {@code [0,1]} invariant structurally, so an out-of-range numeric value is
 * unrepresentable and absence ({@link Recommendation#withoutConfidence}) captures the invalid input.
 * For any such recommendation, and for any well-formed {@link GateThresholds}, when no hard veto
 * pre-empts classification (harm delta is {@link HarmDelta#NONE}, so the veto in step 1 does not
 * fire), the gate must fail closed: it classifies {@link GateDecision#BLOCK} and routes the
 * recommendation to human review. The gate never ALLOWs on invalid confidence, independent of how
 * the allow/ask thresholds are positioned.
 *
 * <p>The invariant is checked across the full threshold space (arbitrary ordered thresholds in
 * {@code [0,1]}) and arbitrary Evidence_Chains, confirming the fail-closed outcome does not depend
 * on the threshold values the recommendation would otherwise be compared against.
 *
 * <p><strong>Validates: Requirements 8.5</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.5</a>
 */
class InvalidConfidenceFailClosedProperties {

    private final DecisionGate gate = new DefaultDecisionGate();

    // Feature: terravow, Property 29: Invalid confidence forces BLOCK and human review
    @Property(tries = 100)
    void absentConfidenceWithNoVetoAlwaysBlocksAndRoutesToReview(
            @ForAll("evidenceChains") EvidenceChain evidence,
            @ForAll("thresholds") GateThresholds thresholds) {

        // A recommendation with no confidence value is the invalid-confidence case (Req 8.5).
        Recommendation rec = Recommendation.withoutConfidence("rec-1", evidence);

        // HarmDelta.NONE guarantees the hard veto (step 1) does not pre-empt the confidence-validity
        // check, so the outcome under test is the fail-closed path, not the veto path.
        GateVerdict verdict = gate.classify(rec, thresholds, HarmDelta.NONE);

        // Fail-closed: BLOCK and routed to human review, regardless of the thresholds (Req 8.5).
        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.routedToHumanReview()).isTrue();
        // The invalid-confidence path is distinct from the hard veto: no veto was applied here.
        assertThat(verdict.vetoApplied()).isFalse();
    }

    /** Well-formed thresholds: {@code 0 <= askThreshold <= allowThreshold <= 1}. */
    @Provide
    Arbitrary<GateThresholds> thresholds() {
        Arbitrary<Double> allow = Arbitraries.doubles().between(0.0, 1.0).filter(Double::isFinite);
        return allow.flatMap(allowThreshold ->
                Arbitraries.doubles()
                        .between(0.0, allowThreshold)
                        .filter(Double::isFinite)
                        .map(askThreshold -> new GateThresholds(allowThreshold, askThreshold)));
    }

    /** Evidence_Chains ranging from empty to a few links across all source types. */
    @Provide
    Arbitrary<EvidenceChain> evidenceChains() {
        return evidenceLinks().list().ofMinSize(0).ofMaxSize(5).map(EvidenceChain::new);
    }

    private Arbitrary<EvidenceLink> evidenceLinks() {
        Arbitrary<SourceType> types = Arbitraries.of(SourceType.class);
        Arbitrary<SourceRef> refs = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(24)
                .map(SourceRef::new);
        return Combinators.combine(types, refs).as(EvidenceLink::new);
    }
}
