package com.terravow.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for the bounded-retry guarantee of
 * {@link DefaultReasoningAgentOrchestrator} (Req 11.4).
 *
 * <p>Feature: terravow, Property 35: Retries are bounded by the clamped limit.
 *
 * <p>For an always-invalid specialist-agent runtime and <em>any</em> requested retry limit —
 * negative, zero, in range {@code [1, 5]}, or greater than 5 — the orchestrator performs exactly
 * {@link RetryPolicy#clamped(int)}-many attempts: the requested value coerced into {@code [1, 5]}.
 * The number of runtime invocations therefore never exceeds the clamped limit, so a mis-set or
 * adversarial configuration cannot drive unbounded reasoning calls (Req 11.4).
 *
 * @see DefaultReasoningAgentOrchestrator
 * @see RetryPolicy#clamped(int)
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.4</a>
 */
class BoundedRetriesProperties {

    private static final CapabilityId CAPABILITY = CapabilityId.EID_ATTRIBUTION;

    private static final PromptSpec PROMPT =
            PromptSpec.ofMasked("compute EID", Map.of("machineId", "M-***"));

    private static final GroundingSpec GROUNDING =
            new GroundingSpec(new DataStoreRef("corpus"), GroundingMode.VERTEX_AI_SEARCH);

    private static final List<ToolDeclaration> NO_TOOLS = List.of();

    /** A schema that rejects every output, forcing the loop to exhaust its clamped budget. */
    private static final OutputSchema<String> ALWAYS_INVALID_SCHEMA = new OutputSchema<>() {
        @Override
        public boolean validate(Object rawOutput) {
            return false;
        }

        @Override
        public java.util.Optional<String> coerce(Object rawOutput) {
            return java.util.Optional.empty();
        }

        @Override
        public String name() {
            return "always-invalid-schema";
        }
    };

    /**
     * A deterministic runtime that always returns a schema-invalid output and counts how many
     * times it was invoked. Reuses the counting-stub pattern from
     * {@code DefaultReasoningAgentOrchestratorTest}.
     */
    private static final class CountingRuntime implements SpecialistAgentRuntime {
        private int invocationCount;

        @Override
        public AgentInvocation invoke(
                CapabilityId capability,
                PromptSpec prompt,
                List<ToolDeclaration> tools,
                GroundingSpec grounding) {
            invocationCount++;
            // Null is always schema-invalid under ALWAYS_INVALID_SCHEMA.
            return new AgentInvocation(null, new ArrayList<>());
        }

        int invocationCount() {
            return invocationCount;
        }
    }

    // Feature: terravow, Property 35: Retries are bounded by the clamped limit
    /**
     * Attempts never exceed the clamped limit, for any requested value including out-of-range ones.
     *
     * <p>**Validates: Requirements 11.4**
     */
    @Property(tries = 100)
    void retriesAreBoundedByTheClampedLimit(@ForAll @IntRange(min = -1000, max = 1000) int requestedLimit) {
        CountingRuntime runtime = new CountingRuntime();
        RetryPolicy policy = RetryPolicy.clamped(requestedLimit);
        int expectedAttempts = policy.retryLimit();

        ReasoningResult<String> result = new DefaultReasoningAgentOrchestrator(runtime).reason(
                CAPABILITY, PROMPT, ALWAYS_INVALID_SCHEMA, NO_TOOLS, GROUNDING, policy);

        // The clamped limit is always a safe bound within [1, 5].
        assertThat(expectedAttempts)
                .isBetween(RetryPolicy.MIN_RETRY_LIMIT, RetryPolicy.MAX_RETRY_LIMIT);

        // Every always-invalid run escalates after exhausting exactly the clamped budget.
        assertThat(result.status()).isEqualTo(ReasoningStatus.ESCALATED_HUMAN_REVIEW);
        assertThat(result.attemptsPerformed()).isEqualTo(expectedAttempts);
        assertThat(runtime.invocationCount())
                .isEqualTo(expectedAttempts)
                .isLessThanOrEqualTo(RetryPolicy.MAX_RETRY_LIMIT);
    }
}
