package com.terravow.capability.lifecycle;

import com.terravow.domain.MachineId;

/**
 * The Reasoning_Agent seam the {@link MaintenanceLifecycle} drives to forecast the EID trajectory of
 * a degraded Machine_Asset over a configured deferral horizon (Req 15.1).
 *
 * <p>Requirement 15.1 mandates that the EID trajectory be forecast <em>through a Reasoning_Agent</em>
 * — a counterfactual determination that does not exist in any database, which Req 11.1 routes to a
 * Gemini-on-Vertex-AI specialist agent via the
 * {@link com.terravow.reasoning.ReasoningAgentOrchestrator}. This interface is the narrow contract
 * the lifecycle depends on: it names the asset and the horizon and returns the forecast (or an
 * undetermined result when the data is insufficient per Req 15.10). The production implementation
 * builds the agent prompt, invokes the orchestrator with the trajectory output schema, folds the
 * returned grounding citations into the Evidence_Chain, and maps an escalation to an undetermined
 * forecast; tests supply a deterministic stub so the lifecycle's own logic — pricing, priority,
 * ranking, effectiveness — can be verified without a live agent.
 *
 * <p>Modelling the Reasoning_Agent as an injected collaborator (rather than calling the orchestrator
 * inline) keeps {@link DefaultMaintenanceLifecycle} self-contained and deterministic under test, in
 * line with the interface + implementation and constructor-injection conventions, while preserving
 * the Req 15.1 requirement that the forecast is produced by a Reasoning_Agent.
 *
 * @see MaintenanceLifecycle
 * @see com.terravow.reasoning.ReasoningAgentOrchestrator
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.1, 11.1,
 *      15.10</a>
 */
@FunctionalInterface
public interface EidTrajectoryForecaster {

    /**
     * Forecasts the EID trajectory of a degraded asset over the given horizon via the Reasoning_Agent
     * (Req 15.1).
     *
     * <p>Returns a {@link EidTrajectoryForecast} with one point per horizon step when the agent
     * produces a schema-valid trajectory, or an undetermined forecast with a recorded reason when the
     * data required to forecast is insufficient or the agent escalates (Req 15.10).
     *
     * @param machine the degraded asset to forecast for; never {@code null}
     * @param horizon the configured deferral horizon to forecast over; never {@code null}
     * @return the EID trajectory forecast; never {@code null}
     */
    EidTrajectoryForecast forecast(MachineId machine, Horizon horizon);
}
