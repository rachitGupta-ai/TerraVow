package com.terravow.pipeline;

import com.terravow.gate.DecisionGate;
import com.terravow.gate.DefaultDecisionGate;
import com.terravow.reasoning.DefaultReasoningAgentOrchestrator;
import com.terravow.reasoning.ReasoningAgentOrchestrator;
import com.terravow.reasoning.SpecialistAgentRuntime;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default wiring that makes the cross-cutting control-flow collaborators available as Spring beans
 * (design &rarr; Cross-Cutting Control Flow; Req 8.1, 11.1).
 *
 * <p>The {@link com.terravow.pii.MaskingGate} and {@link com.terravow.evidence.AuditService} already
 * register their own default beans (via {@code IngestionConfig} and the {@code @Service}-annotated
 * {@code DefaultAuditService}), and the {@link SpecialistAgentRuntime} is the {@code @Service}
 * {@code AgentEngineSpecialistAgentRuntime}. This configuration supplies the two remaining pieces
 * the {@link DefaultCrossCuttingControlFlow} depends on — the {@link DecisionGate} and the
 * {@link ReasoningAgentOrchestrator} that wraps the runtime — plus a system {@link Clock} for audit
 * timestamps. Each bean is {@link ConditionalOnMissingBean} so a later configuration can override it
 * without touching this class.
 *
 * @see DefaultCrossCuttingControlFlow
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 10.3, 11.1</a>
 */
@Configuration
public class PipelineConfig {

    /**
     * The deterministic {@link DecisionGate} default bean (Req 8.1).
     *
     * @return a {@link DefaultDecisionGate}
     */
    @Bean
    @ConditionalOnMissingBean(DecisionGate.class)
    public DecisionGate decisionGate() {
        return new DefaultDecisionGate();
    }

    /**
     * The {@link ReasoningAgentOrchestrator} default bean wrapping the injected specialist-agent
     * runtime with the schema-validate &rarr; bounded-retry &rarr; escalate loop (Req 11.1–11.5).
     *
     * @param runtime the specialist-agent runtime port; never {@code null}
     * @return a {@link DefaultReasoningAgentOrchestrator} over {@code runtime}
     */
    @Bean
    @ConditionalOnMissingBean(ReasoningAgentOrchestrator.class)
    public ReasoningAgentOrchestrator reasoningAgentOrchestrator(SpecialistAgentRuntime runtime) {
        return new DefaultReasoningAgentOrchestrator(runtime);
    }

    /**
     * The system {@link Clock} used to stamp audit entries. Supplied as a bean so a test can inject
     * a fixed clock for deterministic audit timestamps.
     *
     * @return the system default-zone clock
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock controlFlowClock() {
        return Clock.systemDefaultZone();
    }
}
