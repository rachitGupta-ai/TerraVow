package com.terravow.capability.aei;

import com.terravow.domain.FacilityLocation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * An airshed assimilative-capacity forecast over the AEI horizon (Req 4.1).
 *
 * <p>Produced when both a meteorological forecast and a {@link ReceptorMap} are available for a
 * facility (Req 4.1). It carries, per forecast step, the <em>harm coefficient</em>: the
 * population-exposure harm per unit of emission mass if that mass were emitted in that step,
 * expressed so that AEI totals come out in person-µg/m³-hours (Req 4.3). The coefficient combines
 * the step's dispersion capacity (lower capacity → higher concentration → higher harm) with the
 * facility's total receptor population weight.
 *
 * <p>The forecast spans {@link #horizon()} at {@link #step()} granularity: the horizon is
 * {@code >= 24h} and the step is {@code <= 1h} (Req 4.1), so {@code harmCoefficientPerStep} always
 * has at least 24 entries. The list is defensively copied into an unmodifiable list.
 *
 * @param facility              the facility this forecast covers; never {@code null}
 * @param horizon               the forecast horizon; {@code >= 24h}
 * @param step                  the forecast step; in {@code (0h, 1h]}
 * @param harmCoefficientPerStep the per-step harm-per-unit-mass coefficients; never {@code null},
 *                              defensively copied, each entry finite and {@code >= 0}
 *
 * @see MeteorologicalForecast
 * @see DefaultAeiService
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.1, 4.3</a>
 */
public record AssimilativeForecast(
        FacilityLocation facility,
        Duration horizon,
        Duration step,
        List<Double> harmCoefficientPerStep) {

    /**
     * Validates the horizon/step bounds (Req 4.1) and the per-step harm-coefficient invariant.
     *
     * @throws NullPointerException     if any argument is {@code null} or the coefficient list
     *                                  contains a {@code null} element
     * @throws IllegalArgumentException if the horizon is shorter than 24h, the step is non-positive
     *                                  or coarser than 1h, the step count is below 24, or any
     *                                  coefficient is non-finite or negative
     */
    public AssimilativeForecast {
        Objects.requireNonNull(facility, "AssimilativeForecast facility must not be null");
        Objects.requireNonNull(horizon, "AssimilativeForecast horizon must not be null");
        Objects.requireNonNull(step, "AssimilativeForecast step must not be null");
        Objects.requireNonNull(harmCoefficientPerStep,
                "AssimilativeForecast harmCoefficientPerStep must not be null");
        if (horizon.compareTo(AeiConfig.MIN_HORIZON) < 0) {
            throw new IllegalArgumentException(
                    "forecast horizon must be at least 24h (Req 4.1), got: " + horizon);
        }
        if (step.isNegative() || step.isZero() || step.compareTo(AeiConfig.MAX_STEP) > 0) {
            throw new IllegalArgumentException(
                    "forecast step must be in (0h, 1h] (Req 4.1), got: " + step);
        }
        harmCoefficientPerStep = List.copyOf(harmCoefficientPerStep);
        if (harmCoefficientPerStep.size() < 24) {
            throw new IllegalArgumentException(
                    "forecast must have at least 24 steps (Req 4.1), got: "
                            + harmCoefficientPerStep.size());
        }
        for (int i = 0; i < harmCoefficientPerStep.size(); i++) {
            double coefficient = harmCoefficientPerStep.get(i);
            if (Double.isNaN(coefficient) || Double.isInfinite(coefficient)) {
                throw new IllegalArgumentException(
                        "harm coefficient at step " + i + " must be finite, got: " + coefficient);
            }
            if (coefficient < 0.0) {
                throw new IllegalArgumentException(
                        "harm coefficient at step " + i + " must be non-negative, got: "
                                + coefficient);
            }
        }
    }

    /**
     * The number of forecast steps.
     *
     * @return the step count, always {@code >= 24}
     */
    public int stepCount() {
        return harmCoefficientPerStep.size();
    }
}
