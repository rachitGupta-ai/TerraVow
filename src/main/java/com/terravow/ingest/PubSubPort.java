package com.terravow.ingest;

/**
 * The transport seam to the managed Pub/Sub topics that readings, telemetry, and job events stream
 * through on their way into the BigQuery landing (Req 3.1).
 *
 * <p>This interface isolates the single publish call to Pub/Sub from the
 * {@link StreamingIngestionService} that enforces TerraVow's masking and triage invariants. The
 * service depends only on this port, so the live Google Cloud publisher is a later wiring and
 * configuration concern and the service stays unit-testable with a deterministic in-memory stub
 * ({@link InMemoryPubSubPort}).
 *
 * <p>A single {@link #publish} call corresponds to exactly one event accepted onto the stream. The
 * port performs no masking, no persistence, and no triage; those belong to the ingestion service.
 * The payload it carries is still the raw, pre-masking event — masking happens in the service
 * before the landing write, exactly as the design requires (Req 13.3).
 *
 * @see StreamingIngestionService
 * @see InMemoryPubSubPort
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 13.3</a>
 */
public interface PubSubPort {

    /**
     * Publishes a single streaming event onto its Pub/Sub topic (Req 3.1).
     *
     * @param event the event to stream; never {@code null}
     * @throws NullPointerException if {@code event} is {@code null}
     */
    void publish(StreamEvent event);
}
