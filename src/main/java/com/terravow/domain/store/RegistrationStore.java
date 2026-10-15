package com.terravow.domain.store;

import com.terravow.domain.MachineAsset;
import com.terravow.domain.MachineId;

import java.util.Optional;

/**
 * The store of registered {@link MachineAsset}s, validating registrations at the ingestion
 * boundary.
 *
 * <p>Registration requires the four identity fields — a stable identifier, an asset class, a
 * facility location, and a commissioning date — plus an optional Commissioning_Baseline link (Req
 * 14.1). A registration missing a required identity field is rejected: the missing field is
 * recorded and no Machine_Asset is created (Req 14.2). The outcome is reported as a
 * {@link StoreResult} rather than a thrown exception so an ingestion run can record the failure and
 * continue with the remaining records (Req 14.14).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1, 14.2</a>
 */
public interface RegistrationStore {

    /**
     * Validates and, if complete, stores a machine registration (Req 14.1, 14.2).
     *
     * <p>If every required identity field is present, a {@link MachineAsset} is constructed, stored
     * under its {@link MachineId}, and returned in a {@link StoreResult.Status#CREATED} result. If a
     * required field is missing, nothing is created and a {@link StoreResult.Status#REJECTED} result
     * naming the first missing field is returned.
     *
     * @param request the registration submission; never {@code null}
     * @return a created-or-rejected result
     * @throws NullPointerException if {@code request} is {@code null}
     */
    StoreResult<MachineAsset> register(RegistrationRequest request);

    /**
     * Looks up a stored Machine_Asset by identifier.
     *
     * @param id the machine identifier; never {@code null}
     * @return the stored asset, or {@link Optional#empty()} if none is registered under {@code id}
     * @throws NullPointerException if {@code id} is {@code null}
     */
    Optional<MachineAsset> find(MachineId id);
}
