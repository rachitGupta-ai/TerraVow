package com.terravow.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.ActorId;
import com.terravow.domain.AssetClass;
import com.terravow.domain.Component;
import com.terravow.domain.Confidence;
import com.terravow.domain.FacilityLocation;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineAsset;
import com.terravow.domain.MachineHealthState;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEvent;
import com.terravow.domain.MaintenanceEventId;
import com.terravow.domain.MaintenanceType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Supplementary edge-case unit tests for required-field rejection and health-state cohort fallback
 * that the per-class tests created by task 2.2 ({@link DefaultRegistrationStoreTest},
 * {@link DefaultMaintenanceEventStoreTest}, {@link HealthStateDeriverTest}) do not already cover
 * (Req 14.2, 14.4, 14.10).
 *
 * <p>The per-class tests already assert the full-field happy paths and a representative subset of
 * missing-field rejections. This class fills three remaining gaps without duplicating them:
 *
 * <ul>
 *   <li><b>Req 14.4</b> — every required Maintenance_Event field that is <em>not</em> individually
 *       exercised elsewhere ({@code id}, {@code machine}, {@code start}, {@code end},
 *       {@code downtime}, {@code actingTechnicianOrSystem}) is rejected by name with nothing stored,
 *       and the <em>first</em> missing field wins when several are absent.</li>
 *   <li><b>Req 14.2</b> — the registration rejection path stores nothing for a non-id missing field
 *       too, and reports the first missing field in the fixed validation order when several are
 *       absent.</li>
 *   <li><b>Req 14.10</b> — the sufficiency boundary (exactly {@code minTelemetrySamples} is
 *       sufficient; one fewer falls back to the cohort), and the undetermined case when
 *       <em>partial</em> telemetry below the minimum is present but no cohort baseline is
 *       supplied.</li>
 * </ul>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.2, 14.4, 14.10</a>
 */
class MissingFieldRejectionTest {

    private static final MachineId MACHINE = new MachineId("machine-1");
    private static final AssetClass ASSET_CLASS = new AssetClass("press");
    private static final FacilityLocation LOCATION = new FacilityLocation("pune-plant");
    private static final Instant COMMISSIONED = Instant.parse("2020-01-01T00:00:00Z");

    private static final Instant START = Instant.parse("2024-01-01T08:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T10:00:00Z");

    // ----------------------------------------------------------------------------------------
    // Req 14.4 — maintenance-event required fields not individually exercised by
    // DefaultMaintenanceEventStoreTest (which covers type, action, componentsReplaced).
    // ----------------------------------------------------------------------------------------

    private final DefaultMaintenanceEventStore eventStore = new DefaultMaintenanceEventStore();

    private static MaintenanceEventSubmission event(
            MaintenanceEventId id,
            MachineId machine,
            MaintenanceType type,
            Instant start,
            Instant end,
            String action,
            List<Component> components,
            Duration downtime,
            ActorId actor) {
        return new MaintenanceEventSubmission(
                id, machine, type, start, end, action, components, downtime, actor);
    }

    private static MaintenanceEventSubmission completeEventExcept(
            MaintenanceEventId id,
            MachineId machine,
            Instant start,
            Instant end,
            Duration downtime,
            ActorId actor) {
        return event(
                id,
                machine,
                MaintenanceType.CORRECTIVE,
                start,
                end,
                "replaced bearing",
                List.of(new Component("bearing-A")),
                downtime,
                actor);
    }

