/**
 * Storage split across BigQuery, Cloud Storage, and Firestore (Req 12.2, 10.4, 9.5).
 *
 * <p>The design maps three storage families onto three managed Google Cloud stores, each reached
 * through a repository port so the deterministic application logic never depends on a live client:
 *
 * <ul>
 *   <li><b>BigQuery</b> — time-series telemetry, Job_Runs, emissions analytics, EID/ERD series,
 *       in-warehouse embeddings/vector indexes, and the landing tables evaluated by continuous
 *       queries. Modelled by {@link com.terravow.gcp.storage.BigQueryAnalyticsStore} (Req 12.2).</li>
 *   <li><b>Cloud Storage</b> — the regulatory corpus, filings, and document primary sources
 *       referenced by Evidence_Chains; this is the Evidence_Chain primary-source store. Modelled by
 *       {@link com.terravow.gcp.storage.DocumentStore} (Req 12.2).</li>
 *   <li><b>Firestore</b> — the public global registry, certificates, and audit trail, retained for
 *       at least seven years. The registry and certificate seams are the certification ports
 *       ({@link com.terravow.capability.certification.PublicRegistry},
 *       {@link com.terravow.capability.certification.CertificateStore}); the audit trail is the
 *       {@link com.terravow.evidence.AuditService}; the retention window is governed by
 *       {@link com.terravow.gcp.storage.FirestoreRetentionPolicy} (Req 12.2, 10.4, 9.5).</li>
 * </ul>
 *
 * <p>TerraVow is local-first with no live Google Cloud in tests, so every store is provided with a
 * deterministic in-memory implementation. {@link com.terravow.gcp.storage.StorageConfig} wires the
 * defaults with {@code @ConditionalOnMissingBean} so a real GCP-backed adapter can override each
 * seam without touching this package. Live BigQuery / Cloud Storage / Firestore clients are a
 * later wiring and configuration concern.
 */
package com.terravow.gcp.storage;
