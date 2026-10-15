package com.terravow.gcp;

/**
 * The transport seam to the managed Vertex AI Agent Engine runtime (Req 11.1).
 *
 * <p>This interface isolates the single network call to Agent Engine from the
 * {@link AgentEngineSpecialistAgentRuntime} adapter that enforces TerraVow's invariants. The adapter
 * depends only on this port, so the live Google Cloud client (the ADK root-orchestrator agent
 * deployed on Agent Engine, delegating to per-capability specialist agents) is a later wiring and
 * configuration concern, and the adapter stays unit-testable with a deterministic stub
 * ({@link StubAgentEnginePort}).
 *
 * <p>A single {@link #invoke} call corresponds to exactly one specialist-agent attempt. The port
 * only ever sees the already-masked payload inside {@link AgentEngineRequest} (Req 13.3) — the
 * adapter re-asserts the masking invariant before calling the transport. Implementations perform no
 * schema validation, retrying, or escalation; those belong to the orchestrator (Req 11.2–11.5).
 *
 * @see AgentEngineSpecialistAgentRuntime
 * @see StubAgentEnginePort
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
public interface AgentEnginePort {

    /**
     * Dispatches a single masked request to the managed Agent Engine runtime and returns its raw,
     * not-yet-validated response plus grounding citations (Req 11.1).
     *
     * @param request the masked request to dispatch; never {@code null}
     * @return the raw agent response and grounding citations for this attempt; never {@code null}
     */
    AgentEngineResponse invoke(AgentEngineRequest request);
}
