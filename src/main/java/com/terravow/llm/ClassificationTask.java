package com.terravow.llm;

/**
 * Identifies a high-volume classification task routed to the Gemini Flash tier (Req 11.1, 12.1).
 *
 * <p>Unlike the deep-reasoning {@link com.terravow.reasoning.CapabilityId} families — which produce
 * counterfactual/economics/certification determinations on the Pro tier — these are latency- and
 * cost-sensitive, high-throughput classification decisions. The design names three explicitly:
 * per-reading threshold checks, confidentiality masking triage, and intent routing. All route to
 * {@link GeminiTier#FLASH}.
 *
 * <p>Flash assistance on these tasks is advisory only; the authoritative deterministic logic (e.g.
 * the deterministic masker, the threshold evaluator) is unchanged by tier selection.
 *
 * @see GeminiModelRouter
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 12.1</a>
 */
public enum ClassificationTask {
    /** High-volume per-reading threshold evaluation for the Pollution Monitor (Req 3). */
    THRESHOLD_CHECK,
    /** Confidentiality masking triage; the deterministic masker remains authoritative (Req 13.3). */
    MASKING_TRIAGE,
    /** Intent routing / request classification. */
    INTENT_ROUTING
}
