package com.terravow.config;

import com.terravow.capability.pollution.DefaultPollutionMonitor;
import com.terravow.capability.pollution.PollutionMonitor;
import com.terravow.capability.pollution.PollutionMonitorConfig;
import com.terravow.capability.pollution.ThresholdRegistry;
import com.terravow.ingest.BigQueryLandingPort;
import com.terravow.ingest.ContinuousQueryPort;
import com.terravow.ingest.InMemoryBigQueryLandingPort;
import com.terravow.ingest.InMemoryContinuousQueryPort;
import com.terravow.ingest.InMemoryPubSubPort;
import com.terravow.ingest.PubSubPort;
import com.terravow.pii.DefaultMaskingGate;
import com.terravow.pii.MaskingGate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default wiring for the event-driven streaming ingestion seams (Req 3.1, 3.3, 3.4, 13.3, 15.3).
 *
 * <p>TerraVow is local-first and has no live Pub/Sub or BigQuery in the dev/test environment, so
 * this configuration provides deterministic default beans for the four seams the
 * {@link com.terravow.ingest.DefaultStreamingIngestionService} depends on — the {@link PubSubPort}
 * transport, the authoritative {@link MaskingGate}, the {@link BigQueryLandingPort}, and the
 * {@link ContinuousQueryPort} — plus the {@link PollutionMonitor} and {@link ThresholdRegistry} the
 * continuous-query triage reuses unchanged. Each bean is {@link ConditionalOnMissingBean} so a
 * later configuration (or a test) can supply a real managed-service client or a tailored stub
 * without touching this class.
 *
 * <p>The default {@link ContinuousQueryPort} delegates reading triage to the shared
 * {@link PollutionMonitor} bean, so the deterministic threshold (Req 3.1, 3.3) and anomaly
 * (Req 3.4) logic is reused exactly as implemented, never re-derived.
 *
 * @see com.terravow.ingest.DefaultStreamingIngestionService
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4, 13.3,
 *      15.3</a>
 */
@Configuration
public class IngestionConfig {

    /**
     * The default Pub/Sub transport for local-first operation: a deterministic in-memory stub that
     * records published events in order (Req 3.1).
     *
     * @return a default {@link PubSubPort} bean
     */
    @Bean
    @ConditionalOnMissingBean(PubSubPort.class)
    public PubSubPort pubSubPort() {
        return new InMemoryPubSubPort();
    }

    /**
     * The default BigQuery continuous-query landing for local-first operation: a deterministic
     * in-memory store that records only masked rows (Req 3.1, 13.3).
     *
     * @return a default {@link BigQueryLandingPort} bean
     */
    @Bean
    @ConditionalOnMissingBean(BigQueryLandingPort.class)
    public BigQueryLandingPort bigQueryLandingPort() {
        return new InMemoryBigQueryLandingPort();
    }

    /**
     * The default authoritative masking gate (Req 13.3, 13.4). Reads
     * {@code terravow.pii.masking.salt} (default a fixed local-first salt) so masks are stable
     * across a deployment; a real deployment overrides the salt via configuration.
     *
     * @param salt the deterministic masking salt; defaults to a local-first value
     * @return a default {@link MaskingGate} bean
     */
    @Bean
    @ConditionalOnMissingBean(MaskingGate.class)
    public MaskingGate maskingGate(
            @Value("${terravow.pii.masking.salt:terravow-local-first-salt}") String salt) {
        return new DefaultMaskingGate(salt);
    }

    /**
     * The default regulatory threshold registry for local-first operation (Req 3.1). Empty by
     * default — a real deployment registers thresholds per regulatory regime; an empty registry
     * records readings as unevaluated-for-threshold with a reason (Req 3.2).
     *
     * @return a default {@link ThresholdRegistry} bean
     */
    @Bean
    @ConditionalOnMissingBean(ThresholdRegistry.class)
    public ThresholdRegistry thresholdRegistry() {
        return ThresholdRegistry.empty();
    }

    /**
     * The default Pollution Monitor the continuous-query triage reuses unchanged (Req 3.1–3.4),
     * built over the shared {@link ThresholdRegistry} and the behaviour-preserving default anomaly
     * configuration.
     *
     * @param thresholds the shared threshold registry; never {@code null}
     * @return a default {@link PollutionMonitor} bean
     */
    @Bean
    @ConditionalOnMissingBean(PollutionMonitor.class)
    public PollutionMonitor pollutionMonitor(ThresholdRegistry thresholds) {
        return new DefaultPollutionMonitor(thresholds, PollutionMonitorConfig.DEFAULT);
    }

    /**
     * The default continuous-query triage for local-first operation: a deterministic in-memory
     * stub that delegates reading triage to the reused {@link PollutionMonitor} and raises
     * lifecycle triage for degraded telemetry/job events (Req 3.1, 3.3, 3.4, 15.3).
     *
     * @param pollutionMonitor the shared Pollution Monitor to delegate to; never {@code null}
     * @return a default {@link ContinuousQueryPort} bean
     */
    @Bean
    @ConditionalOnMissingBean(ContinuousQueryPort.class)
    public ContinuousQueryPort continuousQueryPort(PollutionMonitor pollutionMonitor) {
        return new InMemoryContinuousQueryPort(pollutionMonitor);
    }
}
