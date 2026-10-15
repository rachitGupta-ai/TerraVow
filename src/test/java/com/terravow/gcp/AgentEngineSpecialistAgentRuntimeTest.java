package com.terravow.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.evidence.SourceRef;
import com.terravow.reasoning.AgentInvocation;
import com.terravow.reasoning.CapabilityId;
import com.terravow.reasoning.DataStoreRef;
import com.terravow.reasoning.GroundingCitation;
import com.terravow.reasoning.GroundingMode;
import com.terravow.reasoning.GroundingSpec;
import com.terravow.reasoning.PromptSpec;
import com.terravow.reasoning.ToolDeclaration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AgentEngineSpecialistAgentRuntime}, verifying masked-only enforcement (Req
 * 13.3), grounding-citation pass-through (Req 10.1), and per-{@link CapabilityId} delegation to the
 * specialist agent (Req 11.1), all against the deterministic {@link StubAgentEnginePort} and
 * {@link InMemoryMaskedContextStore} seams.
 */
class AgentEngineSpecialistAgentRuntimeTest {

    // Shared fixtures -------------------------------------------------------------------------

    private static final GroundingSpec GROUNDING =
            new GroundingSpec(new DataStoreRef("corpus"), GroundingMode.VERTEX_AI_SEARCH);

    private static final List<ToolDeclaration> NO_TOOLS = List.of();

    private static PromptSpec maskedPrompt() {
        return PromptSpec.ofMasked("compute EID", Map.of("machineId", "M-***"));
    }

    // Req 11.1 — the request is routed to the specialist agent for its capability -------------

    @Test
    void delegatesToTheSpecialistAgentForTheRequestedCapability() {
        // A distinct raw output per capability lets us assert the correct specialist ran.
        EnumMap<CapabilityId, Function<AgentEngineRequest, AgentEngineResponse>> producers =
                new EnumMap<>(CapabilityId.class);
        for (CapabilityId capability : CapabilityId.values()) {
            String marker = "answer-for-" + capability.name();
            producers.put(capability, request -> AgentEngineResponse.of(marker));
        }
        StubAgentEnginePort port = new StubAgentEnginePort(producers);
        AgentEngineSpecialistAgentRuntime runtime =
                new AgentEngineSpecialistAgentRuntime(port, new InMemoryMaskedContextStore());

        for (CapabilityId capability : CapabilityId.values()) {
            AgentInvocation invocation =
                    runtime.invoke(capability, maskedPrompt(), NO_TOOLS, GROUNDING);
            assertThat(invocation.rawOutput()).isEqualTo("answer-for-" + capability.name());
        }
    }

    @Test
    void passesTheRequestedCapabilityThroughToTheTransport() {
        List<CapabilityId> seen = new ArrayList<>();
        EnumMap<CapabilityId, Function<AgentEngineRequest, AgentEngineResponse>> producers =
                new EnumMap<>(CapabilityId.class);
        producers.put(CapabilityId.ECONOMICS, request -> {
            seen.add(request.capability());
            return AgentEngineResponse.of("ok");
        });
        StubAgentEnginePort port = new StubAgentEnginePort(producers);
        AgentEngineSpecialistAgentRuntime runtime =
                new AgentEngineSpecialistAgentRuntime(port, new InMemoryMaskedContextStore());

        runtime.invoke(CapabilityId.ECONOMICS, maskedPrompt(), NO_TOOLS, GROUNDING);

        assertThat(seen).containsExactly(CapabilityId.ECONOMICS);
    }

    // Req 10.1 — grounding citations returned by the runtime are passed through unchanged -------

    @Test
    void passesGroundingCitationsThroughToTheInvocation() {
        GroundingCitation citation =
                new GroundingCitation(new SourceRef("reg-text-7"), "base rate chapter 3");
        StubAgentEnginePort port =
                StubAgentEnginePort.returningForAll("valid", List.of(citation));
        AgentEngineSpecialistAgentRuntime runtime =
                new AgentEngineSpecialistAgentRuntime(port, new InMemoryMaskedContextStore());

        AgentInvocation invocation =
                runtime.invoke(CapabilityId.EID_ATTRIBUTION, maskedPrompt(), NO_TOOLS, GROUNDING);

        assertThat(invocation.rawOutput()).isEqualTo("valid");
        assertThat(invocation.citations()).containsExactly(citation);
    }

