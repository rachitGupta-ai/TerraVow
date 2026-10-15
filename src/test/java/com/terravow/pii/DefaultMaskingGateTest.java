package com.terravow.pii;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.reasoning.PromptSpec;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultMaskingGate}, exercising deterministic masking of PII and
 * confidential supplier identifiers before any downstream use, stable/irreversible masking, the
 * authority of the deterministic masker over the advisory triage, and the recorded-reason rejection
 * path (Req 13.3, 13.4).
 */
class DefaultMaskingGateTest {

    private static final String SALT = "terravow-test-salt";

    private final MaskingGate gate = new DefaultMaskingGate(SALT);

    private static SupplierData supplier(Set<String> sensitive) {
        return new SupplierData(
                "supplier-record-1",
                Map.of(
                        "supplierName", "Acme Alloys Pvt Ltd",
                        "contactEmail", "ops@acme.example",
                        "supplierId", "SUP-00042",
                        "material", "stainless-steel-316"),
                sensitive);
    }

    // Req 13.3 — PII and confidential supplier identifiers are masked before the payload is usable
    // downstream: the masked fields contain none of the raw identifier values.
    @Test
    void masksPiiAndSupplierIdentifiersBeforeUse() {
        SupplierData data = supplier(Set.of("supplierName", "contactEmail", "supplierId"));

        MaskingResult result = gate.mask(data);

        assertThat(result).isInstanceOf(MaskingResult.Masked.class);
        MaskingResult.Masked masked = (MaskingResult.Masked) result;

        // Raw sensitive values are gone; masked tokens are prefixed and non-raw.
        assertThat(masked.maskedFields().get("supplierName")).isNotEqualTo("Acme Alloys Pvt Ltd");
        assertThat(masked.maskedFields().get("contactEmail")).isNotEqualTo("ops@acme.example");
        assertThat(masked.maskedFields().get("supplierId")).isNotEqualTo("SUP-00042");
        assertThat(masked.maskedFields().values())
                .noneMatch(v -> v.contains("Acme") || v.contains("acme.example") || v.contains("SUP-00042"));
        assertThat(masked.maskedFields().get("supplierName")).startsWith("mask:");

        // Non-sensitive fields are carried through unchanged.
        assertThat(masked.maskedFields().get("material")).isEqualTo("stainless-steel-316");
    }

    // Req 13.3 — masking is deterministic and stable: the same input always yields the same mask.
    @Test
    void maskingIsDeterministicAndStable() {
        SupplierData data = supplier(Set.of("supplierName", "supplierId"));

        MaskingResult.Masked first = (MaskingResult.Masked) gate.mask(data);
        MaskingResult.Masked second =
                (MaskingResult.Masked) new DefaultMaskingGate(SALT).mask(data);

        assertThat(first.maskedFields()).isEqualTo(second.maskedFields());
    }

    // Req 13.3 — equal raw identifiers map to equal masks (stable), and the mask is irreversible
    // (one-way hash, not the raw value).
    @Test
    void equalIdentifiersMapToEqualIrreversibleMasks() {
        SupplierData a = new SupplierData(
                "rec-a", Map.of("supplierId", "SUP-777"), Set.of("supplierId"));
        SupplierData b = new SupplierData(
                "rec-b", Map.of("supplierId", "SUP-777"), Set.of("supplierId"));

        String maskA = ((MaskingResult.Masked) gate.mask(a)).maskedFields().get("supplierId");
        String maskB = ((MaskingResult.Masked) gate.mask(b)).maskedFields().get("supplierId");

        assertThat(maskA).isEqualTo(maskB);
        assertThat(maskA).doesNotContain("SUP-777");
    }

    // Req 13.3 — a different salt produces a different mask, so masks are not globally guessable.
    @Test
    void differentSaltProducesDifferentMask() {
        SupplierData data = supplier(Set.of("supplierId"));

        String withSaltA = ((MaskingResult.Masked) gate.mask(data)).maskedFields().get("supplierId");
        String withSaltB = ((MaskingResult.Masked) new DefaultMaskingGate("other-salt").mask(data))
                .maskedFields().get("supplierId");

        assertThat(withSaltA).isNotEqualTo(withSaltB);
    }

