package com.terravow.api;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import com.terravow.gate.DecisionGate;
import com.terravow.gate.GateDecision;
import com.terravow.gate.GateThresholds;
import com.terravow.gate.GateVerdict;
import com.terravow.gate.HarmDelta;
import com.terravow.gate.Recommendation;
import com.terravow.gate.TrustContext;

/**
 * Exposes the TerraVow {@link DecisionGate} over HTTP so the Python front door can delegate a
 * recommendation's safety classification to the verified Java engine (the first Python&rarr;Java
 * round trip in the single Cloud Run container).
 *
 * <p>The endpoint maps the front door's per-medium delta map onto the gate's {@link HarmDelta} and
 * {@link TrustContext}, then runs the real {@link DecisionGate#classify} ordered algorithm (hard
 * veto first, then confidence classification, then trust-score cap). The trust cap is now part of
 * the core {@code DecisionGate} contract (task 4.5 completed).
 */
@RestController
@RequestMapping("/api/gate")
public class DecisionGateController {

    /** Confidence at or above which a recommendation is allowed (matches the Python gate). */
    static final double ALLOW_THRESHOLD = 0.75;

    /** Confidence at or above which a recommendation needs review rather than a block. */
    static final double ASK_THRESHOLD = 0.40;

    /** Governing data-trust below which an ALLOW is capped to ASK. */
    static final double TRUST_THRESHOLD = 0.60;

    private static final double EPS = 1e-9;

    private final DecisionGate decisionGate;

    /**
     * @param decisionGate the verified Decision_Gate engine; injected by Spring
     */
    public DecisionGateController(DecisionGate decisionGate) {
        this.decisionGate = decisionGate;
    }

    /**
     * Classifies a recommendation through the verified gate and returns the decision.
     *
     * @param request the per-medium deltas, confidence, and data-trust to classify
     * @return the gate decision, veto/review/trust-cap flags, and a short reason
     */
    @PostMapping("/evaluate")
    public GateEvaluateResponse evaluate(@RequestBody GateEvaluateRequest request) {
        Map<String, Double> deltas = request.deltasKg() == null ? Map.of() : request.deltasKg();

        // Map the per-medium deltas onto the gate's HarmDelta: total emitted-mass change, and any
        // positive cross-media relocation (a medium whose load increases) trips the cross-media axis.
        double total = 0.0;
        double crossMediaIncrease = 0.0;
        for (double v : deltas.values()) {
            total += v;
            if (v > EPS) {
                crossMediaIncrease += v;
            }
        }
        HarmDelta harm = new HarmDelta(total, crossMediaIncrease);

        Recommendation rec = buildRecommendation(request.confidence());
        GateThresholds thresholds = new GateThresholds(ALLOW_THRESHOLD, ASK_THRESHOLD);

        double trust = request.dataTrust() == null ? 1.0 : request.dataTrust();
        TrustContext trustContext = new TrustContext(trust, TRUST_THRESHOLD);

        GateVerdict verdict = decisionGate.classify(rec, thresholds, harm, trustContext);

        String reason = reasonFor(verdict, harm);

        return new GateEvaluateResponse(
                verdict.decision().name(),
                verdict.vetoApplied(),
                verdict.routedToHumanReview(),
                verdict.trustCapped(),
                reason,
                "terravow-java-engine");
    }

    private static Recommendation buildRecommendation(Double confidence) {
        if (confidence == null || confidence < 0.0 || confidence > 1.0) {
            // Absent/out-of-range confidence drives the gate's fail-closed BLOCK path (Req 8.5).
            return Recommendation.withoutConfidence("py-delegated", EvidenceChain.EMPTY);
        }
        return Recommendation.of("py-delegated", new Confidence(confidence), EvidenceChain.EMPTY);
    }

    private static String reasonFor(GateVerdict verdict, HarmDelta harm) {
        if (verdict.vetoApplied()) {
            if (harm.totalEmissionMassDelta() > EPS) {
                return "Hard veto: would increase total emissions.";
            }
            return "Hard veto: would relocate load across media instead of reducing it.";
        }
        if (verdict.trustCapped()) {
            return "Input data trust is low, so the result is capped at ASK.";
        }
        return switch (verdict.decision()) {
            case ALLOW -> "Reduces or holds emissions with high confidence.";
            case ASK -> "Plausible, but confidence is moderate: needs human confirmation.";
            case BLOCK -> "Blocked: confidence is missing, invalid, or too low to act on.";
        };
    }
}
