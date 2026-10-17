package com.terravow.gate;

/**
 * Trust context for a recommendation, governing the Decision_Gate's trust-score cap (Req 16.15, 16.16).
 *
 * <p>The {@code governingTrust} is the minimum Data_Trust_Score over all contributing inputs to
 * the recommendation; it flows downstream as a confidence floor. When {@code governingTrust} is below
 * the {@code trustThreshold}, the gate caps the classification at ASK — lowering an ALLOW to ASK and
 * routing it to human review, but never raising a classification and never lifting the hard veto
 * (Req 16.16).
 *
 * @param governingTrust the minimum trust score over contributing inputs, in [0,1]
 * @param trustThreshold the threshold below which an ALLOW is capped to ASK, in [0,1]
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 16.15, 16.16</a>
 */
public record TrustContext(double governingTrust, double trustThreshold) {

    /**
     * Compact constructor enforcing invariants (Req 16.14).
     *
     * @throws IllegalArgumentException if either value is outside [0,1]
     */
    public TrustContext {
        if (governingTrust < 0.0 || governingTrust > 1.0) {
            throw new IllegalArgumentException(
                    "governingTrust must be in [0,1], got " + governingTrust);
        }
        if (trustThreshold < 0.0 || trustThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "trustThreshold must be in [0,1], got " + trustThreshold);
        }
    }
}
