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

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultStreamingIngestionService}, exercising the event-driven streaming
 * pipeline (Req 3.1, 3.3, 3.4, 13.3, 15.3): a streamed reading raises the correct
 * threshold/anomaly condition via the continuous-query triage (delegating to the reused Pollution
 * Monitor), masking is applied before persistence, and a masking rejection lands and triages
 * nothing.
 */
class DefaultStreamingIngestionServiceTest {

    private static final String SALT = "terravow-ingest-test-salt";
    private static final Pollutant PM25 = new Pollutant("PM2.5");
    private static final EnvironmentalMedium AIR = EnvironmentalMedium.AIR;

    private final InMemoryPubSubPort pubSub = new InMemoryPubSubPort();
    private final InMemoryBigQueryLandingPort landing = new InMemoryBigQueryLandingPort();
    private final MaskingGate maskingGate = new DefaultMaskingGate(SALT);

    private StreamingIngestionService serviceWith(PollutionMonitor monitor) {
        return new DefaultStreamingIngestionService(
                pubSub, maskingGate, landing, new InMemoryContinuousQueryPort(monitor));
    }

    private static Reading airReading(String stream, double value, String ref) {
        return new Reading(new StreamId(stream), PM25, AIR, value, new SourceRef(ref));
    }

    // Req 3.1, 3.3 — a streamed reading above its threshold raises a VIOLATION via the triage.
    @Test
    void streamedReadingAboveThresholdRaisesViolationViaTriage() {
        ThresholdRegistry registry = ThresholdRegistry.builder().with(PM25, AIR, 50.0).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);
        StreamingIngestionService service = serviceWith(monitor);

        StreamEvent.ReadingEvent event = StreamEvent.ReadingEvent.of(
                "rec-1", airReading("stack-1", 75.0, "r-1"), Map.of("note", "stack-A"));

        IngestionResult result = service.ingest(event);