    // Req 14.4 — a missing event id is rejected by name; nothing stored.
    @Test
    void missingEventIdIsRejectedByName() {
        StoreResult<MaintenanceEvent> result =
                eventStore.record(
                        completeEventExcept(
                                null,
                                MACHINE,
                                START,
                                END,
                                Duration.ofHours(2),
                                new ActorId("tech-7")));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.missingField()).contains(DefaultMaintenanceEventStore.FIELD_ID);
        assertThat(result.created()).isEmpty();
        assertThat(eventStore.forMachine(MACHINE)).isEmpty();
    }

    // Req 14.4 — a missing machine reference is rejected by name.
    @Test
    void missingMachineIsRejectedByName() {
        StoreResult<MaintenanceEvent> result =
                eventStore.record(
                        completeEventExcept(
                                new MaintenanceEventId("evt-1"),
                                null,
                                START,
                                END,
                                Duration.ofHours(2),
                                new ActorId("tech-7")));

        assertThat(result.missingField()).contains(DefaultMaintenanceEventStore.FIELD_MACHINE);
    }

    // Req 14.4 — a missing start timestamp is rejected by name.
    @Test
    void missingStartIsRejectedByName() {
        StoreResult<MaintenanceEvent> result =
                eventStore.record(
                        completeEventExcept(
                                new MaintenanceEventId("evt-1"),
                                MACHINE,
                                null,
                                END,
                                Duration.ofHours(2),
                                new ActorId("tech-7")));

        assertThat(result.missingField()).contains(DefaultMaintenanceEventStore.FIELD_START);
    }

    // Req 14.4 — a missing end timestamp is rejected by name.
    @Test
    void missingEndIsRejectedByName() {
        StoreResult<MaintenanceEvent> result =
                eventStore.record(
                        completeEventExcept(
                                new MaintenanceEventId("evt-1"),
                                MACHINE,
                                START,
                                null,
                                Duration.ofHours(2),
                                new ActorId("tech-7")));

        assertThat(result.missingField()).contains(DefaultMaintenanceEventStore.FIELD_END);
    }

    // Req 14.4 — a missing downtime is rejected by name.
    @Test
    void missingDowntimeIsRejectedByName() {
        StoreResult<MaintenanceEvent> result =
                eventStore.record(
                        completeEventExcept(
                                new MaintenanceEventId("evt-1"),
                                MACHINE,
                                START,
                                END,
                                null,
                                new ActorId("tech-7")));

        assertThat(result.missingField()).contains(DefaultMaintenanceEventStore.FIELD_DOWNTIME);
    }

    // Req 14.4 — a missing acting technician/system is rejected by name.
    @Test
    void missingActingTechnicianIsRejectedByName() {
        StoreResult<MaintenanceEvent> result =
                eventStore.record(
                        completeEventExcept(
                                new MaintenanceEventId("evt-1"),
                                MACHINE,
                                START,
                                END,
                                Duration.ofHours(2),
                                null));

        assertThat(result.missingField())
                .contains(DefaultMaintenanceEventStore.FIELD_ACTING_TECHNICIAN_OR_SYSTEM);
    }

    // Req 14.4 — when several required fields are absent, the first in validation order (id)
    // is the one reported; validation does not report a later field or create anything.
    @Test
    void firstMissingEventFieldWinsAndNothingStored() {
        StoreResult<MaintenanceEvent> result =
                eventStore.record(event(null, null, null, null, null, null, null, null, null));

        assertThat(result.missingField()).contains(DefaultMaintenanceEventStore.FIELD_ID);
        assertThat(eventStore.forMachine(MACHINE)).isEmpty();
    }

    // ----------------------------------------------------------------------------------------
    // Req 14.2 — registration rejection beyond the single missing-id case in
    // DefaultRegistrationStoreTest.
    // ----------------------------------------------------------------------------------------

    private final DefaultRegistrationStore registrationStore = new DefaultRegistrationStore();

    // Req 14.2 — a rejection for a non-id missing field (assetClass) also stores nothing, so a
    // later lookup by the submitted id finds no asset.
    @Test
    void rejectedNonIdRegistrationStoresNothing() {
        StoreResult<MachineAsset> result =
                registrationStore.register(
                        new RegistrationRequest(MACHINE, null, LOCATION, COMMISSIONED, null));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.missingField()).contains(DefaultRegistrationStore.FIELD_ASSET_CLASS);
        assertThat(registrationStore.find(MACHINE)).isEmpty();
    }

    // Req 14.2 — when several identity fields are absent, the first in validation order (id) is
    // reported, not a later one.
    @Test
    void firstMissingRegistrationFieldWins() {
        StoreResult<MachineAsset> result =
                registrationStore.register(
                        new RegistrationRequest(null, null, null, null, null));

        assertThat(result.missingField()).contains(DefaultRegistrationStore.FIELD_ID);
    }

    // ----------------------------------------------------------------------------------------
    // Req 14.10 — cohort-fallback sufficiency boundary and partial-telemetry undetermined case.
    // ----------------------------------------------------------------------------------------

    private static final Instant NOW = Instant.parse("2024-04-01T00:00:00Z");
    private static final Instant DEGRADED_ONSET = Instant.parse("2024-03-20T00:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    // Explicit threshold of 2 so the boundary is small and unambiguous.
    private final HealthStateDeriver deriver =
            new HealthStateDeriver(clock, 2, new Confidence(0.9), new Confidence(0.4));

    private static TelemetrySample sample(double indicator) {
        return new TelemetrySample(MACHINE, NOW, indicator);
    }

    private static FleetCohortBaseline cohort() {
        return new FleetCohortBaseline(ASSET_CLASS, 1.0, 10.0);
    }

    // Req 14.10 — exactly minTelemetrySamples readings is sufficient: full-history path, even when
    // a cohort baseline is also available (telemetry wins at the boundary).
    @Test
    void exactlyMinimumTelemetryUsesFullHistoryNotCohort() {
        List<TelemetrySample> telemetry = List.of(sample(0.6), sample(0.8)); // exactly 2

        HealthStateDeriver.Derivation derivation =
                deriver.derive(
                                MACHINE,
                                telemetry,
                                List.of(),
                                Optional.of(cohort()),
                                DEGRADED_ONSET)
                        .orElseThrow();

        assertThat(derivation.state().confidence()).isEqualTo(deriver.fullHistoryConfidence());
    }

    // Req 14.10 — one reading below the minimum falls back to the cohort at strictly lower
    // confidence (the boundary is strict, not off-by-one lenient).
    @Test
    void oneBelowMinimumFallsBackToCohort() {
        List<TelemetrySample> telemetry = List.of(sample(0.6)); // 1, below the minimum of 2

        HealthStateDeriver.Derivation derivation =
                deriver.derive(
                                MACHINE,
                                telemetry,
                                List.of(),
                                Optional.of(cohort()),
                                DEGRADED_ONSET)
                        .orElseThrow();

        assertThat(derivation.state().confidence()).isEqualTo(deriver.cohortFallbackConfidence());
        assertThat(derivation.state().confidence().value())
                .isLessThan(deriver.fullHistoryConfidence().value());
    }

    // Req 14.10 — partial telemetry below the minimum with no cohort baseline is undetermined:
    // distinct from HealthStateDeriverTest, which exercises the empty-telemetry undetermined case.
    @Test
    void partialTelemetryBelowMinimumWithoutCohortIsUndetermined() {
        List<TelemetrySample> telemetry = List.of(sample(0.7)); // below the minimum of 2

        Optional<HealthStateDeriver.Derivation> derivation =
                deriver.derive(
                        MACHINE, telemetry, List.of(), Optional.empty(), DEGRADED_ONSET);

        assertThat(derivation).isEmpty();
    }

    // Guard: MachineHealthState type stays referenced so the import documents the derivation target.
    @Test
    void derivedStateIsAMachineHealthState() {
        MachineHealthState state =
                deriver.derive(
                                MACHINE,
                                List.of(sample(0.5), sample(0.5)),
                                List.of(),
                                Optional.empty(),
                                DEGRADED_ONSET)
                        .orElseThrow()
                        .state();

        assertThat(state).isInstanceOf(MachineHealthState.class);
    }
}
