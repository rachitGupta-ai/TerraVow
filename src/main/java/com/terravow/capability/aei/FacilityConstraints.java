package com.terravow.capability.aei;

import java.util.List;
import java.util.Objects;

/**
 * The production constraints that bound how emission mass may be redistributed across steps when
 * rescheduling (Req 4.3, 4.4).
 *
 * <p>Rescheduling computes a harm-minimising schedule of the same total mass "subject to the
 * facility production constraints" (Req 4.3) and recommends a revised schedule within those
 * constraints (Req 4.4). Each step {@code i} carries a maximum emission mass it can bear
 * ({@code maxMassPerStep}) — a shiftable operation can move mass into a step only up to that cap,
 * and a step with a zero cap is one into which no emission can be shifted. The caps are defensively
 * copied into an unmodifiable list.
 *
 * <p>A schedule is <em>feasible</em> under these constraints when, step for step, its mass does not
 * exceed the cap and the number of steps matches ({@link #isFeasible(EmissionSchedule)}).
 *
 * @param maxMassPerStep the per-step maximum emission mass; never {@code null}, defensively copied,
 *                       each entry finite and {@code >= 0}
 *
 * @see EmissionSchedule
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.3, 4.4</a>
 */
public record FacilityConstraints(List<Double> maxMassPerStep) {

    /**
     * Validates presence and the finite, non-negative cap invariant (Req 4.4).
     *
     * @throws NullPointerException     if {@code maxMassPerStep} is {@code null} or contains a
     *                                  {@code null} element
     * @throws IllegalArgumentException if {@code maxMassPerStep} is empty or any cap is non-finite
     *                                  or negative
     */
    public FacilityConstraints {
        Objects.requireNonNull(maxMassPerStep, "FacilityConstraints maxMassPerStep must not be null");
        maxMassPerStep = List.copyOf(maxMassPerStep);
        if (maxMassPerStep.isEmpty()) {
            throw new IllegalArgumentException("FacilityConstraints maxMassPerStep must not be empty");
        }
        for (int i = 0; i < maxMassPerStep.size(); i++) {
            double cap = maxMassPerStep.get(i);
            if (Double.isNaN(cap) || Double.isInfinite(cap)) {
                throw new IllegalArgumentException(
                        "max mass at step " + i + " must be finite, got: " + cap);
            }
            if (cap < 0.0) {
                throw new IllegalArgumentException(
                        "max mass at step " + i + " must be non-negative, got: " + cap);
            }
        }
    }

    /**
     * The number of constrained steps.
     *
     * @return the step count, {@code >= 1}
     */
    public int stepCount() {
        return maxMassPerStep.size();
    }

    /**
     * The total capacity across all steps — the most mass the facility could carry over the
     * horizon.
     *
     * @return the summed cap, {@code >= 0}
     */
    public double totalCapacity() {
        double total = 0.0;
        for (double cap : maxMassPerStep) {
            total += cap;
        }
        return total;
    }

    /**
     * Whether the given schedule fits within these constraints step for step (Req 4.4).
     *
     * <p>A schedule is feasible iff it has the same number of steps and no step's mass exceeds that
     * step's cap. A per-step comparison uses a tiny absolute epsilon so floating-point rounding at
     * the cap does not spuriously reject a feasible schedule.
     *
     * @param schedule the schedule to check; never {@code null}
     * @return {@code true} iff the schedule is feasible under these constraints
     * @throws NullPointerException if {@code schedule} is {@code null}
     */
    public boolean isFeasible(EmissionSchedule schedule) {
        Objects.requireNonNull(schedule, "schedule must not be null");
        if (schedule.stepCount() != maxMassPerStep.size()) {
            return false;
        }
        double epsilon = 1e-9;
        for (int i = 0; i < maxMassPerStep.size(); i++) {
            if (schedule.massPerStep().get(i) > maxMassPerStep.get(i) + epsilon) {
                return false;
            }
        }
        return true;
    }
}
