package com.terravow.capability.aei;

import java.util.Objects;

/**
 * A downwind sensitive receptor drawn from a facility {@link ReceptorMap} (Req 4.7).
 *
 * <p>A receptor is a population-bearing location — a school, hospital, or residential cluster —
 * whose exposure to an emission the AEI weights. The {@code populationWeight} expresses the
 * sensitivity-weighted population at that receptor: a larger weight means harm accrued there counts
 * for more in the person-µg/m³-hours total. When an AEI result is produced, the affected receptors
 * are reported from the Receptor_Map (Req 4.7).
 *
 * @param id               a non-blank receptor identifier
 * @param populationWeight the sensitivity-weighted population; finite and {@code >= 0}
 *
 * @see ReceptorMap
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.7</a>
 */
public record Receptor(String id, double populationWeight) {

    /**
     * Validates the receptor identity and weight (Req 4.7).
     *
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank or {@code populationWeight} is
     *                                  non-finite or negative
     */
    public Receptor {
        Objects.requireNonNull(id, "Receptor id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Receptor id must not be blank");
        }
        if (Double.isNaN(populationWeight) || Double.isInfinite(populationWeight)) {
            throw new IllegalArgumentException(
                    "Receptor populationWeight must be finite, got: " + populationWeight);
        }
        if (populationWeight < 0.0) {
            throw new IllegalArgumentException(
                    "Receptor populationWeight must be non-negative, got: " + populationWeight);
        }
    }
}
