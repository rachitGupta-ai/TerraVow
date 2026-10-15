package com.terravow.capability.substitution;

import java.util.List;
import java.util.Objects;

/**
 * The supply-chain ripple reported for a substitution {@link Candidate}.
 *
 * <p>Ripple is one of the eight reported values for each candidate (Req 5.2): the set of effects the
 * substitution would have on connected supply-chain tiers. Each effect is a human-readable
 * description of the change to a connected tier's delivery requirements. The list is defensively
 * copied into an immutable list and may be empty — an empty ripple is a valid quantified value
 * meaning "no connected tier is affected". When the ripple cannot be assessed from the available
 * data, the enclosing candidate marks the {@link DossierField#RIPPLE} field an unquantified
 * hypothesis with a recorded reason (Req 5.6).
 *
 * @param tierEffects the per-tier delivery-requirement changes; never {@code null}, defensively
 *                   copied, may be empty
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.2, 5.6</a>
 */
public record SupplyChainRipple(List<String> tierEffects) {

    /**
     * Validates and defensively copies the per-tier effects into an immutable list (Req 5.2). A
     * {@code null} element is rejected.
     *
     * @throws NullPointerException if {@code tierEffects} is {@code null} or contains a {@code null}
     *                              element
     */
    public SupplyChainRipple {
        Objects.requireNonNull(tierEffects, "tierEffects must not be null");
        tierEffects = List.copyOf(tierEffects);
    }

    /** A ripple with no affected connected tiers. */
    public static final SupplyChainRipple NONE = new SupplyChainRipple(List.of());
}
