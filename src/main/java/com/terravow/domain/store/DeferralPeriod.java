package com.terravow.domain.store;

import com.terravow.domain.ActionId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The deferral of a past-due, not-yet-completed {@link com.terravow.domain.ScheduledAction},
 * measured from its due time.
 *
 * <p>While a scheduled maintenance action is past its due time and not yet completed, it is marked
 * deferred and its Deferral_Period is recorded, measured from the due time (Req 14.5). The period
 * <em>starts</em> at the action's due time and <em>ends</em> at the completion time if the action
 * has since completed, or at the evaluation time ("now", from the injected clock) if it is still
 * outstanding. The {@link #start()} is the anchor ERD accrual uses when the machine enters a
 * degraded state (Req 14.6): the degraded-onset is set to the Deferral_Period start.
 *
 * <p>The {@link #duration()} is {@code end − start} and is always non-negative because the period
 * is only computed for an action already past due. This is an immutable value object; it is
 * produced by {@link DeferralCalculator} from a due time and an evaluation/completion time, so it
 * reads no wall-clock time itself.
 *
 * @param actionId the deferred action's identifier; never {@code null}
 * @param start    the Deferral_Period start — the action's due time; never {@code null}
 * @param end      the Deferral_Period end — completion time, or evaluation time if outstanding;
 *                 never {@code null}, never before {@code start}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.5, 14.6</a>
 */
public record DeferralPeriod(ActionId actionId, Instant start, Instant end) {

    /**
     * Validates that identifiers/timestamps are present and the period is non-negative (end not
     * before start) (Req 14.5).
     *
     * @throws NullPointerException     if any component is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public DeferralPeriod {
        Objects.requireNonNull(actionId, "DeferralPeriod actionId must not be null");
        Objects.requireNonNull(start, "DeferralPeriod start must not be null");
        Objects.requireNonNull(end, "DeferralPeriod end must not be null");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "DeferralPeriod end must not be before start: start="
                            + start
                            + ", end="
                            + end);
        }
    }

    /**
     * The elapsed Deferral_Period, measured from the due time (Req 14.5).
     *
     * @return {@code end − start}; always non-negative
     */
    public Duration duration() {
        return Duration.between(start, end);
    }

    /**
     * The degraded-onset anchor for ERD accrual — the Deferral_Period start (Req 14.6).
     *
     * @return the start instant of this deferral period
     */
    public Instant degradedOnset() {
        return start;
    }
}
