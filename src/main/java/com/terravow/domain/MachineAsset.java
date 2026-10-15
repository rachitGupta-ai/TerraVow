package com.terravow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A tracked machine registered in TerraVow — the unit to which maintenance events, job runs, and
 * health state are attributed.
 *
 * <p>A Machine_Asset carries a stable identifier, an asset class, a facility location, a
 * commissioning date, and a link to its {@link CommissioningBaseline} when one is known (Req 14.1).
 * The commissioning-baseline link is {@link Optional} because a baseline may be absent, in which
 * case EID falls back to a {@link FleetCohortBaseline} at strictly lower confidence (Req 1.5,
 * 14.10). The four identity fields are mandatory; this record enforces their presence structurally,
 * while the registration store additionally rejects registrations missing them, recording the
 * missing field and creating nothing (Req 14.2 — implemented by the store in task 2.2).
 *
 * @param id                  the stable machine identifier; never {@code null}
 * @param assetClass          the asset class; never {@code null}
 * @param location            the facility location; never {@code null}
 * @param commissioningDate   the commissioning date; never {@code null}
 * @param commissioningBaseline a link to the Commissioning_Baseline when known; never {@code null},
 *                              may be {@link Optional#empty()}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1, 14.2</a>
 */
public record MachineAsset(
        MachineId id,
        AssetClass assetClass,
        FacilityLocation location,
        Instant commissioningDate,
        Optional<BaselineRef> commissioningBaseline) {

    /**
     * Validates that all required identity fields are present (Req 14.1). The optional
     * commissioning-baseline reference itself must be a non-null {@link Optional} (it may be empty).
     *
     * @throws NullPointerException if any required field, or the {@code commissioningBaseline}
     *                              optional wrapper, is {@code null}
     */
    public MachineAsset {
        Objects.requireNonNull(id, "MachineAsset id must not be null");
        Objects.requireNonNull(assetClass, "MachineAsset assetClass must not be null");
        Objects.requireNonNull(location, "MachineAsset location must not be null");
        Objects.requireNonNull(commissioningDate, "MachineAsset commissioningDate must not be null");
        Objects.requireNonNull(
                commissioningBaseline, "MachineAsset commissioningBaseline must not be null");
    }
}
