package com.terravow.domain.store;

import com.terravow.domain.MaintenanceSchedule;
import com.terravow.domain.ScheduledAction;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes the Deferral_Period of past-due, incomplete scheduled maintenance actions and the
 * degraded-onset anchor derived from it (Req 14.5, 14.6).
 *
 * <p>A scheduled action is <em>deferred</em> while it is past its due time and not yet completed
 * (Req 14.5). For such an action the Deferral_Period is measured from the due time: it starts at
 * {@code dueTime} and ends at the completion time when the action has since completed, or at the
 * current time otherwise. "Current time" is read from an injected {@link Clock}, never from
 * {@code Instant.now()}, so the computation is deterministic and testable (the same clock and
 * schedule always yield the same periods).
 *
 * <p>An action that is <em>not</em> past due, or that completed on-or-before its due time, has no
 * Deferral_Period and is skipped. A completed action whose completion was <em>after</em> its due
 * time was deferred up to completion and yields a closed Deferral_Period from due time to
 * completion. The degraded-onset ERD accrual uses is the Deferral_Period start (Req 14.6), surfaced
 * via {@link DeferralPeriod#degradedOnset()} and {@link #degradedOnset(MaintenanceSchedule)}.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.5, 14.6</a>
 */
public final class DeferralCalculator {

    private final Clock clock;

    /**
     * Creates a calculator that reads the current time from the given clock (Req 14.5).
     *
     * @param clock the clock supplying the evaluation time for outstanding actions; never
     *              {@code null}
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    public DeferralCalculator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Computes the Deferral_Period for a single scheduled action, if it is deferred (Req 14.5).
     *
     * <p>The action is deferred when it is past its due time relative to its reference end time —
     * the completion time if completed, else the clock's current time. A completed-on-time or
     * not-yet-due action returns {@link Optional#empty()}.
     *
     * @param action the scheduled action; never {@code null}
     * @return the Deferral_Period measured from the due time, or empty if the action is not deferred
     * @throws NullPointerException if {@code action} is {@code null}
     */
    public Optional<DeferralPeriod> deferralOf(ScheduledAction action) {
        Objects.requireNonNull(action, "action must not be null");
        Instant dueTime = action.dueTime();
        // End of the deferral window: completion time if completed, else the current time.
        Instant end =
                action.completed()
                        ? action.completedAt().orElseGet(() -> Instant.now(clock))
                        : Instant.now(clock);
        // Deferred only while past due: the window end must be strictly after the due time.
        if (!end.isAfter(dueTime)) {
            return Optional.empty();
        }
        return Optional.of(new DeferralPeriod(action.id(), dueTime, end));
    }

    /**
     * Computes the Deferral_Period for every deferred action in a schedule, in schedule order (Req
     * 14.5).
     *
     * @param schedule the maintenance schedule; never {@code null}
     * @return an unmodifiable list of the deferred actions' Deferral_Periods; empty if none are
     *         deferred
     * @throws NullPointerException if {@code schedule} is {@code null}
     */
    public List<DeferralPeriod> deferralsIn(MaintenanceSchedule schedule) {
        Objects.requireNonNull(schedule, "schedule must not be null");
        List<DeferralPeriod> deferrals = new ArrayList<>();
        for (ScheduledAction action : schedule.actions()) {
            deferralOf(action).ifPresent(deferrals::add);
        }
        return List.copyOf(deferrals);
    }

    /**
     * Computes the degraded-onset anchor for a schedule — the earliest Deferral_Period start among
     * its deferred actions (Req 14.6).
     *
     * <p>When a Machine_Asset enters a degraded health state, ERD accrual's degraded-onset is set to
     * the start of the associated Deferral_Period (Req 14.6). When several actions are deferred, the
     * earliest deferral start is the onset so accrual covers the whole degraded window. If no action
     * is deferred, there is no onset.
     *
     * @param schedule the maintenance schedule; never {@code null}
     * @return the earliest Deferral_Period start, or empty if no action is deferred
     * @throws NullPointerException if {@code schedule} is {@code null}
     */
    public Optional<Instant> degradedOnset(MaintenanceSchedule schedule) {
        return deferralsIn(schedule).stream().map(DeferralPeriod::degradedOnset).min(Instant::compareTo);
    }
}
