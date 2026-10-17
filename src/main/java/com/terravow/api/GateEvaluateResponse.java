package com.terravow.api;

/**
 * Response body for the internal Decision_Gate evaluate endpoint.
 *
 * <p>Reports the gate decision computed by the Java engine ({@code ALLOW|ASK|BLOCK}), whether the
 * hard veto fired, whether the recommendation was routed to human review, the trust-cap outcome,
 * and a short human-readable reason — the fields the Python front door surfaces to the UI.
 *
 * @param verdict             ALLOW, ASK, or BLOCK
 * @param vetoApplied         whether the hard veto against increasing harm fired
 * @param routedToHumanReview whether automatic application was withheld pending review
 * @param trustCapped         whether a low data-trust score lowered an ALLOW to ASK
 * @param reason              a short explanation of the decision
 * @param engine              identifies the computing engine ("terravow-java-engine")
 */
public record GateEvaluateResponse(
        String verdict,
        boolean vetoApplied,
        boolean routedToHumanReview,
        boolean trustCapped,
        String reason,
        String engine) {
}
