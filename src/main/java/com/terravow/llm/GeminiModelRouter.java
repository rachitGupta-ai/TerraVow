package com.terravow.llm;

import com.terravow.reasoning.CapabilityId;

/**
 * Routes a unit of work to the appropriate Gemini model tier (Req 11.1, 12.1).
 *
 * <p>Deep reasoning and planning — the counterfactual/economics/certification determination
 * families identified by {@link CapabilityId} — route to {@link GeminiTier#PRO}. High-volume
 * classification tasks identified by {@link ClassificationTask} (threshold checks, masking triage,
 * intent routing) route to {@link GeminiTier#FLASH}.
 *
 * <p>Routing is a pure, deterministic mapping: the same input always resolves to the same tier.
 * The concrete model identifier bound to each tier is a configuration concern resolved by
 * {@link #modelFor(GeminiTier)} over {@link GeminiRoutingProperties}, so this contract requires no
 * live Vertex AI connection.
 *
 * @see GeminiTier
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 12.1</a>
 */
public interface GeminiModelRouter {

    /**
     * Selects the Gemini tier for a deep-reasoning capability. All reasoning capabilities route to
     * {@link GeminiTier#PRO}.
     *
     * @param capability the reasoning capability, must not be {@code null}
     * @return the tier to serve {@code capability}
     * @throws NullPointerException if {@code capability} is {@code null}
     */
    GeminiTier tierFor(CapabilityId capability);

    /**
     * Selects the Gemini tier for a high-volume classification task. All classification tasks route
     * to {@link GeminiTier#FLASH}.
     *
     * @param task the classification task, must not be {@code null}
     * @return the tier to serve {@code task}
     * @throws NullPointerException if {@code task} is {@code null}
     */
    GeminiTier tierFor(ClassificationTask task);

    /**
     * Resolves the configured model identifier bound to a tier.
     *
     * @param tier the Gemini tier, must not be {@code null}
     * @return the configured model identifier for {@code tier}
     * @throws NullPointerException if {@code tier} is {@code null}
     */
    String modelFor(GeminiTier tier);
}
