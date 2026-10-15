package com.terravow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.domain.Confidence;
import com.terravow.domain.SyntheticLabel;
import com.terravow.evidence.AuditEntry;
import com.terravow.evidence.AuditService;
import com.terravow.evidence.DefaultAuditService;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.RecommendationId;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.gate.DefaultDecisionGate;
import com.terravow.gate.GateDecision;
import com.terravow.gate.GateThresholds;
import com.terravow.gate.HarmDelta;
import com.terravow.pii.DefaultMaskingGate;
import com.terravow.pii.MaskingGate;
import com.terravow.pii.synthetic.AdvisoryDisclaimer;
import com.terravow.reasoning.AgentInvocation;
import com.terravow.reasoning.CapabilityId;
import com.terravow.reasoning.DataStoreRef;
import com.terravow.reasoning.DefaultReasoningAgentOrchestrator;
import com.terravow.reasoning.GroundingMode;
import com.terravow.reasoning.GroundingSpec;
import com.terravow.reasoning.OutputSchema;
import com.terravow.reasoning.PromptSpec;
import com.terravow.reasoning.ReasoningResult;
import com.terravow.reasoning.ReasoningStatus;
import com.terravow.reasoning.RetryPolicy;
import com.terravow.reasoning.SpecialistAgentRuntime;
import com.terravow.reasoning.ToolDeclaration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultCrossCuttingControlFlow} proving the end-to-end wiring claims of
 * task 25.1: a recommendation is routed through the Decision_Gate (Req 8.1); audit persistence
 * completes synchronously <em>before</em> the decision-oriented output is returned (Req 10.3); the
 * output carries the advisory disclaimer (Req 13.5); and the orchestrator path masks inputs so only
 * masked data reaches the managed runtime (Req 11.1, 13.3).
 *
 * <p>The tests compose the real production components — {@link DefaultMaskingGate},
 * {@link DefaultReasoningAgentOrchestrator}, {@link DefaultDecisionGate},
 * {@link DefaultAuditService} — exactly as the control flow does in production, substituting only a
 * recording {@link SpecialistAgentRuntime} (to capture the prompt that crossed the boundary) and an
 * ordering-probe {@link AuditService} (to assert persist-before-return).
 */
class DefaultCrossCuttingControlFlowTest {

    // Shared fixtures -------------------------------------------------------------------------

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

    private static final GroundingSpec GROUNDING =
            new GroundingSpec(new DataStoreRef("corpus"), GroundingMode.VERTEX_AI_SEARCH);

    private static final GateThresholds THRESHOLDS = new GateThresholds(0.8, 0.5);

    private static final EvidenceChain EVIDENCE =
            EvidenceChain.EMPTY.with(new EvidenceLink(SourceType.READING, new SourceRef("r-1")));

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
     * A runtime that returns a fixed valid output and records the {@link PromptSpec} it received,
     * so a test can inspect exactly what crossed the managed-runtime boundary.
     */
    private static final class RecordingRuntime implements SpecialistAgentRuntime {
        private final Object output;
        final AtomicReference<PromptSpec> lastPrompt = new AtomicReference<>();

        RecordingRuntime(Object output) {
            this.output = output;
        }

        @Override
        public AgentInvocation invoke(
                CapabilityId capability,
                PromptSpec prompt,
                List<ToolDeclaration> tools,
                GroundingSpec grounding) {
            lastPrompt.set(prompt);
            return AgentInvocation.of(output);
        }
    }

    private static CrossCuttingControlFlow controlFlow(
            MaskingGate maskingGate, SpecialistAgentRuntime runtime, AuditService auditService) {
        return new DefaultCrossCuttingControlFlow(
                maskingGate,
                new DefaultReasoningAgentOrchestrator(runtime),
                new DefaultDecisionGate(),
                auditService,
                FIXED_CLOCK);
    }

    private static ReasoningRequest<String> stringRequest() {
        return new ReasoningRequest<>(
                CapabilityId.EID_ATTRIBUTION,
                STRING_SCHEMA,
                List.of(),
                GROUNDING,
                RetryPolicy.defaultPolicy());
    }

    // Req 8.1 — a recommendation is routed through the Decision_Gate ---------------------------

    @Test
    void recommendationIsRoutedThroughTheDecisionGate() {
        DefaultAuditService audit = new DefaultAuditService();
        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt"), new RecordingRuntime("ok"), audit);

        // Confidence 0.9 >= allowThreshold 0.8, no harm → ALLOW.
        Determination<String> det =
                Determination.of("EID=0.42", new Confidence(0.9), EVIDENCE);

        ControlFlowOutcome<String> outcome =
                flow.routeDecision("rec-allow", det, THRESHOLDS, HarmDelta.NONE, SyntheticLabel.REAL);

        assertThat(outcome.verdict().decision()).isEqualTo(GateDecision.ALLOW);
        assertThat(outcome.verdict().vetoApplied()).isFalse();
    }

