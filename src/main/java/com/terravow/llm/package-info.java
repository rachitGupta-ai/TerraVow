/**
 * Two-tier Gemini model routing (Req 11.1, 12.1).
 *
 * <p>Maps deep-reasoning capabilities ({@link com.terravow.reasoning.CapabilityId}) to the Gemini
 * Pro tier and high-volume classification tasks ({@link com.terravow.llm.ClassificationTask}) to the
 * Gemini Flash tier. Tier-to-model identifiers are configurable via
 * {@link com.terravow.llm.GeminiRoutingProperties} under {@code terravow.llm.*}; the router holds no
 * live Vertex AI client.
 */
package com.terravow.llm;
