package com.terravow.llm;

/**
 * The two Gemini model tiers TerraVow routes work to (Req 11.1, 12.1).
 *
 * <p>Gemini models span a reasoning-optimised Pro tier (deep multi-step reasoning, planning, long
 * context, native tool use) and a latency/cost-optimised Flash tier for high-volume classification.
 * TerraVow routes deep counterfactual/economics/certification reasoning to the {@link #PRO} tier and
 * high-volume classification (threshold checks, masking triage, intent routing) to the {@link #FLASH}
 * tier.
 *
 * <p>This enum is tier identity only — the concrete model identifier bound to each tier is
 * configurable via {@link GeminiRoutingProperties} so no live Vertex AI model name is hard-coded.
 *
 * @see GeminiModelRouter
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 12.1</a>
 */
public enum GeminiTier {
    /** Reasoning-optimised tier for deep multi-step reasoning, planning, and counterfactual work. */
    PRO,
    /** Latency/cost-optimised tier for high-volume classification and triage. */
    FLASH
}
