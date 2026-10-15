package com.terravow.pii.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.SyntheticLabel;

import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for synthetic-label propagation across an arbitrary derivation graph
 * (Req 13.2, 14.15).
 *
 * <p>Validates Correctness Property 37: for any derivation chain or graph built from a mix of
 * {@link SyntheticLabel#REAL REAL} and {@link SyntheticLabel#SYNTHETIC SYNTHETIC} source inputs, any
 * output derived — directly or transitively — from any synthetic input is itself labelled
 * {@link SyntheticLabel#SYNTHETIC}, while an output derived solely from real inputs stays
 * {@link SyntheticLabel#REAL}. The propagation carrier {@link Labeled} and the
 * {@link SyntheticLabel#combine} fold enforce the rule by construction, independent of graph shape
 * or evaluation order.
 *
 * @see Labeled
 * @see SyntheticLabel#combine
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.2, 14.15</a>
 */
class SyntheticLabelPropagationProperties {

    // Feature: terravow, Property 37: Synthetic label propagates to all derived outputs.
    //
    // A single-step derivation (map) carries its one input's label forward unchanged: a synthetic
    // source yields a synthetic output, a real source yields a real output.
    @Property(tries = 100)
    void mapPreservesSourceLabel(@ForAll("sourceLabel") SyntheticLabel sourceLabel) {
        Labeled<Integer> source = new Labeled<>(1, sourceLabel);

        Labeled<Integer> derived = source.map(v -> v + 41);

        assertThat(derived.label()).isEqualTo(sourceLabel);
        assertThat(derived.isSynthetic()).isEqualTo(sourceLabel == SyntheticLabel.SYNTHETIC);
    }

    // Feature: terravow, Property 37: Synthetic label propagates to all derived outputs.
    //
    // Folding an arbitrary set of labelled inputs into one derived output yields SYNTHETIC iff any
    // input is synthetic, and REAL iff every input is real — regardless of input count or order.
    @Property(tries = 100)
    void derivedOutputIsSyntheticIffAnyInputSynthetic(
            @ForAll("labelLists") List<SyntheticLabel> inputLabels) {
        List<Labeled<Integer>> inputs = new ArrayList<>();
        boolean anySynthetic = false;
        int i = 0;
        for (SyntheticLabel label : inputLabels) {
            inputs.add(new Labeled<>(i++, label));
            anySynthetic = anySynthetic || label == SyntheticLabel.SYNTHETIC;
        }

        Labeled<Integer> derived = Labeled.derive(inputs, 999);

        assertThat(derived.isSynthetic()).isEqualTo(anySynthetic);
        assertThat(derived.label())
                .isEqualTo(anySynthetic ? SyntheticLabel.SYNTHETIC : SyntheticLabel.REAL);
    }

    // Feature: terravow, Property 37: Synthetic label propagates to all derived outputs.
    //
    // Transitive propagation across a randomly-wired multi-stage derivation graph: build a chain of
    // layers where every node derives from a subset of the previous layer's nodes. A node is
    // synthetic iff it transitively reaches at least one synthetic source. The Labeled fold must
    // match this independently-computed reachability over the whole graph.
    @Property(tries = 100)
    void syntheticTaintPropagatesTransitivelyThroughGraph(
            @ForAll("sourceLabelLists") List<SyntheticLabel> sourceLabels,
            @ForAll @IntRange(min = 1, max = 4) int layers,
            @ForAll @IntRange(min = 1, max = 5) int nodesPerLayer,
            @ForAll long wiring) {

        // Layer 0: the sources, each carrying its generated label; track synthetic reachability.
        List<Labeled<Integer>> previous = new ArrayList<>();
        List<Boolean> previousSynthetic = new ArrayList<>();
        int value = 0;
        for (SyntheticLabel label : sourceLabels) {
            previous.add(new Labeled<>(value++, label));
            previousSynthetic.add(label == SyntheticLabel.SYNTHETIC);
        }

        // Subsequent layers: each node derives from a deterministic non-empty subset of the prior
        // layer, selected from the per-run wiring seed so the graph shape varies across tries.
        long rng = wiring;
        for (int layer = 0; layer < layers; layer++) {
            List<Labeled<Integer>> current = new ArrayList<>();
            List<Boolean> currentSynthetic = new ArrayList<>();
            for (int n = 0; n < nodesPerLayer; n++) {
                List<Labeled<Integer>> parents = new ArrayList<>();
                boolean reachesSynthetic = false;
                for (int p = 0; p < previous.size(); p++) {
                    rng = nextSeed(rng);
                    // Always include the first parent so every node has at least one input.
                    boolean include = p == 0 || (rng & 1L) == 0L;
                    if (include) {
                        parents.add(previous.get(p));
                        reachesSynthetic = reachesSynthetic || previousSynthetic.get(p);
                    }
                }
                current.add(Labeled.derive(parents, value++));
                currentSynthetic.add(reachesSynthetic);
            }
            previous = current;
            previousSynthetic = currentSynthetic;
        }

        // Every node in the final layer must be labelled synthetic exactly when it transitively
        // reaches a synthetic source, matching the independent reachability computation.
        for (int n = 0; n < previous.size(); n++) {
            assertThat(previous.get(n).isSynthetic()).isEqualTo(previousSynthetic.get(n));
        }
    }

    // Feature: terravow, Property 37: Synthetic label propagates to all derived outputs.
    //
    // A real-only derivation graph never produces a synthetic output, so a synthetic label is never
    // fabricated where no synthetic source exists.
    @Property(tries = 100)
    void realOnlyGraphNeverProducesSyntheticOutput(
            @ForAll @IntRange(min = 1, max = 8) int sourceCount,
            @ForAll @IntRange(min = 1, max = 4) int derivationSteps) {
        List<Labeled<Integer>> inputs = new ArrayList<>();
        for (int i = 0; i < sourceCount; i++) {
            inputs.add(Labeled.real(i));
        }

        Labeled<Integer> derived = Labeled.derive(inputs, 0);
        for (int step = 0; step < derivationSteps; step++) {
            derived = derived.deriveWith(Labeled.real(step), Integer::sum);
        }

        assertThat(derived.isSynthetic()).isFalse();
        assertThat(derived.label()).isEqualTo(SyntheticLabel.REAL);
    }

    // Feature: terravow, Property 37: Synthetic label propagates to all derived outputs.
    //
    // End-to-end: a generated synthetic record flows into a decision-oriented output through the
    // Labeled carrier, and the output remains labelled synthetic however it is combined with real
    // inputs downstream.
    @Property(tries = 100)
    void generatedSyntheticRecordTaintsDerivedDecisionOutput(
            @ForAll long seed, @ForAll @IntRange(min = 0, max = 5) int realCombinations) {
        SyntheticRecord record =
                new DefaultSyntheticDataGenerator()
                        .generate(
                                "stack-cems",
                                List.of(new ParameterRange("stack.so2", 10.0, 200.0, "mg/Nm3")),
                                seed);

        Labeled<Double> derived = record.asLabeled().map(r -> r.values().get("stack.so2"));
        for (int i = 0; i < realCombinations; i++) {
            derived = derived.deriveWith(Labeled.real((double) i), Double::sum);
        }

        DecisionOrientedOutput output = DecisionOrientedOutput.ofDerived(derived);

        assertThat(output.isSynthetic()).isTrue();
        assertThat(output.label()).isEqualTo(SyntheticLabel.SYNTHETIC);
        assertThat(output.surface()).contains("[SYNTHETIC DATA]");
    }

    /** A single source label, either REAL or SYNTHETIC with equal frequency. */
    @Provide
    Arbitrary<SyntheticLabel> sourceLabel() {
        return Arbitraries.of(SyntheticLabel.REAL, SyntheticLabel.SYNTHETIC);
    }

    /** A non-empty list of labels used as derivation inputs. */
    @Provide
    Arbitrary<List<SyntheticLabel>> labelLists() {
        return Arbitraries.of(SyntheticLabel.REAL, SyntheticLabel.SYNTHETIC)
                .list()
                .ofMinSize(1)
                .ofMaxSize(12);
    }

    /** A non-empty list of source labels for a graph's input layer. */
    @Provide
    Arbitrary<List<SyntheticLabel>> sourceLabelLists() {
        return Arbitraries.of(SyntheticLabel.REAL, SyntheticLabel.SYNTHETIC)
                .list()
                .ofMinSize(1)
                .ofMaxSize(6);
    }

    /** SplitMix64-style finaliser producing the next deterministic wiring seed. */
    private static long nextSeed(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
