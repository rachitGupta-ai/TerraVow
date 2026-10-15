package com.terravow.capability.eid;

import java.util.Objects;
import java.util.Optional;

/**
 * The telemetry input for an emissions-intensity computation over an observation window.
 *
 * <p>Emissions intensity is emissions per unit of output (Req 1.1); this record carries the
 * emissions term. The emissions value is {@link Optional} so an absent measurement is represented
 * explicitly: an absent or non-finite/negative emissions value makes the intensity unavailable with
 * a recorded reason (Req 1.2) rather than throwing. Validation of the value is performed by the
 * service so the "absent or invalid → unavailable" rule is centralised there.
 *
 * @param emissions the measured emissions over the window, or empty if not captured (Req 1.1, 1.2)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.1, 1.2</a>
 */
public record TelemetryWindow(Optional<Double> emissions) {

    /**
     * Validates that the optional container itself is present (its contents are validated by the
     * service, so an absent or invalid value degrades gracefully rather than throwing).
     *
     * @throws NullPointerException if {@code emissions} is {@code null}
     */
    public TelemetryWindow {
        Objects.requireNonNull(emissions, "emissions must not be null");
    }

    /**
     * Convenience factory for a window carrying a captured emissions measurement.
     *
     * @param emissions the measured emissions
     * @return a telemetry window wrapping the value
     */
    public static TelemetryWindow of(double emissions) {
        return new TelemetryWindow(Optional.of(emissions));
    }

    /**
     * Convenience factory for a window with no captured emissions measurement.
     *
     * @return a telemetry window with an absent emissions value
     */
    public static TelemetryWindow absent() {
        return new TelemetryWindow(Optional.empty());
    }
}
