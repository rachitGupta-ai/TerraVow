package com.terravow.ingest;

/**
 * The seam to the BigQuery continuous query that evaluates incoming landed rows in near-real-time
 * against regulatory thresholds and expected profiles, triggering Pollution Monitor and Maintenance
 * Lifecycle triage (Req 3.1, 3.3, 3.4, 15.3).
 *
 * <p>This interface isolates the near-real-time triage pass from the {@link StreamingIngestionService}
 * so the live BigQuery continuous query is a later wiring concern and the service stays
 * unit-testable with a deterministic in-memory stub ({@link InMemoryContinuousQueryPort}). The
 * continuous query is an <em>enrichment path</em>: per the design every managed path is degradable,
 * so this port exposes {@link #isAvailable()} as the seam the batch-micro-poll fallback (task 22.2
 * and the degradation policy, Req 12.5) hooks into — when the continuous query is unavailable the
 * ingestion service can retain landed rows and triage them via a batch micro-poll instead, without
 * changing the triage logic itself.
 *
 * <p>Crucially, the deterministic threshold and anomaly logic the continuous query applies is the
 * existing {@link com.terravow.capability.pollution.PollutionMonitor}, reused unchanged. This port
 * triages an already-landed, already-masked row; it never re-implements the threshold/anomaly
 * computation.
 *
 * @see InMemoryContinuousQueryPort
 * @see StreamingIngestionService
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4, 12.5,
 *      15.3</a>
 */
public interface ContinuousQueryPort {

    /**
     * Evaluates one landed event against thresholds / expected profiles in near-real-time and
     * returns the triage outcome (Req 3.1, 3.3, 3.4, 15.3).
     *
     * <p>For a reading, the outcome carries the Pollution Monitor's threshold and anomaly
     * evaluations (reused unchanged); for a degraded telemetry or job event, it carries a
     * {@link LifecycleTriageTrigger}.
     *
     * @param event the landed event to triage; never {@code null}
     * @return the near-real-time triage outcome; never {@code null}
     * @throws NullPointerException if {@code event} is {@code null}
     */
    TriageOutcome triage(StreamEvent event);

    /**
     * Whether the continuous query is currently available (Req 12.5).
     *
     * <p>This is the degradation seam: when it returns {@code false}, the ingestion service treats
     * the near-real-time triage as unavailable and the batch-micro-poll fallback (task 22.2) takes
     * over retaining and later triaging landed rows. The default implementation reports available.
     *
     * @return {@code true} iff near-real-time continuous-query triage is available
     */
    default boolean isAvailable() {
        return true;
    }
}
