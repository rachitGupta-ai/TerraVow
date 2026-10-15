package com.terravow.pii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link MaskingResult} sum type, covering the masked and rejected arms and the
 * invariant that a rejection always carries a recorded reason (Req 13.3, 13.4).
 */
class MaskingResultTest {

    // Req 13.4 — a rejection carries a non-blank recorded reason masking could not complete.
    @Test
    void rejectedCarriesRecordedReason() {
        MaskingResult.Rejected rejected =
                new MaskingResult.Rejected("rec-1", "masking could not complete: primitive unavailable");

        assertThat(rejected.isMasked()).isFalse();
        assertThat(rejected.reason()).isNotBlank();
        assertThat(rejected.recordId()).isEqualTo("rec-1");
    }

    // Req 13.4 — a rejection without a recorded reason is unrepresentable (fails construction).
    @Test
    void rejectedRejectsBlankReason() {
        assertThatThrownBy(() -> new MaskingResult.Rejected("rec-1", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    // Req 13.3 — a masked result reports it is safe to pass downstream.
    @Test
    void maskedReportsItIsMasked() {
        MaskingResult.Masked masked =
                new MaskingResult.Masked("rec-1", Map.of("supplierId", "mask:abc123"));

        assertThat(masked.isMasked()).isTrue();
        assertThat(masked.maskedFields()).containsEntry("supplierId", "mask:abc123");
    }

    // Req 13.3, 13.4 — the two arms are mutually exclusive and exhaustive; callers can switch on
    // them to decide whether downstream use is permitted.
    @Test
    void resultArmsAreExhaustiveAndMutuallyExclusive() {
        MaskingResult masked = new MaskingResult.Masked("rec-1", Map.of());
        MaskingResult rejected = new MaskingResult.Rejected("rec-2", "cannot complete");

        assertThat(describe(masked)).isEqualTo("downstream-allowed");
        assertThat(describe(rejected)).isEqualTo("downstream-forbidden");
    }

    private static String describe(MaskingResult result) {
        if (result instanceof MaskingResult.Masked) {
            return "downstream-allowed";
        }
        if (result instanceof MaskingResult.Rejected) {
            return "downstream-forbidden";
        }
        throw new AssertionError("MaskingResult has an unexpected arm: " + result);
    }
}
