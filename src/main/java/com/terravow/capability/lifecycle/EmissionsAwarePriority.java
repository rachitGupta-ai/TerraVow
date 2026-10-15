package com.terravow.capability.lifecycle;

import java.util.Objects;

/**
 * The Emissions_Aware_Priority of a queued maintenance action: a deterministic ranking score that
 * combines failure risk, downtime cost, and the forecast pollution cost of continued deferral,
 * with the pollution-cost term AEI-weighted where a Receptor_Map and met forecast exist (Req 15.3,
 * 15.4).
 *
 * <p>The score is {@code failureRisk + downtimeCost + weightedPollutionCost}, where
 * {@code weightedPollutionCost = forecastPollutionCost × aeiWeighting.multiplier()} (Req 15.3,
 * 15.4). Each term is retained so a ranking can explain its basis and so monotonicity is checkable:
 * <ul>
 *   <li>holding failure risk and downtime cost fixed, increasing the AEI never decreases the score,
 *       because the AEI multiplier is non-decreasing in the AEI (Property 47, Req 15.4);</li>
 *   <li>an action with a higher weighted pollution cost (higher environmental harm) can outrank an
 *       action with a higher failure risk but lower harm (Property 48, Req 15.3, 15.5).</li>
 * </ul>
 *
 * <p>All inputs are finite and non-negative — a probability-like failure risk, a non-negative
 * monetary cost expressed as a numeric magnitude, and a non-negative forecast pollution cost. The
 * score is a pure function of the inputs, so the ranking is deterministic.
 *
 * @param failureRisk            the failure-risk term (e.g. failure probability in {@code [0,1]});
 *                               finite, {@code >= 0}
 * @param downtimeCost           the downtime-cost term as a numeric magnitude; finite, {@code >= 0}
 * @param forecastPollutionCost  the forecast pollution cost of continued deferral as a numeric
 *                               magnitude, before AEI weighting; finite, {@code >= 0} (Req 15.2,
 *                               15.3)
 * @param aeiWeighting           the AEI weighting applied to the pollution-cost term (Req 15.4)
 *
 * @see QueuedAction
 * @see RankedAction
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.3, 15.4,
 *      15.5</a>
 */
public record EmissionsAwarePriority(
        double failureRisk,
        double downtimeCost,
        double forecastPollutionCost,
        AeiWeighting aeiWeighting) {

    /**
     * Validates the finite, non-negative terms and the AEI weighting reference (Req 15.3).
     *
     * @throws NullPointerException     if {@code aeiWeighting} is {@code null}
     * @throws IllegalArgumentException if any term is non-finite or negative
     */
    public EmissionsAwarePriority {
        Objects.requireNonNull(aeiWeighting, "aeiWeighting must not be null");
        requireFiniteNonNegative(failureRisk, "failureRisk");
        requireFiniteNonNegative(downtimeCost, "downtimeCost");
        requireFiniteNonNegative(forecastPollutionCost, "forecastPollutionCost");
    }

    /**
     * The AEI-weighted pollution-cost term, {@code forecastPollutionCost × multiplier} (Req 15.4).
     *
     * @return the weighted pollution cost
     */
    public double weightedPollutionCost() {
        return forecastPollutionCost * aeiWeighting.multiplier();
    }

    /**
     * The combined Emissions_Aware_Priority score (Req 15.3, 15.4).
     *
     * <p>{@code failureRisk + downtimeCost + weightedPollutionCost}. A higher score ranks earlier in
     * the maintenance queue.
     *
     * @return the priority score; finite and {@code >= 0}
     */
    public double score() {
        return failureRisk + downtimeCost + weightedPollutionCost();
    }

    private static void requireFiniteNonNegative(double v, String name) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be a finite number, got: " + v);
        }
        if (v < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative, got: " + v);
        }
    }
}
