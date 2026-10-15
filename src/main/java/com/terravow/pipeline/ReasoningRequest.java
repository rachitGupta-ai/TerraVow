package com.terravow.pipeline;

import com.terravow.reasoning.CapabilityId;
import com.terravow.reasoning.GroundingSpec;
import com.terravow.reasoning.OutputSchema;
import com.terravow.reasoning.RetryPolicy;
import com.terravow.reasoning.ToolDeclaration;

import java.util.List;
import java.util.Objects;

/**
 * The capability-supplied inputs the cross-cutting control flow needs to drive the
 * {@code Reasoning_Agent} orchestrator for a determination that must be reasoned (design &rarr;
 * Cross-Cutting Control Flow; Req 11.1).
 *
 * <p>Bundles everything the orchestrator's {@code reason} call requires <em>except the prompt</em>:
 * the {@link CapabilityId} naming the specialist agent, the {@link OutputSchema} the determination
 * is validated against, the verified {@link ToolDeclaration tools} the agent may call via function
 * calling, the {@link GroundingSpec} it grounds on, and the {@link RetryPolicy} bounding retries.
 * The prompt is supplied separately as an already-masked
 * {@link com.terravow.reasoning.PromptSpec} so that the control flow — not the capability — owns the
 * guarantee that only masked inputs cross into the managed runtime (Req 13.3): the capability hands
 * raw supplier data to {@link CrossCuttingControlFlow#produceDetermination}, and the control flow
 * masks it through the {@link com.terravow.pii.MaskingGate} before building the prompt.
 *
 * @param <T>        the typed determination the schema produces
 * @param capability the specialist-agent capability; never {@code null}
 * @param schema     the output schema the determination is validated against; never {@code null}
 * @param tools      the verified deterministic tools available via function calling; never
 *                   {@code null}, defensively copied, may be empty
 * @param grounding  the managed-retrieval grounding specification; never {@code null}
 * @param policy     the bounded retry policy (1..5, default 3); never {@code null}
 *
 * @see CrossCuttingControlFlow
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
public record ReasoningRequest<T>(
        CapabilityId capability,
        OutputSchema<T> schema,
        List<ToolDeclaration> tools,
        GroundingSpec grounding,
        RetryPolicy policy) {

    /**
     * Validates the references and defensively copies the tool list.
     *
     * @throws NullPointerException if any argument (or a tool element) is {@code null}
     */
    public ReasoningRequest {
        Objects.requireNonNull(capability, "ReasoningRequest capability must not be null");
        Objects.requireNonNull(schema, "ReasoningRequest schema must not be null");
        Objects.requireNonNull(tools, "ReasoningRequest tools must not be null");
        Objects.requireNonNull(grounding, "ReasoningRequest grounding must not be null");
        Objects.requireNonNull(policy, "ReasoningRequest policy must not be null");
        tools = List.copyOf(tools); // immutable copy; rejects null elements
    }
}
