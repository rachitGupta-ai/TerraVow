package com.terravow.capability.lifecycle;

import com.terravow.capability.erd.ErdComponentRate;
import com.terravow.domain.Currency;
import com.terravow.domain.Money;
import com.terravow.reasoning.tool.ErdComponent;
import com.terravow.reasoning.tool.ErdSummationRequest;
import com.terravow.reasoning.tool.ErdSummationTool;
import com.terravow.reasoning.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The default {@link DeferralCostPricer}: prices the forecast EID trajectory through the ERD
 * components using the verified {@link ErdSummationTool} (Req 15.2, 2.2).
 *
 * <p>Pricing is deterministic and reproducible. For each forecast step the per-step pollution cost
 * is
 *
 * <pre>{@code
 *   stepCost = round( sum(available component per-hour rate) × operatingHours × forecastEid )
 * }</pre>
 *
 * <p>so the cost accrues the ERD-component rates over the step's degraded operating hours, scaled by
 * the forecast excess intensity for that step. The per-step costs are then summed through the
 * verified {@link ErdSummationTool} — never free arithmetic — so the deferral cost equals the
 * ERD-component pricing of the trajectory summed over the horizon (Property 46). A currency-mismatched
 * component, an empty/all-unavailable component set, arithmetic overflow, or a verified-tool failure
 * yields an {@link ToolResult#unavailable(String) unavailable} result, which the lifecycle maps to a
 * deferral cost undetermined with a reason (Req 15.10).
 *
 * @see DeferralCostPricer
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.2, 2.2</a>
 */
public final class VerifiedToolDeferralCostPricer implements DeferralCostPricer {

    /** The verified tool that sums the per-step costs into the deferral cost (Req 2.2). */
    private final ErdSummationTool summationTool;

    /**
     * Creates a pricer backed by the given verified summation tool (constructor injection).
     *
     * @param summationTool the verified ERD summation tool; never {@code null}
     * @throws NullPointerException if {@code summationTool} is {@code null}
     */
    public VerifiedToolDeferralCostPricer(ErdSummationTool summationTool) {
        this.summationTool = Objects.requireNonNull(summationTool, "summationTool must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public ToolResult<Money> price(
            Currency currency,
            List<EidTrajectoryPoint> points,
            List<ErdComponentRate> componentRates) {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(points, "points must not be null");
        Objects.requireNonNull(componentRates, "componentRates must not be null");
        if (points.isEmpty()) {
            return ToolResult.unavailable("no forecast trajectory points to price");
        }

        // Sum the available ERD-component per-operating-hour rates into a single per-hour rate.
        long perHourMinorUnits = 0L;
        int availableComponents = 0;
        for (ErdComponentRate rate : componentRates) {
            if (!rate.isAvailable()) {
                continue;
            }
            Money amount = rate.amountPerHour().orElseThrow();
            if (amount.currency() != currency) {
                return ToolResult.unavailable(
                        "ERD component " + rate.type() + " currency " + amount.currency()
                                + " does not match declared " + currency);
            }
            try {
                perHourMinorUnits = Math.addExact(perHourMinorUnits, amount.minorUnits());
            } catch (ArithmeticException ex) {
                return ToolResult.unavailable(
                        "ERD component rate sum overflowed: " + ex.getMessage());
            }
            availableComponents++;
        }
        if (availableComponents == 0) {
            return ToolResult.unavailable("no available ERD components to price the trajectory");
        }

        // Price each forecast step and present it as a verified-tool summand (Req 15.2, 2.2).
        List<ErdComponent> summands = new ArrayList<>(points.size());
        for (EidTrajectoryPoint point : points) {
            double raw =
                    (double) perHourMinorUnits * point.operatingHours() * point.forecastEid();
            if (Double.isNaN(raw) || Double.isInfinite(raw)) {
                return ToolResult.unavailable(
                        "step cost is not a finite number at step " + point.stepIndex());
            }
            long stepMinorUnits = Math.round(raw);
            summands.add(
                    ErdComponent.available(
                            "deferral-step-" + point.stepIndex(),
                            new Money(stepMinorUnits, currency)));
        }

        // Req 2.2 — sum via the verified tool, not free arithmetic.
        return summationTool.execute(new ErdSummationRequest(currency, summands));
    }
}
