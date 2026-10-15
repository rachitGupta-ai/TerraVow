package com.terravow.domain;

import java.util.Objects;

/**
 * A monetary amount expressed in integer minor units of a single declared {@link Currency}.
 *
 * <p>Minor-unit integer representation avoids floating-point rounding error in monetary
 * computation. ERD is reported as a single {@code Money} in one declared currency (Req 2.1), and
 * its component sum is performed over amounts in the same currency.
 *
 * @param minorUnits the amount in the currency's minor unit (e.g. paise for INR); may be negative
 * @param currency   the declared currency; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 2.1</a>
 */
public record Money(long minorUnits, Currency currency) {

    /**
     * Validates that a currency is present (Req 2.1 — a single declared currency).
     *
     * @throws NullPointerException if {@code currency} is {@code null}
     */
    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
    }

    /**
     * Returns a zero amount in the given currency.
     *
     * @param currency the declared currency
     * @return {@code Money} of zero minor units in {@code currency}
     */
    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    /**
     * Adds another amount in the same currency.
     *
     * @param other the amount to add; must be in the same currency
     * @return the summed amount
     * @throws IllegalArgumentException if {@code other} is in a different currency
     */
    public Money plus(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "Cannot add Money of differing currencies: " + currency + " + " + other.currency);
        }
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }
}
