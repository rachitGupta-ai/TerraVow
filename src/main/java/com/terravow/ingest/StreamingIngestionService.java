package com.terravow.ingest;

/**
 * The event-driven streaming front door: streams readings, telemetry, and job events through
 * Pub/Sub into the BigQuery landing, masks each event before persistence, and triages landed rows
 * in near-real-time via a BigQuery continuous query (Req 3.1, 3.3, 3.4, 13.3, 13.4, 15.3).
 *
 * <p>For each ingested {@link StreamEvent} the service, in order:
 * <ol>
 *   <li>publishes the raw event onto its Pub/Sub topic through the {@link PubSubPort} (Req 3.1);</li>
 *   <li>masks the event deterministically through the {@code MaskingGate} <em>before</em> any
 *       persistence (Req 13.3); if masking cannot complete the event is rejected — nothing is
 *       landed and nothing is triaged (Req 13.4);</li>
 *   <li>persists the masked row into the BigQuery landing through the {@link BigQueryLandingPort}
 *       (Req 3.1);</li>
 *   <li>triages the landed row in near-real-time through the {@link ContinuousQueryPort}, which
 *       delegates reading evaluation to the existing
 *       {@link com.terravow.capability.pollution.PollutionMonitor} (threshold Req 3.1/3.3, anomaly
 *       Req 3.4, reused unchanged) and raises a {@link LifecycleTriageTrigger} for a degraded
 *       telemetry or job event (Req 15.3).</li>
 * </ol>
 *
 * <p>The continuous query is a degradable enrichment path: when it is unavailable the masked event
 * is still landed and triage is deferred to the batch-micro-poll fallback (Req 12.5, task 22.2),
 * returning a {@link IngestionResult.Status#DEFERRED} result. The service never weakens or
 * re-implements the deterministic threshold/anomaly logic; it only orchestrates masking,
 * persistence, and the near-real-time triage pass.
 *
 * @see DefaultStreamingIngestionService
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4, 13.3,
 *      13.4, 15.3</a>
 */
public interface StreamingIngestionService {

    /**
     * Streams one event through Pub/Sub into the BigQuery landing with masking-before-persistence,
     * then triages the landed row in near-real-time (Req 3.1, 3.3, 3.4, 13.3, 15.3).
     *
     * @param event the event to ingest; never {@code null}
     * @return the ingestion outcome: triaged, rejected (masking failed), or deferred (continuous
     *         query unavailable); never {@code null}
     * @throws NullPointerException if {@code event} is {@code null}
     */
    IngestionResult ingest(StreamEvent event);
}
