package com.terravow.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based test for the Decision_Gate hard-veto precedence (Req 8.3, 8.4).
 *
 * <p>Validates <b>Property 27: Hard veto forces BLOCK regardless of confidence</b>: whenever a
 * recommendation would increase total emission mass or cross-media load by any amount above zero,
 * {@link DecisionGate#classify} returns {@link GateDecision#BLOCK} with {@code vetoApplied = true}
 * — and this holds for <em>any</em> confidence value (including a confidence at or above the allow
 * threshold) and even when confidence is absent. The hard veto runs <em>before</em> confidence is
 * examined, so a harm-increasing recommendation can never be ALLOWed or ASKed through on the
 * strength of a high confidence (Req 8.4).
 *
 * <p><b>Validates: Requirements 8.3, 8.4</b>
 *
 * @see DefaultDecisionGate
 */
// Feature: terravow, Property 27: Hard veto forces BLOCK regardless of confidence
class HardVetoPrecedenceProperties {

    private final DecisionGate gate = new DefaultDecisionGate();

    private static EvidenceChain readingChain() {
        return EvidenceChain.EMPTY.with(
                new EvidenceLink(SourceType.READING, new SourceRef("reading-1")));
    }

    /**
     * A harm-increasing delta forces BLOCK with the veto applied and the recommendation routed to
     * human review, independent of the confidence (present at any value in {@code [0,1]} — including
     * values at or above the allow threshold — or absent) and of the thresholds (Req 8.3, 8.4).
     */
    @Property(tries = 100)
    void harmIncreasingDeltaForcesBlockWithVetoRegardlessOfConfidence(
            @ForAll("harmIncreasingDeltas") HarmDelta harm,
            @ForAll("anyConfidence") Optional<Confidence> confidence,
            @ForAll("thresholds") GateThresholds thresholds) {

        Recommendation rec = new Recommendation("rec-1", confidence, readingChain());

        GateVerdict verdict = gate.classify(rec, thresholds, harm);

        assertThat(verdict.decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(verdict.vetoApplied()).isTrue();
        assertThat(verdict.routedToHumanReview()).isTrue();
        // The veto preserves the recommendation's supporting readings (Req 8.3).
        assertThat(verdict.evidence()).isEqualTo(rec.evidence());
    }

    /**
     * Harm deltas that trip the veto: at least one axis is strictly greater than zero. The other
     * axis ranges over negative, zero, and positive values so both "mass only", "cross-media only",
     * and "both" cases are generated.
     */
    @Provide
    Arbitrary<HarmDelta> harmIncreasingDeltas() {
        // A strictly-positive magnitude. A small positive lower bound keeps the generator within
        // a representable decimal scale while still exercising tiny increments (the veto trips on
        // any amount above zero, so the exact magnitude is immaterial).
        Arbitrary<Double> positive =
                Arbitraries.doubles().between(0.0001, 1_000_000.0).ofScale(4);
        Arbitrary<Double> anyFinite =
                Arbitraries.doubles().between(-1_000_000.0, 1_000_000.0).ofScale(4);

        // mass > 0, cross-media any
        Arbitrary<HarmDelta> massPositive =
                Combinators.combine(positive, anyFinite).as(HarmDelta::new);
        // cross-media > 0, mass any
        Arbitrary<HarmDelta> crossPositive =
                Combinators.combine(anyFinite, positive).as(HarmDelta::new);

        return Arbitraries.oneOf(massPositive, crossPositive)
                .filter(HarmDelta::increasesHarm);
    }

    /**
     * Any confidence input the gate may receive: a present {@link Confidence} spanning the whole
     * unit interval (so values at or above the allow threshold are included), or an absent
     * confidence. The veto must dominate both.
     */
    @Provide
    Arbitrary<Optional<Confidence>> anyConfidence() {
        Arbitrary<Optional<Confidence>> present =
                Arbitraries.doubles().between(0.0, 1.0).ofScale(4)
                        .map(v -> Optional.of(new Confidence(v)));
        Arbitrary<Optional<Confidence>> absent = Arbitraries.just(Optional.empty());
        return Arbitraries.oneOf(present, absent);
    }

    /** Well-formed thresholds with {@code 0 <= askThreshold <= allowThreshold <= 1}. */
    @Provide
    Arbitrary<GateThresholds> thresholds() {
        return Combinators.combine(
                        Arbitraries.doubles().between(0.0, 1.0).ofScale(4),
                        Arbitraries.doubles().between(0.0, 1.0).ofScale(4))
                .as((a, b) -> {
                    double allow = Math.max(a, b);
                    double ask = Math.min(a, b);
                    return new GateThresholds(allow, ask);
                });
    }
}
