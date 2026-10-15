package com.terravow.ingest;

import static org.assertj.core.api.Assertions.assertThat;

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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based tests for the event-driven streaming triage (Req 3.1, 3.3, 13.3).
 *
 * <p>These properties guard the two invariants of task 22.1 that must hold across all inputs:
 * <ul>
 *   <li>the continuous-query triage reproduces the reused Pollution Monitor's threshold decision
 *       exactly — the streaming path neither re-implements nor weakens the deterministic
 *       threshold logic (Req 3.1, 3.3);</li>
 *   <li>masking is applied before persistence — a declared sensitive field never appears
 *       verbatim in a landed row (Req 13.3).</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 3.1, 3.3, 13.3</b>
 */
// Feature: terravow, Property: streaming triage preserves threshold logic and masks before landing
class StreamingTriageProperties {

    private static final String SALT = "terravow-prop-ingest-salt";
    private static final Pollutant PM25 = new Pollutant("PM2.5");
    private static final EnvironmentalMedium AIR = EnvironmentalMedium.AIR;
    private static final double THRESHOLD = 50.0;

    /**
     * For any measured value, the triage's threshold decision equals the Pollution Monitor's
     * decision on the same reading — the streaming path reuses the deterministic logic unchanged
     * (Req 3.1, 3.3).
     */
    @Property(tries = 100)
    void triageThresholdDecisionMatchesPollutionMonitor(
            @ForAll @DoubleRange(min = 0.0, max = 200.0) double measured) {

        ThresholdRegistry registry = ThresholdRegistry.builder().with(PM25, AIR, THRESHOLD).build();

        // The streaming pipeline under test.
        InMemoryBigQueryLandingPort landing = new InMemoryBigQueryLandingPort();
        PollutionMonitor streamingMonitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);
        StreamingIngestionService service = new DefaultStreamingIngestionService(
                new InMemoryPubSubPort(),
                new DefaultMaskingGate(SALT),
                landing,
                new InMemoryContinuousQueryPort(streamingMonitor));

        // An independent reference monitor computing the expected decision directly.
        PollutionMonitor referenceMonitor =
                new DefaultPollutionMonitor(registry, PollutionMonitorConfig.DEFAULT);

        Reading reading = new Reading(new StreamId("stack-1"), PM25, AIR, measured, new SourceRef("r"));
        ThresholdEvaluation.Status expected = referenceMonitor.evaluateThreshold(reading).status();

        IngestionResult result = service.ingest(
                StreamEvent.ReadingEvent.of("rec", reading, Map.of()));

        ThresholdEvaluation.Status actual =
                result.triage().orElseThrow().thresholdEvaluation().orElseThrow().status();
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * For any raw sensitive value, the value is masked before it reaches the landing: no landed
     * field holds the raw value verbatim, and the sensitive field carries a masked token
     * (Req 13.3).
     */
    @Property(tries = 100)
    void sensitiveValueIsMaskedBeforeLanding(@ForAll("rawIdentifier") String raw) {
        InMemoryBigQueryLandingPort landing = new InMemoryBigQueryLandingPort();
        MaskingGate gate = new DefaultMaskingGate(SALT);
        PollutionMonitor monitor =
                new DefaultPollutionMonitor(ThresholdRegistry.empty(), PollutionMonitorConfig.DEFAULT);
        StreamingIngestionService service = new DefaultStreamingIngestionService(
                new InMemoryPubSubPort(), gate, landing, new InMemoryContinuousQueryPort(monitor));

        StreamEvent.ReadingEvent event = new StreamEvent.ReadingEvent(
                "rec",
                new Reading(new StreamId("stack-1"), PM25, AIR, 10.0, new SourceRef("r")),
                Map.of("supplierId", raw),
                Set.of("supplierId"));

        service.ingest(event);

        assertThat(landing.rows()).hasSize(1);
        Map<String, String> masked = landing.rows().get(0).maskedFields();
        assertThat(masked.get("supplierId")).startsWith("mask:").isNotEqualTo(raw);
        assertThat(masked).doesNotContainValue(raw);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> rawIdentifier() {
        return net.jqwik.api.Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(40);
    }
}
