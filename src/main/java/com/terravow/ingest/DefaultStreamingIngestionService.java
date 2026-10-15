package com.terravow.ingest;

import com.terravow.pii.MaskingGate;
import com.terravow.pii.MaskingResult;
import com.terravow.pii.SupplierData;

import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Deterministic implementation of {@link StreamingIngestionService} wiring the Pub/Sub, masking,
 * BigQuery-landing, and continuous-query seams in the mandated order (Req 3.1, 3.3, 3.4, 13.3,
 * 13.4, 15.3).
 *
 * <h2>Masking strictly precedes persistence</h2>
 * The service publishes the raw event to Pub/Sub, then masks it through the authoritative
 * {@link MaskingGate} <em>before</em> it is ever handed to the {@link BigQueryLandingPort}. The
 * landing port only accepts a {@link MaskingResult.Masked}, so the control flow and the type system
 * together make it impossible to land a row carrying a raw PII or confidential supplier identifier
 * (Req 13.3). A {@link MaskingResult.Rejected} short-circuits the pipeline: nothing is landed and
 * nothing is triaged, and the recorded rejection reason is returned (Req 13.4).
 *
 * <h2>Triage reuses the deterministic logic unchanged</h2>
 * Near-real-time triage is delegated to the {@link ContinuousQueryPort}, which in turn delegates
 * reading evaluation to the existing {@link com.terravow.capability.pollution.PollutionMonitor}
 * (threshold Req 3.1/3.3, anomaly Req 3.4) and raises a {@link LifecycleTriageTrigger} for a
 * degraded telemetry/job event (Req 15.3). This service re-implements none of that logic.
 *
 * <h2>Continuous query is degradable</h2>
 * The continuous query is an enrichment path: when {@link ContinuousQueryPort#isAvailable()} is
 * {@code false} the masked event is still landed, but near-real-time triage is deferred to the
 * batch-micro-poll fallback (task 22.2), yielding a {@link IngestionResult.Status#DEFERRED} result
 * (Req 12.5). The orchestration reads no wall-clock time and uses no randomness, so a given event
 * always produces the same outcome for a given monitor/stub state.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4, 12.5,
 *      13.3, 13.4, 15.3</a>
 */
@Service
public final class DefaultStreamingIngestionService implements StreamingIngestionService {

    private final PubSubPort pubSub;
    private final MaskingGate maskingGate;
    private final BigQueryLandingPort landing;
    private final ContinuousQueryPort continuousQuery;

    /**
     * Creates the ingestion service over its four seams (Req 3.1, 13.3, 15.3).
     *
     * @param pubSub          the Pub/Sub transport seam; never {@code null}
     * @param maskingGate     the authoritative deterministic masking gate; never {@code null}
     * @param landing         the BigQuery landing persistence seam; never {@code null}
     * @param continuousQuery the near-real-time continuous-query triage seam; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultStreamingIngestionService(
            PubSubPort pubSub,
            MaskingGate maskingGate,
            BigQueryLandingPort landing,
            ContinuousQueryPort continuousQuery) {
        this.pubSub = Objects.requireNonNull(pubSub, "pubSub must not be null");
        this.maskingGate = Objects.requireNonNull(maskingGate, "maskingGate must not be null");
        this.landing = Objects.requireNonNull(landing, "landing must not be null");
        this.continuousQuery =
                Objects.requireNonNull(continuousQuery, "continuousQuery must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public IngestionResult ingest(StreamEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        // 1. Req 3.1 — stream the event onto its Pub/Sub topic.
        pubSub.publish(event);

        // 2. Req 13.3/13.4 — mask BEFORE any persistence; a rejection lands and triages nothing.
        MaskingResult masked = maskingGate.mask(toSupplierData(event));
        if (masked instanceof MaskingResult.Rejected rejected) {
            return IngestionResult.rejected(rejected.recordId(), rejected.reason());
        }
        MaskingResult.Masked maskedPayload = (MaskingResult.Masked) masked;

        // 3. Req 3.1/13.3 — persist the masked row into the BigQuery continuous-query landing.
        landing.land(event.kind(), maskedPayload);

        // 4. Req 12.5 — the continuous query is degradable: if unavailable, land-only and defer
        //    near-real-time triage to the batch-micro-poll fallback (task 22.2).
        if (!continuousQuery.isAvailable()) {
            return IngestionResult.deferred(event.recordId());
        }

        // 5. Req 3.1/3.3/3.4/15.3 — near-real-time triage via the reused deterministic logic.
        TriageOutcome triage = continuousQuery.triage(event);
        return IngestionResult.triaged(triage);
    }

    /**
     * Projects a streaming event into the {@link SupplierData} boundary type the masking gate
     * consumes, carrying the event's raw payload and its declared sensitive field names (Req 13.3).
     *
     * @param event the event to project; never {@code null}
     * @return the pre-masking supplier data for this event
     */
    private static SupplierData toSupplierData(StreamEvent event) {
        return new SupplierData(event.recordId(), event.payload(), event.sensitiveFields());
    }
}
