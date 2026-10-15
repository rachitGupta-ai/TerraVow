package com.terravow.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.ActorId;
import com.terravow.domain.Component;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEvent;
import com.terravow.domain.MaintenanceEventId;
import com.terravow.domain.MaintenanceType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultMaintenanceEventStore}: a complete event stores its full field set,
 * and an event missing a required field (including a blank action) is rejected with the missing
 * field recorded and nothing created (Req 14.3, 14.4).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.3, 14.4</a>
 */
class DefaultMaintenanceEventStoreTest {

    private final DefaultMaintenanceEventStore store = new DefaultMaintenanceEventStore();

    private static final MachineId MACHINE = new MachineId("machine-1");
    private static final Instant START = Instant.parse("2024-01-01T08:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T10:00:00Z");

    private static MaintenanceEventSubmission complete() {
        return new MaintenanceEventSubmission(
                new MaintenanceEventId("evt-1"),
                MACHINE,
                MaintenanceType.CORRECTIVE,
                START,
                END,
                "replaced bearing",
                List.of(new Component("bearing-A")),
                Duration.ofHours(2),
                new ActorId("tech-7"));
    }

    // Req 14.3 — a complete event is created storing the full field set.
    @Test
    void completeEventStoresFullFieldSet() {
        StoreResult<MaintenanceEvent> result = store.record(complete());

        assertThat(result.isCreated()).isTrue();
        MaintenanceEvent event = result.created().orElseThrow();
        assertThat(event.id()).isEqualTo(new MaintenanceEventId("evt-1"));
        assertThat(event.type()).isEqualTo(MaintenanceType.CORRECTIVE);
        assertThat(event.start()).isEqualTo(START);
        assertThat(event.end()).isEqualTo(END);
        assertThat(event.action()).isEqualTo("replaced bearing");
        assertThat(event.componentsReplaced()).containsExactly(new Component("bearing-A"));
        assertThat(event.downtime()).isEqualTo(Duration.ofHours(2));
        assertThat(event.actingTechnicianOrSystem()).isEqualTo(new ActorId("tech-7"));
        assertThat(store.forMachine(MACHINE)).containsExactly(event);
    }

    // Req 14.3 — an empty components list is valid (no components replaced).
    @Test
    void emptyComponentsListIsAccepted() {
        StoreResult<MaintenanceEvent> result =
                store.record(
                        new MaintenanceEventSubmission(
                                new MaintenanceEventId("evt-2"),
                                MACHINE,
                                MaintenanceType.PREVENTIVE,
                                START,
                                END,
                                "inspection",
                                List.of(),
                                Duration.ofMinutes(30),
                                new ActorId("system")));

        assertThat(result.isCreated()).isTrue();
        assertThat(result.created().orElseThrow().componentsReplaced()).isEmpty();
    }

    // Req 14.4 — a missing required field is rejected with the field recorded; nothing created.
    @Test
    void missingTypeIsRejected() {
        StoreResult<MaintenanceEvent> result =
                store.record(
                        new MaintenanceEventSubmission(
                                new MaintenanceEventId("evt-3"),
                                MACHINE,
                                null,
                                START,
                                END,
                                "fix",
                                List.of(),
                                Duration.ZERO,
                                new ActorId("tech-7")));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.missingField()).contains(DefaultMaintenanceEventStore.FIELD_TYPE);
        assertThat(store.forMachine(MACHINE)).isEmpty();
    }

    // Req 14.4 — a blank action counts as missing.
    @Test
    void blankActionIsRejectedAsMissing() {
        StoreResult<MaintenanceEvent> result =
                store.record(
                        new MaintenanceEventSubmission(
                                new MaintenanceEventId("evt-4"),
                                MACHINE,
                                MaintenanceType.PREVENTIVE,
                                START,
                                END,
                                "   ",
                                List.of(),
                                Duration.ZERO,
                                new ActorId("tech-7")));

        assertThat(result.missingField()).contains(DefaultMaintenanceEventStore.FIELD_ACTION);
    }

    // Req 14.4 — a null components list is rejected (distinct from an empty one).
    @Test
    void nullComponentsListIsRejected() {
        StoreResult<MaintenanceEvent> result =
                store.record(
                        new MaintenanceEventSubmission(
                                new MaintenanceEventId("evt-5"),
                                MACHINE,
                                MaintenanceType.PREVENTIVE,
                                START,
                                END,
                                "inspection",
                                null,
                                Duration.ZERO,
                                new ActorId("tech-7")));

        assertThat(result.missingField())
                .contains(DefaultMaintenanceEventStore.FIELD_COMPONENTS_REPLACED);
    }

    // Events for an unknown machine yield an empty list.
    @Test
    void forUnknownMachineIsEmpty() {
        assertThat(store.forMachine(new MachineId("absent"))).isEmpty();
    }
}
