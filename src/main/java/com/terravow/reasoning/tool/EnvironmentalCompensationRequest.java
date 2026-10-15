package com.terravow.reasoning.tool;

import com.terravow.domain.Currency;

/**
 * Input arguments for the Environmental_Compensation (EC) formula tool (Req 6.2).
 *
 * <p>The documented EC formula shape is {@code base rate × days of violation × deterrence factor}.
 * The base rate is expressed in integer minor units of a declared {@link Currency} (consistent with
 * {@link com.terravow.domain.Money}); the result is {@code Money} in the same currency.
 *
 * <p>This record is a plain argument carrier and intentionally does not validate: the tool itself
 * ({@link EnvironmentalCompensationTool}) performs all input validation so that bad inputs surface
 * as an {@link com.terravow.domain.DeterminationStatus#UNAVAILABLE} tool result rather than an
 * exception at construction.
 *
 * @param baseRateMinorUnits the base rate in the currency's minor unit; expected {@code >= 0}
 * @param currency           the currency the base rate (and result) is expressed in; may be
 *                           {@code null}, surfaced as unavailable by the tool
 * @param daysOfViolation    the number of days of violation; expected {@code >= 0}
 * @param deterrenceFactor   the deterrence factor multiplier; expected finite and {@code >= 0}
 *
 * @see EnvironmentalCompensationTool
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.2</a>
 */
public record EnvironmentalCompensationRequest(
        long baseRateMinorUnits,
        Currency currency,
        long daysOfViolation,
        double deterrenceFactor) {
}
