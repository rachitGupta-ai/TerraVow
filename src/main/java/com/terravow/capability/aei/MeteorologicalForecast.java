package com.terravow.capability.aei;

import com.terravow.domain.FacilityLocation;

import java.util.List;
import java.util.Objects;

/**
 * A facility's meteorological forecast input: per-step airshed dispersion capacity (Req 4.1).
 *
 * <p>Each entry is the dispersion capacity for one forecast step — a measure of how readily the
 * airshed carries emitted mass away from receptors in that step (driven by wind speed, mixing
 * height, and stability). A larger value means mass emitted in that step disperses more and reaches
 * receptors as a lower concentration. This is one of the two inputs the AEI forecast requires;
 * together with a {@link ReceptorMap} it yields an assimilative-capacity forecast (Req 4.1), and
 * its absence (empty or null) makes the AEI undetermined (Req 4.2).
 *
 * <p>The per-step values are provided at the forecast granularity (the AEI service maps them onto
 * its configured step grid). Each value must be finite and strictly positive — a zero or negative
 * dispersion capacity is physically meaningless and would make concentration undefined. The list is
 * defensively copied into an unmodifiable list.
 *
 * @param facility               the facility this forecast covers; never {@code null}
 * @param dispersionCapacityPerStep the per-step dispersion capacities; never {@code null},
 *                               defensively copied, each entry finite and {@code > 0}
 *
 * @see AssimilativeForecast
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.1, 4.2</a>
 */
public record MeteorologicalForecast(
        FacilityLocation facility,
        List<Double> dispersionCapacityPerStep) {

    /**
     * Validates presence and the strictly-positive, finite per-step invariant (Req 4.1).
     *
     * @throws NullPointerException     if {@code facility} or {@code dispersionCapacityPerStep} is
     *                                  {@code null}, or the list contains a {@code null} element
     * @throws IllegalArgumentException if any per-step capacity is non-finite or {@code <= 0}
     */
    public MeteorologicalForecast {
        Objects.requireNonNull(facility, "MeteorologicalForecast facility must not be null");
        Objects.requireNonNull(dispersionCapacityPerStep,
                "MeteorologicalForecast dispersionCapacityPerStep must not be null");
        dispersionCapacityPerStep = List.copyOf(dispersionCapacityPerStep);
        for (int i = 0; i < dispersionCapacityPerStep.size(); i++) {
            double capacity = dispersionCapacityPerStep.get(i);
            if (Double.isNaN(capacity) || Double.isInfinite(capacity)) {
                throw new IllegalArgumentException(
                        "dispersion capacity at step " + i + " must be finite, got: " + capacity);
            }
            if (capacity <= 0.0) {
                throw new IllegalArgumentException(
                        "dispersion capacity at step " + i + " must be positive, got: " + capacity);
            }
        }
    }

    /**
     * The number of forecast steps this meteorological forecast provides.
     *
     * @return the step count, {@code >= 0}
     */
    public int stepCount() {
        return dispersionCapacityPerStep.size();
    }
}
