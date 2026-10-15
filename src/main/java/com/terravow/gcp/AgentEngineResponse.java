package com.terravow.gcp;

import com.terravow.reasoning.GroundingCitation;

import java.util.List;
import java.util.Objects;

/**
 * The transport-level response returned by the managed Agent Engine runtime for a single specialist
 * agent invocation (Req 11.1, 10.1).
 *
 * <p>This record is the payload the {@link AgentEnginePort} transport produces: the raw,
 * not-yet-validated determination (an opaque {@link Object}, possibly {@code null} for a malformed
 * response) together with the grounding citations the agent produced via Vertex AI Search / BigQuery
 * vector search. The {@link AgentEngineSpecialistAgentRuntime} maps this directly onto the
 * orchestrator-facing {@link com.terravow.reasoning.AgentInvocation}, which folds the citations into
 * the Evidence_Chain (Req 10.1).
 *
 * @param rawOutput the raw, not-yet-validated determination; may be {@code null} to denote an
 *                  empty/unusable response
 * @param citations the grounding citations returned by the agent; never {@code null}, defensively
 *                  copied, may be empty
 *
 * @see AgentEnginePort
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 10.1</a>
 */
public record AgentEngineResponse(Object rawOutput, List<GroundingCitation> citations) {

    /**
     * Validates that {@code citations} is present and defensively copies it. The {@code rawOutput}
     * is intentionally permitted to be {@code null}.
     *
     * @throws NullPointerException if {@code citations} is {@code null} or contains a {@code null}
     *                              element
     */
    public AgentEngineResponse {
        Objects.requireNonNull(citations, "AgentEngineResponse citations must not be null");
        citations = List.copyOf(citations); // immutable copy; rejects null elements
    }

    /**
     * Creates a response carrying the given raw output and no grounding citations.
     *
     * @param rawOutput the raw determination; may be {@code null}
     * @return an {@code AgentEngineResponse} with an empty citation list
     */
    public static AgentEngineResponse of(Object rawOutput) {
        return new AgentEngineResponse(rawOutput, List.of());
    }
}
