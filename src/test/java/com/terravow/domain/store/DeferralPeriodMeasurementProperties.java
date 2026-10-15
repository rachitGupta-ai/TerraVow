package com.terravow.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.ActionId;
import com.terravow.domain.ScheduledAction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for the Deferral_Period measurement of {@link DeferralCalculator}.
 *
 * <p>Validates Correctness Property 42: while a scheduled maintenance action is past its due time
 * and not yet completed, its Deferral_Period is measured from the due time (Req 14.5). For any
 * past-due incomplete action the Deferral_Period starts exactly at the action's {@code dueTime} and
 * ends at the evaluation time ("now", from the injected clock); for an action completed <em>after</em>
 * its due time the period ends at {@code completedAt}; and an on-time (completed on-or-before due)
 * or not-yet-due action yields no Deferral_Period. A fixed {@link Clock} supplies "now" so every
 * computation is deterministic (the same clock and action always yield the same period).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.5</a>
 */
class DeferralPeriodMeasurementProperties {

    /** The fixed evaluation instant ("now") against which due times are classified as past/future. */
    private static final Instant NOW = Instant.parse("2024-03-10T12:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DeferralCalculator calculator = new DeferralCalculator(clock);

    // Feature: terravow, Property 42: Deferral period is measured from the due time
    @Property(tries = 100)
    void outstandingPastDueActionDefersFromDueTimeToNow(
            @ForAll("positiveSeconds") long pastDueSeconds) {
        // An incomplete action due strictly before "now" is deferred from its due time to "now".
        Instant due = NOW.minusSeconds(pastDueSeconds);
        ScheduledAction act = action("a", due, false, null);

        DeferralPeriod period = calculator.deferralOf(act).orElseThrow();

        assertThat(period.start()).isEqualTo(due);
        assertThat(period.end()).isEqualTo(NOW);
        assertThat(period.duration()).isEqualTo(Duration.between(due, NOW));
    }

    // Feature: terravow, Property 42: Deferral period is measured from the due time
    @Property(tries = 100)
    void lateCompletedActionDefersFromDueTimeToCompletion(
            @ForAll("positiveSeconds") long dueOffsetSeconds,
            @ForAll("positiveSeconds") long completionDelaySeconds) {
        // An action completed strictly after its due time was deferred from due time to completion.
        Instant due = NOW.minusSeconds(dueOffsetSeconds);
        Instant completedAt = due.plusSeconds(completionDelaySeconds);
        ScheduledAction act = action("a", due, true, completedAt);

        DeferralPeriod period = calculator.deferralOf(act).orElseThrow();

        assertThat(period.start()).isEqualTo(due);
        assertThat(period.end()).isEqualTo(completedAt);
        assertThat(period.duration()).isEqualTo(Duration.between(due, completedAt));
    }

    // Feature: terravow, Property 42: Deferral period is measured from the due time
    @Property(tries = 100)
    void notYetDueActionYieldsNoDeferralPeriod(@ForAll("positiveSeconds") long futureSeconds) {
        // An action due strictly after "now" is not past due, so it is not deferred.
        Instant due = NOW.plusSeconds(futureSeconds);
        ScheduledAction act = action("a", due, false, null);

        assertThat(calculator.deferralOf(act)).isEmpty();
    }

    // Feature: terravow, Property 42: Deferral period is measured from the due time
    @Property(tries = 100)
    void onTimeCompletedActionYieldsNoDeferralPeriod(
            @ForAll("positiveSeconds") long dueOffsetSeconds,
            @ForAll("nonNegativeSeconds") long earlyBySeconds) {
        // An action completed on-or-before its due time was never past due while outstanding.
        Instant due = NOW.minusSeconds(dueOffsetSeconds);
        Instant completedAt = due.minusSeconds(earlyBySeconds);
        ScheduledAction act = action("a", due, true, completedAt);

        assertThat(calculator.deferralOf(act)).isEmpty();
    }

    private static ScheduledAction action(
            String id, Instant due, boolean completed, Instant completedAt) {
        return new ScheduledAction(
                new ActionId(id), due, completed, Optional.ofNullable(completedAt));
    }

    /**
     * Strictly-positive second offsets, bounded well under a decade so no instant under/overflows
     * when offset from {@code NOW}.
     */
    @Provide
    Arbitrary<Long> positiveSeconds() {
        return Arbitraries.longs().between(1L, Duration.ofDays(3650).getSeconds());
    }

    /**
     * Non-negative second offsets (including zero, i.e. completed exactly at the due time), bounded
     * well under a decade so no instant under/overflows when offset from {@code NOW}.
     */
    @Provide
    Arbitrary<Long> nonNegativeSeconds() {
        return Arbitraries.longs().between(0L, Duration.ofDays(3650).getSeconds());
    }
}