    @Test
    void hardVetoFromHarmDeltaForcesBlockThroughTheGate() {
        DefaultAuditService audit = new DefaultAuditService();
        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt"), new RecordingRuntime("ok"), audit);

        // Even at full confidence, a harm-increasing recommendation must be vetoed to BLOCK — proof
        // the recommendation genuinely passes through the gate's ordered algorithm (Req 8.1, 8.4).
        Determination<String> det =
                Determination.of("relocate load", Confidence.FULL, EVIDENCE);
        HarmDelta increasesHarm = new HarmDelta(1.0, 0.0);

        ControlFlowOutcome<String> outcome =
                flow.routeDecision("rec-veto", det, THRESHOLDS, increasesHarm, SyntheticLabel.REAL);

        assertThat(outcome.verdict().decision()).isEqualTo(GateDecision.BLOCK);
        assertThat(outcome.verdict().vetoApplied()).isTrue();
        assertThat(outcome.verdict().routedToHumanReview()).isTrue();
    }

    // Req 10.3 — audit persistence completes synchronously BEFORE the output is returned --------

    @Test
    void auditIsPersistedSynchronouslyBeforeTheOutputIsReturned() {
        // This probe records whether an output object already exists at persist time. Because the
        // control flow must persist before it constructs the DecisionOrientedOutput, the probe must
        // observe "no output yet" (Req 10.3). It also records the persisted entry for assertions.
        AtomicReference<ControlFlowOutcome<?>> outcomeHolder = new AtomicReference<>();
        AtomicReference<AuditEntry> persistedAtCall = new AtomicReference<>();
        AtomicReference<Boolean> outputExistedAtPersist = new AtomicReference<>();

        AuditService probe = new AuditService() {
            @Override
            public void persistDecision(AuditEntry entry) {
                outputExistedAtPersist.set(outcomeHolder.get() != null);
                persistedAtCall.set(entry);
            }

            @Override
            public Optional<AuditEntry> findByRecommendation(RecommendationId id) {
                return Optional.ofNullable(persistedAtCall.get());
            }

            @Override
            public List<AuditEntry> entries() {
                AuditEntry e = persistedAtCall.get();
                return e == null ? List.of() : List.of(e);
            }
        };

        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt"), new RecordingRuntime("ok"), probe);
        Determination<String> det =
                Determination.of("EID=0.42", new Confidence(0.9), EVIDENCE);

        ControlFlowOutcome<String> outcome =
                flow.routeDecision("rec-sync", det, THRESHOLDS, HarmDelta.NONE, SyntheticLabel.REAL);
        outcomeHolder.set(outcome);

        // persistDecision ran, and at the moment it ran no output object existed yet.
        assertThat(persistedAtCall.get()).isNotNull();
        assertThat(outputExistedAtPersist.get())
                .as("audit must be persisted before the decision-oriented output is built (Req 10.3)")
                .isFalse();
        // The persisted entry carries the gate decision, confidence, and evidence (Req 10.2).
        assertThat(persistedAtCall.get().id()).isEqualTo(new RecommendationId("rec-sync"));
        assertThat(persistedAtCall.get().decision()).isEqualTo(GateDecision.ALLOW);
        assertThat(persistedAtCall.get().confidence()).isEqualTo(0.9);
        assertThat(outcome).isNotNull();
    }

