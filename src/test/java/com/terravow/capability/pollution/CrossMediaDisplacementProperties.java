package com.terravow.capability.pollution;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based test for cross-media displacement detection (Req 3.6, 3.7).
 *
 * <p>Validates <b>Property 11: Cross-media displacement is flagged on medium trade-off</b>: for any
 * remediation with before and after readings, the remediation is flagged as cross-media
 * displacement if and only if the pollutant load decreases in at least one environmental medium
 * while increasing in at least one other medium, and when flagged the result carries the supporting
 * readings and a non-empty Evidence_Chain.
 *
 * <p>The test generates a per-medium before/after load, derives the oracle (decrease in one medium
 * AND increase in another) independently of the implementation, and asserts the biconditional.
 *
 * <p><b>Validates: Requirements 3.6, 3.7</b>
 *
 * @see DefaultCrossMediaGuardrail
 */
// Feature: terravow, Property 11: Cross-media displacement is flagged on medium trade-off
class CrossMediaDisplacementProperties {

    private static final Pollutant POLLUTANT = new Pollutant("load");
    private final CrossMediaGuardrail guardrail = new DefaultCrossMediaGuardrail();

    /**
     * Flag is set iff some medium's total load strictly decreased while some other medium's total
     * load strictly increased; a set flag carries supporting readings and a non-empty
     * Evidence_Chain (Req 3.6, 3.7).
     */
    @Property(tries = 100)
    void displacementFlaggedIffDecreaseInOneAndIncreaseInAnother(
            @ForAll("mediumLoads") Map<EnvironmentalMedium, double[]> loads) {

        List<Reading> before = new ArrayList<>();
        List<Reading> after = new ArrayList<>();
        int id = 0;
        for (var entry : loads.entrySet()) {
            EnvironmentalMedium medium = entry.getKey();
            double b = entry.getValue()[0];
            double a = entry.getValue()[1];
            before.add(reading(medium, b, "before-" + (id)));
            after.add(reading(medium, a, "after-" + (id)));
            id++;
        }

        boolean anyDecrease = false;
        boolean anyIncrease = false;
        for (var entry : loads.entrySet()) {
            double b = entry.getValue()[0];
            double a = entry.getValue()[1];
            if (a < b) {
                anyDecrease = true;
            } else if (a > b) {
                anyIncrease = true;
            }
        }
        boolean expected = anyDecrease && anyIncrease;

        CrossMediaResult result =
                guardrail.assess(new Remediation("rem-1"), before, after);

        assertThat(result.displacement())
                .as("displacement flagged iff decrease in one medium and increase in another")
                .isEqualTo(expected);

        if (expected) {
            // Req 3.7 — the flag carries the supporting readings and a non-empty Evidence_Chain.
            assertThat(result.supportingReadings()).isNotEmpty();
            assertThat(result.supportingReadings()).containsAll(before);
            assertThat(result.supportingReadings()).containsAll(after);
            assertThat(result.evidence().links()).isNotEmpty();
            assertThat(result.evidence().links())
                    .allMatch(link -> link.type() == SourceType.READING);
        } else {
            assertThat(result.supportingReadings()).isEmpty();
        }
    }

    private static Reading reading(EnvironmentalMedium medium, double value, String refId) {
        return new Reading(
                new StreamId("stream-" + medium), POLLUTANT, medium, value,
                new SourceRef(refId));
    }

    /**
     * Generates, for a non-empty subset of media, a {@code [before, after]} load pair. Each medium
     * independently ranges over decreased, unchanged, and increased loads so the full space of
     * trade-offs (and non-trade-offs) is covered.
     */
    @Provide
    Arbitrary<Map<EnvironmentalMedium, double[]>> mediumLoads() {
        Arbitrary<Double> load = Arbitraries.doubles().between(0.0, 1000.0).ofScale(3);
        Arbitrary<double[]> pair =
                Combinators.combine(load, load).as((b, a) -> new double[] {b, a});

        Arbitrary<Boolean> include = Arbitraries.of(true, false);

        return Combinators.combine(
                        pair, include, pair, include, pair, include)
                .as((airPair, airIn, waterPair, waterIn, solidPair, solidIn) -> {
                    Map<EnvironmentalMedium, double[]> map =
                            new EnumMap<>(EnvironmentalMedium.class);
                    if (airIn) {
                        map.put(EnvironmentalMedium.AIR, airPair);
                    }
                    if (waterIn) {
                        map.put(EnvironmentalMedium.WATER, waterPair);
                    }
                    if (solidIn) {
                        map.put(EnvironmentalMedium.SOLID_WASTE, solidPair);
                    }
                    return map;
                })
                .filter(m -> !m.isEmpty());
    }
}
