package com.terravow.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.pollution.AnomalyEvaluation;
import com.terravow.capability.pollution.DefaultPollutionMonitor;
import com.terravow.capability.pollution.EnvironmentalMedium;
import com.terravow.capability.pollution.Pollutant;
import com.terravow.capability.pollution.PollutionMonitor;
import com.terravow.capability.pollution.PollutionMonitorConfig;
import com.terravow.capability.pollution.Reading;
import com.terravow.capability.pollution.StreamId;
import com.terravow.capability.pollution.ThresholdEvaluation;
import com.terravow.capability.pollution.ThresholdRegistry;
import com.terravow.evidence.SourceRef;
import com.terravow.pii.DefaultMaskingGate;
import com.terravow.pii.MaskingGate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Integration test for the event-driven streaming front door, wiring the <em>real</em>
 * {@link DefaultStreamingIngestionService} over the in-memory Pub/Sub, BigQuery-landing, and
 * continuous-query stubs together with the <em>real</em> {@link DefaultPollutionMonitor}
 * (Req 3.1, 3.3, 3.4).
 *
 * <p>Where {@link DefaultStreamingIngestionServiceTest} unit-tests the service's individual
 * orchestration steps, this test exercises the two end-to-end flows task 22.2 calls out:
 *
 * <ol>
 *   <li><b>Streaming triage end-to-end</b> — a streamed reading above its regulatory threshold
 *       raises a complete threshold {@code VIOLATION}, a reading within the threshold stays
 *       {@code WITHIN_LIMIT}, and, once a stream has sufficient history, a reading that deviates
 *       far from its expected profile raises an {@code ANOMALY} — all through the continuous-query
 *       triage delegating to the reused Pollution Monitor, with the deterministic threshold/anomaly
 *       logic unchanged (Req 3.1, 3.3, 3.4).</li>
 *   <li><b>Continuous-query fallback</b> — when the continuous query is set unavailable
 *       ({@link InMemoryContinuousQueryPort#setAvailable(boolean)}), each masked reading is still
 *       {@code RETAINED} in the BigQuery landing and its near-real-time triage is deferred; the
 *       batch micro-poll fallback then re-triages the retained rows once the query is available
 *       again, yielding exactly the triage outcomes near-real-time triage would have produced
 *       (Req 3.1, 3.3, 3.4; fallback seam task 22.2 / Req 12.5).</li>
 * </ol>
 */
class StreamingTriageContinuousQueryFallbackIntegrationTest {

    private static final String SALT = "terravow-ingest-integration-salt";
    private static final Pollutant PM25 = new Pollutant("PM2.5");
    private static final EnvironmentalMedium AIR = EnvironmentalMedium.AIR;
    private static final String STACK = "stack-1";

    private final InMemoryPubSubPort pubSub = new InMemoryPubSubPort();
    private final InMemoryBigQueryLandingPort landing = new InMemoryBigQueryLandingPort();
    private final MaskingGate maskingGate = new DefaultMaskingGate(SALT);

    private static Reading airReading(double value, String ref) {
        return new Reading(new StreamId(STACK), PM25, AIR, value, new SourceRef(ref));
    }

    // Req 3.1, 3.3, 3.4 — the full pipeline over the real monitor: a streamed over-threshold
    // reading raises a VIOLATION, a within-threshold reading stays WITHIN_LIMIT, and a reading far
    // from an established profile raises an ANOMALY — all via the continuous-query triage.
    @Test
    void streamedReadingsRaiseThresholdAndAnomalyConditionsEndToEnd() {
        // A real monitor with a 50.0 PM2.5/air threshold and a small history window for anomalies.
        ThresholdRegistry registry = ThresholdRegistry.builder().with(PM25, AIR, 50.0).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, new PollutionMonitorConfig(3, 3.0));
        InMemoryContinuousQueryPort continuousQuery = new InMemoryContinuousQueryPort(monitor);
        StreamingIngestionService service =
                new DefaultStreamingIngestionService(pubSub, maskingGate, landing, continuousQuery);

        // Seed a flat baseline profile of 10.0 over >= minHistory readings (all WITHIN_LIMIT).
        for (int i = 0; i < 3; i++) {
            IngestionResult seeded = service.ingest(
                    StreamEvent.ReadingEvent.of("seed-" + i, airReading(10.0, "r-seed-" + i), Map.of()));
            assertThat(seeded.status()).isEqualTo(IngestionResult.Status.TRIAGED);
            assertThat(seeded.triage().orElseThrow().raisedThresholdViolation()).isFalse();
        }

        // (a) A within-threshold reading stays WITHIN_LIMIT.
        IngestionResult within = service.ingest(
                StreamEvent.ReadingEvent.of("within", airReading(10.0, "r-within"), Map.of()));
        assertThat(within.status()).isEqualTo(IngestionResult.Status.TRIAGED);
        assertThat(within.triage().orElseThrow().thresholdEvaluation().orElseThrow().status())
                .isEqualTo(ThresholdEvaluation.Status.WITHIN_LIMIT);

        // (b) An over-threshold reading raises a complete VIOLATION carrying its context.
        IngestionResult violating = service.ingest(
                StreamEvent.ReadingEvent.of("violating", airReading(85.0, "r-violating"), Map.of()));
        assertThat(violating.status()).isEqualTo(IngestionResult.Status.TRIAGED);
        TriageOutcome violatingTriage = violating.triage().orElseThrow();
        assertThat(violatingTriage.raisedThresholdViolation()).isTrue();
        assertThat(violatingTriage.thresholdEvaluation().orElseThrow().status())
                .isEqualTo(ThresholdEvaluation.Status.VIOLATION);
        assertThat(violatingTriage.thresholdEvaluation().orElseThrow().violation()).isPresent();

        // (c) A reading far from the established flat baseline raises an ANOMALY (Req 3.4).
        IngestionResult anomalous = service.ingest(
                StreamEvent.ReadingEvent.of("anomalous", airReading(999.0, "r-anomalous"), Map.of()));
        assertThat(anomalous.status()).isEqualTo(IngestionResult.Status.TRIAGED);
        TriageOutcome anomalousTriage = anomalous.triage().orElseThrow();
        assertThat(anomalousTriage.raisedAnomaly()).isTrue();
        assertThat(anomalousTriage.anomalyEvaluation().orElseThrow().status())
                .isEqualTo(AnomalyEvaluation.Status.ANOMALY);
    }

    // Req 3.1, 3.3, 3.4 + fallback (task 22.2 / Req 12.5) — with the continuous query unavailable,
    // every masked reading is RETAINED in the landing and its triage is DEFERRED; the batch
    // micro-poll fallback then re-triages the retained rows once the query is available again,
    // reproducing the threshold VIOLATION the near-real-time triage would have raised.
    @Test
    void batchMicroPollFallbackRetriagesReadingsRetainedWhileContinuousQueryUnavailable() {
        ThresholdRegistry registry = ThresholdRegistry.builder().with(PM25, AIR, 50.0).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);
        InMemoryContinuousQueryPort continuousQuery = new InMemoryContinuousQueryPort(monitor);
        StreamingIngestionService service =
                new DefaultStreamingIngestionService(pubSub, maskingGate, landing, continuousQuery);

        // The continuous query goes offline — near-real-time triage is unavailable.
        continuousQuery.setAvailable(false);

        // Stream a within-limit reading and an over-threshold reading while the query is down. The
        // batch micro-poll must retain the raw events it could not triage, keyed by record id so it
        // can line them up against the landed rows once the query recovers.
        Map<String, StreamEvent> deferredQueue = new LinkedHashMap<>();
        StreamEvent withinEvent = new StreamEvent.ReadingEvent(
                "rec-within",
                airReading(10.0, "r-within"),
                Map.of("supplierId", "ACME-SUPPLIER-7"),
                java.util.Set.of("supplierId"));
        StreamEvent violatingEvent =
                StreamEvent.ReadingEvent.of("rec-violating", airReading(90.0, "r-violating"), Map.of());

        for (StreamEvent event : List.of(withinEvent, violatingEvent)) {
            IngestionResult result = service.ingest(event);
            // While unavailable: masked, landed, but triage deferred to the batch fallback.
            assertThat(result.status()).isEqualTo(IngestionResult.Status.DEFERRED);
            assertThat(result.triage()).isEmpty();
            deferredQueue.put(result.recordId(), event);
        }

        // Both masked rows are RETAINED in the BigQuery landing so the fallback can find them, and
        // the sensitive supplier id was masked before it was ever persisted (Req 13.3).
        assertThat(landing.rows()).hasSize(2);
        assertThat(landing.rows())
                .extracting(InMemoryBigQueryLandingPort.LandedRow::recordId)
                .containsExactlyInAnyOrder("rec-within", "rec-violating");
        InMemoryBigQueryLandingPort.LandedRow withinRow = landing.rows().stream()
                .filter(r -> r.recordId().equals("rec-within"))
                .findFirst()
                .orElseThrow();
        assertThat(withinRow.maskedFields().get("supplierId")).startsWith("mask:");
        assertThat(withinRow.maskedFields()).doesNotContainValue("ACME-SUPPLIER-7");

        // The continuous query recovers; the batch micro-poll drains the retained events and
        // re-triages each one against the recovered query (the fallback the degradation policy
        // wires in for this path — task 22.2 / Req 12.5).
        continuousQuery.setAvailable(true);
        List<TriageOutcome> replayed = new ArrayList<>();
        for (StreamEvent retained : deferredQueue.values()) {
            replayed.add(continuousQuery.triage(retained));
        }

        // The re-triage reproduces exactly the near-real-time outcomes: the within-limit reading is
        // WITHIN_LIMIT and the over-threshold reading raises a complete VIOLATION — no reading was
        // lost while the continuous query was unavailable (Req 3.1, 3.3).
        assertThat(replayed).hasSize(2);
        TriageOutcome replayedWithin = replayed.get(0);
        assertThat(replayedWithin.recordId()).isEqualTo("rec-within");
        assertThat(replayedWithin.raisedThresholdViolation()).isFalse();
        assertThat(replayedWithin.thresholdEvaluation().orElseThrow().status())
                .isEqualTo(ThresholdEvaluation.Status.WITHIN_LIMIT);

        TriageOutcome replayedViolating = replayed.get(1);
        assertThat(replayedViolating.recordId()).isEqualTo("rec-violating");
        assertThat(replayedViolating.raisedThresholdViolation()).isTrue();
        assertThat(replayedViolating.thresholdEvaluation().orElseThrow().status())
                .isEqualTo(ThresholdEvaluation.Status.VIOLATION);
    }
}
