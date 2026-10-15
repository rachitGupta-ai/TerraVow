package com.terravow.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the Reasoning_Agent orchestrator's schema-valid acceptance (Req 11.3).
 *
 * <p>Validates <b>Property 34: Schema-valid determinations are accepted</b>: for <em>any</em>
 * first-attempt Reasoning_Agent output that passes schema validation, {@link
 * DefaultReasoningAgentOrchestrator#reason} accepts the determination with status {@link
 * ReasoningStatus#ACCEPTED}, makes the validated value available to the requesting capability, and
 * does so on a single attempt ({@code attemptsPerformed == 1}) without recording a failure reason.
 * This holds for arbitrary valid payloads and over the full range of clamped retry limits — a
 * schema-valid first output never triggers a retry.
 *
 * <p><b>Validates: Requirements 11.3</b>
 *
 * @see DefaultReasoningAgentOrchestrator
 */
// Feature: terravow, Property 34: Schema-valid determinations are accepted
class SchemaValidAcceptanceProperties {

    private static final CapabilityId CAPABILITY = CapabilityId.EID_ATTRIBUTION;

    private static final PromptSpec PROMPT =
            PromptSpec.ofMasked("compute EID", Map.of("machineId", "M-***"));

    private static final GroundingSpec GROUNDING =
            new GroundingSpec(new DataStoreRef("corpus"), GroundingMode.VERTEX_AI_SEARCH);

    private static final List<ToolDeclaration> NO_TOOLS = List.of();

    /** A schema that accepts any non-null String and coerces it to itself. */
    private static final OutputSchema<String> STRING_SCHEMA = new OutputSchema<>() {
        @Override
        public boolean validate(Object rawOutput) {
            return rawOutput instanceof String;
        }

        @Override
        public Optional<String> coerce(Object rawOutput) {
            return Optional.of((String) rawOutput);
        }

        @Override
        public String name() {
            return "string-schema";
        }
    };

    /**
     * A deterministic stub runtime that returns a fixed schema-valid raw output on the first (and
     * every) invocation, recording how many times it was invoked so the test can assert that a
     * schema-valid first output is accepted without any retry.
     */
    private static final class ValidFirstRuntime implements SpecialistAgentRuntime {
        private final Object validOutput;
        private int invocationCount;

        ValidFirstRuntime(Object validOutput) {
            this.validOutput = validOutput;
        }

        @Override
        public AgentInvocation invoke(
                CapabilityId capability,
                PromptSpec prompt,
                List<ToolDeclaration> tools,
                GroundingSpec grounding) {
            invocationCount++;
            return AgentInvocation.of(validOutput);
        }

        int invocationCount() {
            return invocationCount;
        }
    }

    /**
     * For any schema-valid first-attempt output and any clamped retry limit, the orchestrator
     * accepts the determination on the first attempt and makes the validated value available to the
     * requesting capability (Req 11.3).
     */
    @Property(tries = 100)
    void schemaValidFirstOutputIsAcceptedOnFirstAttempt(
            @ForAll("validPayloads") String payload,
            @ForAll("retryLimits") int requestedLimit) {

        ValidFirstRuntime runtime = new ValidFirstRuntime(payload);
        ReasoningAgentOrchestrator orchestrator = new DefaultReasoningAgentOrchestrator(runtime);

        ReasoningResult<String> result = orchestrator.reason(
                CAPABILITY, PROMPT, STRING_SCHEMA, NO_TOOLS, GROUNDING,
                RetryPolicy.clamped(requestedLimit));

        assertThat(result.status()).isEqualTo(ReasoningStatus.ACCEPTED);
        assertThat(result.value()).contains(payload);
        assertThat(result.attemptsPerformed()).isEqualTo(1);
        assertThat(result.failureReason()).isEmpty();
        // A valid first output is accepted without any retry: exactly one invocation occurs.
        assertThat(runtime.invocationCount()).isEqualTo(1);
    }

    /** Arbitrary payloads that pass {@link #STRING_SCHEMA} (any non-null String). */
    @Provide
    Arbitrary<String> validPayloads() {
        return Arbitraries.strings().ofMaxLength(64);
    }

    /**
     * Any requested retry limit, spanning below, within, and above the permitted {@code [1, 5]}
     * range so {@link RetryPolicy#clamped(int)} coerces each into a valid bound — the schema-valid
     * first output must be accepted irrespective of the limit.
     */
    @Provide
    Arbitrary<Integer> retryLimits() {
        return Arbitraries.integers().between(-3, 10);
    }
}
