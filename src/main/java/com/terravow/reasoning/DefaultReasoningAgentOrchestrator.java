package com.terravow.reasoning;

import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The default {@link ReasoningAgentOrchestrator}: the schema-validate &rarr; bounded-retry &rarr;
 * escalate loop that wraps every managed Agent Engine invocation with Req 11's determinism and
 * escalation guarantee (Req 11.2, 11.3, 11.4, 11.5).
 *
 * <p>Each call to {@link #reason} drives the following deterministic loop, delegating a single
 * attempt to the injected {@link SpecialistAgentRuntime} port (implemented by the Agent Engine
 * adapter in task 6.3):
 * <ol>
 *   <li>Resolve the attempt budget from {@code policy}; the {@link RetryPolicy} already guarantees
 *       a limit in {@code [1, 5]} (default 3) via its clamping factories (Req 11.4).</li>
 *   <li>For each attempt {@code 1..retryLimit}: invoke the runtime, then validate the raw output
 *       against the capability's {@link OutputSchema} (Req 11.2).</li>
 *   <li>On a schema-valid, coercible output: accept it, fold the invocation's grounding citations
 *       into the determination's Evidence_Chain, and return an {@link ReasoningStatus#ACCEPTED}
 *       result carrying the typed value and the number of attempts performed (Req 11.3).</li>
 *   <li>On a schema-invalid or non-coercible output: record the failure reason and retry until the
 *       budget is exhausted.</li>
 *   <li>On exhaustion: return an {@link ReasoningStatus#ESCALATED_HUMAN_REVIEW} result recording
 *       {@code {capability, attemptsPerformed, failureReason}} (Req 11.5).</li>
 * </ol>
 *
 * <p>The loop is deterministic given a deterministic {@link OutputSchema} and runtime: it uses no
 * wall-clock time, randomness, or iteration-order-dependent structures. Only the masked inputs on
 * the {@link PromptSpec} ever cross the port into the managed runtime (Req 13.3); this orchestrator
 * neither inspects nor unmasks them.
 *
 * @see ReasoningAgentOrchestrator
 * @see SpecialistAgentRuntime
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.2, 11.3, 11.4,
 *      11.5, 13.3</a>
 */
public final class DefaultReasoningAgentOrchestrator implements ReasoningAgentOrchestrator {

    /** Failure reason recorded when every attempt returns a schema-invalid output (Req 11.5). */
    static final String REASON_SCHEMA_INVALID =
            "agent output failed schema validation on every attempt";

    /** Failure reason recorded when a schema-valid output cannot be coerced to the typed value. */
    static final String REASON_NOT_COERCIBLE =
            "schema-valid agent output could not be coerced to the typed determination";

    private final SpecialistAgentRuntime runtime;

    /**
     * Creates an orchestrator over the given specialist-agent runtime port (Req 11.1).
     *
     * @param runtime the port that performs a single Agent Engine invocation per attempt; never
     *                {@code null}
     * @throws NullPointerException if {@code runtime} is {@code null}
     */
    public DefaultReasoningAgentOrchestrator(SpecialistAgentRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs the bounded-retry loop described on the class, invoking {@link SpecialistAgentRuntime}
     * once per attempt. On the first schema-valid, coercible output the determination is accepted
     * (Req 11.3); otherwise retries continue up to the clamped limit and exhaustion escalates with a
     * complete failure record (Req 11.4, 11.5).
     */
    @Override
    public <T> ReasoningResult<T> reason(
            CapabilityId capability,
            PromptSpec prompt,
            OutputSchema<T> schema,
            List<ToolDeclaration> tools,
            GroundingSpec grounding,
            RetryPolicy policy) {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(tools, "tools must not be null");
        Objects.requireNonNull(grounding, "grounding must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        int retryLimit = policy.retryLimit();
        // The last reason seen governs the escalation record; starts as the generic schema reason.
        String lastFailureReason = REASON_SCHEMA_INVALID;

        for (int attempt = 1; attempt <= retryLimit; attempt++) {
            AgentInvocation invocation = runtime.invoke(capability, prompt, tools, grounding);
            Object rawOutput = invocation.rawOutput();

            if (!schema.validate(rawOutput)) {
                lastFailureReason = REASON_SCHEMA_INVALID;
                continue;
            }

            Optional<T> coerced = schema.coerce(rawOutput);
            if (coerced.isEmpty()) {
                // Schema-valid but the typed value could not be materialised: treat as a failure
                // (Req 11.2 — an otherwise-valid payload that cannot produce the determination).
                lastFailureReason = REASON_NOT_COERCIBLE;
                continue;
            }

            EvidenceChain evidence = foldCitations(invocation.citations());
            return ReasoningResult.accepted(
                    coerced.get(), attempt, invocation.citations(), evidence);
        }

        return ReasoningResult.escalated(
                retryLimit, escalationReason(capability, retryLimit, lastFailureReason),
                EvidenceChain.EMPTY);
    }

    /**
     * Folds the grounding citations returned by a specialist agent into an Evidence_Chain, adding
     * one {@link SourceType#REGULATORY_TEXT} link per citation so every grounded value resolves to a
     * primary source (Req 10.1).
     *
     * @param citations the citations returned by the accepted invocation; never {@code null}
     * @return an Evidence_Chain whose links reference each cited source
     */
    private static EvidenceChain foldCitations(List<GroundingCitation> citations) {
        EvidenceChain evidence = EvidenceChain.EMPTY;
        for (GroundingCitation citation : citations) {
            evidence = evidence.with(
                    new EvidenceLink(SourceType.REGULATORY_TEXT, citation.source()));
        }
        return evidence;
    }

    /**
     * Builds the human-review failure record string recording the capability, attempts performed,
     * and the schema-validation failure reason (Req 11.5).
     *
     * @param capability        the capability whose determination failed; never {@code null}
     * @param attemptsPerformed the number of attempts performed before escalation
     * @param failureReason     the last schema-validation failure reason observed
     * @return a non-blank failure record string
     */
    private static String escalationReason(
            CapabilityId capability, int attemptsPerformed, String failureReason) {
        return "capability=" + capability
                + ", attemptsPerformed=" + attemptsPerformed
                + ", failureReason=" + failureReason;
    }
}
