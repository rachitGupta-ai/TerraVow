package com.terravow.gcp.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.capability.certification.Certificate;
import com.terravow.capability.certification.CertificateStore;
import com.terravow.capability.certification.FacilityId;
import com.terravow.capability.certification.InMemoryCertificateStore;
import com.terravow.capability.certification.InMemoryPublicRegistry;
import com.terravow.capability.certification.PublicRegistry;
import com.terravow.capability.certification.RegistryEntry;
import com.terravow.capability.certification.VerdictStatus;
import com.terravow.domain.Confidence;
import com.terravow.evidence.AuditEntry;
import com.terravow.evidence.AuditService;
import com.terravow.evidence.DefaultAuditService;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.RecommendationId;
import com.terravow.evidence.SourceRef;
import com.terravow.gate.GateDecision;

import java.time.Instant;
import java.time.Period;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the storage split across BigQuery, Cloud Storage, and Firestore
 * (Req 12.2, 10.4, 9.5).
 *
 * <p>Each test routes one to three representative records through a store seam and reads them back,
 * verifies that the Firestore retention policy enforces the seven-year floor, and exercises the
 * registry-publication path by reusing the certification registry port. The in-memory
 * implementations stand in for the live BigQuery / Cloud Storage / Firestore clients.
 */
class StorageSplitTest {

    // --- BigQuery seam: telemetry / analytics / EID-ERD series / embeddings / landing (Req 12.2) ---

    @Test
    void bigQueryStoreRoutesRepresentativeRowsPerDataset() {
        InMemoryBigQueryAnalyticsStore store = new InMemoryBigQueryAnalyticsStore();

        TimeSeriesRow telemetry = new TimeSeriesRow(
                BigQueryDataset.TELEMETRY, "machine-1", Instant.parse("2024-01-01T00:00:00Z"),
                Map.of("co2_kg", 12.5));
        TimeSeriesRow analytics = new TimeSeriesRow(
                BigQueryDataset.ANALYTICS, "job-1", Instant.parse("2024-01-01T01:00:00Z"),
                Map.of("intensity", 0.42));
        TimeSeriesRow series = new TimeSeriesRow(
                BigQueryDataset.EID_ERD_SERIES, "machine-1", Instant.parse("2024-01-01T02:00:00Z"),
                Map.of("eid", 3.1, "erd_inr", 1000.0));

        store.insert(telemetry);
        store.insert(analytics);
        store.insert(series);

        assertThat(store.rows(BigQueryDataset.TELEMETRY)).containsExactly(telemetry);
        assertThat(store.rows(BigQueryDataset.ANALYTICS)).containsExactly(analytics);
        assertThat(store.rows(BigQueryDataset.EID_ERD_SERIES)).containsExactly(series);
        assertThat(store.rows(BigQueryDataset.EMBEDDINGS)).isEmpty();
        assertThat(store.totalRows()).isEqualTo(3);
    }

    @Test
    void bigQueryStoreCarriesEmbeddingsAndContinuousQueryLandingRows() {
        InMemoryBigQueryAnalyticsStore store = new InMemoryBigQueryAnalyticsStore();

        TimeSeriesRow embedding = new TimeSeriesRow(
                BigQueryDataset.EMBEDDINGS, "corpus-chunk-7", Instant.parse("2024-02-01T00:00:00Z"),
                Map.of("vector", "[0.1,0.2,0.3]"));
        TimeSeriesRow landing = new TimeSeriesRow(
                BigQueryDataset.CONTINUOUS_QUERY_LANDING, "reading-99",
                Instant.parse("2024-02-01T00:05:00Z"), Map.of("pm25", 55.0));

        store.insert(embedding);
        store.insert(landing);

        assertThat(store.rows(BigQueryDataset.EMBEDDINGS)).containsExactly(embedding);
        assertThat(store.rows(BigQueryDataset.CONTINUOUS_QUERY_LANDING)).containsExactly(landing);
    }

