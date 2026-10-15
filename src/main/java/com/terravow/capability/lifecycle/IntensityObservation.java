package com.terravow.capability.lifecycle;

import com.terravow.domain.AssetClass;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEventId;
import com.terravow.evidence.EvidenceLink;

import java.util.Objects;

/**
 * The before/after emissions-intensity observation for a completed Maintenance_Event, from which the
 * attributable change in emissions intensity is computed (Req 15.6).
 *
 * <p>When a Maintenance_Event completes, the system compares the emissions intensity before the
 * event with the emissions intensity after the event (Req 15.6). This value names the event, its
 * asset and asset class, the maintenance action performed, and the two intensities, each backed by
 * its primary-source {@link EvidenceLink} so the resulting
 * {@link MaintenanceEffectivenessRecord}'s Evidence_Chain resolves to primary sources (Req 15.7).
 *
 * <p>The attributable change is {@code beforeIntensity − afterIntensity} (Req 15.6, Property 49): a
 * positive change means the maintenance reduced emissions intensity. Both intensities are finite and
 * non-negative — an emissions intensity is a non-negative emissions-per-unit-output ratio.
 *
 * @param event           the completed maintenance event; never {@code null}
 * @param machine         the asset the event completed on; never {@code null}
 * @param assetClass      the asset class of the machine (Req 15.7); never {@code null}
 * @param action          the maintenance action performed; non-blank
 * @param beforeIntensity the emissions intensity before the event; finite, {@code >= 0}
 * @param afterIntensity  the emissions intensity after the event; finite, {@code >= 0}
 * @param beforeSource    the primary-source link backing the before intensity; never {@code null}
 * @param afterSource     the primary-source link backing the after intensity; never {@code null}
 *
 * @see MaintenanceEffectivenessRecord
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.6, 15.7</a>
 */
public record IntensityObservation(
        MaintenanceEventId event,
        MachineId machine,
        AssetClass assetClass,
        String action,
        double beforeIntensity,
        double afterIntensity,
        EvidenceLink beforeSource,
        EvidenceLink afterSource) {

    /**
     * Validates the references, the non-blank action, and the finite non-negative intensities
     * (Req 15.6).
     *
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code action} is blank, or an intensity is non-finite or
     *                                  negative
     */
    public IntensityObservation {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(beforeSource, "beforeSource must not be null");
        Objects.requireNonNull(afterSource, "afterSource must not be null");
        if (action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        requireFiniteNonNegative(beforeIntensity, "beforeIntensity");
        requireFiniteNonNegative(afterIntensity, "afterIntensity");
    }

    /**
     * The attributable change in emissions intensity, {@code beforeIntensity − afterIntensity}
     * (Req 15.6, Property 49).
     *
     * @return the signed intensity delta (positive = improvement)
     */
    public double attributableChange() {
        return beforeIntensity - afterIntensity;
    }

    private static void requireFiniteNonNegative(double v, String name) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be a finite number, got: " + v);
        }
        if (v < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative, got: " + v);
        }
    }
}
