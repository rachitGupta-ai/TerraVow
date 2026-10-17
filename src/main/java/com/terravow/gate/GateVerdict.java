package com.terravow.gate;

import com.terravow.evidence.EvidenceChain;

import java.util.Objects;

/**
 * The outcome of a {@link DecisionGate} classification (Req 8, 16.15–16.16).
 *
 * <p>Carries the {@link GateDecision}, whether the hard veto against increasing harm was applied
 * (Req 8.3, 8.4), whether the recommendation was routed to human review (Req 8.2, 8.5), whether
 * the trust-score cap was applied (Req 16.15), and the {@link EvidenceChain} supporting the verdict —
 * including the readings attached when a hard veto fired (Req 8.3). A verdict never carries any claim
 * of authoritative legal, tax, or engineering sign-off (Req 8.6); it is a classification and routing
 * outcome only.
 *
 * <p>Consistency invariants enforced in the compact constructor:
 * <ul>
 *   <li>{@code vetoApplied} implies {@code decision == BLOCK} — a veto always forces BLOCK
 *       (Req 8.4).</li>
 *   <li>{@code routedToHumanReview} implies the decision is below ALLOW (ASK or BLOCK) — ALLOW is
 *       never routed to review (Req 8.2).</li>
 *   <li>{@code trustCapped} implies {@code decision == ASK} — the trust cap can only lower an ALLOW
 *       to ASK, never raise a classification and never lift the hard veto (Req 16.16).</li>
 * </ul>
 *
 * @param decision            the ALLOW/ASK/BLOCK classification; never {@code null}
 * @param vetoApplied         {@code true} iff the hard veto fired (then {@code decision} is BLOCK)
 * @param routedToHumanReview {@code true} iff automatic application was withheld pending review
 * @param trustCapped         {@code true} iff the trust-score cap lowered an ALLOW to ASK
 * @param evidence            the supporting Evidence_Chain; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.2, 8.3, 8.4, 8.6,
 *      16.15, 16.16</a>
 */
public record GateVerdict(
        GateDecision decision,
        boolean vetoApplied,
        boolean routedToHumanReview,
        boolean trustCapped,
        EvidenceChain evidence) {

    /**
     * Validates the decision and evidence references and the verdict consistency invariants
     * (Req 8.2, 8.4, 16.16).
     *
     * @throws NullPointerException     if {@code decision} or {@code evidence} is {@code null}
     * @throws IllegalArgumentException if {@code vetoApplied} is set without a BLOCK decision,
     *                                  {@code routedToHumanReview} is set with an ALLOW decision,
     *                                  or {@code trustCapped} is set without an ASK decision
     */
    public GateVerdict {
        Objects.requireNonNull(decision, "GateVerdict decision must not be null");
        Objects.requireNonNull(evidence, "GateVerdict evidence must not be null");
        if (vetoApplied && decision != GateDecision.BLOCK) {
            throw new IllegalArgumentException(
                    "A hard veto must force BLOCK, but decision was " + decision);
        }
        if (routedToHumanReview && decision == GateDecision.ALLOW) {
            throw new IllegalArgumentException(
                    "An ALLOW decision must not be routed to human review");
        }
        if (trustCapped && decision != GateDecision.ASK) {
            throw new IllegalArgumentException(
                    "The trust cap can only produce ASK, but decision was " + decision);
        }
    }
}
