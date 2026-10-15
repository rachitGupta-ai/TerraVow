package com.terravow.domain.store;

// Feature: terravow, Property 40: Machine registration round-trips identity fields

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.AssetClass;
import com.terravow.domain.BaselineRef;
import com.terravow.domain.FacilityLocation;
import com.terravow.domain.MachineAsset;
import com.terravow.domain.MachineId;

import java.time.Instant;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for machine-registration round-trip (Property 40).
 *
 * <p>For any valid machine registration carrying the four required identity fields — a stable
 * identifier, an asset class, a facility location, and a commissioning date (plus an optional
 * commissioning-baseline link) — registering the submission and then fetching the resulting
 * {@link MachineAsset} by its {@link MachineId} yields an asset whose identity fields equal those
 * supplied at registration (Req 14.1).
 *
 * <p><strong>Validates: Requirements 14.1</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1</a>
 */
class MachineRegistrationRoundTripProperties {

    /**
     * Property 40: registering a valid submission then finding it round-trips every identity field
     * unchanged — the stored asset is created, is retrievable by its id, and each of id, asset
     * class, location, commissioning date, and the optional baseline link equals the submitted
     * value (Req 14.1).
     */
    @Property(tries = 100)
    void registrationRoundTripsIdentityFields(@ForAll("validRequests") RegistrationRequest request) {
        DefaultRegistrationStore store = new DefaultRegistrationStore();

        StoreResult<MachineAsset> result = store.register(request);

        assertThat(result.isCreated()).isTrue();
        MachineAsset created = result.created().orElseThrow();

        Optional<MachineAsset> found = store.find(request.id());
        assertThat(found).isPresent();
        MachineAsset asset = found.orElseThrow();

        // The fetched asset is the created asset, and both carry the submitted identity fields.
        assertThat(asset).isEqualTo(created);
        assertThat(asset.id()).isEqualTo(request.id());
        assertThat(asset.assetClass()).isEqualTo(request.assetClass());
        assertThat(asset.location()).isEqualTo(request.location());
        assertThat(asset.commissioningDate()).isEqualTo(request.commissioningDate());
        assertThat(asset.commissioningBaseline()).isEqualTo(request.commissioningBaselineOptional());
    }

    /** Arbitrary valid registrations: all four identity fields present; baseline optionally set. */
    @Provide
    Arbitrary<RegistrationRequest> validRequests() {
        Arbitrary<MachineId> ids = nonBlankStrings().map(MachineId::new);
        Arbitrary<AssetClass> assetClasses = nonBlankStrings().map(AssetClass::new);
        Arbitrary<FacilityLocation> locations = nonBlankStrings().map(FacilityLocation::new);
        Arbitrary<Instant> commissioningDates =
                Arbitraries.longs()
                        .between(0L, 4_102_444_800L) // epoch .. year ~2100 (seconds)
                        .map(Instant::ofEpochSecond);
        // Optional baseline: either absent (null) or a non-blank reference.
        Arbitrary<BaselineRef> baselines =
                Arbitraries.oneOf(
                        Arbitraries.just(null),
                        nonBlankStrings().map(BaselineRef::new));

        return Combinators.combine(ids, assetClasses, locations, commissioningDates, baselines)
                .as(RegistrationRequest::new);
    }

    /** Non-blank identifier strings: alphanumeric, length 1..32, so value objects accept them. */
    private static Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(32).filter(
                s -> !s.isBlank());
    }
}
