package com.terravow.gcp.storage;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One row routed to BigQuery through the {@link BigQueryAnalyticsStore} (Req 12.2).
 *
 * <p>A single, generic row shape carries every BigQuery workload the design maps to the warehouse —
 * telemetry, Job_Run/analytics, EID/ERD series, embeddings, and continuous-query landing rows —
 * discriminated by its {@link BigQueryDataset}. Keeping one shape behind one seam avoids a bespoke
 * table-per-workload interface while still letting the warehouse co-locate analytics and vector
 * data as the design intends.
 *
 * <p>The row is partitioned by {@code at} (BigQuery's natural time partition), keyed by a non-blank
 * {@code key} within its dataset, and carries an immutable {@code fields} map of column values.
 * Values are stored as an immutable copy so a row is a stable, deterministic record.
 *
 * @param dataset the logical BigQuery dataset family this row belongs to; never {@code null}
 * @param key     a non-blank identifier for the row within its dataset; never {@code null}
 * @param at      the row's event/partition time; never {@code null}
 * @param fields  the column values; never {@code null}, no {@code null} keys or values
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2</a>
 */
public record TimeSeriesRow(
        BigQueryDataset dataset,
        String key,
        Instant at,
        Map<String, Object> fields) {

    /**
     * Validates presence of every field, a non-blank key, and a null-free fields map (Req 12.2).
     *
     * @throws NullPointerException     if any field is {@code null}, or {@code fields} contains a
     *                                  {@code null} key or value
     * @throws IllegalArgumentException if {@code key} is blank
     */
    public TimeSeriesRow {
        Objects.requireNonNull(dataset, "dataset must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        fields = Map.copyOf(fields); // defensive immutable copy; rejects null keys/values
    }
}
