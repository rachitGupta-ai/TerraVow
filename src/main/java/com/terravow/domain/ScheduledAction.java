package com.terravow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A single planned maintenance action within a {@link MaintenanceSchedule}.
 *
 * <p>A scheduled action carries a due time, a completion flag, and an optional completion timestamp.
 * While a scheduled action is past its due time and not yet completed, it is marked deferred and its
 * Deferral_Period is measured from the due time (Req 14.5). The Deferral_Period is
 * {@code (completedAt | now) − dueTime}; this record holds the raw fields, and the deferral
 * computation is performed by the schedule store (task 2.2), which also anchors the degraded-state
 * onset to the Deferral_Period start (Req 14.6). The {@code completedAt} is {@link Optional} because
 * an outstanding action has no completion timestamp.
 *
 * @param id          the scheduled-action identifier; never {@code null}
 * @param dueTime     the time the action is due; never {@code null}
 * @param completed   whether the action has been completed
 * @param completedAt the completion timestamp when completed; never {@code null}, may be
 *                    {@link Optional#empty()}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.5, 14.6</a>
 */
public record ScheduledAction(
        ActionId id, Instant dueTime, boolean completed, Optional<Instant> completedAt) {

    /**
     * Validates that the identifier, due time, and completion-timestamp optional wrapper are
     * present (Req 14.5). {@code completedAt} may be empty but the {@link Optional} itself must be
     * non-null.
     *
     * @throws NullPointerException if {@code id}, {@code dueTime}, or the {@code completedAt}
     *                              optional wrapper is {@code null}
     */
    public ScheduledAction {
        Objects.requireNonNull(id, "ScheduledAction id must not be null");
        Objects.requireNonNull(dueTime, "ScheduledAction dueTime must not be null");
        Objects.requireNonNull(completedAt, "ScheduledAction completedAt must not be null");
    }
}
