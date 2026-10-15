package com.terravow.ingest;

import com.terravow.capability.pollution.Reading;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A single event streamed through the Pub/Sub front door into the BigQuery landing (Req 3.1).
 *
 * <p>The three streaming event kinds the design routes through Pub/Sub topics — readings,
 * telemetry, and job events — are modelled as a sealed, exhaustive sum type so the ingestion
 * pipeline and the continuous-query triage handle each kind without an open-ended {@code instanceof}
 * ladder. Every event carries a {@link #recordId()} used to correlate the masked landing row and
 * any masking rejection reason, and a {@link #payload()} of raw name-keyed fields that is masked
 * before persistence (Req 13.3).
 *
 * <ul>
 *   <li>{@link ReadingEvent} wraps a stack/effluent/sensor {@link Reading}; the continuous-query
 *       triage evaluates it against regulatory thresholds and expected profiles via the Pollution
 *       Monitor (Req 3.1, 3.3, 3.4).</li>
 *   <li>{@link TelemetryEvent} carries machine telemetry that can indicate degradation and so
 *       trigger a Maintenance Lifecycle triage (Req 15.3).</li>
 *   <li>{@link JobEvent} carries a job-run lifecycle event that is landed for analytics and can
 *       likewise feed lifecycle triage (Req 15.3).</li>
 * </ul>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4, 15.3</a>
 */
public sealed interface StreamEvent
        permits StreamEvent.ReadingEvent, StreamEvent.TelemetryEvent, StreamEvent.JobEvent {

    /**
     * A non-blank identifier correlating this event to its masked landing row and any masking
     * rejection reason (Req 13.3).
     *
     * @return the record id; never {@code null} or blank
     */
    String recordId();

    /**
     * The raw, pre-masking field payload of this event, keyed by field name (Req 13.3).
     *
     * <p>This payload may legitimately contain PII or commercially confidential supplier
     * identifiers, which is exactly why it is routed through the deterministic masking gate before
     * it is persisted to the BigQuery landing.
     *
     * @return the raw field payload; never {@code null}, immutable, may be empty
     */
    Map<String, String> payload();

    /**
     * The names of the {@link #payload()} fields that carry PII or confidential supplier
     * identifiers and must be masked before the event is persisted to the BigQuery landing
     * (Req 13.3).
     *
     * <p>The ingestion service builds a {@link com.terravow.pii.SupplierData} from the payload and
     * these names, so every name here must appear in {@link #payload()}. May be empty when the
     * event carries no sensitive field.
     *
     * @return the sensitive field names; never {@code null}, immutable
     */
    Set<String> sensitiveFields();

    /**
     * A stable, machine-readable kind discriminator used as the landing table's event-kind column.
     *
     * @return the event kind; never {@code null}
     */
    Kind kind();

    /**
     * Enforces that every declared sensitive field name is present in the payload, so a
     * {@link com.terravow.pii.SupplierData} can be built from the event (Req 13.3).
     *
     * @param sensitiveFields the declared sensitive field names; never {@code null}
     * @param payload         the event payload; never {@code null}
     * @throws IllegalArgumentException if a sensitive field is absent from the payload
     */
    private static void requireSensitivePresent(
            Set<String> sensitiveFields, Map<String, String> payload) {
        for (String name : sensitiveFields) {
            if (!payload.containsKey(name)) {
                throw new IllegalArgumentException(
                        "Declared sensitive field is absent from payload: " + name);
            }
        }
    }

    /** The three mutually-exclusive streaming event kinds routed through Pub/Sub. */
    enum Kind {
        /** A stack/effluent/sensor reading (Req 3.1). */
        READING,
        /** Machine telemetry (Req 15.3). */
        TELEMETRY,
        /** A job-run lifecycle event (Req 15.3). */
        JOB
    }

    /**
     * A streamed stack/effluent/sensor reading event (Req 3.1).
     *
     * <p>Carries the typed {@link Reading} the Pollution Monitor evaluates and the raw field
     * payload masked before persistence. The {@code reading} is the deterministic input to the
     * threshold/anomaly logic (which is reused unchanged from the Pollution Monitor); the
     * {@code payload} is what is masked and landed.
     *
     * @param recordId        the correlation id; never {@code null} or blank
     * @param reading         the typed reading evaluated by the continuous-query triage; never
     *                        {@code null}
     * @param payload         the raw field payload masked before persistence; never {@code null}
     * @param sensitiveFields the payload field names to mask before persistence (Req 13.3); never
     *                        {@code null}, every name must appear in {@code payload}
     *
     * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3, 3.4</a>
     */
    record ReadingEvent(
            String recordId, Reading reading, Map<String, String> payload, Set<String> sensitiveFields)
            implements StreamEvent {

        /**
         * Validates identity, defensively copies the payload and sensitive set, and enforces that
         * every declared sensitive field is present in the payload (Req 3.1, 13.3).
         *
         * @throws NullPointerException     if any argument, key, or value is {@code null}
         * @throws IllegalArgumentException if {@code recordId} is blank, or a declared sensitive
         *                                  field is absent from {@code payload}
         */
        public ReadingEvent {
            Objects.requireNonNull(recordId, "ReadingEvent recordId must not be null");
            Objects.requireNonNull(reading, "ReadingEvent reading must not be null");
            Objects.requireNonNull(payload, "ReadingEvent payload must not be null");
            Objects.requireNonNull(sensitiveFields, "ReadingEvent sensitiveFields must not be null");
            if (recordId.isBlank()) {
                throw new IllegalArgumentException("ReadingEvent recordId must not be blank");
            }
            payload = Map.copyOf(payload);
            sensitiveFields = Set.copyOf(sensitiveFields);
            requireSensitivePresent(sensitiveFields, payload);
        }

        /**
         * Convenience factory for a reading event with no sensitive payload fields.
         *
         * @param recordId the correlation id; never {@code null} or blank
         * @param reading  the typed reading; never {@code null}
         * @param payload  the raw field payload; never {@code null}
         * @return a reading event declaring no sensitive fields
         */
        public static ReadingEvent of(String recordId, Reading reading, Map<String, String> payload) {
            return new ReadingEvent(recordId, reading, payload, Set.of());
        }

        @Override
        public Kind kind() {
            return Kind.READING;
        }
    }

    /**
     * A streamed machine-telemetry event that can indicate degradation and trigger lifecycle
     * triage (Req 15.3).
     *
     * @param recordId        the correlation id; never {@code null} or blank
     * @param machineId       the non-blank id of the machine the telemetry is for
     * @param degraded        whether this telemetry indicates the asset is degraded (drives Req
     *                        15.3 lifecycle triage)
     * @param payload         the raw field payload masked before persistence; never {@code null}
     * @param sensitiveFields the payload field names to mask before persistence (Req 13.3); never
     *                        {@code null}, every name must appear in {@code payload}
     *
     * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 15.3</a>
     */
    record TelemetryEvent(
            String recordId,
            String machineId,
            boolean degraded,
            Map<String, String> payload,
            Set<String> sensitiveFields)
            implements StreamEvent {

        /**
         * Validates identity, defensively copies the payload and sensitive set, and enforces that
         * every declared sensitive field is present in the payload (Req 13.3, 15.3).
         *
         * @throws NullPointerException     if any argument, key, or value is {@code null}
         * @throws IllegalArgumentException if {@code recordId} or {@code machineId} is blank, or a
         *                                  declared sensitive field is absent from {@code payload}
         */
        public TelemetryEvent {
            Objects.requireNonNull(recordId, "TelemetryEvent recordId must not be null");
            Objects.requireNonNull(machineId, "TelemetryEvent machineId must not be null");
            Objects.requireNonNull(payload, "TelemetryEvent payload must not be null");
            Objects.requireNonNull(
                    sensitiveFields, "TelemetryEvent sensitiveFields must not be null");
            if (recordId.isBlank()) {
                throw new IllegalArgumentException("TelemetryEvent recordId must not be blank");
            }
            if (machineId.isBlank()) {
                throw new IllegalArgumentException("TelemetryEvent machineId must not be blank");
            }
            payload = Map.copyOf(payload);
            sensitiveFields = Set.copyOf(sensitiveFields);
            requireSensitivePresent(sensitiveFields, payload);
        }

        /**
         * Convenience factory for a telemetry event with no sensitive payload fields.
         *
         * @param recordId  the correlation id; never {@code null} or blank
         * @param machineId the machine id; never {@code null} or blank
         * @param degraded  whether the telemetry indicates degradation
         * @param payload   the raw field payload; never {@code null}
         * @return a telemetry event declaring no sensitive fields
         */
        public static TelemetryEvent of(
                String recordId, String machineId, boolean degraded, Map<String, String> payload) {
            return new TelemetryEvent(recordId, machineId, degraded, payload, Set.of());
        }

        @Override
        public Kind kind() {
            return Kind.TELEMETRY;
        }
    }

    /**
     * A streamed job-run lifecycle event, landed for analytics and able to feed lifecycle triage
     * (Req 15.3).
     *
     * @param recordId        the correlation id; never {@code null} or blank
     * @param machineId       the non-blank id of the machine the job ran on
     * @param jobId           the non-blank id of the job run
     * @param degraded        whether this job event indicates the asset is degraded (drives Req
     *                        15.3 lifecycle triage)
     * @param payload         the raw field payload masked before persistence; never {@code null}
     * @param sensitiveFields the payload field names to mask before persistence (Req 13.3); never
     *                        {@code null}, every name must appear in {@code payload}
     *
     * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 15.3</a>
     */
    record JobEvent(
            String recordId,
            String machineId,
            String jobId,
            boolean degraded,
            Map<String, String> payload,
            Set<String> sensitiveFields)
            implements StreamEvent {

        /**
         * Validates identity, defensively copies the payload and sensitive set, and enforces that
         * every declared sensitive field is present in the payload (Req 13.3, 15.3).
         *
         * @throws NullPointerException     if any argument, key, or value is {@code null}
         * @throws IllegalArgumentException if {@code recordId}, {@code machineId}, or {@code jobId}
         *                                  is blank, or a declared sensitive field is absent from
         *                                  {@code payload}
         */
        public JobEvent {
            Objects.requireNonNull(recordId, "JobEvent recordId must not be null");
            Objects.requireNonNull(machineId, "JobEvent machineId must not be null");
            Objects.requireNonNull(jobId, "JobEvent jobId must not be null");
            Objects.requireNonNull(payload, "JobEvent payload must not be null");
            Objects.requireNonNull(sensitiveFields, "JobEvent sensitiveFields must not be null");
            if (recordId.isBlank()) {
                throw new IllegalArgumentException("JobEvent recordId must not be blank");
            }
            if (machineId.isBlank()) {
                throw new IllegalArgumentException("JobEvent machineId must not be blank");
            }
            if (jobId.isBlank()) {
                throw new IllegalArgumentException("JobEvent jobId must not be blank");
            }
            payload = Map.copyOf(payload);
            sensitiveFields = Set.copyOf(sensitiveFields);
            requireSensitivePresent(sensitiveFields, payload);
        }

        /**
         * Convenience factory for a job event with no sensitive payload fields.
         *
         * @param recordId  the correlation id; never {@code null} or blank
         * @param machineId the machine id; never {@code null} or blank
         * @param jobId     the job id; never {@code null} or blank
         * @param degraded  whether the job event indicates degradation
         * @param payload   the raw field payload; never {@code null}
         * @return a job event declaring no sensitive fields
         */
        public static JobEvent of(
                String recordId,
                String machineId,
                String jobId,
                boolean degraded,
                Map<String, String> payload) {
            return new JobEvent(recordId, machineId, jobId, degraded, payload, Set.of());
        }

        @Override
        public Kind kind() {
            return Kind.JOB;
        }
    }
}
