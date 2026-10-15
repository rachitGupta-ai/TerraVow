package com.terravow.pii;

import java.util.Map;
import java.util.Objects;

/**
 * Raw ingested supplier data submitted to the {@link MaskingGate} before it may reach reasoning,
 * any managed Google service, or persistence (Req 13.3).
 *
 * <p>This is the <em>pre-masking</em> boundary type: it may legitimately contain PII and commercially
 * confidential supplier identifiers, which is precisely why no instance of {@code SupplierData} may
 * cross into reasoning, an Agent Engine prompt / Session / Memory Bank, a Vertex AI Search query, or
 * BigQuery embeddings/storage. Only a {@link MaskingResult.Masked} produced by the gate from this
 * input is safe to pass downstream.
 *
 * <p>Fields are modelled as a free-form, name-keyed map so the gate is self-contained and does not
 * depend on a particular supplier schema: {@code sensitiveFields} enumerates the field names the
 * caller knows carry PII or confidential identifiers (always masked), while {@code fields} carries
 * every field's raw value. A field named in {@code sensitiveFields} must be present in
 * {@code fields}.
 *
 * @param recordId        a non-blank identifier for the supplier record, used only to correlate the
 *                        masked result and any rejection reason (never itself treated as a payload
 *                        identifier to mask)
 * @param fields          every raw field value keyed by field name; never {@code null}, defensively
 *                        copied, may be empty, must not contain {@code null} keys or values
 * @param sensitiveFields the names of the fields known to carry PII or confidential supplier
 *                        identifiers that must be masked; never {@code null}, defensively copied,
 *                        every name must appear in {@code fields}
 *
 * @see MaskingGate
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.3, 13.4</a>
 */
public record SupplierData(String recordId, Map<String, String> fields, java.util.Set<String> sensitiveFields) {

    /**
     * Validates identity and the field maps, defensively copies them, and enforces that every
     * declared sensitive field is actually present (Req 13.3).
     *
     * @throws NullPointerException     if any argument, key, or value is {@code null}
     * @throws IllegalArgumentException if {@code recordId} is blank, or a declared sensitive field
     *                                  is absent from {@code fields}
     */
    public SupplierData {
        Objects.requireNonNull(recordId, "SupplierData recordId must not be null");
        Objects.requireNonNull(fields, "SupplierData fields must not be null");
        Objects.requireNonNull(sensitiveFields, "SupplierData sensitiveFields must not be null");
        if (recordId.isBlank()) {
            throw new IllegalArgumentException("SupplierData recordId must not be blank");
        }
        fields = Map.copyOf(fields); // immutable copy; rejects null keys/values
        sensitiveFields = java.util.Set.copyOf(sensitiveFields); // immutable copy; rejects nulls
        for (String name : sensitiveFields) {
            if (!fields.containsKey(name)) {
                throw new IllegalArgumentException(
                        "Declared sensitive field is absent from fields: " + name);
            }
        }
    }
}
