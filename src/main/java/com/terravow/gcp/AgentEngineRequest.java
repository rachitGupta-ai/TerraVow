package com.terravow.gcp;

import com.terravow.reasoning.CapabilityId;
import com.terravow.reasoning.GroundingSpec;
import com.terravow.reasoning.PromptSpec;
import com.terravow.reasoning.ToolDeclaration;

import java.util.List;
import java.util.Objects;

/**
 * The transport-level request handed to the managed Agent Engine runtime for a single specialist
 * agent invocation (Req 11.1, 13.3).
 *
 * <p>This record is the payload the {@link AgentEnginePort} transport sees. It carries the target
 * {@link CapabilityId} (so the root-orchestrator agent can delegate to the correct per-capability
 * specialist agent — Req 11.1), the {@link PromptSpec} (which structurally guarantees only masked
 * inputs — Req 13.3), the verified {@link ToolDeclaration}s exposed via Gemini function calling, and
 * the {@link GroundingSpec} that grounds interpretive values on the regulatory corpus.
 *
 * <p>No raw PII or confidential supplier identifier can reach the transport through this request:
 * the {@link PromptSpec} canonical constructor rejects an unmasked spec, and the
 * {@link AgentEngineSpecialistAgentRuntime} re-asserts the masking invariant before constructing
 * this request (Req 13.3).
 *
 * @param capability the determination family the request targets; never {@code null}
 * @param prompt     the prompt carrying only masked inputs; never {@code null}
 * @param tools      the verified deterministic tools available via function calling; never
 *                   {@code null}, defensively copied, may be empty
 * @param grounding  the managed-retrieval grounding specification; never {@code null}
 *
 * @see AgentEnginePort
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
public record AgentEngineRequest(
        CapabilityId capability,
        PromptSpec prompt,
        List<ToolDeclaration> tools,
        GroundingSpec grounding) {

    /**
     * Validates presence of all fields and defensively copies the tool list.
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public AgentEngineRequest {
        Objects.requireNonNull(capability, "AgentEngineRequest capability must not be null");
        Objects.requireNonNull(prompt, "AgentEngineRequest prompt must not be null");
        Objects.requireNonNull(tools, "AgentEngineRequest tools must not be null");
        Objects.requireNonNull(grounding, "AgentEngineRequest grounding must not be null");
        tools = List.copyOf(tools); // immutable copy; rejects null elements
    }
}
