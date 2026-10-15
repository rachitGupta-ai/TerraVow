package com.terravow.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultReasoningAgentOrchestrator}, driving the schema-validate &rarr;
 * bounded-retry &rarr; escalate loop with a stub {@link SpecialistAgentRuntime} that returns
 * valid-first, invalid-then-valid, and always-invalid sequences (Req 11.3, 11.4, 11.5).
 */
class DefaultReasoningAgentOrchestratorTest {

    // Shared fixtures -------------------------------------------------------------------------

    private static final CapabilityId CAPABILITY = CapabilityId.EID_ATTRIBUTION;

    private static final PromptSpec PROMPT =
            PromptSpec.ofMasked("compute EID", Map.of("machineId", "M-***"));

    private static final GroundingSpec GROUNDING =
            new GroundingSpec(new DataStoreRef("corpus"), GroundingMode.VERTEX_AI_SEARCH);

    private static final List<ToolDeclaration> NO_TOOLS = List.of();

    /** A schema that accepts non-null Strings and coerces them to themselves. */
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
     * A deterministic stub runtime that replays a fixed queue of raw outputs (one per attempt) and
     * records how many times it was invoked. Each queued element becomes one
     * {@link AgentInvocation}; {@code citations} are attached to every invocation.
     */
    private static final class StubRuntime implements SpecialistAgentRuntime {
        // ArrayList (not ArrayDeque) because queued outputs may legitimately be null.
        private final List<Object> outputs;
        private final List<GroundingCitation> citations;
        private int invocationCount;

        StubRuntime(List<Object> outputs, List<GroundingCitation> citations) {
            this.outputs = new ArrayList<>(outputs);
            this.citations = List.copyOf(citations);
        }

        static StubRuntime of(Object... outputs) {
            List<Object> list = new ArrayList<>();
            for (Object o : outputs) {
                list.add(o);
            }
            return new StubRuntime(list, List.of());
        }

        @Override
        public AgentInvocation invoke(
                CapabilityId capability,
                PromptSpec prompt,
                List<ToolDeclaration> tools,
                GroundingSpec grounding) {
            Object next = invocationCount < outputs.size() ? outputs.get(invocationCount) : null;
            invocationCount++;
            return new AgentInvocation(next, citations);
        }

        int invocationCount() {
            return invocationCount;
        }
    }

    private static ReasoningAgentOrchestrator orchestratorOver(StubRuntime runtime) {
        return new DefaultReasoningAgentOrchestrator(runtime);
    }

    // Req 11.3 — schema-valid first output is accepted after a single attempt -----------------

    @Test
    void validFirstOutputIsAcceptedOnFirstAttempt() {
        StubRuntime runtime = StubRuntime.of("valid-determination");
        ReasoningResult<String> result = orchestratorOver(runtime).reason(
                CAPABILITY, PROMPT, STRING_SCHEMA, NO_TOOLS, GROUNDING, RetryPolicy.defaultPolicy());

        assertThat(result.status()).isEqualTo(ReasoningStatus.ACCEPTED);
        assertThat(result.value()).contains("valid-determination");
        assertThat(result.attemptsPerformed()).isEqualTo(1);
        assertThat(result.failureReason()).isEmpty();
        assertThat(runtime.invocationCount()).isEqualTo(1);
    }

    // Req 11.4 — invalid-then-valid retries, then accepts on the first valid attempt ----------

    @Test
    void invalidThenValidRetriesAndAcceptsWithAttemptCount() {
        // Two invalid outputs (null, non-String) then a valid one; limit 3.
        StubRuntime runtime = StubRuntime.of(null, 42, "valid-on-third");
        ReasoningResult<String> result = orchestratorOver(runtime).reason(
                CAPABILITY, PROMPT, STRING_SCHEMA, NO_TOOLS, GROUNDING, RetryPolicy.defaultPolicy());

        assertThat(result.status()).isEqualTo(ReasoningStatus.ACCEPTED);
        assertThat(result.value()).contains("valid-on-third");
        assertThat(result.attemptsPerformed()).isEqualTo(3);
        assertThat(runtime.invocationCount()).isEqualTo(3);
    }

    // Req 11.5 — always-invalid exhausts the clamped limit and escalates with a failure record --

