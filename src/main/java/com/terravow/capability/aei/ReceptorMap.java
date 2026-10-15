package com.terravow.capability.aei;

import com.terravow.domain.FacilityLocation;

import java.util.List;
import java.util.Objects;

/**
 * A facility's geographic map of downwind sensitive receptors (Req 4.1, 4.7).
 *
 * <p>The Receptor_Map is one of the two inputs the AEI forecast requires: when it and a
 * meteorological forecast are both present for a facility, an assimilative-capacity forecast is
 * computed (Req 4.1); when either is absent, the AEI is undetermined with a recorded reason
 * (Req 4.2). The map's receptors are reported as the affected receptors on every AEI result
 * (Req 4.7). The receptor list is defensively copied into an unmodifiable list.
 *
 * @param facility  the facility this map covers; never {@code null}
 * @param receptors the downwind receptors; never {@code null}, defensively copied, may be empty
 *
 * @see Receptor
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.1, 4.2, 4.7</a>
 */
public record ReceptorMap(FacilityLocation facility, List<Receptor> receptors) {

    /**
     * Validates presence and defensively copies the receptor list (Req 4.7).
     *
     * @throws NullPointerException if {@code facility} or {@code receptors} is {@code null}, or
     *                              {@code receptors} contains a {@code null} element
     */
    public ReceptorMap {
        Objects.requireNonNull(facility, "ReceptorMap facility must not be null");
        Objects.requireNonNull(receptors, "ReceptorMap receptors must not be null");
        receptors = List.copyOf(receptors);
    }

    /**
     * The sum of all receptor population weights — the total sensitivity-weighted population the
     * facility's emissions can expose.
     *
     * @return the total population weight, {@code >= 0}
     */
    public double totalPopulationWeight() {
        double total = 0.0;
        for (Receptor receptor : receptors) {
            total += receptor.populationWeight();
        }
        return total;
    }
}
