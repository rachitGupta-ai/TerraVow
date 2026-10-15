package com.terravow.capability.lifecycle;

import java.util.Objects;
import java.util.Optional;

/**
 * The AEI weighting applied to the forecast-pollution-cost term of an {@link EmissionsAwarePriority}
 * where a Receptor_Map and a meteorological forecast are available for the asset (Req 15.4).
 *
 * <p>Req 15.4 is a {@code WHERE} clause: the pollution-cost term is weighted by the AEI of the
 * forecast emissions <em>only when</em> both a Receptor_Map and a meteorological forecast exist for
 * the asset; otherwise the term is used unweighted. This value models exactly that choice:
 * {@link #none()} is the unweighted case (multiplier 1.0, no AEI inputs), and
 * {@link #ofAei(double)} is the weighted case carrying the AEI of the forecast emissions.
 *
 * <p>The multiplier is <em>non-decreasing</em> in the AEI: a higher avoidable-exposure harm yields a
 * multiplier at least as large, so increasing the AEI never decreases the resulting priority
 * (Property 47, Req 15.4). The mapping is deterministic and monotone — {@code multiplier = 1 + aei}
 * for a non-negative AEI — and the AEI itself (in person-µg/m³-hours) is retained so the ranking's
 * Evidence_Chain can record the weighting basis (Req 15.5).
 *
 * @param applied    whether AEI weighting was applied (a Receptor_Map and met forecast were present)
 * @param aei        the AEI of the forecast emissions used for weighting; present iff {@code applied}
 * @param multiplier the non-negative, monotone-in-AEI weight applied to the pollution-cost term;
 *                   {@code 1.0} when not applied
 *
 * @see EmissionsAwarePriority
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.4, 15.5</a>
 */
public record AeiWeighting(boolean applied, Optional<Double> aei, double multiplier) {

    /**
     * Validates the applied/AEI consistency and the finite, non-negative, monotone multiplier
     * (Req 15.4).
     *
     * @throws NullPointerException     if {@code aei} is {@code null}
     * @throws IllegalArgumentException if the AEI presence does not match {@code applied}, or the
     *                                  AEI or multiplier is non-finite or negative, or the
     *                                  multiplier contradicts the monotone mapping
     */
    public AeiWeighting {
        Objects.requireNonNull(aei, "aei must not be null");
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "multiplier must be a finite, non-negative number, got: " + multiplier);
        }
        if (applied) {
            if (aei.isEmpty()) {
                throw new IllegalArgumentException(
                        "an applied AEI weighting must carry the AEI value (Req 15.4)");
            }
            double value = aei.get();
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        "AEI must be a finite, non-negative number, got: " + value);
            }
            if (multiplier != expectedMultiplier(value)) {
                throw new IllegalArgumentException(
                        "multiplier must equal the monotone AEI mapping " + expectedMultiplier(value)
                                + ", got: " + multiplier);
            }
        } else {
            if (aei.isPresent()) {
                throw new IllegalArgumentException(
                        "an unapplied AEI weighting must not carry an AEI value");
            }
            if (multiplier != 1.0) {
                throw new IllegalArgumentException(
                        "an unapplied AEI weighting must have multiplier 1.0, got: " + multiplier);
            }
        }
    }

    /**
     * The deterministic, monotone-non-decreasing weight for a non-negative AEI: {@code 1 + aei}.
     *
     * <p>Holding the base pollution cost fixed, increasing the AEI increases the multiplier, so the
     * weighted term — and the priority — never decreases (Property 47).
     */
    private static double expectedMultiplier(double aei) {
        return 1.0 + aei;
    }

    /**
     * The unweighted case: no Receptor_Map or met forecast, so the pollution-cost term is used
     * as-is (Req 15.4).
     *
     * @return an unapplied weighting with multiplier {@code 1.0}
     */
    public static AeiWeighting none() {
        return new AeiWeighting(false, Optional.empty(), 1.0);
    }

    /**
     * The weighted case: a Receptor_Map and met forecast are present, so the pollution-cost term is
     * weighted by the AEI of the forecast emissions (Req 15.4).
     *
     * @param aei the AEI of the forecast emissions in person-µg/m³-hours; must be finite and
     *            {@code >= 0}
     * @return an applied weighting whose multiplier is non-decreasing in {@code aei}
     * @throws IllegalArgumentException if {@code aei} is non-finite or negative
     */
    public static AeiWeighting ofAei(double aei) {
        if (Double.isNaN(aei) || Double.isInfinite(aei) || aei < 0.0) {
            throw new IllegalArgumentException(
                    "AEI must be a finite, non-negative number, got: " + aei);
        }
        return new AeiWeighting(true, Optional.of(aei), expectedMultiplier(aei));
    }
}
