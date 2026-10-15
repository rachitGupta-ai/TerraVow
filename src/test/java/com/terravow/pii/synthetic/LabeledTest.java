package com.terravow.pii.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.SyntheticLabel;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Labeled}: the synthetic label propagates to every output derived — directly
 * and transitively — from synthetic data, in any order (Req 13.2, 14.15).
 */
class LabeledTest {

    // Req 13.2, 14.15 — a value derived from a synthetic input via map is itself synthetic.
    @Test
    void mapPropagatesSyntheticLabelToDirectDerivation() {
        Labeled<Integer> synthetic = Labeled.synthetic(10);

        Labeled<Integer> derived = synthetic.map(v -> v * 2);

        assertThat(derived.value()).isEqualTo(20);
        assertThat(derived.isSynthetic()).isTrue();
    }

    // Req 13.2 — a value derived solely from real inputs via map stays real.
    @Test
    void mapKeepsRealLabelForRealInput() {
        Labeled<Integer> real = Labeled.real(10);

        Labeled<Integer> derived = real.map(v -> v + 1);

        assertThat(derived.isSynthetic()).isFalse();
    }

    // Req 13.2 — a derivation that combines a real and a synthetic input is synthetic (any synthetic
    // input taints the output).
    @Test
    void deriveWithTaintsOutputWhenEitherInputSynthetic() {
        Labeled<Integer> real = Labeled.real(3);
        Labeled<Integer> synthetic = Labeled.synthetic(4);

        Labeled<Integer> fromRealThenSynthetic = real.deriveWith(synthetic, Integer::sum);
        Labeled<Integer> fromSyntheticThenReal = synthetic.deriveWith(real, Integer::sum);

        assertThat(fromRealThenSynthetic.isSynthetic()).isTrue();
        assertThat(fromSyntheticThenReal.isSynthetic()).isTrue();
        assertThat(fromRealThenSynthetic.value()).isEqualTo(7);
        // Commutative: order of combination does not change the propagated label.
        assertThat(fromRealThenSynthetic.label()).isEqualTo(fromSyntheticThenReal.label());
    }

    // Req 13.2 — all-real inputs combine to a real output.
    @Test
    void deriveWithStaysRealWhenAllInputsReal() {
        Labeled<Integer> a = Labeled.real(1);
        Labeled<Integer> b = Labeled.real(2);

        assertThat(a.deriveWith(b, Integer::sum).isSynthetic()).isFalse();
    }

    // Req 13.2, 14.15 — folding many inputs yields SYNTHETIC iff any input is synthetic, regardless
    // of order (combine is commutative and associative).
    @Test
    void propagatedLabelIsSyntheticIffAnyInputSynthetic() {
        SyntheticLabel allReal =
                Labeled.propagatedLabel(List.of(Labeled.real(1), Labeled.real(2), Labeled.real(3)));
        SyntheticLabel oneSynthetic =
                Labeled.propagatedLabel(List.of(Labeled.real(1), Labeled.synthetic(2), Labeled.real(3)));
        SyntheticLabel reordered =
                Labeled.propagatedLabel(List.of(Labeled.synthetic(2), Labeled.real(3), Labeled.real(1)));

        assertThat(allReal).isEqualTo(SyntheticLabel.REAL);
        assertThat(oneSynthetic).isEqualTo(SyntheticLabel.SYNTHETIC);
        assertThat(reordered).isEqualTo(SyntheticLabel.SYNTHETIC);
    }

    // Req 13.2 — a value derived from no synthetic input (empty fold) is real (identity element).
    @Test
    void propagatedLabelOfNoInputsIsReal() {
        assertThat(Labeled.propagatedLabel(List.of())).isEqualTo(SyntheticLabel.REAL);
    }

    // Req 13.2, 14.15 — the synthetic label propagates TRANSITIVELY across a multi-step derivation
    // chain: synthetic → derived → derived-of-derived all remain synthetic.
    @Test
    void syntheticLabelPropagatesTransitivelyAcrossChain() {
        // Step 1: a synthetic source value.
        Labeled<Integer> source = Labeled.synthetic(5);
        // Step 2: an output derived directly from the synthetic source.
        Labeled<Integer> firstDerived = source.map(v -> v + 100);
        // Step 3: an output derived from the first derived output combined with a REAL input.
        Labeled<Integer> secondDerived = firstDerived.deriveWith(Labeled.real(1), Integer::sum);
        // Step 4: an output derived from the second derived output alone.
        Labeled<Integer> thirdDerived = secondDerived.map(v -> v * 2);

        assertThat(firstDerived.isSynthetic()).isTrue();
        assertThat(secondDerived.isSynthetic()).isTrue();
        assertThat(thirdDerived.isSynthetic()).isTrue();
        assertThat(thirdDerived.value()).isEqualTo((5 + 100 + 1) * 2);
    }

    // Req 13.2, 14.15 — a synthetic record flows into a derived decision output and the output is
    // labelled synthetic (end-to-end propagation from generation to a derived result).
    @Test
    void syntheticRecordPropagatesIntoDerivedOutput() {
        SyntheticRecord record =
                new DefaultSyntheticDataGenerator()
                        .generate(
                                "stack-cems",
                                List.of(new ParameterRange("stack.so2", 10.0, 200.0, "mg/Nm3")),
                                11L);

        // Derive an EID-like figure from the synthetic reading and a real baseline.
        Labeled<Double> reading = record.asLabeled().map(r -> r.values().get("stack.so2"));
        Labeled<Double> baseline = Labeled.real(8.0);
        Labeled<Double> eid = reading.deriveWith(baseline, (current, base) -> current - base);

        assertThat(eid.isSynthetic()).isTrue();
    }
}
