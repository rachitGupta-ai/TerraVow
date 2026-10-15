package com.terravow.capability.lifecycle;

import com.terravow.domain.Currency;
import com.terravow.gate.GateThresholds;

import java.util.Objects;

/**
 * Configurable parameters for the Emissions-Aware Maintenance Lifecycle (Req 15).
 *
 * <p>Holds the default deferral {@link #defaultHorizon()} the lifecycle forecasts and prices over
 * when a caller does not supply one (Req 15.1), the declared {@link #currency()} the forecast
 * deferral cost is reported in (Req 15.2), and the {@link #gateThresholds()} the resulting
 * maintenance recommendation is classified against by the Decision_Gate (Req 15.9). The horizon is
 * made configurable per the task; the gate thresholds and currency are kept here so the whole
 * lifecycle is configured in one place.
 *
 * @param defaultHorizon the default deferral horizon to forecast/price over (Req 15.1); never
 *                       {@code null}
 * @param currency       the declared currency for the forecast deferral cost (Req 15.2); never
 *                       {@code null}
 * @param gateThresholds the confidence thresholds the maintenance recommendation is classified
 *                       against (Req 15.9); never {@code null}
 *
 * @see DefaultMaintenanceLifecycle
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.1, 15.2,
 *      15.9</a>
 */
public record MaintenanceLifecycleConfig(
        Horizon defaultHorizon, Currency currency, GateThresholds gateThresholds) {

    /** The default deferral horizon: 24 hours in one-hour steps (Req 15.1). */
    public static final Horizon DEFAULT_HORIZON = Horizon.ofHourlySteps(24);

    /** The default Decision_Gate thresholds for maintenance recommendations (Req 15.9). */
    public static final GateThresholds DEFAULT_GATE_THRESHOLDS = new GateThresholds(0.8, 0.5);

    /**
     * Validates that all references are present (Req 15.1, 15.2, 15.9).
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public MaintenanceLifecycleConfig {
        Objects.requireNonNull(defaultHorizon, "defaultHorizon must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(gateThresholds, "gateThresholds must not be null");
    }

    /**
     * The safe baseline configuration: a 24-hour hourly horizon, INR currency, and the default gate
     * thresholds (Req 15.1, 15.2, 15.9).
     *
     * @return the default {@code MaintenanceLifecycleConfig}
     */
    public static MaintenanceLifecycleConfig defaults() {
        return new MaintenanceLifecycleConfig(
                DEFAULT_HORIZON, Currency.INR, DEFAULT_GATE_THRESHOLDS);
    }

    /**
     * Returns a copy of this config with a different default horizon (Req 15.1).
     *
     * @param horizon the new default horizon; never {@code null}
     * @return a config with the given default horizon
     */
    public MaintenanceLifecycleConfig withDefaultHorizon(Horizon horizon) {
        return new MaintenanceLifecycleConfig(horizon, currency, gateThresholds);
    }
}
