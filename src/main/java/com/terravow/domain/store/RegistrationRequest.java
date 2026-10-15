package com.terravow.domain.store;

import com.terravow.domain.AssetClass;
import com.terravow.domain.BaselineRef;
import com.terravow.domain.FacilityLocation;
import com.terravow.domain.MachineAsset;
import com.terravow.domain.MachineId;

import java.time.Instant;
import java.util.Optional;

/**
 * A raw, possibly-incomplete machine-registration submission validated by the
 * {@link RegistrationStore}.
 *
 * <p>Unlike {@link MachineAsset} — whose compact constructor rejects any {@code null} required
 * field — this submission permits {@code null} identity fields so the store can detect a missing
 * required field, record it, and create no Machine_Asset (Req 14.2). The four identity fields are
 * required: {@code id}, {@code assetClass}, {@code location}, {@code commissioningDate}. The
 * commissioning-baseline link is optional (Req 14.1) and defaults to {@link Optional#empty()} when
 * omitted.
 *
 * <p>This is an inert data carrier: it performs no validation itself (that is the store's job) and
 * holds no behaviour, so construction never throws on a missing field.
 *
 * @param id                    the stable machine identifier; may be {@code null} (missing)
 * @param assetClass            the asset class; may be {@code null} (missing)
 * @param location              the facility location; may be {@code null} (missing)
 * @param commissioningDate     the commissioning date; may be {@code null} (missing)
 * @param commissioningBaseline a link to the Commissioning_Baseline when known; may be {@code null},
 *                              treated as absent
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1, 14.2</a>
 */
public record RegistrationRequest(
        MachineId id,
        AssetClass assetClass,
        FacilityLocation location,
        Instant commissioningDate,
        BaselineRef commissioningBaseline) {

    /**
     * The optional commissioning-baseline link as an {@link Optional}, never {@code null}.
     *
     * @return the baseline reference wrapped in an {@link Optional}, empty when absent
     */
    public Optional<BaselineRef> commissioningBaselineOptional() {
        return Optional.ofNullable(commissioningBaseline);
    }
}
