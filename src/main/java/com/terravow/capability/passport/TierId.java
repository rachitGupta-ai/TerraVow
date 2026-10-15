package com.terravow.capability.passport;

import java.util.Objects;

/**
 * The stable identifier of a supply-chain tier (a node in the dependency graph).
 *
 * <p>Cascade-risk propagation (Req 7.6) and substitution ripple (Req 7.7) both reason over a
 * supply-chain graph whose nodes are tiers. Modelling a tier reference as a dedicated value type
 * keeps the graph keys type-safe and distinct from batch and facility identifiers.
 *
 * @param value a non-blank, stable tier identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.6, 7.7</a>
 */
public record TierId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 7.6, 7.7).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public TierId {
        Objects.requireNonNull(value, "TierId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TierId value must not be blank");
        }
    }
}
