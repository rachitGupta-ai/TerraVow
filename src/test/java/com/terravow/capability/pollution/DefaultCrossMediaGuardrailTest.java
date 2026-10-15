package com.terravow.capability.pollution;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultCrossMediaGuardrail}, exercising cross-media displacement detection
 * (Req 3.6, 3.7) with specific examples and boundary values.
 */
class DefaultCrossMediaGuardrailTest {

    private static final Pollutant LOAD = new Pollutant("load");
    private final CrossMediaGuardrail guardrail = new DefaultCrossMediaGuardrail();

    private static Reading reading(EnvironmentalMedium medium, double value, String ref) {
        return new Reading(
                new StreamId("stream-" + medium), LOAD, medium, value, new SourceRef(ref));
    }

    // Req 3.6, 3.7 — air load falls, water load rises → cross-media displacement flagged.
    @Test
    void airReducedWhileWaterIncreasedIsDisplacement() {
        List<Reading> before = List.of(
                reading(EnvironmentalMedium.AIR, 100.0, "a-before"),
                reading(EnvironmentalMedium.WATER, 20.0, "w-before"));
        List<Reading> after = List.of(
                reading(EnvironmentalMedium.AIR, 40.0, "a-after"),
                reading(EnvironmentalMedium.WATER, 90.0, "w-after"));

        CrossMediaResult result = guardrail.assess(new Remediation("rem-1"), before, after);

        assertThat(result.displacement()).isTrue();
        assertThat(result.supportingReadings()).hasSize(4);
        assertThat(result.evidence().links()).hasSize(4);
        assertThat(result.evidence().links())
                .allMatch(l -> l.type() == SourceType.READING);
    }

    // Req 3.6 — load falls in both media (a genuine improvement) → no displacement.
    @Test
    void loadReducedInAllMediaIsNotDisplacement() {
        List<Reading> before = List.of(
                reading(EnvironmentalMedium.AIR, 100.0, "a-before"),
                reading(EnvironmentalMedium.WATER, 80.0, "w-before"));
        List<Reading> after = List.of(
                reading(EnvironmentalMedium.AIR, 40.0, "a-after"),
                reading(EnvironmentalMedium.WATER, 30.0, "w-after"));

        CrossMediaResult result = guardrail.assess(new Remediation("rem-1"), before, after);

        assertThat(result.displacement()).isFalse();
        assertThat(result.supportingReadings()).isEmpty();
        assertThat(result.evidence().links()).isEmpty();
    }

    // Req 3.6 — load rises in both media (strictly worse, but not a trade-off) → no displacement.
    @Test
    void loadIncreasedInAllMediaIsNotDisplacement() {
        List<Reading> before = List.of(
                reading(EnvironmentalMedium.AIR, 10.0, "a-before"),
                reading(EnvironmentalMedium.WATER, 10.0, "w-before"));
        List<Reading> after = List.of(
                reading(EnvironmentalMedium.AIR, 50.0, "a-after"),
                reading(EnvironmentalMedium.WATER, 50.0, "w-after"));

        CrossMediaResult result = guardrail.assess(new Remediation("rem-1"), before, after);

        assertThat(result.displacement()).isFalse();
    }

    // Req 3.6 — no change in any medium → no displacement.
    @Test
    void noChangeIsNotDisplacement() {
        List<Reading> before = List.of(reading(EnvironmentalMedium.AIR, 10.0, "a-before"));
        List<Reading> after = List.of(reading(EnvironmentalMedium.AIR, 10.0, "a-after"));

        CrossMediaResult result = guardrail.assess(new Remediation("rem-1"), before, after);

        assertThat(result.displacement()).isFalse();
    }

    // Req 3.7 — displacement across three media (air down, solid waste up) is flagged.
    @Test
    void airReducedWhileSolidWasteIncreasedIsDisplacement() {
        List<Reading> before = List.of(
                reading(EnvironmentalMedium.AIR, 100.0, "a-before"),
                reading(EnvironmentalMedium.SOLID_WASTE, 5.0, "s-before"));
        List<Reading> after = List.of(
                reading(EnvironmentalMedium.AIR, 10.0, "a-after"),
                reading(EnvironmentalMedium.SOLID_WASTE, 60.0, "s-after"));

        CrossMediaResult result = guardrail.assess(new Remediation("rem-1"), before, after);

        assertThat(result.displacement()).isTrue();
    }

    // Req 3.6 — multiple readings in the same medium are aggregated before comparison.
    @Test
    void readingsAreAggregatedPerMediumBeforeComparison() {
        // Air before total = 60, after total = 100 (increase). Water before = 100, after = 50
        // (decrease). → displacement.
        List<Reading> before = List.of(
                reading(EnvironmentalMedium.AIR, 30.0, "a1-before"),
                reading(EnvironmentalMedium.AIR, 30.0, "a2-before"),
                reading(EnvironmentalMedium.WATER, 100.0, "w-before"));
        List<Reading> after = List.of(
                reading(EnvironmentalMedium.AIR, 50.0, "a1-after"),
                reading(EnvironmentalMedium.AIR, 50.0, "a2-after"),
                reading(EnvironmentalMedium.WATER, 50.0, "w-after"));

        CrossMediaResult result = guardrail.assess(new Remediation("rem-1"), before, after);

        assertThat(result.displacement()).isTrue();
    }

    // A medium present only in "after" counts as an increase from zero.
    @Test
    void mediumAppearingOnlyInAfterCountsAsIncrease() {
        List<Reading> before = List.of(reading(EnvironmentalMedium.AIR, 100.0, "a-before"));
        List<Reading> after = List.of(
                reading(EnvironmentalMedium.AIR, 40.0, "a-after"),
                reading(EnvironmentalMedium.WATER, 30.0, "w-after")); // new load in water

        CrossMediaResult result = guardrail.assess(new Remediation("rem-1"), before, after);

        assertThat(result.displacement()).isTrue();
    }

    // Empty before and after → no displacement.
    @Test
    void emptyReadingsAreNotDisplacement() {
        CrossMediaResult result =
                guardrail.assess(new Remediation("rem-1"), List.of(), List.of());

        assertThat(result.displacement()).isFalse();
        assertThat(result.supportingReadings()).isEmpty();
    }
}
