package com.terravow.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.AssetClass;
import com.terravow.domain.BaselineRef;
import com.terravow.domain.FacilityLocation;
import com.terravow.domain.MachineAsset;
import com.terravow.domain.MachineId;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultRegistrationStore}: registrations with the full identity field set
 * are created and stored, and registrations missing a required identity field are rejected with the
 * missing field recorded and nothing created (Req 14.1, 14.2).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1, 14.2</a>
 */
class DefaultRegistrationStoreTest {

    private final DefaultRegistrationStore store = new DefaultRegistrationStore();

    private static final MachineId MACHINE = new MachineId("machine-1");
    private static final AssetClass ASSET_CLASS = new AssetClass("press");
    private static final FacilityLocation LOCATION = new FacilityLocation("pune-plant");
    private static final Instant COMMISSIONED = Instant.parse("2020-01-01T00:00:00Z");

    private static RegistrationRequest complete() {
        return new RegistrationRequest(MACHINE, ASSET_CLASS, LOCATION, COMMISSIONED, null);
    }

    // Req 14.1 — a complete registration creates and stores a Machine_Asset with its full field set.
    @Test
    void completeRegistrationCreatesAndStoresAsset() {
        StoreResult<MachineAsset> result = store.register(complete());

        assertThat(result.isCreated()).isTrue();
        MachineAsset asset = result.created().orElseThrow();
        assertThat(asset.id()).isEqualTo(MACHINE);
        assertThat(asset.assetClass()).isEqualTo(ASSET_CLASS);
        assertThat(asset.location()).isEqualTo(LOCATION);
        assertThat(asset.commissioningDate()).isEqualTo(COMMISSIONED);
        assertThat(asset.commissioningBaseline()).isEmpty();
        assertThat(store.find(MACHINE)).contains(asset);
    }

    // Req 14.1 — an optional commissioning-baseline link is stored when supplied.
    @Test
    void optionalCommissioningBaselineIsStored() {
        BaselineRef ref = new BaselineRef("baseline-1");
        StoreResult<MachineAsset> result =
                store.register(
                        new RegistrationRequest(
                                MACHINE, ASSET_CLASS, LOCATION, COMMISSIONED, ref));

        assertThat(result.created().orElseThrow().commissioningBaseline()).contains(ref);
    }

    // Req 14.2 — a missing identifier is rejected, the field recorded, and nothing created.
    @Test
    void missingIdIsRejectedAndNothingCreated() {
        StoreResult<MachineAsset> result =
                store.register(
                        new RegistrationRequest(
                                null, ASSET_CLASS, LOCATION, COMMISSIONED, null));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.missingField()).contains(DefaultRegistrationStore.FIELD_ID);
        assertThat(result.created()).isEmpty();
    }

    // Req 14.2 — each required identity field, when absent, is reported by name.
    @Test
    void missingAssetClassIsRejectedByName() {
        StoreResult<MachineAsset> result =
                store.register(
                        new RegistrationRequest(MACHINE, null, LOCATION, COMMISSIONED, null));

        assertThat(result.missingField()).contains(DefaultRegistrationStore.FIELD_ASSET_CLASS);
    }

    @Test
    void missingLocationIsRejectedByName() {
        StoreResult<MachineAsset> result =
                store.register(
                        new RegistrationRequest(MACHINE, ASSET_CLASS, null, COMMISSIONED, null));

        assertThat(result.missingField()).contains(DefaultRegistrationStore.FIELD_LOCATION);
    }

    @Test
    void missingCommissioningDateIsRejectedByName() {
        StoreResult<MachineAsset> result =
                store.register(
                        new RegistrationRequest(MACHINE, ASSET_CLASS, LOCATION, null, null));

        assertThat(result.missingField())
                .contains(DefaultRegistrationStore.FIELD_COMMISSIONING_DATE);
    }

    // Req 14.2 — a rejected registration stores nothing, so a later lookup finds no asset.
    @Test
    void rejectedRegistrationStoresNothing() {
        store.register(new RegistrationRequest(null, ASSET_CLASS, LOCATION, COMMISSIONED, null));

        assertThat(store.find(MACHINE)).isEmpty();
    }

    // Lookup of an unregistered machine yields empty.
    @Test
    void findUnknownMachineIsEmpty() {
        assertThat(store.find(new MachineId("absent"))).isEqualTo(Optional.empty());
    }
}
