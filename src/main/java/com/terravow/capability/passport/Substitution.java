package com.terravow.capability.passport;

import java.util.Objects;

/**
 * A substitution applied at one supply-chain tier, carrying the fractional change it imposes on that
 * tier's delivery requirement (Req 7.7).
 *
 * <p>When a substitution is applied at a tier, TerraVow computes the supply-chain ripple on the
 * delivery requirements of each connected tier (Req 7.7). The {@code deliveryRequirementDelta} is the
 * signed fractional change in delivery requirement the substitution causes at the applied tier
 * (e.g. {@code 0.10} for a 10&nbsp;percent increase, {@code -0.05} for a 5&nbsp;percent reduction);
 * the ripple propagates a deterministically attenuated change to each connected tier.
 *
 * @param label                    a non-blank description of the substituted input/material
 * @param deliveryRequirementDelta the signed fractional delivery-requirement change at the applied
 *                                 tier; finite
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.7</a>
 */
public record Substitution(String label, double deliveryRequirementDelta) {

    /**
     * Validates a non-blank label and a finite delta (Req 7.7).
     *
     * @throws NullPointerException     if {@code label} is {@code null}
     * @throws IllegalArgumentException if {@code label} is blank or {@code deliveryRequirementDelta}
     *                                  is non-finite
     */
    public Substitution {
        Objects.requireNonNull(label, "label must not be null");
        if (label.isBlank()) {
            throw new IllegalArgumentException("Substitution label must not be blank");
        }
        if (Double.isNaN(deliveryRequirementDelta) || Double.isInfinite(deliveryRequirementDelta)) {
            throw new IllegalArgumentException(
                    "deliveryRequirementDelta must be a finite number, got: "
                            + deliveryRequirementDelta);
        }
    }
}
