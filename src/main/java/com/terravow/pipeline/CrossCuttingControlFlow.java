package com.terravow.pipeline;

import com.terravow.domain.Confidence;
import com.terravow.domain.SyntheticLabel;
import com.terravow.gate.GateThresholds;
import com.terravow.gate.HarmDelta;
import com.terravow.pii.SupplierData;
import com.terravow.reasoning.ReasoningResult;

/**
 * The end-to-end cross-cutting control flow that ties the already-implemented structural pieces —
 * the {@code Reasoning_Agent} orchestrator, the Decision_Gate, the Evidence/Audit service, the
 * masking gate, and the advisory disclaimer — into the single pipeline every capability runs
 * through (design &rarr; Cross-Cutting Control Flow; Req 8.1, 10.3, 11.1, 13.5).
 *
 * <p>It composes two cross-cutting responsibilities so no individual capability has to reimplement
 * (or forget) them:
 *
 * <ol>
 *   <li><b>Reasoned determinations (Req 11.1, 13.3).</b> {@link #produceDetermination} masks the
 *       capability's raw inputs through the {@link com.terravow.pii.MaskingGate} and, only if
 *       masking succeeds, drives the {@link com.terravow.reasoning.ReasoningAgentOrchestrator} —
 *       which runs the managed Agent Engine with the masked prompt, verified tools, and grounding,
 *       folding grounding citations into the Evidence_Chain. If masking is rejected, no reasoning
 *       happens at all (Req 13.4).</li>
 *   <li><b>Decision-oriented outputs (Req 8.1, 10.3, 13.5).</b> {@link #routeDecision} routes a
 *       determination's recommendation through the Decision_Gate (Req 8.1), persists the resulting
 *       classification <em>synchronously</em> via the {@link com.terravow.evidence.AuditService}
 *       before the output is returned (Req 10.3), and returns a
 *       {@link com.terravow.pii.synthetic.DecisionOrientedOutput} that always carries the advisory
 *       disclaimer (Req 13.5) and propagates the synthetic label (Req 13.2).</li>
 * </ol>
 *
 * <p>The two methods compose: a capability typically calls {@link #produceDetermination} to obtain a
 * reasoned {@link Determination}, then {@link #routeDecision} to gate it, audit it, and surface it
 * with the disclaimer. Neither method modifies the production components it orchestrates — it only
 * sequences them in the order the design mandates.
 *
 * @see DefaultCrossCuttingControlFlow
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 10.3, 11.1,
 *      13.3, 13.5</a>
 */
public interface CrossCuttingControlFlow {

    /**
     * Produces a reasoned determination for a capability, masking the raw inputs before any of them
     * reaches the managed Agent Engine runtime (Req 11.1, 13.3, 13.4).
     *
     * <p>The raw {@link SupplierData} is first passed through the deterministic masking gate. On a
     * rejection the method short-circuits to an
     * {@link com.terravow.reasoning.ReasoningStatus#ESCALATED_HUMAN_REVIEW} result and performs no
     * reasoning, persistence, or managed-service call (Req 13.4). On success the masked payload is
     * turned into a masked {@link com.terravow.reasoning.PromptSpec} and handed to the orchestrator,
     * which drives the Agent Engine with the masked inputs, the request's verified tools, and its
     * grounding spec; grounding citations are folded into the Evidence_Chain of the returned result
     * (Req 11.1).
     *
     * @param <T>         the typed determination the request's schema produces
     * @param rawInputs   the capability's raw inputs, masked before use; never {@code null}
     * @param instruction the natural-language instruction for the specialist agent; never
     *                    {@code null}, non-blank
     * @param request     the capability, schema, tools, grounding, and retry policy; never
     *                    {@code null}
     * @return the orchestrator's result: an accepted typed value with its Evidence_Chain, or an
     *         escalation (including the masking-rejected short-circuit)
     */
    <T> ReasoningResult<T> produceDetermination(
            SupplierData rawInputs, String instruction, ReasoningRequest<T> request);

    /**
     * Routes a decision-oriented determination through the Decision_Gate and synchronous audit
     * persistence, returning an output that carries the advisory disclaimer (Req 8.1, 10.3, 13.5).
     *
     * <p>The method, in strict order: (1) builds a {@link com.terravow.gate.Recommendation} from the
     * determination's confidence and Evidence_Chain and classifies it through the Decision_Gate
     * against {@code thresholds} and {@code harm} (Req 8.1); (2) persists the classification —
     * recommendation id, decision, confidence, Evidence_Chain — synchronously, so the audit record
     * is durable before this method returns anything (Req 10.3); and only then (3) builds and
     * returns the {@link com.terravow.pii.synthetic.DecisionOrientedOutput}, which surfaces with the
     * advisory disclaimer (Req 13.5) and carries the propagated {@code label} (Req 13.2).
     *
     * @param <T>            the capability value type
     * @param recommendationId a non-blank identifier recorded in the audit trail (Req 10.2)
     * @param determination  the determination being surfaced as a decision; never {@code null}
     * @param thresholds     the gate confidence thresholds; never {@code null}
     * @param harm           the harm delta driving the gate hard veto; never {@code null}
     * @param label          the synthetic label to propagate onto the output; never {@code null}
     * @return the control-flow outcome bundling the gate verdict, the disclaimer-bearing output,
     *         and the routed determination
     */
    <T> ControlFlowOutcome<T> routeDecision(
            String recommendationId,
            Determination<T> determination,
            GateThresholds thresholds,
            HarmDelta harm,
            SyntheticLabel label);

    /**
     * Lifts an {@link com.terravow.reasoning.ReasoningStatus#ACCEPTED} reasoning result and a
     * confidence into a {@link Determination}, reusing the result's folded Evidence_Chain.
     *
     * <p>Convenience bridge between {@link #produceDetermination} and {@link #routeDecision}: a
     * capability that accepted a reasoned value attaches the confidence it derived and gets back the
     * uniform {@link Determination} triple to route.
     *
     * @param <T>        the determination value type
     * @param result     an {@code ACCEPTED} reasoning result; never {@code null}
     * @param confidence the confidence to attach; never {@code null}
     * @return a determination carrying the accepted value, the confidence, and the result's
     *         Evidence_Chain
     * @throws IllegalArgumentException if {@code result} is not {@code ACCEPTED}
     */
    <T> Determination<T> toDetermination(ReasoningResult<T> result, Confidence confidence);
}
