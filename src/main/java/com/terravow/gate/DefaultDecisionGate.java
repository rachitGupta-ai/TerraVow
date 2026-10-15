package com.terravow.gate;

import com.terravow.domain.Confidence;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic implementation of the {@link DecisionGate} applying the ordered
 * hard-veto-before-confidence algorithm (Req 8.1–8.6).
 *
 * <p>The algorithm runs in a fixed order so that safety is structural rather than advisory:
 *
 * <ol>
 *   <li><b>Hard veto first (Req 8.3, 8.4):</b> if the recommendation would increase total emission
 *       mass or cross-media load by any amount above zero, it is classified
 *       {@link GateDecision#BLOCK} with {@code vetoApplied = true} and the supporting readings
 *       attached, <em>before</em> confidence is examined. A vetoed recommendation is BLOCK
 *       regardless of its confidence.</li>
 *   <li><b>Confidence validity (Req 8.5):</b> if the confidence value is absent (or could not be
 *       constructed within {@code [0,1]}), the recommendation is classified BLOCK and routed to
 *       human review. This is the fail-closed default: on invalid input the gate never ALLOWs.</li>
 *   <li><b>Confidence classification (Req 8.1):</b> {@code confidence >= allowThreshold} → ALLOW;
 *       {@code askThreshold <= confidence < allowThreshold} → ASK;
 *       {@code confidence < askThreshold} → BLOCK.</li>
 *   <li><b>Human-review routing (Req 8.2):</b> any below-allow outcome (ASK or BLOCK) withholds
 *       automatic application and routes to human review.</li>
 * </ol>
 *
 * <p>The logic is purely a function of its inputs — it reads no wall-clock time, uses no randomness,
 * and depends on no iteration order — so the same recommendation, thresholds, and harm delta always
 * produce the same verdict. The verdict's Evidence_Chain is derived from the recommendation's own
 * chain; the gate adds no claim of authoritative sign-off (Req 8.6).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 8.2, 8.3, 8.4,
 *      8.5, 8.6</a>
 */
public final class DefaultDecisionGate implements DecisionGate {

    /**
     * {@inheritDoc}
     *
     * <p>Executes the ordered algorithm described on the class. The order is load-bearing: the hard
     * veto (step 1) precedes confidence classification (steps 2–3) so that a harm-increasing
     * recommendation can never be ALLOWed or ASKed through on the strength of a high confidence
     * (Req 8.4).
     */
    @Override
    public GateVerdict classify(Recommendation rec, GateThresholds thresholds, HarmDelta harm) {
        Objects.requireNonNull(rec, "rec must not be null");
        Objects.requireNonNull(thresholds, "thresholds must not be null");
        Objects.requireNonNull(harm, "harm must not be null");

        // Step 1 — Hard veto first (Req 8.3, 8.4). Precedes confidence classification entirely.
        // The supporting readings are carried on the recommendation's Evidence_Chain, which the
        // verdict preserves.
        if (harm.increasesHarm()) {
            return new GateVerdict(GateDecision.BLOCK, true, true, rec.evidence());
        }

        // Step 2 — Confidence validity (Req 8.5). Absent/un-constructable confidence fails closed:
        // BLOCK and route to human review. (An out-of-range numeric value is unrepresentable
        // because Confidence enforces [0,1] structurally, so absence captures the invalid case.)
        Optional<Confidence> maybeConfidence = rec.confidence();
        if (maybeConfidence.isEmpty()) {
            return new GateVerdict(GateDecision.BLOCK, false, true, rec.evidence());
        }

        // Step 3 — Confidence classification (Req 8.1).
        double confidence = maybeConfidence.get().value();
        GateDecision decision;
        if (confidence >= thresholds.allowThreshold()) {
            decision = GateDecision.ALLOW;
        } else if (confidence >= thresholds.askThreshold()) {
            decision = GateDecision.ASK;
        } else {
            decision = GateDecision.BLOCK;
        }

        // Step 4 — Human-review routing (Req 8.2): any below-allow decision is withheld and routed.
        boolean routedToHumanReview = decision != GateDecision.ALLOW;
        return new GateVerdict(decision, false, routedToHumanReview, rec.evidence());
    }
}