        assertThat(result.status()).isEqualTo(IngestionResult.Status.TRIAGED);
        TriageOutcome triage = result.triage().orElseThrow();
        assertThat(triage.raisedThresholdViolation()).isTrue();
        assertThat(triage.thresholdEvaluation().orElseThrow().status())
                .isEqualTo(ThresholdEvaluation.Status.VIOLATION);
    }

    // Req 3.1 — a streamed reading within its threshold is WITHIN_LIMIT (no violation).
    @Test
    void streamedReadingWithinThresholdRaisesNoViolation() {
        ThresholdRegistry registry = ThresholdRegistry.builder().with(PM25, AIR, 50.0).build();
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);
        StreamingIngestionService service = serviceWith(monitor);

        IngestionResult result = service.ingest(
                StreamEvent.ReadingEvent.of("rec-ok", airReading("stack-1", 10.0, "r-ok"), Map.of()));

        assertThat(result.triage().orElseThrow().raisedThresholdViolation()).isFalse();
        assertThat(result.triage().orElseThrow().thresholdEvaluation().orElseThrow().status())
                .isEqualTo(ThresholdEvaluation.Status.WITHIN_LIMIT);
    }

    // Req 3.4 — once a stream has sufficient history, a far-from-profile reading raises an anomaly
    // via the triage, using the reused Pollution Monitor's deterministic logic.
    @Test
    void streamedReadingDeviatingFromProfileRaisesAnomalyViaTriage() {
        // No thresholds registered → threshold triage is UNEVALUATED; we assert on the anomaly arm.
        PollutionMonitor monitor = new DefaultPollutionMonitor(
                ThresholdRegistry.empty(), new PollutionMonitorConfig(3, 3.0));
        StreamingIngestionService service = serviceWith(monitor);

        // Seed a flat profile of 10.0 across >= minHistory readings.
        service.ingest(StreamEvent.ReadingEvent.of("s0", airReading("stack-1", 10.0, "r0"), Map.of()));
        service.ingest(StreamEvent.ReadingEvent.of("s1", airReading("stack-1", 10.0, "r1"), Map.of()));
        service.ingest(StreamEvent.ReadingEvent.of("s2", airReading("stack-1", 10.0, "r2"), Map.of()));

        // A value far from the flat baseline is maximally deviant → ANOMALY.
        IngestionResult result = service.ingest(
                StreamEvent.ReadingEvent.of("s3", airReading("stack-1", 999.0, "r3"), Map.of()));

        TriageOutcome triage = result.triage().orElseThrow();
        assertThat(triage.raisedAnomaly()).isTrue();
        assertThat(triage.anomalyEvaluation().orElseThrow().status())
                .isEqualTo(AnomalyEvaluation.Status.ANOMALY);
    }

    // Req 3.4, 3.5 — below the minimum history, anomaly triage is deferred as INSUFFICIENT_HISTORY.
    @Test
    void streamedReadingBelowMinHistoryDefersAnomaly() {
        PollutionMonitor monitor = new DefaultPollutionMonitor(
                ThresholdRegistry.empty(), new PollutionMonitorConfig(5, 3.0));
        StreamingIngestionService service = serviceWith(monitor);

        IngestionResult result = service.ingest(
                StreamEvent.ReadingEvent.of("s0", airReading("stack-1", 10.0, "r0"), Map.of()));

        assertThat(result.triage().orElseThrow().anomalyEvaluation().orElseThrow().status())
                .isEqualTo(AnomalyEvaluation.Status.INSUFFICIENT_HISTORY);
    }

    // Req 13.3 — masking is applied BEFORE persistence: the landed row carries the masked token,
    // never the raw sensitive value, and the raw value appears in no landed field.
    @Test
    void maskingIsAppliedBeforePersistence() {
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), PollutionMonitorConfig.DEFAULT);
        StreamingIngestionService service = serviceWith(monitor);

        String rawSupplier = "ACME-SUPPLIER-42";
        StreamEvent.ReadingEvent event = new StreamEvent.ReadingEvent(
                "rec-mask",
                airReading("stack-1", 10.0, "r-mask"),
                Map.of("supplierId", rawSupplier, "pollutant", "PM2.5"),
                Set.of("supplierId"));

        service.ingest(event);

        assertThat(landing.rows()).hasSize(1);
        InMemoryBigQueryLandingPort.LandedRow row = landing.rows().get(0);
        assertThat(row.kind()).isEqualTo(StreamEvent.Kind.READING);
        // The sensitive field was masked to an irreversible token before it reached the landing.
        assertThat(row.maskedFields().get("supplierId")).startsWith("mask:");
        assertThat(row.maskedFields()).doesNotContainValue(rawSupplier);
        // The non-sensitive field is carried through unchanged.
        assertThat(row.maskedFields().get("pollutant")).isEqualTo("PM2.5");
    }

    // Req 13.4 — when masking cannot complete, nothing is landed and nothing is triaged.
    @Test
    void maskingRejectionLandsAndTriagesNothing() {
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), PollutionMonitorConfig.DEFAULT);
        // A masking gate that always rejects, to prove the pipeline short-circuits before landing.
        MaskingGate alwaysRejects = data ->
                new com.terravow.pii.MaskingResult.Rejected(data.recordId(), "forced test rejection");
        StreamingIngestionService service = new DefaultStreamingIngestionService(
                pubSub, alwaysRejects, landing, new InMemoryContinuousQueryPort(monitor));

        IngestionResult result = service.ingest(
                StreamEvent.ReadingEvent.of("rec-x", airReading("stack-1", 999.0, "r-x"), Map.of()));

        assertThat(result.status()).isEqualTo(IngestionResult.Status.REJECTED);
        assertThat(result.rejectionReason()).contains("forced test rejection");
        assertThat(landing.rows()).isEmpty();
        assertThat(result.triage()).isEmpty();
        // The event was still published to Pub/Sub before masking (streaming front door, Req 3.1).
        assertThat(pubSub.published()).hasSize(1);
    }

    // Req 15.3 — a degraded telemetry event triggers Maintenance Lifecycle triage.
    @Test
    void degradedTelemetryTriggersLifecycleTriage() {
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), PollutionMonitorConfig.DEFAULT);
        StreamingIngestionService service = serviceWith(monitor);

        IngestionResult result = service.ingest(
                StreamEvent.TelemetryEvent.of("tel-1", "machine-7", true, Map.of("temp", "high")));

        LifecycleTriageTrigger trigger =
                result.triage().orElseThrow().lifecycleTrigger().orElseThrow();
        assertThat(trigger.machine().value()).isEqualTo("machine-7");
        assertThat(trigger.reason()).isEqualTo(LifecycleTriageTrigger.Reason.DEGRADED_TELEMETRY);
    }

    // Req 15.3 — a non-degraded telemetry event is landed but raises no lifecycle triage.
    @Test
    void healthyTelemetryRaisesNoLifecycleTriage() {
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), PollutionMonitorConfig.DEFAULT);
        StreamingIngestionService service = serviceWith(monitor);

        IngestionResult result = service.ingest(
                StreamEvent.TelemetryEvent.of("tel-2", "machine-7", false, Map.of("temp", "ok")));

        assertThat(result.triage().orElseThrow().lifecycleTrigger()).isEmpty();
        assertThat(landing.rows()).hasSize(1);
    }

    // Req 12.5 — when the continuous query is unavailable, the masked row is landed but triage is
    // deferred to the batch-micro-poll fallback (the seam task 22.2 hooks into).
    @Test
    void unavailableContinuousQueryLandsButDefersTriage() {
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), PollutionMonitorConfig.DEFAULT);
        InMemoryContinuousQueryPort cq = new InMemoryContinuousQueryPort(monitor);
        cq.setAvailable(false);
        StreamingIngestionService service =
                new DefaultStreamingIngestionService(pubSub, maskingGate, landing, cq);

        IngestionResult result = service.ingest(
                StreamEvent.ReadingEvent.of("rec-d", airReading("stack-1", 10.0, "r-d"), Map.of()));

        assertThat(result.status()).isEqualTo(IngestionResult.Status.DEFERRED);
        assertThat(result.triage()).isEmpty();
        // The masked row is still landed so the batch fallback can retrieve and triage it later.
        assertThat(landing.rows()).hasSize(1);
    }
}