    @Test
    void alwaysInvalidExhaustsRetriesAndEscalates() {
        StubRuntime runtime = StubRuntime.of(null, null, null);
        ReasoningResult<String> result = orchestratorOver(runtime).reason(
                CAPABILITY, PROMPT, STRING_SCHEMA, NO_TOOLS, GROUNDING, RetryPolicy.defaultPolicy());

        assertThat(result.status()).isEqualTo(ReasoningStatus.ESCALATED_HUMAN_REVIEW);
        assertThat(result.value()).isEmpty();
        assertThat(result.attemptsPerformed()).isEqualTo(3);
        assertThat(runtime.invocationCount()).isEqualTo(3);
        assertThat(result.failureReason()).isPresent();
        // The failure record carries capability, attempts performed, and the failure reason.
        assertThat(result.failureReason().get())
                .contains("capability=" + CAPABILITY)
                .contains("attemptsPerformed=3")
                .contains(DefaultReasoningAgentOrchestrator.REASON_SCHEMA_INVALID);
    }

    // Req 11.4 — the retry limit bounds the number of attempts exactly -------------------------

    @Test
    void retryLimitBoundsNumberOfAttempts() {
        // Five invalid outputs with a clamped limit of 2 → exactly 2 attempts.
        StubRuntime runtime = StubRuntime.of(null, null, null, null, null);
        ReasoningResult<String> result = orchestratorOver(runtime).reason(
                CAPABILITY, PROMPT, STRING_SCHEMA, NO_TOOLS, GROUNDING, RetryPolicy.clamped(2));

        assertThat(result.status()).isEqualTo(ReasoningStatus.ESCALATED_HUMAN_REVIEW);
        assertThat(result.attemptsPerformed()).isEqualTo(2);
        assertThat(runtime.invocationCount()).isEqualTo(2);
    }

    // Req 11.4 — a limit of 1 performs a single attempt and does not retry ---------------------

    @Test
    void retryLimitOfOnePerformsSingleAttempt() {
        StubRuntime runtime = StubRuntime.of(null, "would-be-valid-second");
        ReasoningResult<String> result = orchestratorOver(runtime).reason(
                CAPABILITY, PROMPT, STRING_SCHEMA, NO_TOOLS, GROUNDING, RetryPolicy.clamped(1));

        assertThat(result.status()).isEqualTo(ReasoningStatus.ESCALATED_HUMAN_REVIEW);
        assertThat(result.attemptsPerformed()).isEqualTo(1);
        assertThat(runtime.invocationCount()).isEqualTo(1);
    }

    // Req 11.2 — schema-valid but non-coercible output is treated as a failure and retried -----

    @Test
    void schemaValidButNonCoercibleOutputDrivesRetryAndEscalation() {
        // A schema that validates any non-null String but can never materialise a typed value.
        OutputSchema<String> nonCoercible = new OutputSchema<>() {
            @Override
            public boolean validate(Object rawOutput) {
                return rawOutput instanceof String;
            }

            @Override
            public Optional<String> coerce(Object rawOutput) {
                return Optional.empty();
            }

            @Override
            public String name() {
                return "non-coercible";
            }
        };
        StubRuntime runtime = StubRuntime.of("a", "b", "c");
        ReasoningResult<String> result = orchestratorOver(runtime).reason(
                CAPABILITY, PROMPT, nonCoercible, NO_TOOLS, GROUNDING, RetryPolicy.defaultPolicy());

        assertThat(result.status()).isEqualTo(ReasoningStatus.ESCALATED_HUMAN_REVIEW);
        assertThat(result.attemptsPerformed()).isEqualTo(3);
        assertThat(result.failureReason().get())
                .contains(DefaultReasoningAgentOrchestrator.REASON_NOT_COERCIBLE);
    }

    // Req 10.1 / 11.3 — grounding citations are folded into the Evidence_Chain on acceptance ----

    @Test
    void acceptedResultFoldsCitationsIntoEvidenceChain() {
        GroundingCitation citation =
                new GroundingCitation(new SourceRef("reg-text-7"), "base rate chapter 3");
        StubRuntime runtime = new StubRuntime(List.of("valid"), List.of(citation));

        ReasoningResult<String> result = orchestratorOver(runtime).reason(
                CAPABILITY, PROMPT, STRING_SCHEMA, NO_TOOLS, GROUNDING, RetryPolicy.defaultPolicy());

        assertThat(result.status()).isEqualTo(ReasoningStatus.ACCEPTED);
        assertThat(result.citations()).containsExactly(citation);
        assertThat(result.evidence().links())
                .containsExactly(new EvidenceLink(SourceType.REGULATORY_TEXT, citation.source()));
    }
}
