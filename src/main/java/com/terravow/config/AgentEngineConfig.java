package com.terravow.config;

import com.terravow.gcp.AgentEnginePort;
import com.terravow.gcp.InMemoryMaskedContextStore;
import com.terravow.gcp.MaskedContextStore;
import com.terravow.gcp.StubAgentEnginePort;
import com.terravow.reasoning.CapabilityId;

import java.util.EnumMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default wiring for the Vertex AI Agent Engine seams (Req 11.1, 13.3).
 *
 * <p>TerraVow is local-first and has no live Vertex AI in the dev/test environment, so this
 * configuration provides deterministic default beans for the two seams the
 * {@link com.terravow.gcp.AgentEngineSpecialistAgentRuntime} depends on: the
 * {@link AgentEnginePort} transport and the {@link MaskedContextStore} (Sessions / Memory Bank).
 * Each bean is declared {@link ConditionalOnMissingBean} so a later configuration can supply the
 * real managed Google Cloud client without touching this class — the live client is a wiring and
 * configuration concern (Req 11.1).
 *
 * <p>The default {@link AgentEnginePort} is a {@link StubAgentEnginePort} with no per-capability
 * producers: it returns a {@code null} raw output for every capability, which the orchestrator
 * treats as a schema-invalid attempt and escalates to human review. This is the safe local default
 * — the stub never fabricates a determination; a real specialist agent (or a test-specific stub)
 * supplies actual outputs.
 *
 * @see com.terravow.gcp.AgentEngineSpecialistAgentRuntime
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
@Configuration
public class AgentEngineConfig {

    /**
     * The default Agent Engine transport for local-first operation: a deterministic stub with no
     * producers, yielding a {@code null} raw output per capability so the orchestrator escalates
     * rather than accepting any invented determination (Req 11.1).
     *
     * @return a deterministic {@link AgentEnginePort} default bean
     */
    @Bean
    @ConditionalOnMissingBean(AgentEnginePort.class)
    public AgentEnginePort agentEnginePort() {
        return new StubAgentEnginePort(new EnumMap<>(CapabilityId.class));
    }

    /**
     * The default Sessions / Memory Bank store for local-first operation: an in-memory store that
     * retains masked context only (Req 13.3).
     *
     * @return a deterministic {@link MaskedContextStore} default bean
     */
    @Bean
    @ConditionalOnMissingBean(MaskedContextStore.class)
    public MaskedContextStore maskedContextStore() {
        return new InMemoryMaskedContextStore();
    }
}
