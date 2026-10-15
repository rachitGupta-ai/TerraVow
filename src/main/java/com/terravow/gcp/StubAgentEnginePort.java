package com.terravow.gcp;

import com.terravow.reasoning.CapabilityId;
import com.terravow.reasoning.GroundingCitation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A deterministic stand-in for the live Agent Engine transport, for tests and local development
 * (Req 11.1).
 *
 * <p>TerraVow is local-first and has no live Vertex AI in the test environment, so this stub plays
 * the role of the managed runtime's root-orchestrator agent: it inspects the request's
 * {@link CapabilityId} and routes to a per-capability response producer, mirroring how the ADK root
 * orchestrator would delegate to a per-capability specialist agent (Req 11.1). Each producer is a
 * pure {@link Function} of the request, so the stub is fully deterministic — no wall-clock time,
 * randomness, or iteration-order dependence.
 *
 * <p>Only the already-masked {@link AgentEngineRequest} ever reaches a producer (Req 13.3); the stub
 * itself does not unmask or inspect raw identifiers. A capability with no registered producer yields
 * an {@link AgentEngineResponse} with a {@code null} raw output and no citations, which the
 * orchestrator treats as a schema-invalid attempt.
 *
 * @see AgentEnginePort
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
public final class StubAgentEnginePort implements AgentEnginePort {

    private final Map<CapabilityId, Function<AgentEngineRequest, AgentEngineResponse>> producers;

    /**
     * Creates a stub transport whose per-capability producers model the specialist agents (Req
     * 11.1).
     *
     * @param producers the per-capability response producers; never {@code null}, defensively
     *                  copied, may be empty
     * @throws NullPointerException if {@code producers} is {@code null} or contains a {@code null}
     *                              key/value
     */
    public StubAgentEnginePort(
            Map<CapabilityId, Function<AgentEngineRequest, AgentEngineResponse>> producers) {
        this.producers = Map.copyOf(Objects.requireNonNull(producers, "producers must not be null"));
    }

    /**
     * Creates a stub transport that returns the same raw output and citations for every capability.
     *
     * @param rawOutput the raw determination to return for every capability; may be {@code null}
     * @param citations the grounding citations to return for every capability; never {@code null}
     * @return a stub transport with a single uniform producer across all capabilities
     */
    public static StubAgentEnginePort returningForAll(
            Object rawOutput, List<GroundingCitation> citations) {
        List<GroundingCitation> copy = List.copyOf(citations);
        AgentEngineResponse response = new AgentEngineResponse(rawOutput, copy);
        java.util.EnumMap<CapabilityId, Function<AgentEngineRequest, AgentEngineResponse>> map =
                new java.util.EnumMap<>(CapabilityId.class);
        for (CapabilityId capability : CapabilityId.values()) {
            map.put(capability, request -> response);
        }
        return new StubAgentEnginePort(map);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Routes the request to the producer registered for its {@link CapabilityId}, modelling the
     * root orchestrator's delegation to the matching specialist agent (Req 11.1). An unregistered
     * capability yields an empty, citation-free response.
     *
     * @throws NullPointerException if {@code request} is {@code null}
     */
    @Override
    public AgentEngineResponse invoke(AgentEngineRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Function<AgentEngineRequest, AgentEngineResponse> producer =
                producers.get(request.capability());
        if (producer == null) {
            return AgentEngineResponse.of(null);
        }
        return Objects.requireNonNull(
                producer.apply(request),
                "StubAgentEnginePort producer must not return null");
    }
}
