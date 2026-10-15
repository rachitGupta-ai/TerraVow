/**
 * Ingestion and validation (Req 14), plus event-driven streaming intake (Req 22).
 *
 * <p>Validates registrations/events, streams readings/telemetry/job events through Pub/Sub into
 * BigQuery, and applies deterministic masking before persistence.
 */
package com.terravow.ingest;
