package com.terravow.llm;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.terravow.reasoning.CapabilityId;

/**
 * Default {@link GeminiModelRouter} implementing the two-tier routing policy (Req 11.1, 12.1).
 *
 * <p>The mapping is total and deterministic:
 * <ul>
 *   <li>Every {@link CapabilityId} — the deep-reasoning counterfactual/economics/certification
 *       families (EID, ERD, AEI, substitution, economics, certification, lifecycle, and the
 *       pollution/passport families reasoned by the root orchestrator) — routes to
 *       {@link GeminiTier#PRO}.</li>
 *   <li>Every {@link ClassificationTask} — threshold check, masking triage, intent routing —
 *       routes to {@link GeminiTier#FLASH}.</li>
 * </ul>
 *
 * <p>Model identifiers are resolved from {@link GeminiRoutingProperties}; the router holds no live
 * Vertex AI client, so it is safe to construct and exercise without any managed service.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 12.1</a>
 */
@Service
public class DefaultGeminiModelRouter implements GeminiModelRouter {

    private final GeminiRoutingProperties properties;

    /**
     * Creates the router over the bound routing properties (constructor injection).
     *
     * @param properties the tier-to-model configuration, must not be {@code null}
     */
    public DefaultGeminiModelRouter(GeminiRoutingProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public GeminiTier tierFor(CapabilityId capability) {
        Objects.requireNonNull(capability, "capability");
        // Deep reasoning / planning always routes to the Pro tier (Req 11.1, 12.1).
        return GeminiTier.PRO;
    }

    @Override
    public GeminiTier tierFor(ClassificationTask task) {
        Objects.requireNonNull(task, "task");
        // High-volume classification always routes to the Flash tier (Req 11.1, 12.1).
        return GeminiTier.FLASH;
    }

    @Override
    public String modelFor(GeminiTier tier) {
        Objects.requireNonNull(tier, "tier");
        return properties.modelFor(tier);
    }
}