    @Test
    void timeSeriesRowRejectsBlankKey() {
        assertThatThrownBy(() -> new TimeSeriesRow(
                BigQueryDataset.TELEMETRY, "  ", Instant.now(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Cloud Storage seam: regulatory corpus / filings / primary sources (Req 12.2) ---

    @Test
    void documentStoreRoutesAndResolvesPrimarySourcesByReference() {
        InMemoryDocumentStore store = new InMemoryDocumentStore();

        SourceRef corpusRef = new SourceRef("gs://corpus/ec-guideline.txt");
        SourceRef filingRef = new SourceRef("gs://filings/return-2024.pdf");
        StoredDocument corpus = new StoredDocument(
                corpusRef, DocumentKind.REGULATORY_CORPUS, "text/plain",
                "base rate x days x deterrence".getBytes());
        StoredDocument filing = new StoredDocument(
                filingRef, DocumentKind.FILING, "application/pdf", new byte[] {1, 2, 3});

        store.put(corpus);
        store.put(filing);

        assertThat(store.get(corpusRef)).contains(corpus);
        assertThat(store.get(filingRef)).contains(filing);
        assertThat(store.get(new SourceRef("gs://missing/none.txt"))).isEmpty();
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void storedDocumentContentIsDefensivelyCopied() {
        byte[] original = {9, 8, 7};
        StoredDocument document = new StoredDocument(
                new SourceRef("gs://corpus/x"), DocumentKind.PRIMARY_SOURCE, "text/plain", original);

        original[0] = 0;        // mutate caller's array
        document.content()[1] = 0; // mutate returned copy

        assertThat(document.content()).containsExactly(9, 8, 7);
    }

    // --- Firestore seam: retention policy >= 7 years (Req 10.4) ---

    @Test
    void retentionPolicyDefaultsToSevenYearsAndMeetsMinimum() {
        FirestoreRetentionPolicy policy = FirestoreRetentionPolicy.sevenYears();

        assertThat(policy.retention()).isEqualTo(Period.ofYears(7));
        assertThat(policy.meetsRegulatoryMinimum()).isTrue();
    }

    @Test
    void retentionPolicyAllowsConfigurationAboveSevenYears() {
        FirestoreRetentionPolicy policy = FirestoreRetentionPolicy.ofYears(10);

        assertThat(policy.retention()).isEqualTo(Period.ofYears(10));
        assertThat(policy.meetsRegulatoryMinimum()).isTrue();
    }

    @Test
    void retentionPolicyRejectsLessThanSevenYears() {
        assertThatThrownBy(() -> FirestoreRetentionPolicy.ofYears(6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7 years");

        assertThatThrownBy(() -> new FirestoreRetentionPolicy(Period.ofMonths(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Firestore seam: audit trail persisted through the reused AuditService (Req 10.4) ---

    @Test
    void auditTrailPersistsThroughReusedAuditService() {
        AuditService audit = new DefaultAuditService();
        RecommendationId id = new RecommendationId("rec-1");
        AuditEntry entry = new AuditEntry(
                id, GateDecision.ALLOW, 0.9, EvidenceChain.EMPTY, Instant.parse("2024-01-01T00:00:00Z"));

        audit.persistDecision(entry);

        assertThat(audit.findByRecommendation(id)).contains(entry);
        assertThat(audit.entries()).containsExactly(entry);
    }

    // --- Firestore seam: registry publication path reuses the certification registry (Req 9.5) ---

    @Test
    void registryPublicationPathReusesCertificationRegistryAndStore() {
        CertificateStore certificateStore = new InMemoryCertificateStore();
        PublicRegistry registry = new InMemoryPublicRegistry();

        Certificate certificate = new Certificate(
                "CERT-2024-001",
                new FacilityId("facility-7"),
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                Confidence.FULL,
                EvidenceChain.EMPTY);

        // Firestore flow: persist the certificate, then publish its public projection.
        certificateStore.save(certificate);
        registry.publish(RegistryEntry.from(certificate));

        assertThat(certificateStore.findByReference("CERT-2024-001")).contains(certificate);
        assertThat(registry.findByReference("CERT-2024-001"))
                .hasValueSatisfying(e -> {
                    assertThat(e.facility()).isEqualTo(new FacilityId("facility-7"));
                    assertThat(e.verdict()).isEqualTo(VerdictStatus.ISSUED);
                    assertThat(e.validFrom()).isEqualTo(certificate.validFrom());
                    assertThat(e.validUntil()).isEqualTo(certificate.validUntil());
                });
    }
}
