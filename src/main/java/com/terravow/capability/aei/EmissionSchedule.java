package com.terravow.capability.aei;

import java.util.List;
import java.util.Objects;

/**
 * A facility's emission schedule over the AEI forecast horizon: emission mass per step (Req 4.3).
 *
 * <p>Entry {@code i} is the emission mass (in a consistent unit, e.g. kilograms) the facility emits
 * in forecast step {@code i}. The AEI compares the harm of this <em>actual</em> schedule against
 * the harm of a harm-minimising schedule of the <em>same total mass</em> (Req 4.3); rescheduling
 * then proposes a revised schedule whose AEI is no greater than the actual (Req 4.4) while
 * preserving the {@link #totalMass() total mass} within tolerance (Req 4.5). Each per-step mass must
 * be finite and non-negative. The list is defensively copied into an unmodifiable list.
 *
 * @param massPerStep the per-step emission masses; never {@code null}, defensively copied, each
 *                    entry finite and {@code >= 0}
 *
 * @see FacilityConstraints
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.3, 4.4, 4.5</a>
 */
public record EmissionSchedule(List<Double> massPerStep) {

    /**
     * Validates presence and the finite, non-negative per-step invariant (Req 4.3).
     *
     * @throws NullPointerException     if {@code massPerStep} is {@code null} or contains a
     *                                  {@code null} element
     * @throws IllegalArgumentException if {@code massPerStep} is empty or any entry is non-finite or
     *                                  negative
     */
    public EmissionSchedule {
        Objects.requireNonNull(massPerStep, "EmissionSchedule massPerStep must not be null");
        massPerStep = List.copyOf(massPerStep);
        if (massPerStep.isEmpty()) {
            throw new IllegalArgumentException("EmissionSchedule massPerStep must not be empty");
        }
        for (int i = 0; i < massPerStep.size(); i++) {
            double mass = massPerStep.get(i);
            if (Double.isNaN(mass) || Double.isInfinite(mass)) {
                throw new IllegalArgumentException(
                        "emission mass at step " + i + " must be finite, got: " + mass);
            }
            if (mass < 0.0) {
                throw new IllegalArgumentException(
                        "emission mass at step " + i + " must be non-negative, got: " + mass);
            }
        }
    }

    /**
     * The number of steps in this schedule.
     *
     * @return the step count, {@code >= 1}
     */
    public int stepCount() {
        return massPerStep.size();
    }

    /**
     * The total emission mass across all steps — the quantity rescheduling must preserve (Req 4.5).
     *
     * @return the summed mass, {@code >= 0}
     */
    public double totalMass() {
        double total = 0.0;
        for (double mass : massPerStep) {
            total += mass;
        }
        return total;
    }
}