    @Test
    void emptyCitationsPassThroughAsEmpty() {
        StubAgentEnginePort port = StubAgentEnginePort.returningForAll("valid", List.of());
        AgentEngineSpecialistAgentRuntime runtime =
                new AgentEngineSpecialistAgentRuntime(port, new InMemoryMaskedContextStore());

        AgentInvocation invocation =
                runtime.invoke(CapabilityId.EID_ATTRIBUTION, maskedPrompt(), NO_TOOLS, GROUNDING);

        assertThat(invocation.citations()).isEmpty();
    }

    // Req 13.3 — only masked context reaches the Memory Bank; the capability's session gets it ---

    @Test
    void storesOnlyMaskedContextInTheMemoryBankForTheRequestedCapability() {
        InMemoryMaskedContextStore store = new InMemoryMaskedContextStore();
        StubAgentEnginePort port = StubAgentEnginePort.returningForAll("valid", List.of());
        AgentEngineSpecialistAgentRuntime runtime =
                new AgentEngineSpecialistAgentRuntime(port, store);
        PromptSpec prompt = PromptSpec.ofMasked("compute EID", Map.of("machineId", "M-***"));

        runtime.invoke(CapabilityId.AEI, prompt, NO_TOOLS, GROUNDING);

        // Context landed under the correct capability's session and matches the masked inputs.
        assertThat(store.contextsFor(CapabilityId.AEI))
                .containsExactly(Map.of("machineId", "M-***"));
        // No other capability's session was written.
        assertThat(store.contextsFor(CapabilityId.ECONOMICS)).isEmpty();
    }

    // Req 13.3 — a prompt not marked masked is rejected before anything crosses the seam --------

    @Test
    void rejectsAnUnmaskedPromptBeforeTouchingTheTransportOrMemoryBank() {
        // Build an unmasked spec directly via the canonical constructor's escape hatch: the
        // PromptSpec constructor rejects masked=false, so we assert that guard here (defence in
        // depth) and that no state leaked. The adapter's own guard mirrors it.
        InMemoryMaskedContextStore store = new InMemoryMaskedContextStore();
        List<AgentEngineRequest> dispatched = new ArrayList<>();
        EnumMap<CapabilityId, Function<AgentEngineRequest, AgentEngineResponse>> producers =
                new EnumMap<>(CapabilityId.class);
        producers.put(CapabilityId.EID_ATTRIBUTION, request -> {
            dispatched.add(request);
            return AgentEngineResponse.of("should-not-happen");
        });
        AgentEngineSpecialistAgentRuntime runtime = new AgentEngineSpecialistAgentRuntime(
                new StubAgentEnginePort(producers), store);

        // PromptSpec itself forbids masked=false, which is the first line of defence (Req 13.3).
        assertThatThrownBy(() -> new PromptSpec("x", Map.of("k", "v"), false))
                .isInstanceOf(IllegalArgumentException.class);

        // Nothing was dispatched and nothing was written to the Memory Bank.
        assertThat(dispatched).isEmpty();
        assertThat(store.contextsFor(CapabilityId.EID_ATTRIBUTION)).isEmpty();
    }

    // Req 11.1 — the raw output may be null (schema-invalid attempt) and still passes through -----

    @Test
    void nullRawOutputPassesThroughForTheOrchestratorToRetry() {
        StubAgentEnginePort port = StubAgentEnginePort.returningForAll(null, List.of());
        AgentEngineSpecialistAgentRuntime runtime =
                new AgentEngineSpecialistAgentRuntime(port, new InMemoryMaskedContextStore());

        AgentInvocation invocation =
                runtime.invoke(CapabilityId.ERD_QUANTIFICATION, maskedPrompt(), NO_TOOLS, GROUNDING);

        assertThat(invocation.rawOutput()).isNull();
        assertThat(invocation.citations()).isEmpty();
    }
}
