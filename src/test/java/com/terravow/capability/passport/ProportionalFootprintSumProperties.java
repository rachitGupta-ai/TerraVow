package com.terravow.capability.passport;

// Feature: terravow, Property 24: Passport footprint is the proportional sum of consumed inputs

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.BatchId;
import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for proportional downstream footprint propagation (Property 24).
 *
 * <p>When a downstream batch consumes a set of input batches, its Embedded Pollution Passport
 * footprint is the sum of each consumed input's embedded footprint weighted by the proportion of
 * that input's quantity consumed — the quantity-weighted mean
 * {@code sum(footprint_i * quantity_i) / sum(quantity_i)} (Req 7.2). This property generates
 * arbitrary consumed inputs, each with a strictly-positive quantity and a Passport carrying an
 * arbitrary non-negative footprint, and checks that
 * {@link PassportService#propagate(BatchId, List)} returns exactly that quantity-weighted mean for
 * every input combination.
 *
 * <p><strong>Validates: Requirements 7.2</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.2</a>
 */
class ProportionalFootprintSumProperties {

    private static final double TOLERANCE = 1.0e-9;

    private final PassportService service = new DefaultPassportService();

    /**
     * Property 24: for any set of consumed inputs, the propagated downstream footprint equals the
     * quantity-weighted mean of the input footprints (Req 7.2).
     */
    @Property(tries = 100)
    void propagatedFootprintIsQuantityWeightedMean(
            @ForAll("consumedInputs") @Size(min = 1, max = 8) List<ConsumedInput> inputs) {
        Passport downstream = service.propagate(new BatchId("downstream"), inputs);

        double weightedSum = 0.0;
        double totalQuantity = 0.0;
        for (ConsumedInput input : inputs) {
            weightedSum += input.passport().embeddedFootprint() * input.quantity();
            totalQuantity += input.quantity();
        }
        double expected = weightedSum / totalQuantity;

        assertThat(downstream.embeddedFootprint()).isCloseTo(expected, within());
        // The weighted mean is bounded by the min and max input footprints.
        double minFootprint =
                inputs.stream().mapToDouble(i -> i.passport().embeddedFootprint()).min().orElseThrow();
        double maxFootprint =
                inputs.stream().mapToDouble(i -> i.passport().embeddedFootprint()).max().orElseThrow();
        assertThat(downstream.embeddedFootprint())
                .isGreaterThanOrEqualTo(minFootprint - TOLERANCE)
                .isLessThanOrEqualTo(maxFootprint + TOLERANCE);
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(TOLERANCE);
    }

    /**
     * Generates lists of consumed inputs with distinct batch ids, each carrying a strictly-positive
     * quantity and a Passport with an arbitrary finite non-negative footprint (Req 7.2).
     *
     * @return a generator of consumed-input lists
     */
    @Provide
    Arbitrary<List<ConsumedInput>> consumedInputs() {
        Arbitrary<Double> footprints = Arbitraries.doubles().between(0.0, 10_000.0).ofScale(4);
        Arbitrary<Double> quantities =
                Arbitraries.doubles().between(0.001, 10_000.0).ofScale(4).filter(q -> q > 0.0);
        Arbitrary<ConsumedInput> input =
                Combinators.combine(footprints, quantities)
                        .as((footprint, quantity) -> {
                            BatchId batch = new BatchId("input-" + System.identityHashCode(footprint));
                            Passport passport =
                                    new Passport(
                                            batch,
                                            footprint,
                                            new Confidence(0.9),
                                            new EvidenceChain(
                                                    List.of(
                                                            new EvidenceLink(
                                                                    SourceType.READING,
                                                                    new SourceRef("reading")))));
                            return new ConsumedInput(batch, quantity, passport);
                        });
        // Ensure distinct batch ids per element so inputs are well-formed independent of generation.
        return input.list().ofMinSize(1).ofMaxSize(8).map(ProportionalFootprintSumProperties::withDistinctBatchIds);
    }

    /** Rewrites each element with a unique batch id so the list is always well-formed. */
    private static List<ConsumedInput> withDistinctBatchIds(List<ConsumedInput> raw) {
        List<ConsumedInput> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            ConsumedInput original = raw.get(i);
            BatchId batch = new BatchId("input-" + i);
            Passport passport =
                    new Passport(
                            batch,
                            original.passport().embeddedFootprint(),
                            original.passport().confidence(),
                            original.passport().evidence());
            result.add(new ConsumedInput(batch, original.quantity(), passport));
        }
        return result;
    }
}
