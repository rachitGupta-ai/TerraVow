package com.terravow.gcp.storage;

import com.terravow.capability.certification.CertificateStore;
import com.terravow.capability.certification.InMemoryCertificateStore;
import com.terravow.capability.certification.InMemoryPublicRegistry;
import com.terravow.capability.certification.PublicRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default wiring for the storage split across BigQuery, Cloud Storage, and Firestore
 * (Req 12.2, 10.4, 9.5).
 *
 * <p>TerraVow is local-first with no live Google Cloud in the dev/test environment, so this
 * configuration provides deterministic in-memory default beans for each storage seam:
 *
 * <ul>
 *   <li>{@link BigQueryAnalyticsStore} for telemetry / analytics / EID-ERD series / embeddings /
 *       continuous-query landing rows (Req 12.2);</li>
 *   <li>{@link DocumentStore} for the regulatory corpus / filings / document primary sources —
 *       the Evidence_Chain primary-source store (Req 12.2);</li>
 *   <li>{@link PublicRegistry} and {@link CertificateStore} — the certification ports reused as the
 *       Firestore registry / certificate seams (Req 9.5, 12.2);</li>
 *   <li>{@link FirestoreRetentionPolicy} governing audit-trail / certificate / registry retention,
 *       defaulting to the seven-year regulatory minimum and configurable above it via
 *       {@code terravow.storage.firestore.retention-years} (Req 10.4).</li>
 * </ul>
 *
 * <p>The audit trail itself is persisted through {@link com.terravow.evidence.AuditService}, whose
 * default in-memory implementation ({@link com.terravow.evidence.DefaultAuditService}) stands in for
 * the Firestore-backed trail; this config supplies the retention policy that a Firestore-backed
 * audit adapter will enforce (Req 10.4). Each bean is {@link ConditionalOnMissingBean} so a later
 * configuration can supply the real BigQuery / Cloud Storage / Firestore client for any one seam
 * without touching this class — the live clients are a wiring and configuration concern (Req 12.2).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2, 10.4, 9.5</a>
 */
@Configuration
public class StorageConfig {

    /**
     * The default BigQuery analytics store for local-first operation: a deterministic in-memory
     * store for telemetry / analytics / EID-ERD series / embeddings / landing rows (Req 12.2).
     *
     * @return a default {@link BigQueryAnalyticsStore} bean
     */
    @Bean
    @ConditionalOnMissingBean(BigQueryAnalyticsStore.class)
    public BigQueryAnalyticsStore bigQueryAnalyticsStore() {
        return new InMemoryBigQueryAnalyticsStore();
    }

    /**
     * The default Cloud Storage document store for local-first operation: a deterministic in-memory
     * Evidence_Chain primary-source store (Req 12.2).
     *
     * @return a default {@link DocumentStore} bean
     */
    @Bean
    @ConditionalOnMissingBean(DocumentStore.class)
    public DocumentStore documentStore() {
        return new InMemoryDocumentStore();
    }

    /**
     * The default Firestore public-registry seam for local-first operation (Req 9.5, 12.2). Reuses
     * the certification {@link PublicRegistry} port so publication flows through the same seam the
     * certification logic already drives.
     *
     * @return a default {@link PublicRegistry} bean
     */
    @Bean
    @ConditionalOnMissingBean(PublicRegistry.class)
    public PublicRegistry publicRegistry() {
        return new InMemoryPublicRegistry();
    }

    /**
     * The default Firestore certificate-store seam for local-first operation (Req 9.5, 12.2).
     * Reuses the certification {@link CertificateStore} port.
     *
     * @return a default {@link CertificateStore} bean
     */
    @Bean
    @ConditionalOnMissingBean(CertificateStore.class)
    public CertificateStore certificateStore() {
        return new InMemoryCertificateStore();
    }

    /**
     * The Firestore retention policy for the audit trail, certificates, and public registry
     * (Req 10.4). Reads {@code terravow.storage.firestore.retention-years} (default 7); the
     * {@link FirestoreRetentionPolicy} invariant rejects any value below the seven-year floor, so a
     * misconfiguration fails fast rather than silently weakening retention.
     *
     * @param retentionYears the configured retention in whole years; defaults to 7
     * @return a default {@link FirestoreRetentionPolicy} bean
     */
    @Bean
    @ConditionalOnMissingBean(FirestoreRetentionPolicy.class)
    public FirestoreRetentionPolicy firestoreRetentionPolicy(
            @Value("${terravow.storage.firestore.retention-years:7}") int retentionYears) {
        return FirestoreRetentionPolicy.ofYears(retentionYears);
    }
}
