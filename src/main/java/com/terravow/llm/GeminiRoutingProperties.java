package com.terravow.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration for the two-tier Gemini model router (Req 11.1, 12.1).
 *
 * <p>Binds the concrete model identifiers for each {@link GeminiTier} under the
 * {@code terravow.llm.*} namespace, so the Pro and Flash model names are a configuration concern
 * and no live Vertex AI model name is hard-coded in routing logic. Both fields default to safe,
 * behaviour-preserving placeholder identifiers so the router resolves a non-blank model id even
 * when no live Vertex AI is configured (TerraVow is local-first in dev/test).
 *
 * <ul>
 *   <li>{@code terravow.llm.pro-model} — identifier bound to {@link GeminiTier#PRO}.</li>
 *   <li>{@code terravow.llm.flash-model} — identifier bound to {@link GeminiTier#FLASH}.</li>
 * </ul>
 *
 * @see GeminiModelRouter
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 12.1</a>
 */
@ConfigurationProperties(prefix = "terravow.llm")
public class GeminiRoutingProperties {

    /** Safe default model identifier for the reasoning-optimised Pro tier. */
    static final String DEFAULT_PRO_MODEL = "gemini-3.1-pro";

    /** Safe default model identifier for the latency/cost-optimised Flash tier. */
    static final String DEFAULT_FLASH_MODEL = "gemini-flash";

    private String proModel = DEFAULT_PRO_MODEL;
    private String flashModel = DEFAULT_FLASH_MODEL;

    /**
     * Returns the model identifier bound to the Pro tier.
     *
     * @return the Pro-tier model identifier (never blank; defaults to {@value #DEFAULT_PRO_MODEL})
     */
    public String getProModel() {
        return proModel;
    }

    /**
     * Sets the Pro-tier model identifier. A {@code null} or blank value is coerced back to the safe
     * default so the router always resolves a usable identifier.
     *
     * @param proModel the Pro-tier model identifier
     */
    public void setProModel(String proModel) {
        this.proModel = (proModel == null || proModel.isBlank()) ? DEFAULT_PRO_MODEL : proModel;
    }

    /**
     * Returns the model identifier bound to the Flash tier.
     *
     * @return the Flash-tier model identifier (never blank; defaults to {@value #DEFAULT_FLASH_MODEL})
     */
    public String getFlashModel() {
        return flashModel;
    }

    /**
     * Sets the Flash-tier model identifier. A {@code null} or blank value is coerced back to the
     * safe default so the router always resolves a usable identifier.
     *
     * @param flashModel the Flash-tier model identifier
     */
    public void setFlashModel(String flashModel) {
        this.flashModel = (flashModel == null || flashModel.isBlank()) ? DEFAULT_FLASH_MODEL : flashModel;
    }

    /**
     * Resolves the configured model identifier for a given tier.
     *
     * @param tier the Gemini tier to resolve, must not be {@code null}
     * @return the configured model identifier for {@code tier}
     * @throws NullPointerException if {@code tier} is {@code null}
     */
    public String modelFor(GeminiTier tier) {
        return switch (tier) {
            case PRO -> proModel;
            case FLASH -> flashModel;
        };
    }
}
