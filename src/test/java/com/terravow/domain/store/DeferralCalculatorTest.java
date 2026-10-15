package com.terravow.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.ActionId;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceSchedule;
import com.terravow.domain.ScheduledAction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeferralCalculator}: the Deferral_Period is measured from the due time of a
 * past-due incomplete scheduled action, and the degraded-onset anchors to the Deferral_Period start
 * (Req 14.5, 14.6). A fixed {@link Clock} supplies "now" so the computation is deterministic.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.5, 14.6</a>
 */
class DeferralCalculatorTest {

    private static final Instant NOW = Instant.parse("2024-03-10T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DeferralCalculator calculator = new DeferralCalculator(clock);

    private static ScheduledAction action(
            String id, Instant due, boolean completed, Instant completedAt) {
        return new ScheduledAction(
                new ActionId(id), due, completed, Optional.ofNullable(completedAt));
    }

    // Req 14.5 — an outstanding past-due action is deferred from its due time to "now".
    @Test
    void outstandingPastDueActionDefersFromDueTimeToNow() {
        Instant due = NOW.minus(Duration.ofHours(5));
        ScheduledAction act = action("a1", due, false, null);

        DeferralPeriod period = calculator.deferralOf(act).orElseThrow();

        assertThat(period.start()).isEqualTo(due);
        assertThat(period.end()).isEqualTo(NOW);
        assertThat(period.duration()).isEqualTo(Duration.ofHours(5));
    }

    // Req 14.5 — a not-yet-due action is not deferred.
    @Test
    void futureDueActionIsNotDeferred() {
        ScheduledAction act = action("a2", NOW.plus(Duration.ofHours(1)), false, null);

        assertThat(calculator.deferralOf(act)).isEmpty();
    }

    // Req 14.5 — an action completed after its due time was deferred up to completion.
    @Test
    void lateCompletedActionDefersFromDueTimeToCompletion() {
        Instant due = NOW.minus(Duration.ofHours(8));
        Instant completedAt = NOW.minus(Duration.ofHours(2));
        ScheduledAction act = action("a3", due, true, completedAt);

        DeferralPeriod period = calculator.deferralOf(act).orElseThrow();

        assertThat(period.start()).isEqualTo(due);
        assertThat(period.end()).isEqualTo(completedAt);
        assertThat(period.duration()).isEqualTo(Duration.ofHours(6));
    }

    // Req 14.5 — an action completed on or before its due time is not deferred.
    @Test
    void onTimeCompletedActionIsNotDeferred() {
        Instant due = NOW.minus(Duration.ofHours(3));
        ScheduledAction act = action("a4", due, true, due.minus(Duration.ofMinutes(10)));

        assertThat(calculator.deferralOf(act)).isEmpty();
    }

    // Req 14.6 — the degraded-onset is the earliest Deferral_Period start among deferred actions.
    @Test
    void degradedOnsetIsEarliestDeferralStart() {
        Instant earliestDue = NOW.minus(Duration.ofDays(2));
        Instant laterDue = NOW.minus(Duration.ofHours(6));
        MaintenanceSchedule schedule =
                new MaintenanceSchedule(
                        new MachineId("machine-1"),
                        List.of(
                                action("later", laterDue, false, null),
                                action("earliest", earliestDue, false, null),
                                action("future", NOW.plus(Duration.ofHours(1)), false, null)));

        assertThat(calculator.deferralsIn(schedule)).hasSize(2);
        assertThat(calculator.degradedOnset(schedule)).contains(earliestDue);
    }

    // Req 14.6 — a schedule with no deferred action has no degraded-onset.
    @Test
    void noDeferredActionYieldsNoDegradedOnset() {
        MaintenanceSchedule schedule =
                new MaintenanceSchedule(
                        new MachineId("machine-1"),
                        List.of(action("future", NOW.plus(Duration.ofHours(2)), false, null)));

        assertThat(calculator.degradedOnset(schedule)).isEmpty();
    }

    // Req 14.6 — the DeferralPeriod exposes its start as the degraded-onset anchor.
    @Test
    void deferralPeriodExposesStartAsDegradedOnset() {
        Instant due = NOW.minus(Duration.ofHours(4));
        DeferralPeriod period = calculator.deferralOf(action("a5", due, false, null)).orElseThrow();

        assertThat(period.degradedOnset()).isEqualTo(due);
    }
}
