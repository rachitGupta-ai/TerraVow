package com.terravow.capability.lifecycle;

import com.terravow.domain.AssetClass;
import com.terravow.domain.Confidence;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEventId;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;

/**
 * A verified, observed record of the change in emissions intensity attributable to a completed
 * Maintenance_Event on a given asset class (Req 15.7).
 *
 * <p>When an attributable change in emissions intensity is computed for a completed Maintenance_Event
 * (Req 15.6), a {@code MaintenanceEffectivenessRecord} is recorded carrying the asset class, the
 * maintenance action, the measured change in emissions intensity, a confidence value, and an
 * Evidence_Chain (Req 15.7). Accumulated over time these records form the facility-specific
 * maintenance-effectiveness knowledge base that is surfaced as supporting evidence to Substitution
 * Dossiers and Trajectory Certification verdicts (Req 15.8).
 *
 * <p>The record retains the event and machine identifiers alongside the asset class so a specific
 * observation can be traced and so the store can key records by asset class for retrieval. The
 * measured change is the before-minus-after emissions-intensity delta (Req 15.6).
 *
 * @param event          the completed maintenance event; never {@code null}
 * @param machine        the asset the event completed on; never {@code null}
 * @param assetClass     the asset class the record is attributed to (Req 15.7); never {@code null}
 * @param action         the maintenance action performed (Req 15.7); non-blank
 * @param intensityDelta the measured change in emissions intensity, before minus after (Req 15.6,
 *                       15.7); finite
 * @param confidence     the confidence value attached to the record (Req 15.7); never {@code null}
 * @param evidence       the Evidence_Chain backing the record (Req 15.7); never {@code null}
 *
 * @see MaintenanceEffectivenessStore
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.6, 15.7,
 *      15.8</a>
 */
public record MaintenanceEffectivenessRecord(
        MaintenanceEventId event,
        MachineId machine,
        AssetClass assetClass,
        String action,
        double intensityDelta,
        Confidence confidence,
        EvidenceChain evidence) {

    /**
     * Validates the references, the non-blank action, and the finite intensity delta (Req 15.7).
     *
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code action} is blank or {@code intensityDelta} is
     *                                  non-finite
     */
    public MaintenanceEffectivenessRecord {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        if (action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (Double.isNaN(intensityDelta) || Double.isInfinite(intensityDelta)) {
            throw new IllegalArgumentException(
                    "intensityDelta must be a finite number, got: " + intensityDelta);
        }
    }
}
