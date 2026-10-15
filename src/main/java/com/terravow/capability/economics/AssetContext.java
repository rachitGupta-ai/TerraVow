package com.terravow.capability.economics;

import com.terravow.domain.AssetClass;
import com.terravow.domain.Currency;
import com.terravow.domain.FacilityLocation;

import java.util.Objects;

/**
 * The asset context submitted to incentive assessment and instrument-conflict reporting (Req 6.3,
 * 6.4).
 *
 * <p>Incentive eligibility is asset- and location-specific: PAT/ESCerts applies to designated
 * consumers, Income Tax Act Section 32 depreciation depends on the asset, state industrial-policy
 * incentives depend on the facility's state, and avoided-EC depends on the violation at the asset.
 * This context carries the retrieval key for the {@link IncentivePort} and
 * {@link InstrumentConflictPort}: the asset class, the facility location, and the reporting
 * currency. All inputs are retained verbatim.
 *
 * @param assetClass the class of the asset under evaluation; never {@code null}
 * @param location   the facility location (its state selects state-policy incentives); never
 *                   {@code null}
 * @param currency   the currency incentive and net-position values are reported in; never
 *                   {@code null}
 *
 * @see EconomicsEngine
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.3, 6.4</a>
 */
public record AssetContext(AssetClass assetClass, FacilityLocation location, Currency currency) {

    /**
     * Validates presence of the asset class, location, and currency (Req 6.3, 6.4).
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public AssetContext {
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }
}
