package com.terravow.capability.aei;

import java.time.Duration;

/**
 * Configurable bounds for the AEI airshed assimilative-capacity forecast (Req 4.1) and the
 * mass-preservation tolerance applied to rescheduling candidates (Req 4.5).
 *
 * <p>The forecast must span a horizon of <em>at least</em> 24 hours in time steps <em>no coarser
 * than</em> 1 hour (Req 4.1). This config lets both the horizon and the step be tuned while keeping
 * safe, behaviour-preserving defaults: a 24-hour horizon at a 1-hour step. The
 * {@link #toleranceFraction()} is the permitted relative deviation of total emission mass a
 * rescheduling candidate may show before it is rejected — 0.1 percent ({@code 0.001}) per Req 4.5.
 *
 * <p>Construction is via {@link #of(Duration, Duration, double)}, which <em>clamps</em> rather than
 * rejects out-of-range input so a mis-configuration degrades to a safe forecast rather than failing
 * the determination: the horizon is clamped up to the 24-hour minimum, the step is clamped into
 * {@code (0h, 1h]}, and the tolerance is clamped into {@code [0, 1]}. The step is additionally
 * clamped so it never exceeds the horizon. {@link #defaults()} returns the safe baseline.
 *
 * @param horizon           the forecast horizon; always {@code >= 24h}
 * @param step              the forecast time step; always in {@code (0h, 1h]} and {@code <= horizon}
 * @param toleranceFraction the mass-preservation tolerance as a fraction; always in {@code [0, 1]}
 *
 * @see DefaultAeiService
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.1, 4.5</a>
 */
public record AeiConfig(Duration horizon, Duration step, double toleranceFraction) {

    /** The minimum forecast horizon mandated by Req 4.1. */
    public static final Duration MIN_HORIZON = Duration.ofHours(24);

    /** The maximum (coarsest) forecast step mandated by Req 4.1. */
    public static final Duration MAX_STEP = Duration.ofHours(1);

    /** The default mass-preservation tolerance (0.1 percent) mandated by Req 4.5. */
    public static final double DEFAULT_TOLERANCE_FRACTION = 0.001;

    /**
     * Validates the invariants a constructed {@code AeiConfig} must hold. Instances are expected to
     * be built via {@link #of(Duration, Duration, double)}, which clamps inputs into range; this
     * constructor enforces the result so an invalid instance is unrepresentable.
     *
     * @throws NullPointerException     if {@code horizon} or {@code step} is {@code null}
     * @throws IllegalArgumentException if the horizon/step/tolerance bounds are violated
     */
    public AeiConfig {
        if (horizon == null) {
            throw new NullPointerException("horizon must not be null");
        }
        if (step == null) {
            throw new NullPointerException("step must not be null");
        }
        if (horizon.compareTo(MIN_HORIZON) < 0) {
            throw new IllegalArgumentException(
                    "horizon must be at least 24h (Req 4.1), got: " + horizon);
        }
        if (step.isNegative() || step.isZero()) {
            throw new IllegalArgumentException("step must be positive, got: " + step);
        }
        if (step.compareTo(MAX_STEP) > 0) {
            throw new IllegalArgumentException(
                    "step must be no coarser than 1h (Req 4.1), got: " + step);
        }
        if (step.compareTo(horizon) > 0) {
            throw new IllegalArgumentException(
                    "step must not exceed the horizon, got step=" + step + ", horizon=" + horizon);
        }
        if (Double.isNaN(toleranceFraction) || Double.isInfinite(toleranceFraction)
                || toleranceFraction < 0.0 || toleranceFraction > 1.0) {
            throw new IllegalArgumentException(
                    "toleranceFraction must be within [0, 1], got: " + toleranceFraction);
        }
    }

    /**
     * Builds a config, clamping each input into its safe range (Req 4.1, 4.5).
     *
     * <p>A {@code null} horizon or step falls back to its default. The horizon is clamped up to the
     * 24-hour minimum; the step is clamped into {@code (0h, 1h]} and then down to the horizon; the
     * tolerance is clamped into {@code [0, 1]} with a non-finite value falling back to the default.
     *
     * @param horizon           the requested horizon, or {@code null} for the default
     * @param step              the requested step, or {@code null} for the default
     * @param toleranceFraction the requested mass tolerance
     * @return a valid, clamped {@code AeiConfig}
     */
    public static AeiConfig of(Duration horizon, Duration step, double toleranceFraction) {
        Duration safeHorizon = (horizon == null) ? MIN_HORIZON : horizon;
        if (safeHorizon.compareTo(MIN_HORIZON) < 0) {
            safeHorizon = MIN_HORIZON;
        }
        Duration safeStep = (step == null) ? MAX_STEP : step;
        if (safeStep.isNegative() || safeStep.isZero()) {
            safeStep = MAX_STEP;
        }
        if (safeStep.compareTo(MAX_STEP) > 0) {
            safeStep = MAX_STEP;
        }
        if (safeStep.compareTo(safeHorizon) > 0) {
            safeStep = safeHorizon;
        }
        double safeTolerance = toleranceFraction;
        if (Double.isNaN(safeTolerance) || Double.isInfinite(safeTolerance)) {
            safeTolerance = DEFAULT_TOLERANCE_FRACTION;
        }
        if (safeTolerance < 0.0) {
            safeTolerance = 0.0;
        }
        if (safeTolerance > 1.0) {
            safeTolerance = 1.0;
        }
        return new AeiConfig(safeHorizon, safeStep, safeTolerance);
    }

    /**
     * The safe baseline configuration: a 24-hour horizon at a 1-hour step with a 0.1 percent
     * mass-preservation tolerance (Req 4.1, 4.5).
     *
     * @return the default {@code AeiConfig}
     */
    public static AeiConfig defaults() {
        return new AeiConfig(MIN_HORIZON, MAX_STEP, DEFAULT_TOLERANCE_FRACTION);
    }

    /**
     * The number of discrete forecast steps the horizon divides into at the configured step size.
     *
     * <p>Computed as {@code ceil(horizon / step)} so the whole horizon is always covered even when
     * the step does not divide it evenly. Always {@code >= 24} because the horizon is {@code >= 24h}
     * and the step is {@code <= 1h} (Req 4.1).
     *
     * @return the step count, always {@code >= 1}
     */
    public int stepCount() {
        long horizonSeconds = horizon.getSeconds();
        long stepSeconds = step.getSeconds();
        long count = (horizonSeconds + stepSeconds - 1) / stepSeconds;
        return (int) Math.max(1, count);
    }
}
