package com.terravow.capability.passport;

import com.terravow.domain.BatchId;

import java.util.Objects;

/**
 * One input batch consumed by a downstream tier, with the quantity consumed and that batch's
 * Passport (Req 7.2).
 *
 * <p>Downstream propagation adds each consumed input's embedded footprint to the resulting
 * downstream batch's Passport <em>in proportion to the quantity of that input consumed</em>
 * (Req 7.2). The proportion for an input is its {@code quantity} divided by the total quantity
 * consumed across all inputs, so the downstream footprint is the quantity-weighted mean of the input
 * footprints.
 *
 * @param batch    the identifier of the consumed input batch; never {@code null}
 * @param quantity the quantity of this input batch consumed; finite and strictly positive
 * @param passport the Embedded Pollution Passport of the consumed input batch; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.2</a>
 */
public record ConsumedInput(BatchId batch, double quantity, Passport passport) {

    /**
     * Validates presence of the batch and Passport and a finite, strictly-positive quantity
     * (Req 7.2). A zero or negative quantity is rejected because a consumed input must contribute a
     * positive proportion.
     *
     * @throws NullPointerException     if {@code batch} or {@code passport} is {@code null}
     * @throws IllegalArgumentException if {@code quantity} is non-finite or not strictly positive
     */
    public ConsumedInput {
        Objects.requireNonNull(batch, "batch must not be null");
        Objects.requireNonNull(passport, "passport must not be null");
        if (Double.isNaN(quantity) || Double.isInfinite(quantity)) {
            throw new IllegalArgumentException(
                    "quantity must be a finite number, got: " + quantity);
        }
        if (quantity <= 0.0) {
            throw new IllegalArgumentException(
                    "quantity must be strictly positive, got: " + quantity);
        }
    }
}