    // Req 13.3 — the deterministic masker is authoritative over the advisory triage: a Flash hint
    // can only cause MORE masking (union-in), never less.
    @Test
    void triageHintUnionsInAdditionalSensitiveFields() {
        MaskingTriage flashHint = d -> TriageHint.suggesting("contactEmail");
        MaskingGate gateWithTriage = new DefaultMaskingGate(SALT, flashHint);

        // Caller declares only supplierId sensitive; Flash advises contactEmail is also sensitive.
        SupplierData data = supplier(Set.of("supplierId"));

        MaskingResult.Masked masked = (MaskingResult.Masked) gateWithTriage.mask(data);

        // Both the declared and the advised fields are masked.
        assertThat(masked.maskedFields().get("supplierId")).startsWith("mask:");
        assertThat(masked.maskedFields().get("contactEmail")).startsWith("mask:");
        assertThat(masked.maskedFields().get("contactEmail")).isNotEqualTo("ops@acme.example");
    }

    // Req 13.3 — triage never bypasses masking: a hint cannot leave a declared-sensitive field
    // unmasked, and a hint naming a missing field changes nothing.
    @Test
    void triageNeverBypassesDeclaredMaskingAndIgnoresUnknownFields() {
        // A hostile/incorrect triage that suggests NOTHING must not reduce masking.
        MaskingTriage emptyHint = d -> TriageHint.NONE;
        // A triage naming a non-existent field must be ignored (no rejection, no leak).
        MaskingTriage bogusHint = d -> TriageHint.suggesting("doesNotExist");

        SupplierData data = supplier(Set.of("supplierName", "supplierId"));

        MaskingResult.Masked viaEmpty =
                (MaskingResult.Masked) new DefaultMaskingGate(SALT, emptyHint).mask(data);
        MaskingResult.Masked viaBogus =
                (MaskingResult.Masked) new DefaultMaskingGate(SALT, bogusHint).mask(data);

        // Declared-sensitive fields are masked in both cases regardless of the advisory input.
        assertThat(viaEmpty.maskedFields().get("supplierName")).startsWith("mask:");
        assertThat(viaEmpty.maskedFields().get("supplierId")).startsWith("mask:");
        assertThat(viaBogus.maskedFields()).isEqualTo(viaEmpty.maskedFields());
    }

    // Req 13.3 — an advisory triage that throws must never block the authoritative deterministic
    // masker; masking still completes over the declared sensitive fields.
    @Test
    void triageFailureDoesNotBlockDeterministicMasking() {
        MaskingTriage failingTriage = d -> {
            throw new IllegalStateException("flash unavailable");
        };
        MaskingGate gateWithFailingTriage = new DefaultMaskingGate(SALT, failingTriage);

        SupplierData data = supplier(Set.of("supplierId"));

        MaskingResult result = gateWithFailingTriage.mask(data);

        assertThat(result).isInstanceOf(MaskingResult.Masked.class);
        assertThat(((MaskingResult.Masked) result).maskedFields().get("supplierId"))
                .startsWith("mask:");
    }

    // Req 13.3 — the masked payload bridges to the reasoning boundary only through a masked
    // PromptSpec, so no raw identifier can reach the Agent Engine runtime.
    @Test
    void maskedPayloadBridgesToMaskedPromptSpec() {
        SupplierData data = supplier(Set.of("supplierName", "supplierId"));
        MaskingResult.Masked masked = (MaskingResult.Masked) gate.mask(data);

        PromptSpec spec = masked.toPromptSpec("Diagnose supplier footprint");

        assertThat(spec.masked()).isTrue();
        assertThat(spec.maskedInputs().get("supplierName")).isEqualTo(
                masked.maskedFields().get("supplierName"));
        assertThat(spec.maskedInputs().values())
                .noneMatch(v -> String.valueOf(v).contains("Acme"));
    }
}
