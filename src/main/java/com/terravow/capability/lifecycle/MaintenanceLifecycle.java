package com.terravow.capability.lifecycle;

import com.terravow.capability.erd.ErdComponentRate;
import com.terravow.domain.AssetClass;
import com.terravow.domain.MachineId;
import com.terravow.evidence.RecommendationId;

import java.util.List;

/**
 * The AI-Driven Emissions-Aware Maintenance Lifecycle (Req 15).
 *
 * <p>The lifecycle prioritises maintenance by the <em>pollution cost of deferral</em> and verifies
 * it by its measured emissions outcome, so the maintenance queue minimises environmental harm rather
 * than only downtime cost. It:
 * <ul>
 *   <li>forecasts the EID trajectory of a degraded asset over a configured horizon through a
 *       Reasoning_Agent (Req 15.1);</li>
 *   <li>prices the forecast deferral cost by pricing that trajectory through the ERD components via
 *       verified tools, reporting it undetermined when the data is insufficient (Req 15.2,
 *       15.10);</li>
 *   <li>computes an Emissions_Aware_Priority per queued action combining failure risk, downtime
 *       cost, and the forecast pollution cost, AEI-weighting the pollution-cost term where a
 *       Receptor_Map and met forecast exist (Req 15.3, 15.4);</li>
 *   <li>ranks the queue so a higher-environmental-harm action outranks a higher-failure-probability
 *       but lower-harm action, attaching an Evidence_Chain (Req 15.5);</li>
 *   <li>on completion of a Maintenance_Event, computes the before/after intensity delta and records a
 *       Maintenance_Effectiveness_Record (Req 15.6, 15.7), surfaced to substitution/certification
 *       (Req 15.8);</li>
 *   <li>routes the resulting maintenance recommendation through the Decision_Gate (Req 15.9).</li>
 * </ul>
 *
 * <p>Follows the interface + {@code Default...} implementation convention; all logic is deterministic
 * given its inputs and injected collaborators.
 *
 * @see DefaultMaintenanceLifecycle
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.1–15.10</a>
 */
public interface MaintenanceLifecycle {

    /**
     * Forecasts the EID trajectory of a degraded asset over the configured deferral horizon through
     * the Reasoning_Agent (Req 15.1).
     *
     * @param machine the degraded asset; never {@code null}
     * @param horizon the deferral horizon to forecast over; never {@code null}
     * @return the EID trajectory forecast (computed, or undetermined with a reason per Req 15.10)
     * @throws NullPointerException if any argument is {@code null}
     */
    EidTrajectoryForecast forecastTrajectory(MachineId machine, Horizon horizon);

    /**
     * Prices a forecast EID trajectory through the ERD components over the horizon to produce the
     * forecast pollution cost of continued deferral (Req 15.2); an undetermined trajectory or a
     * verified-tool failure yields a deferral cost undetermined with a reason (Req 15.10).
     *
     * @param forecast       the EID trajectory forecast to price; never {@code null}
     * @param componentRates the ERD-component per-operating-hour rates to price through; never
     *                       {@code null}
     * @return the forecast deferral cost (computed, or undetermined with a reason)
     * @throws NullPointerException if any argument is {@code null}
     */
    DeferralCostResult forecastDeferralCost(
            EidTrajectoryForecast forecast, List<ErdComponentRate> componentRates);

    /**
     * Computes the Emissions_Aware_Priority of each queued maintenance action and ranks the queue,
     * so a higher-environmental-harm action is ranked above a lower-harm action that has a higher
     * failure probability, attaching an Evidence_Chain to each ranking (Req 15.3, 15.4, 15.5).
     *
     * @param queue the queued maintenance actions across assets; never {@code null}
     * @return the ranked actions, highest Emissions_Aware_Priority first; never {@code null}
     * @throws NullPointerException if {@code queue} is {@code null} or contains a {@code null}
     *                              element
     */
    List<RankedAction> prioritise(List<QueuedAction> queue);

    /**
     * On completion of a Maintenance_Event, computes the before/after emissions-intensity delta and
     * records a Maintenance_Effectiveness_Record, which is surfaced to substitution/certification
     * (Req 15.6, 15.7, 15.8).
     *
     * @param observation the before/after intensity observation for the completed event; never
     *                    {@code null}
     * @return the recorded Maintenance_Effectiveness_Record
     * @throws NullPointerException if {@code observation} is {@code null}
     */
    MaintenanceEffectivenessRecord recordEffectiveness(IntensityObservation observation);

    /**
     * Produces a maintenance recommendation from the ranked queue and the forecast deferral cost and
     * routes it through the Decision_Gate, which classifies it ALLOW/ASK/BLOCK and routes a
     * below-allow recommendation to human review (Req 15.9).
     *
     * @param id           the recommendation identifier; never {@code null}
     * @param queue        the queued maintenance actions to rank and recommend; never {@code null}
     * @param deferralCost the forecast deferral cost driving the recommendation (Req 15.2); never
     *                     {@code null}
     * @return the recommendation carrying the ranking, the deferral cost, and the gate verdict
     * @throws NullPointerException if any argument is {@code null}
     */
    MaintenanceRecommendation recommend(
            RecommendationId id, List<QueuedAction> queue, DeferralCostResult deferralCost);

    /**
     * Returns the applicable Maintenance_Effectiveness_Records for an asset class, surfaced as
     * supporting evidence to Substitution Dossiers and Trajectory Certification verdicts (Req 15.8).
     *
     * @param assetClass the asset class to retrieve records for; never {@code null}
     * @return the records for that asset class, in insertion order; never {@code null}
     * @throws NullPointerException if {@code assetClass} is {@code null}
     */
    List<MaintenanceEffectivenessRecord> effectivenessRecordsForAssetClass(AssetClass assetClass);
}
