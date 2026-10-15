package com.terravow.reasoning.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.terravow.domain.DeterminationStatus;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MassPreservationTool}: the within-tolerance preservation check and the
 * UNAVAILABLE-on-failure path (Req 4.5, 4.6).
 */
class MassPreservationToolTest {

    private static final double TOLERANCE = 0.001; // 0.1 percent per Req 4.5

    private final MassPreservationTool tool = new MassPreservationTool();

    @Test
    void equalMassesArePreserved() {
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(1_000.0, 1_000.0, TOLERANCE));

        assertThat(result.isComputed()).isTrue();
        MassPreservationResult value = result.value().orElseThrow();
        assertThat(value.preserved()).isTrue();
        assertThat(value.relativeDeviation()).isEqualTo(0.0);
    }

    @Test
    void deviationWithinToleranceIsPreserved() {
        // 0.05 percent deviation, within 0.1 percent
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(1_000.0, 1_000.5, TOLERANCE));

        MassPreservationResult value = result.value().orElseThrow();
        assertThat(value.preserved()).isTrue();
        assertThat(value.relativeDeviation()).isCloseTo(0.0005, within(1e-9));
    }

    @Test
    void deviationExactlyAtToleranceIsPreserved() {
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(1_000.0, 1_001.0, TOLERANCE));

        assertThat(result.value().orElseThrow().preserved()).isTrue();
    }

    @Test
    void deviationBeyondToleranceIsNotPreserved() {
        // 0.2 percent deviation, beyond 0.1 percent
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(1_000.0, 1_002.0, TOLERANCE));

        assertThat(result.value().orElseThrow().preserved()).isFalse();
    }

    @Test
    void zeroOriginalWithZeroCandidateIsPreserved() {
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(0.0, 0.0, TOLERANCE));

        MassPreservationResult value = result.value().orElseThrow();
        assertThat(value.preserved()).isTrue();
        assertThat(value.relativeDeviation()).isEqualTo(0.0);
    }

    @Test
    void zeroOriginalWithPositiveCandidateIsNotPreserved() {
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(0.0, 5.0, TOLERANCE));

        MassPreservationResult value = result.value().orElseThrow();
        assertThat(value.preserved()).isFalse();
        assertThat(value.relativeDeviation()).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void nonFiniteMassIsUnavailable() {
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(Double.NaN, 1_000.0, TOLERANCE));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void negativeMassIsUnavailable() {
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(1_000.0, -1.0, TOLERANCE));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void negativeToleranceIsUnavailable() {
        ToolResult<MassPreservationResult> result =
                tool.execute(new MassPreservationRequest(1_000.0, 1_000.0, -0.1));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void nullRequestIsUnavailable() {
        assertThat(tool.execute(null).status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }
}
