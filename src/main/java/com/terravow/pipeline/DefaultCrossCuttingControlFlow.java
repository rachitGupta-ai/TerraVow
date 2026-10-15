package com.terravow.pipeline;

import com.terravow.domain.Confidence;
import com.terravow.domain.SyntheticLabel;
import com.terravow.evidence.AuditEntry;
import com.terravow.evidence.AuditService;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.RecommendationId;
import com.terravow.gate.DecisionGate;
import com.terravow.gate.GateThresholds;
import com.terravow.gate.GateVerdict;
import com.terravow.gate.HarmDelta;
import com.terravow.gate.Recommendation;
import com.terravow.pii.MaskingGate;
import com.terravow.pii.MaskingResult;
import com.terravow.pii.SupplierData;
import com.terravow.pii.synthetic.DecisionOrientedOutput;
import com.terravow.reasoning.PromptSpec;
import com.terravow.reasoning.ReasoningAgentOrchestrator;
import com.terravow.reasoning.ReasoningResult;
import com.terravow.reasoning.ReasoningStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Default {@link CrossCuttingControlFlow} that sequences the production safety components in the
 * order the design mandates, composing — never modifying — the masking gate, the orchestrator, the
 * Decision_Gate, and the Evidence/Audit service (design &rarr; Cross-Cutting Control Flow; Req 8.1,
 * 10.3, 11.1, 13.3, 13.5).
 *
 * <p><b>Reasoned determinations (Req 11.1, 13.3, 13.4).</b> {@link #produceDetermination} runs the
 * deterministic {@link MaskingGate} first. A {@link MaskingResult.Rejected} short-circuits to an
 * escalation with the recorded reason and performs no reasoning or managed-service call (Req 13.4).
 * A {@link MaskingResult.Masked} is turned into a masked {@link PromptSpec} via
 * {@link MaskingResult.Masked#toPromptSpec(String)} — whose canonical constructor rejects any
 * unmasked prompt — so only masked inputs ever reach the {@link ReasoningAgentOrchestrator}, which
 * drives the Agent Engine with the request's verified tools and grounding (Req 11.1).
 *
 * <p><b>Decision-oriented outputs (Req 8.1, 10.3, 13.5).</b> {@link #routeDecision} classifies the
 * recommendation through the {@link DecisionGate} (Req 8.1), then calls
 * {@link AuditService#persistDecision(AuditEntry)} and lets it return — the write is synchronous —
 * <em>before</em> it constructs the {@link DecisionOrientedOutput} (Req 10.3). The output is a
 * {@link DecisionOrientedOutput}, which structurally carries the advisory disclaimer on every
 * surface and export (Req 13.5) and propagates the synthetic label (Req 13.2). The ordering is
 * load-bearing: no decision-oriented output object is created until its audit record is durable.
 *
 * <p>All collaborators are injected via the constructor and are only orchestrated here; this class
 * holds no mutable state and reads the clock only to stamp the audit entry's {@code at} instant.
 *
 * @see CrossCuttingControlFlow
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 10.3, 11.1,
 *      13.3, 13.5</a>
 */
@Service
public class DefaultCrossCuttingControlFlow implements CrossCuttingControlFlow {

    /** Escalation reason recorded when masking rejects the inputs before any reasoning (Req 13.4). */
    static final String REASON_MASKING_REJECTED =
            "inputs rejected by masking gate before reasoning: ";

    private final MaskingGate maskingGate;
    private final ReasoningAgentOrchestrator orchestrator;
    private final DecisionGate decisionGate;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Creates the control flow over its collaborators (Req 8.1, 10.3, 11.1, 13.3).
     *
     * @param maskingGate  the deterministic masking gate run before reasoning; never {@code null}
     * @param orchestrator the reasoning orchestrator driving the Agent Engine; never {@code null}
     * @param decisionGate the Decision_Gate recommendations are routed through; never {@code null}
     * @param auditService the audit service providing synchronous persistence; never {@code null}
     * @param clock        the clock used to stamp audit entries; never {@code null}
     * @throws NullPointerException if any collaborator is {@code null}
     */
    public DefaultCrossCuttingControlFlow(
            MaskingGate maskingGate,
            ReasoningAgentOrchestrator orchestrator,
            DecisionGate decisionGate,
            AuditService auditService,
            Clock clock) {
        this.maskingGate = Objects.requireNonNull(maskingGate, "maskingGate must not be null");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
        this.decisionGate = Objects.requireNonNull(decisionGate, "decisionGate must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Masks the raw inputs first; a rejection escalates with the recorded reason and no reasoning
     * is performed (Req 13.4). Otherwise the masked payload is handed to the orchestrator as a
     * masked prompt, so no raw identifier reaches the managed runtime (Req 11.1, 13.3).
     */
    @Override
    public <T> ReasoningResult<T> produceDetermination(
            SupplierData rawInputs, String instruction, ReasoningRequest<T> request) {
        Objects.requireNonNull(rawInputs, "rawInputs must not be null");
        Objects.requireNonNull(instruction, "instruction must not be null");
        Objects.requireNonNull(request, "request must not be null");

        MaskingResult masking = maskingGate.mask(rawInputs);
        if (masking instanceof MaskingResult.Rejected rejected) {
            // Req 13.4: masking could not complete — no reasoning, no managed-service call.
            return ReasoningResult.escalated(
                    1, REASON_MASKING_REJECTED + rejected.reason(), EvidenceChain.EMPTY);
        }

        // Req 13.3: only the masked payload crosses into the orchestrator. PromptSpec's constructor
        // rejects any prompt not marked masked, so this is the single masked entry point.
        MaskingResult.Masked masked = (MaskingResult.Masked) masking;
        PromptSpec prompt = masked.toPromptSpec(instruction);

        return orchestrator.reason(
                request.capability(),
                prompt,
                request.schema(),
                request.tools(),
                request.grounding(),
                request.policy());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Classifies the recommendation (Req 8.1), persists the classification synchronously
     * (Req 10.3), and only then builds the disclaimer-bearing output (Req 13.5). The audit write
     * returns before the output object is constructed, so no decision-oriented output can precede
     * its audit record.
     */
    @Override
    public <T> ControlFlowOutcome<T> routeDecision(
            String recommendationId,
            Determination<T> determination,
            GateThresholds thresholds,
            HarmDelta harm,
            SyntheticLabel label) {
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");
        Objects.requireNonNull(determination, "determination must not be null");
        Objects.requireNonNull(thresholds, "thresholds must not be null");
        Objects.requireNonNull(harm, "harm must not be null");
        Objects.requireNonNull(label, "label must not be null");

        // Step 1 — route the recommendation through the Decision_Gate (Req 8.1).
        Recommendation recommendation = Recommendation.of(
                recommendationId, determination.confidence(), determination.evidence());
        GateVerdict verdict = decisionGate.classify(recommendation, thresholds, harm);

        // Step 2 — persist the classification synchronously BEFORE any output is returned
        // (Req 10.3). persistDecision returns only once the entry is durably recorded.
        AuditEntry entry = new AuditEntry(
                new RecommendationId(recommendationId),
                verdict.decision(),
                determination.confidence().value(),
                verdict.evidence(),
                Instant.now(clock));
        auditService.persistDecision(entry);

        // Step 3 — only now construct the decision-oriented output, which always carries the
        // advisory disclaimer (Req 13.5) and propagates the synthetic label (Req 13.2).
        DecisionOrientedOutput output =
                new DecisionOrientedOutput(String.valueOf(determination.value()), label);

        return new ControlFlowOutcome<>(verdict, output, determination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Determination<T> toDetermination(ReasoningResult<T> result, Confidence confidence) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        if (result.status() != ReasoningStatus.ACCEPTED || result.value().isEmpty()) {
            throw new IllegalArgumentException(
                    "toDetermination requires an ACCEPTED reasoning result, got: "
                            + result.status());
        }
        return new Determination<>(result.value().get(), confidence, result.evidence());
    }
}
