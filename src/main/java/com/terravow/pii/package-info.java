/**
 * Confidentiality / PII masking gate (Req 13.3, 13.4).
 *
 * <p>The deterministic {@link com.terravow.pii.MaskingGate} is the structural chokepoint that masks
 * PII and commercially confidential supplier identifiers <em>before</em> any data reaches reasoning,
 * the Agent Engine prompt / Session / Memory Bank, a Vertex AI Search grounding query, BigQuery
 * embeddings/storage, or any other persistence (Req 13.3). A Gemini Flash tier may <em>assist</em>
 * masking triage via the optional {@link com.terravow.pii.MaskingTriage} advisory seam, but the
 * deterministic masker is authoritative and never deferred to. If masking cannot complete the data
 * is rejected — not reasoned over, not persisted, not sent to any managed service — with a recorded
 * reason (Req 13.4).
 */
package com.terravow.pii;
