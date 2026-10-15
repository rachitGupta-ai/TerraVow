package com.terravow.domain;

import java.util.Objects;

/**
 * The class of a {@link MachineAsset} — the grouping that a {@link FleetCohortBaseline} is keyed by.
 *
 * <p>A Machine_Asset carries a required asset class (Req 14.1). When a machine's
 * Commissioning_Baseline is absent, the Fleet_Cohort_Baseline for comparable machines of the same
 * asset class supplies a substitute reference profile at strictly lower confidence (Req 14.10).
 * Modelling the asset class as a value type keeps that cohort key explicit.
 *
 * @param value a non-blank asset-class label (e.g. a machine family or model group)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1, 14.10</a>
 */
public record AssetClass(String value) {

    /**
     * Validates that the asset-class label is present and non-blank (Req 14.1).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public AssetClass {
        Objects.requireNonNull(value, "AssetClass value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AssetClass value must not be blank");
        }
    }
}
