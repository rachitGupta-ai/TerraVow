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
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for the exhausted-retry escalation guarantee of
 * {@link DefaultReasoningAgentOrchestrator} (Req 11.5).
 *
 * <p>Validates Correctness Property 36: when the injected {@link SpecialistAgentRuntime} returns a
 * schema-invalid output on <em>every</em> attempt, the orchestrator exhausts the clamped retry
 * limit and escalates with a complete failure record. For any {@link CapabilityId} and any requested
 * retry limit (including out-of-range values, which {@link RetryPolicy#clamped(int)} coerces into
 * {@code [1, 5]}), the terminal result must be
 * {@link ReasoningStatus#ESCALATED_HUMAN_REVIEW} with: the accepted value absent, a
 * {@code failureReason} present, {@code attemptsPerformed} equal to the clamped limit, and the
 * failure record referencing the capability (Req 11.5).
 *
 * <p>The invariant is checked across the full capability space and across requested retry limits
 * that span below, within, and above the permitted range, confirming the escalation record is
 * complete regardless of how the retry budget was configured.
 *
 * <p><strong>Validates: Requirements 11.5</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.5</a>
 */
class ExhaustedRetryEscalationProperties {

    private static final PromptSpec PROMPT =
            PromptSpec.ofMasked("compute determination", Map.of("machineId", "M-***"));

    private static final GroundingSpec GROUNDING =
            new GroundingSpec(new DataStoreRef("corpus"), GroundingMode.VERTEX_AI_SEARCH);

    private static final List<ToolDeclaration> NO_TOOLS = List.of();

    /** A schema that accepts only non-null Strings — so a {@code null} output is always invalid. */
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
     * A runtime that always returns a schema-invalid ({@code null}) output and counts invocations,
     * so every attempt fails validation and the loop escalates on exhaustion (Req 11.5).
     */
    private static final class AlwaysInvalidRuntime implements SpecialistAgentRuntime {
        private int invocationCount;

        @Override
        public AgentInvocation invoke(
                CapabilityId capability,
                PromptSpec prompt,
                List<ToolDeclaration> tools,
                GroundingSpec grounding) {
            invocationCount++;
            return AgentInvocation.of(null);
        }

        int invocationCount() {
            return invocationCount;
        }
    }

    // Feature: terravow, Property 36: Exhausted retries escalate with a complete failure record
    @Property(tries = 100)
    void alwaysInvalidRuntimeEscalatesWithCompleteFailureRecord(
            @ForAll("capabilities") CapabilityId capability,
            @ForAll @IntRange(min = -3, max = 8) int requestedLimit) {

        RetryPolicy policy = RetryPolicy.clamped(requestedLimit);
        int clampedLimit = policy.retryLimit();

        AlwaysInvalidRuntime runtime = new AlwaysInvalidRuntime();
        ReasoningResult<String> result = new DefaultReasoningAgentOrchestrator(runtime).reason(
                capability, PROMPT, STRING_SCHEMA, NO_TOOLS, GROUNDING, policy);

        // Escalates to human review on exhaustion (Req 11.5).
        assertThat(result.status()).isEqualTo(ReasoningStatus.ESCALATED_HUMAN_REVIEW);
        // No accepted value is produced.
        assertThat(result.value()).isEmpty();
        // A complete failure record is present: attempts equal the clamped limit, the reason is
        // present, and the record references the capability (Req 11.5).
        assertThat(result.attemptsPerformed()).isEqualTo(clampedLimit);
        assertThat(runtime.invocationCount()).isEqualTo(clampedLimit);
        assertThat(result.failureReason()).isPresent();
        assertThat(result.failureReason().get())
                .contains("capability=" + capability)
                .contains("attemptsPerformed=" + clampedLimit)
                .contains(DefaultReasoningAgentOrchestrator.REASON_SCHEMA_INVALID);
    }

    /** The full capability space the orchestrator can be invoked for (Req 11.1, 11.5). */
    @Provide
    Arbitrary<CapabilityId> capabilities() {
        return Arbitraries.of(CapabilityId.class);
    }
}
