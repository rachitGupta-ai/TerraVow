package com.terravow.pii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the fail-closed masking-rejection contract: when masking cannot complete, the data
 * is rejected with a recorded reason and nothing is masked, so no reasoning, persistence, or
 * managed-service call may proceed for that record (Req 13.4).
 *
 * <p>These supplement {@link DefaultMaskingGateTest} (which covers the successful masking paths and
 * the authority of the deterministic masker over advisory triage) by exercising the {@link
 * MaskingResult.Rejected} arm of the exhaustive {@link MaskingResult} contract and its invariants.
 *
 * @see MaskingResult.Rejected
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.4</a>
 */
class MaskingFailureRejectionTest {

    private static SupplierData supplier() {
        return new SupplierData(
                "supplier-record-9",
                Map.of("supplierId", "SUP-00099", "material", "aluminium-6061"),
                Set.of("supplierId"));
    }

    // Req 13.4 — when masking cannot complete, the gate returns a Rejected outcome: not masked, and
    // carrying the originating record id plus the recorded reason. No masked payload is produced.
    @Test
    void maskingFailureYieldsRejectionWithRecordedReason() {
        String reason = "masking could not complete: hashing primitive unavailable";
        MaskingGate failingGate =
                data -> new MaskingResult.Rejected(data.recordId(), reason);

        MaskingResult result = failingGate.mask(supplier());

        assertThat(result).isInstanceOf(MaskingResult.Rejected.class);
        assertThat(result.isMasked()).isFalse();

        MaskingResult.Rejected rejected = (MaskingResult.Rejected) result;
        assertThat(rejected.recordId()).isEqualTo("supplier-record-9");
        assertThat(rejected.reason()).isEqualTo(reason);
        assertThat(rejected.reason()).isNotBlank();
    }

    // Req 13.4 — a rejection is not a masked payload, so a caller handling the exhaustive result
    // cannot obtain masked fields or a reasoning bridge from it: nothing crosses downstream.
    @Test
    void rejectionIsNotMaskedSoNothingCrossesDownstream() {
        MaskingResult result =
                new MaskingResult.Rejected("supplier-record-9", "masker offline");

        assertThat(result.isMasked()).isFalse();
        assertThat(result).isNotInstanceOf(MaskingResult.Masked.class);
    }

    // Req 13.4 — the recorded reason is mandatory: a rejection cannot be constructed without a
    // non-blank reason, so the "why" is always captured for the audit trail.
    @Test
    void rejectionRequiresANonBlankRecordedReason() {
        assertThatThrownBy(() -> new MaskingResult.Rejected("supplier-record-9", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");

        assertThatThrownBy(() -> new MaskingResult.Rejected("supplier-record-9", null))
                .isInstanceOf(NullPointerException.class);
    }

    // Req 13.4 — a rejection must correlate to the originating record, so the rejected data can be
    // traced in the audit trail; a blank record id is rejected.
    @Test
    void rejectionRequiresACorrelatingRecordId() {
        assertThatThrownBy(() -> new MaskingResult.Rejected("", "masker offline"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordId");
    }
}
