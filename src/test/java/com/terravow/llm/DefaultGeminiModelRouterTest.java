package com.terravow.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.terravow.reasoning.CapabilityId;

/**
 * Unit tests for {@link DefaultGeminiModelRouter} two-tier routing (Req 11.1, 12.1).
 *
 * <p>Verifies that every deep-reasoning capability routes to the Pro tier, every high-volume
 * classification task routes to the Flash tier, and tier-to-model resolution reflects the bound
 * {@link GeminiRoutingProperties}.
 */
class DefaultGeminiModelRouterTest {

    private final GeminiRoutingProperties properties = new GeminiRoutingProperties();
    private final GeminiModelRouter router = new DefaultGeminiModelRouter(properties);

    @ParameterizedTest
    @EnumSource(CapabilityId.class)
    void deepReasoningCapabilitiesRouteToPro(CapabilityId capability) {
        assertThat(router.tierFor(capability)).isEqualTo(GeminiTier.PRO);
    }

    @Test
    void deepReasoningCoreCapabilitiesRouteToPro() {
        // Explicit coverage of the capabilities named in the task: EID, ERD, AEI, substitution,
        // economics, certification, lifecycle, and the orchestrator-root reasoning families.
        assertThat(router.tierFor(CapabilityId.EID_ATTRIBUTION)).isEqualTo(GeminiTier.PRO);
        assertThat(router.tierFor(CapabilityId.ERD_QUANTIFICATION)).isEqualTo(GeminiTier.PRO);
        assertThat(router.tierFor(CapabilityId.AEI)).isEqualTo(GeminiTier.PRO);
        assertThat(router.tierFor(CapabilityId.SUBSTITUTION_DOSSIER)).isEqualTo(GeminiTier.PRO);
        assertThat(router.tierFor(CapabilityId.ECONOMICS)).isEqualTo(GeminiTier.PRO);
        assertThat(router.tierFor(CapabilityId.CERTIFICATION)).isEqualTo(GeminiTier.PRO);
        assertThat(router.tierFor(CapabilityId.MAINTENANCE_LIFECYCLE)).isEqualTo(GeminiTier.PRO);
    }

    @ParameterizedTest
    @EnumSource(ClassificationTask.class)
    void classificationTasksRouteToFlash(ClassificationTask task) {
        assertThat(router.tierFor(task)).isEqualTo(GeminiTier.FLASH);
    }

    @Test
    void classificationNamedTasksRouteToFlash() {
        assertThat(router.tierFor(ClassificationTask.THRESHOLD_CHECK)).isEqualTo(GeminiTier.FLASH);
        assertThat(router.tierFor(ClassificationTask.MASKING_TRIAGE)).isEqualTo(GeminiTier.FLASH);
        assertThat(router.tierFor(ClassificationTask.INTENT_ROUTING)).isEqualTo(GeminiTier.FLASH);
    }

    @Test
    void modelForResolvesConfiguredIdentifiersPerTier() {
        properties.setProModel("gemini-3.1-pro-custom");
        properties.setFlashModel("gemini-flash-custom");

        assertThat(router.modelFor(GeminiTier.PRO)).isEqualTo("gemini-3.1-pro-custom");
        assertThat(router.modelFor(GeminiTier.FLASH)).isEqualTo("gemini-flash-custom");
    }

    @Test
    void modelForUsesSafeDefaultsWhenUnconfigured() {
        assertThat(router.modelFor(GeminiTier.PRO)).isEqualTo("gemini-3.1-pro");
        assertThat(router.modelFor(GeminiTier.FLASH)).isEqualTo("gemini-flash");
    }

    @Test
    void blankModelIdentifiersCoerceBackToSafeDefaults() {
        properties.setProModel("  ");
        properties.setFlashModel(null);

        assertThat(router.modelFor(GeminiTier.PRO)).isEqualTo("gemini-3.1-pro");
        assertThat(router.modelFor(GeminiTier.FLASH)).isEqualTo("gemini-flash");
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> router.tierFor((CapabilityId) null));
        assertThatNullPointerException().isThrownBy(() -> router.tierFor((ClassificationTask) null));
        assertThatNullPointerException().isThrownBy(() -> router.modelFor(null));
    }
}
