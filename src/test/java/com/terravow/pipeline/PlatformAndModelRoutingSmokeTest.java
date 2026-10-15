package com.terravow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.gcp.AgentEnginePort;
import com.terravow.gcp.InMemoryMaskedContextStore;
import com.terravow.gcp.MaskedContextStore;
import com.terravow.gcp.StubAgentEnginePort;
import com.terravow.gcp.storage.FirestoreRetentionPolicy;
import com.terravow.llm.ClassificationTask;
import com.terravow.llm.GeminiModelRouter;
import com.terravow.llm.GeminiTier;
import com.terravow.reasoning.AgentInvocation;
import com.terravow.reasoning.CapabilityId;
import com.terravow.reasoning.DataStoreRef;
import com.terravow.reasoning.GroundingMode;
import com.terravow.reasoning.GroundingSpec;
import com.terravow.reasoning.PromptSpec;
import com.terravow.reasoning.ReasoningAgentOrchestrator;
import com.terravow.reasoning.SpecialistAgentRuntime;
import com.terravow.reasoning.ToolDeclaration;

import java.time.Period;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Platform and model-routing smoke test: asserts the deployment wiring is in place for a local-first
 * run that mirrors Cloud Run + Vertex AI / Agent Engine, rather than hitting any live Google Cloud
 * service (Req 11.1, 12.1, 10.4).
 *
 * <p>TerraVow runs its capability services on Cloud Run and reasons over Vertex AI Agent Engine
 * (Req 12.1); there is no live GCP in dev/test, so these checks assert the <em>seams</em> are wired:
 *
 * <ul>
 *   <li><strong>Services start with the Agent Engine / orchestrator beans present (Req 11.1,
 *       12.1).</strong> The Spring context loads and exposes the local-first stand-ins for the
 *       managed runtime — the {@link SpecialistAgentRuntime} adapter, its {@link AgentEnginePort}
 *       transport, the {@link MaskedContextStore} (Sessions / Memory Bank), and the
 *       {@link ReasoningAgentOrchestrator} that drives them.</li>
 *   <li><strong>Memory Bank / Sessions store masked context only (Req 11.1).</strong> Invoking the
 *       adapter writes exactly the masked inputs to the store — no raw identifiers — reusing the
 *       pattern from {@code AgentEngineSpecialistAgentRuntimeTest}.</li>
 *   <li><strong>Two-tier routing binds deep reasoning to Pro and classification to Flash (Req 11.1,
 *       12.1).</strong> Every {@link CapabilityId} resolves to {@link GeminiTier#PRO} and every
 *       {@link ClassificationTask} to {@link GeminiTier#FLASH} via the wired
 *       {@link GeminiModelRouter}.</li>
 *   <li><strong>Audit retention is at least seven years (Req 10.4).</strong> The wired
 *       {@link FirestoreRetentionPolicy} meets the regulatory floor.</li>
 * </ul>
 */
@SpringBootTest
class PlatformAndModelRoutingSmokeTest {

    private static final GroundingSpec GROUNDING =
            new GroundingSpec(new DataStoreRef("corpus"), GroundingMode.VERTEX_AI_SEARCH);

    private static final List<ToolDeclaration> NO_TOOLS = List.of();

    @Autowired
    private SpecialistAgentRuntime specialistAgentRuntime;

    @Autowired
    private ReasoningAgentOrchestrator reasoningAgentOrchestrator;

    @Autowired
    private AgentEnginePort agentEnginePort;

    @Autowired
    private MaskedContextStore maskedContextStore;

    @Autowired
    private GeminiModelRouter geminiModelRouter;

    @Autowired
    private FirestoreRetentionPolicy firestoreRetentionPolicy;

    // Req 11.1, 12.1 — services start on Cloud Run with Vertex AI / Agent Engine reachable: the
    // local-first stand-ins for the managed runtime wire up and are reachable from the context.

    @Test
    void agentEngineAndOrchestratorBeansAreWiredAndReachable() {
        assertThat(specialistAgentRuntime).isNotNull();
        assertThat(reasoningAgentOrchestrator).isNotNull();
        assertThat(agentEnginePort).isNotNull();
        assertThat(maskedContextStore).isNotNull();
        assertThat(geminiModelRouter).isNotNull();
    }

    // Req 11.1 — Memory Bank / Sessions store masked context only: invoking the wired adapter stores
    // exactly the masked inputs under the capability's session (pattern from the adapter unit test).

    @Test
    void memoryBankStoresOnlyTheMaskedContextForTheInvokedCapability() {
        // A dedicated store/runtime pair keeps the assertion isolated from the shared context bean,
        // while still exercising the real adapter the context wires (Req 11.1, 13.3).
        InMemoryMaskedContextStore store = new InMemoryMaskedContextStore();
        AgentEnginePort port = new StubAgentEnginePort(Map.of());
        SpecialistAgentRuntime runtime =
                new com.terravow.gcp.AgentEngineSpecialistAgentRuntime(port, store);
        PromptSpec maskedPrompt =
                PromptSpec.ofMasked("compute EID", Map.of("machineId", "M-***"));

        AgentInvocation invocation =
                runtime.invoke(CapabilityId.AEI, maskedPrompt, NO_TOOLS, GROUNDING);

        // The stored context equals the masked inputs and nothing else; a different capability's
        // session is untouched.
        assertThat(store.contextsFor(CapabilityId.AEI))
                .containsExactly(Map.of("machineId", "M-***"));
        assertThat(store.contextsFor(CapabilityId.ECONOMICS)).isEmpty();
        // Unregistered-producer stub yields a null raw output (schema-invalid attempt) and no
        // citations — the invocation still passes through cleanly.
        assertThat(invocation.rawOutput()).isNull();
        assertThat(invocation.citations()).isEmpty();
    }

    // Req 11.1, 12.1 — two-tier routing: deep-reasoning capabilities bind to Pro.

    @ParameterizedTest
    @EnumSource(CapabilityId.class)
    void deepReasoningCapabilitiesBindToPro(CapabilityId capability) {
        assertThat(geminiModelRouter.tierFor(capability)).isEqualTo(GeminiTier.PRO);
    }

    // Req 11.1, 12.1 — two-tier routing: high-volume classification tasks bind to Flash.

    @ParameterizedTest
    @EnumSource(ClassificationTask.class)
    void classificationTasksBindToFlash(ClassificationTask task) {
        assertThat(geminiModelRouter.tierFor(task)).isEqualTo(GeminiTier.FLASH);
    }

    @Test
    void bothTiersResolveToAConfiguredModelIdentifier() {
        assertThat(geminiModelRouter.modelFor(GeminiTier.PRO)).isNotBlank();
        assertThat(geminiModelRouter.modelFor(GeminiTier.FLASH)).isNotBlank();
    }

    // Req 10.4 — audit retention is configured to at least seven years.

    @Test
    void auditRetentionMeetsTheSevenYearFloor() {
        assertThat(firestoreRetentionPolicy).isNotNull();
        assertThat(firestoreRetentionPolicy.meetsRegulatoryMinimum()).isTrue();
        // Normalised to whole years, the configured retention is at least the seven-year floor
        // (Period is not Comparable, so compare the normalised year count directly).
        Period retention = firestoreRetentionPolicy.retention();
        long approxYears =
                retention.getYears() + (retention.getMonths() / 12L) + (retention.getDays() / 365L);
        assertThat(approxYears).isGreaterThanOrEqualTo(7L);
    }
}
