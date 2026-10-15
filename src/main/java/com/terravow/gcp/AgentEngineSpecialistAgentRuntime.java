package com.terravow.gcp;

import com.terravow.reasoning.AgentInvocation;
import com.terravow.reasoning.CapabilityId;
import com.terravow.reasoning.GroundingSpec;
import com.terravow.reasoning.PromptSpec;
import com.terravow.reasoning.SpecialistAgentRuntime;
import com.terravow.reasoning.ToolDeclaration;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * The Vertex AI Agent Engine adapter implementing {@link SpecialistAgentRuntime} (Req 11.1, 13.3).
 *
 * <p>This adapter represents the managed Agent Engine runtime: an ADK <em>root-orchestrator</em>
 * agent that delegates each request to the <em>per-capability specialist agent</em> named by the
 * {@link CapabilityId}. It is the concrete realisation of the port the orchestrator (task 6.2)
 * drives one attempt at a time. The adapter owns three responsibilities and nothing else — schema
 * validation, retrying, and escalation remain the orchestrator's (Req 11.2–11.5):
 *
 * <ol>
 *   <li><strong>Masked-only enforcement (Req 13.3).</strong> Only the masked inputs on the
 *       {@link PromptSpec} may reach the managed runtime, its Sessions/Memory Bank, or any grounding
 *       query. The {@code PromptSpec} is structurally masked (its constructor rejects an unmasked
 *       spec), and this adapter re-asserts the invariant before any data crosses the transport seam,
 *       so an unmasked prompt can never leak even if constructed reflectively.</li>
 *   <li><strong>Masked context to Memory Bank (Req 13.3).</strong> Before dispatch, the adapter
 *       writes the prompt's masked inputs to the {@link MaskedContextStore} (Sessions / Memory
 *       Bank), which therefore only ever holds masked context.</li>
 *   <li><strong>Citation pass-through (Req 10.1, 11.1).</strong> The grounding citations the runtime
 *       returns are carried back unchanged on the {@link AgentInvocation}, which the orchestrator
 *       folds into the determination's Evidence_Chain.</li>
 * </ol>
 *
 * <p>Delegation to the correct specialist agent per {@link CapabilityId} (Req 11.1) is modelled by
 * the injected {@link AgentEnginePort}: the request carries the capability, and the transport routes
 * it to the matching specialist. The live Google Cloud client is a later wiring/config concern; a
 * deterministic {@link StubAgentEnginePort} serves tests and local dev.
 *
 * @see SpecialistAgentRuntime
 * @see AgentEnginePort
 * @see MaskedContextStore
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3, 10.1</a>
 */
@Service
public final class AgentEngineSpecialistAgentRuntime implements SpecialistAgentRuntime {

    /**
     * Guard message when a prompt reaches the adapter without the masking invariant asserted. This
     * is a defence-in-depth check behind {@link PromptSpec}'s own constructor guard (Req 13.3).
     */
    static final String UNMASKED_PROMPT_REJECTED =
            "PromptSpec must be masked before reaching the Agent Engine runtime (Req 13.3)";

    private final AgentEnginePort agentEngine;
    private final MaskedContextStore maskedContextStore;

    /**
     * Creates the adapter over the Agent Engine transport and the masked-context (Memory Bank)
     * store (Req 11.1, 13.3).
     *
     * @param agentEngine        the transport seam to the managed runtime; never {@code null}
     * @param maskedContextStore the Sessions / Memory Bank store that holds masked context only;
     *                           never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public AgentEngineSpecialistAgentRuntime(
            AgentEnginePort agentEngine, MaskedContextStore maskedContextStore) {
        this.agentEngine = Objects.requireNonNull(agentEngine, "agentEngine must not be null");
        this.maskedContextStore =
                Objects.requireNonNull(maskedContextStore, "maskedContextStore must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Enforces the masking invariant (Req 13.3), stores the masked context in the Memory Bank,
     * delegates the attempt to the specialist agent for {@code capability} via the transport (Req
     * 11.1), and returns the raw output with grounding citations folded through to the orchestrator
     * (Req 10.1).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code prompt} is not marked masked (Req 13.3)
     */
    @Override
    public AgentInvocation invoke(
            CapabilityId capability,
            PromptSpec prompt,
            List<ToolDeclaration> tools,
            GroundingSpec grounding) {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(tools, "tools must not be null");
        Objects.requireNonNull(grounding, "grounding must not be null");

        // Req 13.3: defence-in-depth — no unmasked data may cross the transport seam or reach the
        // Memory Bank, even if a PromptSpec were constructed bypassing its own constructor guard.
        if (!prompt.masked()) {
            throw new IllegalArgumentException(UNMASKED_PROMPT_REJECTED);
        }

        // Req 13.3: Sessions / Memory Bank receive masked context only.
        maskedContextStore.storeMaskedContext(capability, prompt.maskedInputs());

        // Req 11.1: delegate to the per-capability specialist agent via the managed runtime.
        AgentEngineRequest request =
                new AgentEngineRequest(capability, prompt, tools, grounding);
        AgentEngineResponse response = agentEngine.invoke(request);

        // Req 10.1: pass grounding citations through for the orchestrator to fold into the
        // Evidence_Chain. rawOutput is intentionally allowed to be null (schema-invalid attempt).
        return new AgentInvocation(response.rawOutput(), response.citations());
    }
}
