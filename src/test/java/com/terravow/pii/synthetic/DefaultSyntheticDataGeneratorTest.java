package com.terravow.pii.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.domain.SyntheticLabel;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultSyntheticDataGenerator}: synthetic data is grounded inside published
 * regulatory parameter ranges and labelled synthetic, deterministically (Req 13.1, 13.2, 14.15).
 */
class DefaultSyntheticDataGeneratorTest {

    private final SyntheticDataGenerator generator = new DefaultSyntheticDataGenerator();

    private static List<ParameterRange> stackRanges() {
        return List.of(
                new ParameterRange("stack.so2", 10.0, 200.0, "mg/Nm3"),
                new ParameterRange("stack.nox", 50.0, 300.0, "mg/Nm3"),
                new ParameterRange("stack.pm", 5.0, 50.0, "mg/Nm3"));
    }

    // Req 13.1, 14.15 — every generated value lies within its published parameter range (grounded
    // in published ranges and real instrument structure), and the record is labelled SYNTHETIC.
    @Test
    void generatesValuesGroundedWithinPublishedRanges() {
        List<ParameterRange> ranges = stackRanges();

        SyntheticRecord record = generator.generate("stack-cems", ranges, 42L);

        assertThat(record.label()).isEqualTo(SyntheticLabel.SYNTHETIC);
        assertThat(record.structure()).isEqualTo("stack-cems");
        assertThat(record.parameters().keySet())
                .containsExactlyInAnyOrder("stack.so2", "stack.nox", "stack.pm");

        for (ParameterRange range : ranges) {
            SyntheticParameter parameter = record.parameters().get(range.parameter());
            assertThat(parameter.unit()).isEqualTo(range.unit());
            assertThat(range.contains(parameter.value()))
                    .as("value %s for %s must be within [%s, %s]",
                            parameter.value(), range.parameter(), range.min(), range.max())
                    .isTrue();
        }
    }

    // Req 13.1 — generation is deterministic: same structure, ranges, and seed → same record.
    @Test
    void generationIsDeterministicInSeed() {
        SyntheticRecord first = generator.generate("stack-cems", stackRanges(), 7L);
        SyntheticRecord second =
                new DefaultSyntheticDataGenerator().generate("stack-cems", stackRanges(), 7L);

        assertThat(first).isEqualTo(second);
    }

    // Req 13.1 — a degenerate range (min == max) still grounds to the single published point.
    @Test
    void groundsToSinglePointWhenRangeIsDegenerate() {
        List<ParameterRange> ranges =
                List.of(new ParameterRange("baseline.intensity", 1.25, 1.25, "kgCO2e/t"));

        SyntheticRecord record = generator.generate("commissioning-baseline", ranges, 99L);

        assertThat(record.parameters().get("baseline.intensity").value()).isEqualTo(1.25);
    }

    // Req 13.1 — duplicate parameter names are rejected so a record cannot ground two values under
    // one instrument field.
    @Test
    void rejectsDuplicateParameterNames() {
        List<ParameterRange> ranges =
                List.of(
                        new ParameterRange("stack.so2", 10.0, 20.0, "mg/Nm3"),
                        new ParameterRange("stack.so2", 30.0, 40.0, "mg/Nm3"));

        assertThatThrownBy(() -> generator.generate("stack-cems", ranges, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate parameter");
    }

    // Req 13.1 — empty ranges and blank structure are rejected (a synthetic record is always
    // grounded in at least one published range mirroring a named instrument).
    @Test
    void rejectsEmptyRangesAndBlankStructure() {
        assertThatThrownBy(() -> generator.generate("stack-cems", List.of(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate("  ", stackRanges(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Req 13.2, 14.15 — the generated record exposes itself as a Labeled carrying SYNTHETIC, the
    // propagation entry point for every derived output.
    @Test
    void recordExposesSyntheticLabeledForPropagation() {
        SyntheticRecord record = generator.generate("job-run", stackRanges(), 3L);

        Labeled<SyntheticRecord> labeled = record.asLabeled();

        assertThat(labeled.isSynthetic()).isTrue();
        assertThat(labeled.value()).isEqualTo(record);
    }
}
