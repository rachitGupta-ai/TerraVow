package com.terravow.gcp.storage;

import java.util.List;

/**
 * The BigQuery repository seam for telemetry, analytics, EID/ERD series, embeddings, and
 * continuous-query landing tables (Req 12.2).
 *
 * <p>The design routes to BigQuery all time-series telemetry, Job_Runs, emissions-intensity
 * analytics, EID/ERD series, in-warehouse embeddings/vector indexes, and the landing tables that
 * continuous queries evaluate. This interface is the single seam between the application and that
 * warehouse; rows are discriminated by {@link BigQueryDataset} so one port carries every BigQuery
 * workload. The design routes this to live BigQuery in a later wiring task (Req 12.2); an in-memory
 * adapter ({@link InMemoryBigQueryAnalyticsStore}) backs local-first operation and tests.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2</a>
 */
public interface BigQueryAnalyticsStore {

    /**
     * Appends a single row to its dataset (Req 12.2).
     *
     * @param row the row to append; never {@code null}
     * @throws NullPointerException if {@code row} is {@code null}
     */
    void insert(TimeSeriesRow row);

    /**
     * Returns, in insertion order, every row routed to the given dataset.
     *
     * @param dataset the dataset family to read; never {@code null}
     * @return an immutable snapshot of the dataset's rows, oldest first; never {@code null}
     * @throws NullPointerException if {@code dataset} is {@code null}
     */
    List<TimeSeriesRow> rows(BigQueryDataset dataset);
}
