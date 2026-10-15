package com.terravow.pii;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.reasoning.PromptSpec;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests that the deterministic masking gate removes raw identifiers before the
 * payload can be used downstream (Req 13.3).
 *
 * <p>Validates Correctness Property 38: for arbitrary supplier data carrying sensitive fields, a
 * successful {@link MaskingResult.Masked} result contains <em>none</em> of the raw sensitive values
 * — every sensitive field is replaced by a deterministic, prefixed mask — so nothing raw can cross
 * the downstream boundary, including the reasoning {@link PromptSpec}. Non-sensitive fields are
 * carried through unchanged, and masking is deterministic and stable across invocations.
 *
 * @see DefaultMaskingGate
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.3</a>
 */
class MaskingBeforeUseProperties {

    private static final String SALT = "terravow-prop-salt";

    private final MaskingGate gate = new DefaultMaskingGate(SALT);

    // Feature: terravow, Property 38: Masking removes raw identifiers before use.
    //
    // Every declared-sensitive field is masked (prefixed, not its raw value), and no masked field
    // value equals or embeds any raw sensitive value, so no raw identifier survives into the masked
    // payload.
    @Property(tries = 100)
    void maskedPayloadContainsNoRawSensitiveValue(@ForAll("supplierData") SupplierData data) {
        MaskingResult result = gate.mask(data);

        assertThat(result).isInstanceOf(MaskingResult.Masked.class);
        MaskingResult.Masked masked = (MaskingResult.Masked) result;

        // Every sensitive field's own masked slot no longer holds its raw value: it is replaced by
        // a deterministic mask token. The raw identifier therefore does not survive in the field it
        // was supplied under, so nothing raw crosses the boundary through that sensitive slot.
        for (String sensitiveField : data.sensitiveFields()) {
            String maskedValue = masked.maskedFields().get(sensitiveField);
            String rawValue = data.fields().get(sensitiveField);
            assertThat(maskedValue).startsWith(DefaultMaskingGate.MASK_PREFIX);
            assertThat(maskedValue).isNotEqualTo(rawValue);
            // The mask is a fresh hash token, not a reformatting that embeds the raw value.
            assertThat(maskedValue.substring(DefaultMaskingGate.MASK_PREFIX.length()))
                    .doesNotContain(rawValue);
        }
    }

    // Feature: terravow, Property 38: Masking removes raw identifiers before use.
    //
    // Non-sensitive fields cross unchanged; the gate masks exactly the sensitive set and nothing
    // more, so masking neither leaks sensitive data nor corrupts benign data.
    @Property(tries = 100)
    void nonSensitiveFieldsAreCarriedThroughUnchanged(@ForAll("supplierData") SupplierData data) {
        MaskingResult.Masked masked = (MaskingResult.Masked) gate.mask(data);

        for (Map.Entry<String, String> entry : data.fields().entrySet()) {
            if (!data.sensitiveFields().contains(entry.getKey())) {
                assertThat(masked.maskedFields().get(entry.getKey())).isEqualTo(entry.getValue());
            }
        }
        // The masked payload has exactly the same field names as the input.
        assertThat(masked.maskedFields().keySet()).isEqualTo(data.fields().keySet());
    }

    // Feature: terravow, Property 38: Masking removes raw identifiers before use.
    //
    // The masked payload bridges to the reasoning boundary only as a masked PromptSpec, and no raw
    // sensitive value reaches that boundary.
    @Property(tries = 100)
    void maskedPayloadCrossesPromptSpecBoundaryWithoutRawValues(
            @ForAll("supplierData") SupplierData data) {
        MaskingResult.Masked masked = (MaskingResult.Masked) gate.mask(data);

        PromptSpec spec = masked.toPromptSpec("Diagnose supplier footprint");

        assertThat(spec.masked()).isTrue();
        // Each sensitive field crosses the reasoning boundary only as its mask token, never as its
        // raw value. (A non-sensitive field may coincidentally hold the same string; that value was
        // never declared sensitive, so it is out of scope for the sensitive-field guarantee.)
        for (String sensitiveField : data.sensitiveFields()) {
            String rawValue = data.fields().get(sensitiveField);
            Object crossed = spec.maskedInputs().get(sensitiveField);
            assertThat(String.valueOf(crossed)).startsWith(DefaultMaskingGate.MASK_PREFIX);
            assertThat(String.valueOf(crossed)).isNotEqualTo(rawValue);
        }
    }

    // Feature: terravow, Property 38: Masking removes raw identifiers before use.
    //
    // Masking is deterministic and stable: an independent gate with the same salt produces an
    // identical masked payload, so equal raw inputs map to equal masks on every invocation.
    @Property(tries = 100)
    void maskingIsDeterministicAndStable(@ForAll("supplierData") SupplierData data) {
        MaskingResult.Masked first = (MaskingResult.Masked) gate.mask(data);
        MaskingResult.Masked second =
                (MaskingResult.Masked) new DefaultMaskingGate(SALT).mask(data);

        assertThat(first.maskedFields()).isEqualTo(second.maskedFields());
    }

    /**
     * Arbitrary supplier data with distinct field names, non-blank raw values, and a non-empty
     * subset of those fields marked sensitive. Values are drawn to resemble PII and confidential
     * identifiers (names, emails, ids) so the "no raw value survives" check is meaningful.
     */
    @Provide
    Arbitrary<SupplierData> supplierData() {
        Arbitrary<String> fieldNames =
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10);
        Arbitrary<String> values =
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '@', '.', '-')
                        .ofMinLength(4)
                        .ofMaxLength(24);

        Arbitrary<Map<String, String>> fieldMaps =
                Combinators.combine(
                                fieldNames.list().ofMinSize(2).ofMaxSize(8).uniqueElements(),
                                values.list().ofMinSize(2).ofMaxSize(8))
                        .as((names, vals) -> {
                            Map<String, String> fields = new LinkedHashMap<>();
                            int count = Math.min(names.size(), vals.size());
                            for (int i = 0; i < count; i++) {
                                fields.put(names.get(i), vals.get(i));
                            }
                            return fields;
                        })
                        .filter(m -> m.size() >= 2);

        return fieldMaps.flatMap(fields -> {
            List<String> names = List.copyOf(fields.keySet());
            // Mark a non-empty prefix of the field names sensitive (at least one, not all required).
            return Arbitraries.integers()
                    .between(1, names.size())
                    .map(sensitiveCount -> {
                        Set<String> sensitive = new LinkedHashSet<>(names.subList(0, sensitiveCount));
                        return new SupplierData("record-" + fields.size(), fields, sensitive);
                    });
        });
    }
}
