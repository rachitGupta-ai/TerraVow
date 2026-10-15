package com.terravow.reasoning;

import java.util.List;

/**
 * The TerraVow-owned contract wrapping every Vertex AI Agent Engine invocation with the
 * schema-validate &rarr; bounded-retry &rarr; escalate loop (Req 11.1–11.5).
 *
 * <p>A determination that does not exist in any database is produced through a Reasoning_Agent — a
 * per-capability specialist agent on the managed Agent Engine runtime (ADK), coordinated by a root
 * orchestrator agent, using Gemini on Vertex AI (Req 11.1). The returned determination is validated
 * against its {@link OutputSchema} (Req 11.2); a valid determination is accepted and returned to the
 * requesting capability (Req 11.3); an invalid determination is retried up to the clamped limit
 * carried by the {@link RetryPolicy} (1..5, default 3) (Req 11.4); exhausting every attempt routes
 * the determination to human review and records the capability, attempts performed, and the
 * schema-validation failure reason (Req 11.5).
 *
 * <p>Deterministic values are produced by the verified {@link ToolDeclaration tools} the specialist
 * agent invokes via Gemini function calling, not free-generated; grounding citations returned under
 * the {@link GroundingSpec} are folded into the Evidence_Chain of the result. Only masked inputs are
 * ever placed in the {@link PromptSpec}, in Agent Engine Sessions/Memory Bank, and in any grounding
 * query — no raw PII or confidential supplier identifier reaches the managed runtime (Req 13.3).
 *
 * <p>This interface defines the contract only; the validate &rarr; retry &rarr; escalate loop is
 * implemented in task 6.2 and the Agent Engine runtime adapter in task 6.3.
 *
 * @see ReasoningResult
 * @see RetryPolicy
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 11.2, 11.3,
 *      11.4, 11.5, 13.3</a>
 */
public interface ReasoningAgentOrchestrator {

    /**
     * Produces a schema-validated determination for the given capability (Req 11.1–11.5).
     *
     * <p>Delegates execution to the managed Agent Engine runtime (root orchestrator plus the
     * per-capability specialist agent), passing only masked inputs. Retries on schema-validation
     * failure up to the {@code policy}'s clamped limit; on exhaustion returns an
     * {@link ReasoningStatus#ESCALATED_HUMAN_REVIEW} result carrying the failure record.
     *
     * @param <T>        the typed determination produced for the capability
     * @param capability the determination family this request belongs to; never {@code null}
     * @param prompt     the prompt carrying only masked inputs; never {@code null}
     * @param schema     the output schema the determination is validated against; never
     *                   {@code null}
     * @param tools      the verified deterministic tools available via function calling; never
     *                   {@code null}, may be empty
     * @param grounding  the managed-retrieval grounding specification; never {@code null}
     * @param policy     the bounded retry policy (1..5, default 3); never {@code null}
     * @return the reasoning result: an accepted typed determination, or an escalation with a
     *         complete failure record
     */
    <T> ReasoningResult<T> reason(
            CapabilityId capability,
            PromptSpec prompt,
            OutputSchema<T> schema,
            List<ToolDeclaration> tools,
            GroundingSpec grounding,
            RetryPolicy policy);
}
