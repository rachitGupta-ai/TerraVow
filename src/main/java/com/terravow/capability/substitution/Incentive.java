package com.terravow.capability.substitution;

import com.terravow.domain.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * A single applicable incentive reported for a substitution {@link Candidate}.
 *
 * <p>Candidates report their applicable incentives among the eight reported values (Req 5.2). An
 * incentive names the instrument (e.g. a PAT/ESCerts entitlement or a tax-depreciation benefit) and
 * carries its estimated monetary value when quantifiable. When the available data is insufficient to
 * quantify the value, {@code estimatedValue} is {@link Optional#empty()} and the enclosing
 * candidate marks the {@link DossierField#INCENTIVES} field an unquantified hypothesis (Req 5.6).
 *
 * @param instrument     the non-blank name of the incentive instrument; never {@code null}
 * @param estimatedValue the estimated monetary value, empty when not quantifiable; never
 *                       {@code null}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.2, 5.6</a>
 */
public record Incentive(String instrument, Optional<Money> estimatedValue) {

    /**
     * Validates the instrument name is present and non-blank and the estimated value is present
     * (possibly empty) (Req 5.2).
     *
     * @throws NullPointerException     if {@code instrument} or {@code estimatedValue} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code instrument} is blank
     */
    public Incentive {
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(estimatedValue, "estimatedValue must not be null");
        if (instrument.isBlank()) {
            throw new IllegalArgumentException("Incentive instrument must not be blank");
        }
    }

    /**
     * Creates an incentive with a quantified monetary value.
     *
     * @param instrument the non-blank instrument name
     * @param value      the estimated monetary value; never {@code null}
     * @return a quantified incentive
     */
    public static Incentive quantified(String instrument, Money value) {
        return new Incentive(instrument, Optional.of(Objects.requireNonNull(value, "value")));
    }

    /**
     * Creates an incentive whose monetary value could not be quantified from the available data
     * (Req 5.6).
     *
     * @param instrument the non-blank instrument name
     * @return an incentive with an empty estimated value
     */
    public static Incentive unquantified(String instrument) {
        return new Incentive(instrument, Optional.empty());
    }
}
