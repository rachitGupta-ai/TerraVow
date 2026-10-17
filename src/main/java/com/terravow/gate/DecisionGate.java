package com.terravow.gate;

/**
 * The single chokepoint through which every recommendation passes before it can be surfaced or
 * applied (Req 8).
 *
 * <p>The gate applies an ordered, deterministic algorithm — hard veto first, confidence
 * classification second — so a recommendation that would increase harm is blocked regardless of how
 * confident it is (Req 8.4). The classification is one of {@link GateDecision#ALLOW},
 * {@link GateDecision#ASK}, or {@link GateDecision#BLOCK}, with any below-allow outcome withheld
 * from automatic application and routed to human review (Req 8.2). The gate never claims
 * authoritative legal, tax, or engineering sign-off (Req 8.6).
 *
 * @see DefaultDecisionGate
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 8.2, 8.3, 8.4,
 *      8.5, 8.6</a>
 */
public interface DecisionGate {

    /**
     * Classifies a recommendation against the given thresholds, harm delta, and trust context
     * (Req 8.1–8.6, 16.15–16.16).
     *
     * @param rec          the recommendation to classify; never {@code null}
     * @param thresholds   the confidence thresholds partitioning ALLOW/ASK/BLOCK; never {@code null}
     * @param harm         the change in harm the recommendation would cause, driving the hard veto;
     *                     never {@code null}
     * @param trustContext the governing trust score and threshold, driving the trust cap;
     *                     never {@code null}
     * @return the gate verdict carrying the decision, veto flag, human-review routing, trust-cap flag,
     *         and supporting Evidence_Chain
     */
    GateVerdict classify(Recommendation rec, GateThresholds thresholds, HarmDelta harm, TrustContext trustContext);
}
