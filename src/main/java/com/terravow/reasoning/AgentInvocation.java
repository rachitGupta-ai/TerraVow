package com.terravow.reasoning;

import java.util.List;
import java.util.Objects;

/**
 * The raw result of a single invocation of the managed Agent Engine runtime, as returned by a
 * {@link SpecialistAgentRuntime} and consumed by the orchestrator's validate &rarr; retry &rarr;
 * escalate loop (Req 11.1, 11.2, 11.3).
 *
 * <p>This is the <em>port</em> boundary between the orchestrator (task 6.2) and the Agent Engine
 * adapter (task 6.3). The adapter performs one specialist-agent invocation and returns the raw,
 * not-yet-validated output as an opaque {@link Object} together with any grounding citations the
 * agent produced. The orchestrator validates {@code rawOutput} against the capability's
 * {@link OutputSchema} (Req 11.2) and, on acceptance, folds {@code citations} into the determination's
 * Evidence_Chain (Req 10.1). A single {@code AgentInvocation} corresponds to exactly one attempt in
 * the bounded-retry count (Req 11.4).
 *
 * <p>The {@code rawOutput} may be {@code null} to represent an invocation that produced no usable
 * payload (for example a malformed response); such an output is simply not schema-valid and drives a
 * retry rather than an exception.
 *
 * @param rawOutput the raw, not-yet-validated determination from the specialist agent; may be
 *                  {@code null} to denote an empty/unusable response
 * @param citations the grounding citations returned by the agent; never {@code null}, defensively
 *                  copied, may be empty
 *
 * @see SpecialistAgentRuntime
 * @see ReasoningResult
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 11.2, 11.3,
 *      10.1</a>
 */
public record AgentInvocation(Object rawOutput, List<GroundingCitation> citations) {

    /**
     * Validates that {@code citations} is present and defensively copies it into an immutable list.
     * The {@code rawOutput} is intentionally permitted to be {@code null}.
     *
     * @throws NullPointerException if {@code citations} is {@code null} or contains a {@code null}
     *                              element
     */
    public AgentInvocation {
        Objects.requireNonNull(citations, "AgentInvocation citations must not be null");
        citations = List.copyOf(citations); // immutable copy; rejects null elements
    }

    /**
     * Creates an invocation carrying the given raw output and no grounding citations.
     *
     * @param rawOutput the raw determination; may be {@code null}
     * @return an {@code AgentInvocation} with an empty citation list
     */
    public static AgentInvocation of(Object rawOutput) {
        return new AgentInvocation(rawOutput, List.of());
    }
}
