package com.terravow.gcp;

import com.terravow.reasoning.CapabilityId;

import java.util.Map;

/**
 * The seam to Agent Engine's Sessions / Memory Bank durable cross-session context (Req 11.1, 13.3).
 *
 * <p>Agent Engine offers Sessions and a Memory Bank for durable cross-session context. TerraVow
 * uses that context only to carry forward <strong>masked</strong> state: no raw PII or confidential
 * supplier identifier may ever be written to the Memory Bank (Req 13.3). This port isolates that
 * write so the live managed store is a configuration concern and the invariant is enforced in one
 * place by {@link AgentEngineSpecialistAgentRuntime}, with a deterministic in-memory implementation
 * ({@link InMemoryMaskedContextStore}) for tests and local dev.
 *
 * @see InMemoryMaskedContextStore
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
public interface MaskedContextStore {

    /**
     * Records the masked context for a capability's specialist-agent session (Req 13.3).
     *
     * <p>Callers must pass only already-masked inputs; the {@link AgentEngineSpecialistAgentRuntime}
     * guarantees this by sourcing the context from a {@link com.terravow.reasoning.PromptSpec} that
     * is structurally masked. Implementations must never persist raw identifiers.
     *
     * @param capability    the capability whose session context is being stored; never {@code null}
     * @param maskedContext the already-masked context to retain; never {@code null}
     */
    void storeMaskedContext(CapabilityId capability, Map<String, Object> maskedContext);
}
