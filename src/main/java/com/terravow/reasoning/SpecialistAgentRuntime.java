package com.terravow.reasoning;

import java.util.List;

/**
 * The port through which the {@link ReasoningAgentOrchestrator} invokes the managed Agent Engine
 * runtime for a single specialist-agent attempt (Req 11.1).
 *
 * <p>This interface is the seam between the orchestrator's schema-validate &rarr; bounded-retry
 * &rarr; escalate loop (task 6.2) and the concrete Vertex AI Agent Engine adapter (task 6.3). The
 * orchestrator calls {@link #invoke} once per retry attempt and never knows how the determination is
 * produced; the adapter in task 6.3 implements it by driving the root-orchestrator agent and the
 * per-capability specialist agent on the managed runtime, passing only the masked inputs carried by
 * the {@link PromptSpec} (Req 13.3) and returning the raw output plus grounding citations as an
 * {@link AgentInvocation}.
 *
 * <p>Implementations must not perform schema validation, retrying, or escalation — those are the
 * orchestrator's responsibility (Req 11.2–11.5). A single call represents a single attempt and is
 * expected to be free of orchestration state so the orchestrator can retry it deterministically.
 *
 * @see ReasoningAgentOrchestrator
 * @see AgentInvocation
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
public interface SpecialistAgentRuntime {

    /**
     * Performs one invocation of the specialist agent for the given capability and returns its raw,
     * not-yet-validated output together with any grounding citations (Req 11.1).
     *
     * <p>Only the masked inputs in {@code prompt} are placed before the managed runtime, its
     * Sessions/Memory Bank, and any grounding query (Req 13.3). This method represents a single
     * attempt; the orchestrator decides whether to accept, retry, or escalate based on schema
     * validation of the returned {@link AgentInvocation#rawOutput()}.
     *
     * @param capability the determination family the request belongs to; never {@code null}
     * @param prompt     the prompt carrying only masked inputs; never {@code null}
     * @param tools      the verified deterministic tools available via function calling; never
     *                   {@code null}, may be empty
     * @param grounding  the managed-retrieval grounding specification; never {@code null}
     * @return the raw agent output and grounding citations for this attempt; never {@code null}
     */
    AgentInvocation invoke(
            CapabilityId capability,
            PromptSpec prompt,
            List<ToolDeclaration> tools,
            GroundingSpec grounding);
}