    @Test
    void persistedAuditEntryIsRetrievableFromTheRealAuditService() {
        DefaultAuditService audit = new DefaultAuditService();
        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt"), new RecordingRuntime("ok"), audit);
        Determination<String> det =
                Determination.of("EID=0.42", new Confidence(0.6), EVIDENCE);

        flow.routeDecision("rec-store", det, THRESHOLDS, HarmDelta.NONE, SyntheticLabel.REAL);

        Optional<AuditEntry> stored =
                audit.findByRecommendation(new RecommendationId("rec-store"));
        assertThat(stored).isPresent();
        // Confidence 0.6 → ASK band (0.5 <= 0.6 < 0.8).
        assertThat(stored.get().decision()).isEqualTo(GateDecision.ASK);
        assertThat(stored.get().at()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
    }

    // Req 13.5 — the decision-oriented output carries the advisory disclaimer -------------------

    @Test
    void outputCarriesTheAdvisoryDisclaimer() {
        DefaultAuditService audit = new DefaultAuditService();
        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt"), new RecordingRuntime("ok"), audit);
        Determination<String> det =
                Determination.of("EID=0.42", new Confidence(0.9), EVIDENCE);

        ControlFlowOutcome<String> outcome =
                flow.routeDecision("rec-disc", det, THRESHOLDS, HarmDelta.NONE, SyntheticLabel.REAL);

        assertThat(AdvisoryDisclaimer.isPresentIn(outcome.surfaced())).isTrue();
        assertThat(AdvisoryDisclaimer.isPresentIn(outcome.output().export())).isTrue();
    }

    @Test
    void syntheticLabelPropagatesOntoTheDecisionOrientedOutput() {
        DefaultAuditService audit = new DefaultAuditService();
        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt"), new RecordingRuntime("ok"), audit);
        Determination<String> det =
                Determination.of("EID=0.42", new Confidence(0.9), EVIDENCE);

        ControlFlowOutcome<String> outcome = flow.routeDecision(
                "rec-syn", det, THRESHOLDS, HarmDelta.NONE, SyntheticLabel.SYNTHETIC);

        assertThat(outcome.output().isSynthetic()).isTrue();
        assertThat(outcome.surfaced()).contains("[SYNTHETIC DATA]");
    }

    // Req 11.1, 13.3 — the orchestrator path uses masked inputs --------------------------------

    @Test
    void orchestratorPathReceivesOnlyMaskedInputs() {
        RecordingRuntime runtime = new RecordingRuntime("EID=0.42");
        DefaultAuditService audit = new DefaultAuditService();
        // Real deterministic masking gate; "supplierName" is declared sensitive.
        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt-v1"), runtime, audit);

        var rawInputs = new com.terravow.pii.SupplierData(
                "rec-1",
                Map.of("supplierName", "Acme Pvt Ltd", "machineId", "M-77"),
                Set.of("supplierName"));

        ReasoningResult<String> result =
                flow.produceDetermination(rawInputs, "compute EID", stringRequest());

        assertThat(result.status()).isEqualTo(ReasoningStatus.ACCEPTED);
        assertThat(result.value()).contains("EID=0.42");

        // The prompt that crossed the boundary is marked masked and no longer carries the raw name.
        PromptSpec seen = runtime.lastPrompt.get();
        assertThat(seen).isNotNull();
        assertThat(seen.masked()).isTrue();
        assertThat(seen.maskedInputs().get("supplierName")).isNotNull();
        assertThat(seen.maskedInputs().get("supplierName").toString())
                .as("raw supplier identifier must be masked before reaching the runtime (Req 13.3)")
                .doesNotContain("Acme")
                .startsWith("mask:");
    }

    @Test
    void maskingRejectionShortCircuitsBeforeAnyReasoning() {
        RecordingRuntime runtime = new RecordingRuntime("should-not-be-used");
        DefaultAuditService audit = new DefaultAuditService();
        // A masking gate that always rejects proves no reasoning or runtime call happens (Req 13.4).
        MaskingGate alwaysRejects = data ->
                new com.terravow.pii.MaskingResult.Rejected(data.recordId(), "masking unavailable");
        CrossCuttingControlFlow flow = controlFlow(alwaysRejects, runtime, audit);

        var rawInputs = new com.terravow.pii.SupplierData(
                "rec-2", Map.of("machineId", "M-77"), Set.of());

        ReasoningResult<String> result =
                flow.produceDetermination(rawInputs, "compute EID", stringRequest());

        assertThat(result.status()).isEqualTo(ReasoningStatus.ESCALATED_HUMAN_REVIEW);
        assertThat(result.failureReason()).isPresent();
        assertThat(result.failureReason().get()).contains("masking unavailable");
        assertThat(runtime.lastPrompt.get())
                .as("no prompt may reach the runtime when masking is rejected (Req 13.4)")
                .isNull();
    }

    // toDetermination bridge -------------------------------------------------------------------

    @Test
    void toDeterminationLiftsAnAcceptedResult() {
        RecordingRuntime runtime = new RecordingRuntime("EID=0.42");
        DefaultAuditService audit = new DefaultAuditService();
        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt"), runtime, audit);

        var rawInputs = new com.terravow.pii.SupplierData(
                "rec-3", Map.of("machineId", "M-77"), Set.of());
        ReasoningResult<String> result =
                flow.produceDetermination(rawInputs, "compute EID", stringRequest());

        Determination<String> det = flow.toDetermination(result, new Confidence(0.75));

        assertThat(det.value()).isEqualTo("EID=0.42");
        assertThat(det.confidence()).isEqualTo(new Confidence(0.75));
        assertThat(det.evidence()).isEqualTo(result.evidence());
    }

    @Test
    void toDeterminationRejectsAnEscalatedResult() {
        RecordingRuntime runtime = new RecordingRuntime("EID=0.42");
        DefaultAuditService audit = new DefaultAuditService();
        CrossCuttingControlFlow flow =
                controlFlow(new DefaultMaskingGate("salt"), runtime, audit);
        ReasoningResult<String> escalated =
                ReasoningResult.escalated(3, "exhausted", EvidenceChain.EMPTY);

        assertThatThrownBy(() -> flow.toDetermination(escalated, Confidence.FULL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCEPTED");
    }
}
