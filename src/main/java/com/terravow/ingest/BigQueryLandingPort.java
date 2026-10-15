package com.terravow.ingest;

import com.terravow.pii.MaskingResult;

/**
 * The persistence seam to the BigQuery continuous-query landing table that streamed events are
 * written into after masking (Req 3.1, 13.3).
 *
 * <p>This interface isolates the single landing write from the {@link StreamingIngestionService}.
 * The service depends only on this port, so the live BigQuery streaming-insert client is a later
 * wiring concern and the service stays unit-testable with a deterministic in-memory stub
 * ({@link InMemoryBigQueryLandingPort}).
 *
 * <p>By contract this port only ever accepts an already-masked payload: the parameter type is
 * {@link MaskingResult.Masked}, not the raw event, so it is structurally impossible to land a row
 * carrying raw PII or confidential supplier identifiers (Req 13.3). The service masks each event
 * through the {@code MaskingGate} and persists here <em>only</em> on a {@code Masked} outcome; a
 * rejection is never landed.
 *
 * @see StreamingIngestionService
 * @see InMemoryBigQueryLandingPort
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 13.3</a>
 */
public interface BigQueryLandingPort {

    /**
     * Persists one masked event row into the continuous-query landing table (Req 3.1, 13.3).
     *
     * @param kind   the event kind column value; never {@code null}
     * @param masked the already-masked payload to land; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    void land(StreamEvent.Kind kind, MaskingResult.Masked masked);
}
