package com.terravow.capability.lifecycle;

import com.terravow.domain.ActionId;
import com.terravow.domain.AssetClass;
import com.terravow.domain.MachineId;

import java.util.Objects;

/**
 * A queued maintenance action across Machine_Assets, awaiting an Emissions_Aware_Priority (Req 15.3).
 *
 * <p>Each queued action names the asset and the scheduled action, the asset class, and the three
 * inputs the priority combines: the {@link #failureRisk()}, the {@link #downtimeCost()}, and the
 * {@link #forecastPollutionCost()} of continued deferral (the EID trajectory priced through ERD per
 * Req 15.2). It also carries the {@link #aeiWeighting()} that is applied to the pollution-cost term
 * where a Receptor_Map and met forecast exist for the asset, and {@link AeiWeighting#none()}
 * otherwise (Req 15.4).
 *
 * <p>Carrying the already-computed forecast pollution cost (rather than re-deriving it) keeps the
 * {@link MaintenanceLifecycle#prioritise prioritise} step a pure, deterministic function of its
 * inputs: the lifecycle computes each action's deferral cost through
 * {@link MaintenanceLifecycle#forecastDeferralCost} first, then ranks the queue. All numeric inputs
 * are finite and non-negative.
 *
 * @param machine               the asset the action is for; never {@code null}
 * @param action                the scheduled action; never {@code null}
 * @param assetClass            the asset class of the machine; never {@code null}
 * @param failureRisk           the failure-risk input (e.g. failure probability in {@code [0,1]});
 *                              finite, {@code >= 0}
 * @param downtimeCost          the downtime-cost input as a numeric magnitude; finite, {@code >= 0}
 * @param forecastPollutionCost the forecast pollution cost of deferral as a numeric magnitude,
 *                              before AEI weighting (Req 15.2, 15.3); finite, {@code >= 0}
 * @param aeiWeighting          the AEI weighting to apply to the pollution-cost term (Req 15.4);
 *                              never {@code null}
 *
 * @see RankedAction
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.3, 15.4</a>
 */
public record QueuedAction(
        MachineId machine,
        ActionId action,
        AssetClass assetClass,
        double failureRisk,
        double downtimeCost,
        double forecastPollutionCost,
        AeiWeighting aeiWeighting) {

    /**
     * Validates the identity references, the AEI weighting, and the finite non-negative inputs
     * (Req 15.3).
     *
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if any numeric input is non-finite or negative
     */
    public QueuedAction {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(aeiWeighting, "aeiWeighting must not be null");
        requireFiniteNonNegative(failureRisk, "failureRisk");
        requireFiniteNonNegative(downtimeCost, "downtimeCost");
        requireFiniteNonNegative(forecastPollutionCost, "forecastPollutionCost");
    }

    /**
     * Builds the {@link EmissionsAwarePriority} for this action from its inputs (Req 15.3, 15.4).
     *
     * @return the Emissions_Aware_Priority of this queued action
     */
    public EmissionsAwarePriority priority() {
        return new EmissionsAwarePriority(
                failureRisk, downtimeCost, forecastPollutionCost, aeiWeighting);
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
