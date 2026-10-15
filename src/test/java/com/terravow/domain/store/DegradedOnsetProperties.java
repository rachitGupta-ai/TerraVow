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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for the degraded-onset anchoring of {@link DeferralCalculator}.
 *
 * <p>Validates Correctness Property 43: for any machine that enters a degraded health state
 * associated with one or more deferred (past-due, incomplete) actions, the degraded-health-state
 * period onset used by ERD accrual equals the start of the associated Deferral_Period — the
 * earliest {@code dueTime} among the deferred actions (Req 14.6). A schedule with no deferred
 * action yields no onset. A fixed {@link Clock} supplies "now" so every computation is
 * deterministic.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.6</a>
 */
class DegradedOnsetProperties {

    /** The fixed evaluation instant ("now") against which due times are classified as past/future. */
    private static final Instant NOW = Instant.parse("2024-03-10T12:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DeferralCalculator calculator = new DeferralCalculator(clock);

    // Feature: terravow, Property 43: Degraded onset anchors ERD accrual to the deferral start
    @Property(tries = 100)
    void degradedOnsetEqualsEarliestDeferralStart(
            @ForAll("pastDueOffsets") List<Long> pastDueSeconds,
            @ForAll("futureOffsets") List<Long> futureSeconds) {
        // At least one deferred (past-due, incomplete) action, plus arbitrary not-yet-due actions
        // that must never contribute to the onset.
        List<ScheduledAction> actions = new ArrayList<>();
        int index = 0;
        List<Instant> deferredDueTimes = new ArrayList<>();
        for (long seconds : pastDueSeconds) {
            Instant due = NOW.minusSeconds(seconds);
            deferredDueTimes.add(due);
            actions.add(action("deferred-" + index++, due, false, null));
        }
        for (long seconds : futureSeconds) {
            actions.add(action("future-" + index++, NOW.plusSeconds(seconds), false, null));
        }

        MaintenanceSchedule schedule = new MaintenanceSchedule(new MachineId("machine-1"), actions);

        Instant expectedOnset = deferredDueTimes.stream().min(Instant::compareTo).orElseThrow();

        assertThat(calculator.degradedOnset(schedule)).contains(expectedOnset);
    }

    // Feature: terravow, Property 43: Degraded onset anchors ERD accrual to the deferral start
    @Property(tries = 100)
    void scheduleWithNoDeferredActionYieldsNoOnset(
            @ForAll("futureOffsets") List<Long> futureSeconds) {
        // Only not-yet-due actions: nothing is deferred, so there is no degraded-onset.
        List<ScheduledAction> actions = new ArrayList<>();
        int index = 0;
        for (long seconds : futureSeconds) {
            actions.add(action("future-" + index++, NOW.plusSeconds(seconds), false, null));
        }

        MaintenanceSchedule schedule = new MaintenanceSchedule(new MachineId("machine-1"), actions);

        assertThat(calculator.degradedOnset(schedule)).isEmpty();
    }

    private static ScheduledAction action(
            String id, Instant due, boolean completed, Instant completedAt) {
        return new ScheduledAction(
                new ActionId(id), due, completed, Optional.ofNullable(completedAt));
    }

    /**
     * One or more strictly-positive second offsets subtracted from {@code NOW} to produce past-due
     * due times (so each action is deferred). Bounded well under a decade so no instant underflows.
     */
    @Provide
    Arbitrary<List<Long>> pastDueOffsets() {
        Arbitrary<Long> seconds =
                Arbitraries.longs().between(1L, Duration.ofDays(3650).getSeconds());
        return seconds.list().ofMinSize(1).ofMaxSize(8);
    }

    /**
     * Zero or more strictly-positive second offsets added to {@code NOW} to produce not-yet-due due
     * times (so each such action is never deferred and never contributes to the onset).
     */
    @Provide
    Arbitrary<List<Long>> futureOffsets() {
        Arbitrary<Long> seconds =
                Arbitraries.longs().between(1L, Duration.ofDays(3650).getSeconds());
        return seconds.list().ofMinSize(0).ofMaxSize(8);
    }
}
