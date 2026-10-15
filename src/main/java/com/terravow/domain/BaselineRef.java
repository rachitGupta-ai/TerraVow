package com.terravow.domain;

import java.util.Objects;

/**
 * A resolvable reference from a {@link MachineAsset} to its {@link CommissioningBaseline}.
 *
 * <p>A Machine_Asset links to its Commissioning_Baseline when one is known (Req 14.1); the link is
 * optional on the asset because the baseline may be absent, in which case EID falls back to a
 * {@link FleetCohortBaseline} at strictly lower confidence (Req 1.5, 14.10). The reference is an
 * opaque, non-blank identifier resolved to the stored baseline by the domain stores.
 *
 * @param id a non-blank identifier of the referenced Commissioning_Baseline
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1</a>
 */
public record BaselineRef(String id) {

    /**
     * Validates that the reference identifier is present and non-blank (Req 14.1).
     *
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public BaselineRef {
        Objects.requireNonNull(id, "BaselineRef id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("BaselineRef id must not be blank");
        }
    }
}
