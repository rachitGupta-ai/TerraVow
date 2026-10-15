package com.terravow.pii;

/**
 * The deterministic masking gate that masks PII and commercially confidential supplier identifiers
 * before any data reaches reasoning, a managed Google service, or persistence (Req 13.3, 13.4).
 *
 * <p>This gate is the structural confidentiality chokepoint. Masking must complete <em>before</em>
 * data is placed in an Agent Engine prompt / Session / Memory Bank, used as a Vertex AI Search
 * grounding query, embedded or stored in BigQuery for vector search, or otherwise persisted
 * (Req 13.3). A Gemini Flash tier may <em>assist</em> masking triage through the advisory
 * {@link MaskingTriage} seam, but this deterministic gate is authoritative — it decides what is
 * masked and whether to reject, and never defers that decision to the triage.
 *
 * <p>If masking cannot complete, the gate returns a {@link MaskingResult.Rejected} carrying a
 * recorded reason; the caller must then reason over, persist, and send to a managed service exactly
 * nothing for that record (Req 13.4). On success it returns a {@link MaskingResult.Masked} payload
 * safe to pass downstream.
 *
 * @see DefaultMaskingGate
 * @see MaskingResult
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.3, 13.4</a>
 */
public interface MaskingGate {

    /**
     * Masks the raw supplier data deterministically, or rejects it with a recorded reason
     * (Req 13.3, 13.4).
     *
     * <p>The returned {@link MaskingResult} is exhaustive: a {@link MaskingResult.Masked} payload is
     * safe to pass to reasoning, managed services, and persistence; a {@link MaskingResult.Rejected}
     * outcome means the data must not be reasoned over, persisted, or sent to any managed service.
     *
     * @param data the raw supplier data to mask; never {@code null}
     * @return a masked payload, or a rejection carrying the recorded reason masking could not
     *         complete
     * @throws NullPointerException if {@code data} is {@code null}
     */
    MaskingResult mask(SupplierData data);
}
