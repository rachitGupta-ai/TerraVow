package com.terravow.gcp.storage;

/**
 * The BigQuery dataset families TerraVow routes through the analytics store (Req 12.2).
 *
 * <p>The design maps BigQuery to time-series telemetry, Job_Runs, emissions-intensity analytics,
 * EID/ERD series, in-warehouse embeddings/vector indexes, and the landing tables evaluated by
 * continuous queries. This enum names those families so a routed {@link TimeSeriesRow} declares
 * which logical dataset it belongs to, keeping the single store seam able to carry every BigQuery
 * workload without a per-table interface.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2</a>
 */
public enum BigQueryDataset {

    /** Time-series machine telemetry rows. */
    TELEMETRY,

    /** Job_Run and emissions-intensity analytics rows. */
    ANALYTICS,

    /** EID / ERD computed series rows. */
    EID_ERD_SERIES,

    /** In-warehouse embedding vectors for corpus/evidence similarity. */
    EMBEDDINGS,

    /** Landing tables evaluated by Pub/Sub-fed continuous queries. */
    CONTINUOUS_QUERY_LANDING
}
