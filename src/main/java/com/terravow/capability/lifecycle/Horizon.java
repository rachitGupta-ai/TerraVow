package com.terravow.capability.lifecycle;

import java.time.Duration;
import java.util.Objects;

/**
 * The configured deferral horizon over which an EID trajectory is forecast and priced (Req 15.1,
 * 15.2).
 *
 * <p>A {@code Horizon} is a total {@link #duration()} divided into a whole number of equal
 * {@link #stepCount()} forecast steps. Each step contributes one {@link EidTrajectoryPoint} to the
 * forecast; the deferral cost prices each step's forecast EID through the ERD components and sums
 * over the horizon (Req 15.2). The horizon is configurable per the task — the lifecycle holds a
 * default in {@link MaintenanceLifecycleConfig} and callers may override it per request.
 *
 * <p>The step duration is derived as {@code duration / stepCount}; the compact constructor enforces
 * that the duration divides evenly into whole steps so the trajectory covers exactly the horizon
 * with no remainder. Both a positive duration and a positive step count are required.
 *
 * @param duration  the total deferral horizon; must be positive
 * @param stepCount the number of equal forecast steps the horizon is divided into; must be
 *                  {@code >= 1} and must divide {@code duration} evenly
 *
 * @see MaintenanceLifecycleConfig
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.1, 15.2</a>
 */
public record Horizon(Duration duration, int stepCount) {

    /**
     * Validates that the duration is present and positive, the step count is at least one, and the
     * duration divides evenly into the steps (Req 15.1).
     *
     * @throws NullPointerException     if {@code duration} is {@code null}
     * @throws IllegalArgumentException if the duration is non-positive, the step count is less than
     *                                  one, or the duration does not divide evenly into the steps
     */
    public Horizon {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("horizon duration must be positive, got: " + duration);
        }
        if (stepCount < 1) {
            throw new IllegalArgumentException("stepCount must be at least 1, got: " + stepCount);
        }
        if (duration.toSeconds() % stepCount != 0L) {
            throw new IllegalArgumentException(
                    "horizon duration " + duration + " must divide evenly into " + stepCount
                            + " steps");
        }
    }

    /**
     * Builds a horizon of {@code hours} total length divided into one step per hour (Req 15.1).
     *
     * @param hours the horizon length in whole hours; must be {@code >= 1}
     * @return a horizon with one forecast step per hour
     * @throws IllegalArgumentException if {@code hours} is less than one
     */
    public static Horizon ofHourlySteps(int hours) {
        if (hours < 1) {
            throw new IllegalArgumentException("hours must be at least 1, got: " + hours);
        }
        return new Horizon(Duration.ofHours(hours), hours);
    }

    /**
     * The duration of a single forecast step, {@code duration / stepCount} (Req 15.2).
     *
     * @return the per-step duration
     */
    public Duration step() {
        return duration.dividedBy(stepCount);
    }
}
