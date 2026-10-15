package com.terravow.domain.store;

import com.terravow.domain.MachineId;

import java.time.Instant;
import java.util.Objects;

/**
 * A single machine telemetry reading feeding Machine_Health_State derivation.
 *
 * <p>A Machine_Health_State is derived from telemetry and maintenance history (Req 14.9). This is
 * the minimal telemetry value type the {@link HealthStateDeriver} consumes: a timestamped health
 * indicator in {@code [0, 1]} for a machine, where higher means healthier. It is defined locally in
 * the store package so health-state derivation stays self-contained and does not depend on the
 * streaming/ingest layers (Pub/Sub landing tables, task 22) that will ultimately produce telemetry.
 *
 * @param machine   the machine the reading belongs to; never {@code null}
 * @param at        the time the reading was taken; never {@code null}
 * @param indicator a health indicator in {@code [0.0, 1.0]} (1.0 = healthiest)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.9</a>
 */
public record TelemetrySample(MachineId machine, Instant at, double indicator) {

    /**
     * Validates the references and bounds the indicator to {@code [0.0, 1.0]} (Req 14.9).
     *
     * @throws NullPointerException     if {@code machine} or {@code at} is {@code null}
     * @throws IllegalArgumentException if {@code indicator} is non-finite or outside {@code [0,1]}
     */
    public TelemetrySample {
        Objects.requireNonNull(machine, "TelemetrySample machine must not be null");
        Objects.requireNonNull(at, "TelemetrySample at must not be null");
        if (Double.isNaN(indicator) || Double.isInfinite(indicator)) {
            throw new IllegalArgumentException(
                    "TelemetrySample indicator must be a finite number, got: " + indicator);
        }
        if (indicator < 0.0 || indicator > 1.0) {
            throw new IllegalArgumentException(
                    "TelemetrySample indicator must be within [0.0, 1.0], got: " + indicator);
        }
    }
}
