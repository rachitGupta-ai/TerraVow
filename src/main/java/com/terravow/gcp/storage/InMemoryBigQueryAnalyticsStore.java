package com.terravow.gcp.storage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A deterministic in-memory {@link BigQueryAnalyticsStore} for local-first operation and tests
 * (Req 12.2).
 *
 * <p>Backs each {@link BigQueryDataset} with an insertion-ordered, append-only list so tests can
 * route representative telemetry/analytics/series/embedding/landing rows and read them back in
 * order. It holds no live BigQuery connection and performs no network I/O; the design routes this
 * seam to real BigQuery in a later wiring task. The store is self-contained and references no other
 * capability package.
 *
 * @see BigQueryAnalyticsStore
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2</a>
 */
public final class InMemoryBigQueryAnalyticsStore implements BigQueryAnalyticsStore {

    private final ConcurrentMap<BigQueryDataset, List<TimeSeriesRow>> rowsByDataset =
            new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void insert(TimeSeriesRow row) {
        Objects.requireNonNull(row, "row must not be null");
        rowsByDataset
                .computeIfAbsent(row.dataset(), d -> new CopyOnWriteArrayList<>())
                .add(row);
    }

    /** {@inheritDoc} */
    @Override
    public List<TimeSeriesRow> rows(BigQueryDataset dataset) {
        Objects.requireNonNull(dataset, "dataset must not be null");
        return List.copyOf(rowsByDataset.getOrDefault(dataset, List.of()));
    }

    /**
     * Returns the total number of rows across every dataset. Intended for tests.
     *
     * @return the count of inserted rows
     */
    public int totalRows() {
        return rowsByDataset.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns a per-dataset snapshot of row counts. Intended for tests.
     *
     * @return an immutable map of dataset to row count
     */
    public Map<BigQueryDataset, Integer> rowCounts() {
        return rowsByDataset.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> e.getValue().size()));
    }
}
