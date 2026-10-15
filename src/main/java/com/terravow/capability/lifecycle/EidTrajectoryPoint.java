package com.terravow.capability.lifecycle;

/**
 * A single step of a forecast EID trajectory: the forecast EID value expected to hold during one
 * forecast step of the deferral {@link Horizon} (Req 15.1).
 *
 * <p>The forecast EID is the emissions-intensity degradation (current intensity minus baseline)
 * the Reasoning_Agent expects the degraded asset to exhibit over this step if maintenance is
 * deferred. The {@link #operatingHours()} is the number of degraded operating hours this step
 * represents — the deferral-cost pricing accrues the ERD components over these hours (Req 15.2).
 *
 * <p>Both values are finite and non-negative: an EID trajectory for a degraded asset forecasts a
 * non-negative excess intensity, and a step always spans a non-negative number of operating hours.
 *
 * @param stepIndex     the zero-based index of this step within the horizon
 * @param forecastEid   the forecast EID (excess emissions intensity) over this step; must be finite
 *                      and {@code >= 0}
 * @param operatingHours the degraded operating hours this step represents; must be finite and
 *                      {@code >= 0}
 *
 * @see EidTrajectoryForecast
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.1, 15.2</a>
 */
public record EidTrajectoryPoint(int stepIndex, double forecastEid, double operatingHours) {

    /**
     * Validates the step index and the finite, non-negative forecast and operating-hour values
     * (Req 15.1).
     *
     * @throws IllegalArgumentException if the step index is negative, or either value is non-finite
     *                                  or negative
     */
    public EidTrajectoryPoint {
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must not be negative, got: " + stepIndex);
        }
        requireFiniteNonNegative(forecastEid, "forecastEid");
        requireFiniteNonNegative(operatingHours, "operatingHours");
    }

    private static void requireFiniteNonNegative(double v, String name) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be a finite number, got: " + v);
        }
        if (v < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative, got: " + v);
        }
    }

    /**
     * Builds a point for the given step with a one-operating-hour span (Req 15.1).
     *
     * @param stepIndex   the zero-based step index
     * @param forecastEid the forecast EID over the step; must be finite and {@code >= 0}
     * @return a trajectory point spanning one operating hour
     */
    public static EidTrajectoryPoint hourly(int stepIndex, double forecastEid) {
        return new EidTrajectoryPoint(stepIndex, forecastEid, 1.0);
    }
}
