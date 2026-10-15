package com.terravow.pii;

import com.terravow.reasoning.PromptSpec;

import java.util.Map;
import java.util.Objects;

/**
 * The outcome of a {@link MaskingGate#mask} call: either a {@link Masked} payload that is safe to
 * pass downstream, or a {@link Rejected} outcome carrying the recorded reason masking could not
 * complete (Req 13.3, 13.4).
 *
 * <p>This is a sealed, exhaustive sum type so every caller must handle both arms: there is no
 * "masked-ish" or partially-masked intermediate state. Only a {@link Masked} value may be placed in
 * a reasoning prompt, an Agent Engine Session / Memory Bank, a Vertex AI Search query, or BigQuery
 * embeddings/storage; a {@link Rejected} value means the data must be reasoned over, persisted, or
 * sent to no managed service at all (Req 13.4).
 *
 * @see MaskingGate
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.3, 13.4</a>
 */
public sealed interface MaskingResult permits MaskingResult.Masked, MaskingResult.Rejected {

    /**
     * @return {@code true} iff masking completed and the payload is safe to pass downstream
     */
    boolean isMasked();

    /**
     * A successfully masked payload, safe to pass to reasoning, managed services, and persistence
     * (Req 13.3).
     *
     * <p>Every field named sensitive (by the caller or an advisory triage hint) has been replaced
     * by a deterministic, irreversible token; non-sensitive fields are carried through unchanged.
     * The {@code maskedFields} map therefore contains <em>no</em> raw PII or confidential supplier
     * identifier.
     *
     * @param recordId     the originating {@link SupplierData#recordId()}, for correlation only
     * @param maskedFields the field values after deterministic masking; never {@code null},
     *                     defensively copied
     *
     * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.3</a>
     */
    record Masked(String recordId, Map<String, String> maskedFields) implements MaskingResult {

        /**
         * Validates and defensively copies the masked payload.
         *
         * @throws NullPointerException     if any argument, key, or value is {@code null}
         * @throws IllegalArgumentException if {@code recordId} is blank
         */
        public Masked {
            Objects.requireNonNull(recordId, "Masked recordId must not be null");
            Objects.requireNonNull(maskedFields, "Masked maskedFields must not be null");
            if (recordId.isBlank()) {
                throw new IllegalArgumentException("Masked recordId must not be blank");
            }
            maskedFields = Map.copyOf(maskedFields);
        }

        @Override
        public boolean isMasked() {
            return true;
        }

        /**
         * Bridges the masked payload to the reasoning boundary (Req 11.1, 13.3).
         *
         * <p>Builds a {@link PromptSpec} marked masked from the given instruction and this payload's
         * already-masked fields. Because {@link PromptSpec} rejects any spec that is not marked
         * masked, routing supplier data to reasoning <em>only</em> through this method structurally
         * guarantees no raw identifier reaches the managed Agent Engine runtime.
         *
         * @param instruction the agent instruction; never {@code null}, non-blank
         * @return a masked {@link PromptSpec} wrapping this payload's fields
         */
        public PromptSpec toPromptSpec(String instruction) {
            return PromptSpec.ofMasked(instruction, Map.copyOf(maskedFields));
        }
    }

    /**
     * A rejection: masking could not complete, so the data must not be reasoned over, persisted, or
     * sent to any managed service (Req 13.4).
     *
     * @param recordId the originating {@link SupplierData#recordId()}, for correlation and audit
     * @param reason   the recorded, non-blank reason masking could not complete
     *
     * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.4</a>
     */
    record Rejected(String recordId, String reason) implements MaskingResult {

        /**
         * Validates the rejection carries a correlation id and a non-blank recorded reason
         * (Req 13.4).
         *
         * @throws NullPointerException     if {@code recordId} or {@code reason} is {@code null}
         * @throws IllegalArgumentException if {@code recordId} or {@code reason} is blank
         */
        public Rejected {
            Objects.requireNonNull(recordId, "Rejected recordId must not be null");
            Objects.requireNonNull(reason, "Rejected reason must not be null");
            if (recordId.isBlank()) {
                throw new IllegalArgumentException("Rejected recordId must not be blank");
            }
            if (reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Rejected reason must not be blank — the reason must be recorded (Req 13.4)");
            }
        }

        @Override
        public boolean isMasked() {
            return false;
        }
    }
}
