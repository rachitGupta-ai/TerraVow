package com.terravow.capability.lifecycle;

import com.terravow.domain.AssetClass;
import com.terravow.domain.MachineId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An in-memory, insertion-ordered {@link MaintenanceEffectivenessStore} for the
 * maintenance-effectiveness knowledge base (Req 15.7, 15.8).
 *
 * <p>Records are appended in the order they are recorded and returned in that same order, so the
 * surfaced knowledge base is reproducible and deterministic (matching the certification/substitution
 * in-memory stores already in the codebase). Retrieval filters by asset class (Req 15.8) or by
 * machine, returning immutable copies so callers cannot mutate the store's state.
 *
 * <p>The store is synchronized on its own monitor so concurrent recording and reading remain
 * consistent; the methods are short and non-blocking.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.7, 15.8</a>
 */
public final class InMemoryMaintenanceEffectivenessStore implements MaintenanceEffectivenessStore {

    /** The accumulated records in insertion order. */
    private final List<MaintenanceEffectivenessRecord> records = new ArrayList<>();

    /** {@inheritDoc} */
    @Override
    public synchronized void record(MaintenanceEffectivenessRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        records.add(record);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<MaintenanceEffectivenessRecord> recordsForAssetClass(
            AssetClass assetClass) {
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        List<MaintenanceEffectivenessRecord> matches = new ArrayList<>();
        for (MaintenanceEffectivenessRecord r : records) {
            if (r.assetClass().equals(assetClass)) {
                matches.add(r);
            }
        }
        return List.copyOf(matches);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<MaintenanceEffectivenessRecord> recordsForMachine(MachineId machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        List<MaintenanceEffectivenessRecord> matches = new ArrayList<>();
        for (MaintenanceEffectivenessRecord r : records) {
            if (r.machine().equals(machine)) {
                matches.add(r);
            }
        }
        return List.copyOf(matches);
    }
}
