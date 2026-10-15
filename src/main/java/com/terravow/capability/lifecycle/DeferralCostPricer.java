package com.terravow.capability.lifecycle;

import com.terravow.capability.erd.ErdComponentRate;
import com.terravow.domain.Currency;
import com.terravow.reasoning.tool.ToolResult;
import com.terravow.domain.Money;

import java.util.List;

/**
 * Prices a forecast EID trajectory through the ERD components over the deferral horizon, via a
 * verified tool (Req 15.2).
 *
 * <p>Requirement 15.2 computes the forecast pollution cost of continued deferral by pricing the
 * forecast EID trajectory through the ERD components over the deferral horizon; the design states
 * this pricing is done by the verified ERD-component tools via function calling. This seam names
 * exactly that operation: it takes the available ERD-component per-operating-hour rates and the
 * forecast trajectory, and returns the single monetary deferral cost in the declared currency, or an
 * unavailable result when the verified summation cannot be performed.
 *
 * <p>The default implementation, {@link VerifiedToolDeferralCostPricer}, performs the summation
 * through the verified {@link com.terravow.reasoning.tool.ErdSummationTool} so the total is never
 * free-generated — matching how {@code DefaultErdQuantificationService} prices ERD (Req 2.2).
 * Modelling it as an injected collaborator keeps {@link DefaultMaintenanceLifecycle} deterministic
 * and lets a test substitute a stub.
 *
 * @see VerifiedToolDeferralCostPricer
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.2, 2.2</a>
 */
public interface DeferralCostPricer {

    /**
     * Prices the trajectory through the available ERD components over the horizon (Req 15.2).
     *
     * <p>For each forecast step the per-step pollution cost is the sum of the available ERD-component
     * per-operating-hour rates scaled by the step's degraded operating hours and its forecast EID;
     * the per-step costs are summed through the verified tool into the single deferral cost. An empty
     * or all-unavailable component set, or a verified-tool failure, yields an
     * {@link ToolResult#unavailable(String) unavailable} result.
     *
     * @param currency       the declared currency of the deferral cost; never {@code null}
     * @param points         the forecast trajectory points to price; never {@code null}, non-empty
     * @param componentRates the ERD-component per-operating-hour rates (available or unavailable);
     *                       never {@code null}
     * @return the single monetary deferral cost, or an unavailable result with a recorded reason
     */
    ToolResult<Money> price(
            Currency currency, List<EidTrajectoryPoint> points, List<ErdComponentRate> componentRates);
}
