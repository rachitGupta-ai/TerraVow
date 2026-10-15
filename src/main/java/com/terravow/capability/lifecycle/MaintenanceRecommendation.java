package com.terravow.capability.lifecycle;

import com.terravow.evidence.RecommendationId;
import com.terravow.gate.GateVerdict;

import java.util.List;
import java.util.Objects;

/**
 * A maintenance recommendation — an Emissions_Aware_Priority ranking paired with the forecast
 * deferral cost — routed through the Decision_Gate (Req 15.9).
 *
 * <p>Requirement 15.9 requires that when an Emissions_Aware_Priority ranking or a forecast pollution
 * cost of deferral is produced, the Decision_Gate classifies the resulting maintenance recommendation
 * as ALLOW/ASK/BLOCK and routes a below-allow recommendation to human review. This value carries the
 * recommendation identifier, the ranked queue (Req 15.5), the forecast deferral cost (Req 15.2), and
 * the {@link GateVerdict} the gate returned (Req 15.9). The verdict's own invariants guarantee the
 * routing: a below-ALLOW decision is routed to human review.
 *
 * @param id       the recommendation identifier recorded in the audit trail; never {@code null}
 * @param ranking  the ranked maintenance queue, highest priority first (Req 15.5); never
 *                 {@code null}, defensively copied
 * @param deferralCost the forecast deferral cost that drove the recommendation (Req 15.2); never
 *                 {@code null}
 * @param verdict  the Decision_Gate verdict (Req 15.9); never {@code null}
 *
 * @see MaintenanceLifecycle#recommend
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.2, 15.5,
 *      15.9</a>
 */
public record MaintenanceRecommendation(
        RecommendationId id,
        List<RankedAction> ranking,
        DeferralCostResult deferralCost,
        GateVerdict verdict) {

    /**
     * Validates the references and defensively copies the ranking (Req 15.9).
     *
     * @throws NullPointerException if any argument is {@code null} or a ranked action is
     *                              {@code null}
     */
    public MaintenanceRecommendation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ranking, "ranking must not be null");
        Objects.requireNonNull(deferralCost, "deferralCost must not be null");
        Objects.requireNonNull(verdict, "verdict must not be null");
        ranking = List.copyOf(ranking);
    }

    /**
     * Whether the recommendation was routed to human review (below the allow threshold) (Req 15.9).
     *
     * @return {@code true} iff the gate routed the recommendation to human review
     */
    public boolean routedToHumanReview() {
        return verdict.routedToHumanReview();
    }
}
