package com.terravow.capability.lifecycle;

import com.terravow.domain.AssetClass;
import com.terravow.domain.MachineId;

import java.util.List;

/**
 * The facility-specific maintenance-effectiveness knowledge base: the accumulated
 * {@link MaintenanceEffectivenessRecord}s, surfaced as supporting evidence to Substitution Dossiers
 * and Trajectory Certification verdicts (Req 15.7, 15.8).
 *
 * <p>Each completed Maintenance_Event with a computed attributable intensity change contributes a
 * record (Req 15.7); over time the store accumulates them into a knowledge base. When a Substitution
 * Dossier or a Trajectory Certification verdict is produced for a Machine_Asset, the applicable
 * records — those for the asset's asset class — are made available as supporting evidence (Req 15.8).
 * The {@link #recordsForAssetClass} and {@link #recordsForMachine} queries are the surfacing seam
 * those capabilities read through; records are returned in insertion order so the knowledge base is
 * reproducible.
 *
 * @see InMemoryMaintenanceEffectivenessStore
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.7, 15.8</a>
 */
public interface MaintenanceEffectivenessStore {

    /**
     * Records a Maintenance_Effectiveness_Record in the knowledge base (Req 15.7).
     *
     * @param record the record to append; never {@code null}
     * @throws NullPointerException if {@code record} is {@code null}
     */
    void record(MaintenanceEffectivenessRecord record);

    /**
     * Returns the applicable records for an asset class, surfaced to substitution and certification
     * (Req 15.8).
     *
     * @param assetClass the asset class to retrieve records for; never {@code null}
     * @return the records for that asset class, in insertion order; never {@code null}, possibly
     *         empty, immutable
     * @throws NullPointerException if {@code assetClass} is {@code null}
     */
    List<MaintenanceEffectivenessRecord> recordsForAssetClass(AssetClass assetClass);

    /**
     * Returns the records attributed to a specific machine, in insertion order.
     *
     * @param machine the machine to retrieve records for; never {@code null}
     * @return the records for that machine, in insertion order; never {@code null}, possibly empty,
     *         immutable
     * @throws NullPointerException if {@code machine} is {@code null}
     */
    List<MaintenanceEffectivenessRecord> recordsForMachine(MachineId machine);
}
