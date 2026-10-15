package com.terravow.domain.store;

import com.terravow.domain.MachineAsset;
import com.terravow.domain.MachineId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RegistrationStore} that validates required identity fields before creating a
 * {@link MachineAsset} (Req 14.1, 14.2).
 *
 * <p>Validation order is fixed — {@code id}, {@code assetClass}, {@code location},
 * {@code commissioningDate} — so the <em>first</em> missing field is reported deterministically for
 * a given submission. When all four are present the asset is constructed (the record's own compact
 * constructor is a redundant structural backstop) and stored under its {@link MachineId}; a later
 * registration under the same id overwrites the stored asset. When a required field is missing,
 * nothing is created and the missing field is recorded in the returned {@link StoreResult}.
 *
 * <p>Persistence is a {@link ConcurrentHashMap} for now; the Firestore persistence split is task
 * 23. The map makes concurrent registrations from multiple callers safe. No wall-clock time or
 * randomness is read, so validation of a given submission is deterministic.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1, 14.2,
 *      14.14</a>
 */
public final class DefaultRegistrationStore implements RegistrationStore {

    /** Field name recorded when the machine identifier is missing (Req 14.2). */
    static final String FIELD_ID = "id";

    /** Field name recorded when the asset class is missing (Req 14.2). */
    static final String FIELD_ASSET_CLASS = "assetClass";

    /** Field name recorded when the facility location is missing (Req 14.2). */
    static final String FIELD_LOCATION = "location";

    /** Field name recorded when the commissioning date is missing (Req 14.2). */
    static final String FIELD_COMMISSIONING_DATE = "commissioningDate";

    private final Map<MachineId, MachineAsset> assets = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     *
     * <p>Checks the four identity fields in a fixed order and returns a rejection naming the first
     * absent one (Req 14.2); otherwise constructs and stores the asset (Req 14.1).
     */
    @Override
    public StoreResult<MachineAsset> register(RegistrationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (request.id() == null) {
            return StoreResult.rejected(FIELD_ID);
        }
        if (request.assetClass() == null) {
            return StoreResult.rejected(FIELD_ASSET_CLASS);
        }
        if (request.location() == null) {
            return StoreResult.rejected(FIELD_LOCATION);
        }
        if (request.commissioningDate() == null) {
            return StoreResult.rejected(FIELD_COMMISSIONING_DATE);
        }

        MachineAsset asset =
                new MachineAsset(
                        request.id(),
                        request.assetClass(),
                        request.location(),
                        request.commissioningDate(),
                        request.commissioningBaselineOptional());
        assets.put(asset.id(), asset);
        return StoreResult.created(asset);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<MachineAsset> find(MachineId id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(assets.get(id));
    }
}
