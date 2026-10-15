package com.terravow.gcp.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.certification.Certificate;
import com.terravow.capability.certification.CertificationVerdict;
import com.terravow.capability.certification.DefaultCertificationService;
import com.terravow.capability.certification.EffortHistory;
import com.terravow.capability.certification.FacilityId;
import com.terravow.capability.certification.InMemoryCertificateStore;
import com.terravow.capability.certification.InMemoryPublicRegistry;
import com.terravow.capability.certification.MeasurementPoint;
import com.terravow.capability.certification.VerdictStatus;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for storage routing across the BigQuery / Cloud Storage / Firestore split and
 * for registry publication driven end-to-end by the real certification service (Req 12.2, 9.5,
 * 9.6).
 *
 * <p>This test complements the unit-level {@link StorageSplitTest} (which routes one record per
 * seam and exercises the registry port directly) by covering the two things that unit test does
 * not:
 *
 * <ul>
 *   <li><b>Cross-store routing (Req 12.2):</b> one to three representative records are routed
 *       through every store family at once — BigQuery analytics across its datasets, Cloud Storage
 *       documents, and the Firestore-backed registry / certificate / audit seams — and read back,
 *       confirming records land in the correct store and nothing leaks across seams.</li>
 *   <li><b>End-to-end registry publication (Req 9.5, 9.6):</b> the real
 *       {@link DefaultCertificationService} is driven over the in-memory certificate store and
 *       public registry (the Firestore-emulator stand-in). An ISSUED verdict both persists the
 *       certificate and publishes it to the registry (Req 9.5); a WITHHELD verdict (insufficient
 *       history or gaming) persists and publishes <em>nothing</em> and is omitted from the registry
 *       (Req 9.6).</li>
 * </ul>
 *
 * <p>The in-memory adapters stand in for the live BigQuery / Cloud Storage / Firestore clients, so
 * the routing contract and the certification side effects can be verified without network I/O.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2, 9.5, 9.6</a>
 */
class StorageRoutingAndRegistryPublicationIntegrationTest {

    /** A positive certificate validity window; its exact length does not affect routing. */
    private static final Duration CERT_VALIDITY = Duration.ofDays(365);

    /** The minimum trajectory observation period used by the certification evaluations. */
    private static final Period MIN_OBSERVATION = Period.ofDays(30);

    // ------------------------------------------------------------------------------------------
    // (a) Cross-store routing: representative records per store, read back (Req 12.2)
    // ------------------------------------------------------------------------------------------

    /**
     * Routes representative records through all three store families in one flow and reads them
     * back, asserting each record lands in the correct store family and does not leak across seams
     * (Req 12.2).
     */
    @Test
    void routesRepresentativeRecordsThroughEveryStoreAndReadsThemBack() {
        InMemoryBigQueryAnalyticsStore bigQuery = new InMemoryBigQueryAnalyticsStore();
        InMemoryDocumentStore cloudStorage = new InMemoryDocumentStore();
        InMemoryCertificateStore firestoreCertificates = new InMemoryCertificateStore();
        InMemoryPublicRegistry firestoreRegistry = new InMemoryPublicRegistry();

        // --- BigQuery: three representative analytics rows across distinct datasets ---
        TimeSeriesRow telemetry = new TimeSeriesRow(
                BigQueryDataset.TELEMETRY, "machine-7", Instant.parse("2024-03-01T00:00:00Z"),
                Map.of("co2_kg", 9.4));
        TimeSeriesRow analytics = new TimeSeriesRow(
                BigQueryDataset.ANALYTICS, "job-42", Instant.parse("2024-03-01T01:00:00Z"),
                Map.of("intensity", 0.37));
        TimeSeriesRow series = new TimeSeriesRow(
                BigQueryDataset.EID_ERD_SERIES, "machine-7", Instant.parse("2024-03-01T02:00:00Z"),
                Map.of("eid", 2.2, "erd_inr", 850.0));
        bigQuery.insert(telemetry);
        bigQuery.insert(analytics);
        bigQuery.insert(series);

        // --- Cloud Storage: two representative primary-source documents ---
        SourceRef corpusRef = new SourceRef("gs://corpus/ec-guideline-ch4.txt");
        SourceRef filingRef = new SourceRef("gs://filings/annual-return-2024.pdf");
        StoredDocument corpus = new StoredDocument(
                corpusRef, DocumentKind.REGULATORY_CORPUS, "text/plain",
                "base rate x days x deterrence".getBytes());
        StoredDocument filing = new StoredDocument(
                filingRef, DocumentKind.FILING, "application/pdf", new byte[] {4, 5, 6});
        cloudStorage.put(corpus);
        cloudStorage.put(filing);

        // --- Firestore: one issued certificate persisted and published ---
        FacilityId facility = new FacilityId("facility-routing-1");
        CertificationVerdict verdict = new DefaultCertificationService(
                firestoreCertificates, firestoreRegistry, CERT_VALIDITY)
                .evaluate(facility, improvingHistory(facility), MIN_OBSERVATION);
        assertThat(verdict.status()).isEqualTo(VerdictStatus.ISSUED);
        String certReference = verdict.certificate().orElseThrow().reference();

        // --- Read back: BigQuery rows land only in their own dataset (Req 12.2) ---
        assertThat(bigQuery.rows(BigQueryDataset.TELEMETRY)).containsExactly(telemetry);
        assertThat(bigQuery.rows(BigQueryDataset.ANALYTICS)).containsExactly(analytics);
        assertThat(bigQuery.rows(BigQueryDataset.EID_ERD_SERIES)).containsExactly(series);
        assertThat(bigQuery.rows(BigQueryDataset.EMBEDDINGS)).isEmpty();
        assertThat(bigQuery.rows(BigQueryDataset.CONTINUOUS_QUERY_LANDING)).isEmpty();
        assertThat(bigQuery.totalRows()).isEqualTo(3);

        // --- Read back: Cloud Storage resolves documents by their primary-source ref (Req 12.2) ---
        assertThat(cloudStorage.get(corpusRef)).contains(corpus);
        assertThat(cloudStorage.get(filingRef)).contains(filing);
        assertThat(cloudStorage.get(new SourceRef("gs://corpus/does-not-exist.txt"))).isEmpty();
        assertThat(cloudStorage.size()).isEqualTo(2);

        // --- Read back: Firestore certificate + registry carry the issued certificate (Req 12.2, 9.5) ---
        assertThat(firestoreCertificates.findByReference(certReference))
                .hasValueSatisfying(c -> assertThat(c.facility()).isEqualTo(facility));
        assertThat(firestoreRegistry.findByReference(certReference))
                .hasValueSatisfying(e -> {
                    assertThat(e.facility()).isEqualTo(facility);
                    assertThat(e.verdict()).isEqualTo(VerdictStatus.ISSUED);
                });

        // --- The three store families stayed isolated: a BigQuery key is not a Cloud Storage ref ---
        assertThat(cloudStorage.get(new SourceRef("machine-7"))).isEmpty();
        assertThat(firestoreCertificates.size()).isEqualTo(1);
        assertThat(firestoreRegistry.size()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // (b) End-to-end registry publication via the real certification service (Req 9.5, 9.6)
    // ------------------------------------------------------------------------------------------

    /**
     * Drives the real {@link DefaultCertificationService} so an ISSUED verdict persists the
     * certificate to the Firestore certificate store and publishes its public projection to the
     * registry (Req 9.5).
     */
    @Test
    void issuedCertificateIsPersistedAndPublishedToTheRegistry() {
        InMemoryCertificateStore store = new InMemoryCertificateStore();
        InMemoryPublicRegistry registry = new InMemoryPublicRegistry();
        DefaultCertificationService service =
                new DefaultCertificationService(store, registry, CERT_VALIDITY);
        FacilityId facility = new FacilityId("facility-issued");

        CertificationVerdict verdict =
                service.evaluate(facility, improvingHistory(facility), MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.ISSUED);
        Certificate certificate = verdict.certificate().orElseThrow();

        // Persisted to Firestore certificate store (Req 9.4, 9.5).
        assertThat(store.size()).isEqualTo(1);
        assertThat(store.findByReference(certificate.reference())).contains(certificate);

        // Published to the public registry with the certificate's public projection (Req 9.5).
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.findByReference(certificate.reference()))
                .hasValueSatisfying(entry -> {
                    assertThat(entry.facility()).isEqualTo(facility);
                    assertThat(entry.verdict()).isEqualTo(VerdictStatus.ISSUED);
                    assertThat(entry.validFrom()).isEqualTo(certificate.validFrom());
                    assertThat(entry.validUntil()).isEqualTo(certificate.validUntil());
                });
    }

    /**
     * Drives the real {@link DefaultCertificationService} with a history shorter than the minimum
     * observation period: the verdict is withheld for insufficient history, nothing is persisted or
     * published, and the facility is omitted from the registry (Req 9.6).
     */
    @Test
    void insufficientHistoryVerdictPersistsNothingAndIsOmittedFromRegistry() {
        InMemoryCertificateStore store = new InMemoryCertificateStore();
        InMemoryPublicRegistry registry = new InMemoryPublicRegistry();
        DefaultCertificationService service =
                new DefaultCertificationService(store, registry, CERT_VALIDITY);
        FacilityId facility = new FacilityId("facility-thin-history");

        CertificationVerdict verdict =
                service.evaluate(facility, shortHistory(facility), MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_INSUFFICIENT_HISTORY);
        assertThat(verdict.certificate()).isEmpty();
        assertThat(verdict.routedToHumanReview()).isTrue();

        // No certificate persisted and no registry entry published (Req 9.6).
        assertThat(store.size()).isZero();
        assertThat(registry.size()).isZero();
    }

    /**
     * Drives the real {@link DefaultCertificationService} with gaming evidence (an unverifiable
     * reading): the verdict is withheld for gaming, nothing is persisted or published, and the
     * facility is omitted from the registry (Req 9.6).
     */
    @Test
    void gamingVerdictPersistsNothingAndIsOmittedFromRegistry() {
        InMemoryCertificateStore store = new InMemoryCertificateStore();
        InMemoryPublicRegistry registry = new InMemoryPublicRegistry();
        DefaultCertificationService service =
                new DefaultCertificationService(store, registry, CERT_VALIDITY);
        FacilityId facility = new FacilityId("facility-gaming");

        CertificationVerdict verdict =
                service.evaluate(facility, gamingHistory(facility), MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_GAMING);
        assertThat(verdict.certificate()).isEmpty();

        // No certificate persisted and no registry entry published (Req 9.6).
        assertThat(store.size()).isZero();
        assertThat(registry.size()).isZero();
    }

    /**
     * Confirms the registry carries only issued facilities when a mix of verdicts is evaluated
     * against the same stores: an issued facility is published while a withheld one is omitted
     * (Req 9.5, 9.6).
     */
    @Test
    void onlyIssuedFacilitiesAppearInTheRegistryWhenVerdictsAreMixed() {
        InMemoryCertificateStore store = new InMemoryCertificateStore();
        InMemoryPublicRegistry registry = new InMemoryPublicRegistry();
        DefaultCertificationService service =
                new DefaultCertificationService(store, registry, CERT_VALIDITY);

        FacilityId issuedFacility = new FacilityId("facility-mixed-issued");
        FacilityId withheldFacility = new FacilityId("facility-mixed-withheld");

        CertificationVerdict issued =
                service.evaluate(issuedFacility, improvingHistory(issuedFacility), MIN_OBSERVATION);
        CertificationVerdict withheld =
                service.evaluate(withheldFacility, shortHistory(withheldFacility), MIN_OBSERVATION);

        assertThat(issued.status()).isEqualTo(VerdictStatus.ISSUED);
        assertThat(withheld.status()).isEqualTo(VerdictStatus.WITHHELD_INSUFFICIENT_HISTORY);

        // Exactly one certificate persisted and one registry entry — the issued facility only.
        assertThat(store.size()).isEqualTo(1);
        assertThat(registry.size()).isEqualTo(1);
        String issuedRef = issued.certificate().orElseThrow().reference();
        assertThat(registry.findByReference(issuedRef))
                .hasValueSatisfying(e -> assertThat(e.facility()).isEqualTo(issuedFacility));

        // The withheld facility is absent from both the certificate store and the registry.
        assertThat(store.findByReference(
                "cert:" + withheldFacility.value() + ":0:0")).isEmpty();
        assertThat(registry.findByReference(
                "cert:" + withheldFacility.value() + ":0:0")).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------

    /**
     * Builds a verified, sustained-improvement effort history spanning more than the minimum
     * observation period so {@link DefaultCertificationService} issues a certificate (Req 9.1,
     * 9.2).
     *
     * @param facility the facility the history belongs to
     * @return a sustained-improvement effort history
     */
    private static EffortHistory improvingHistory(FacilityId facility) {
        return new EffortHistory(facility, List.of(
                verifiedPoint("2024-01-01T00:00:00Z", 1.0, "reading-a"),
                verifiedPoint("2024-02-01T00:00:00Z", 2.0, "reading-b"),
                verifiedPoint("2024-03-15T00:00:00Z", 3.5, "reading-c")));
    }

    /**
     * Builds a verified history that spans less than the minimum observation period so the verdict
     * is withheld for insufficient history (Req 9.3).
     *
     * @param facility the facility the history belongs to
     * @return a too-short effort history
     */
    private static EffortHistory shortHistory(FacilityId facility) {
        return new EffortHistory(facility, List.of(
                verifiedPoint("2024-01-01T00:00:00Z", 1.0, "reading-a"),
                verifiedPoint("2024-01-05T00:00:00Z", 2.0, "reading-b")));
    }

    /**
     * Builds a history containing an unverifiable reading, which the integrity gate treats as a
     * gaming signal (Req 9.6).
     *
     * @param facility the facility the history belongs to
     * @return an effort history with an unverifiable reading
     */
    private static EffortHistory gamingHistory(FacilityId facility) {
        return new EffortHistory(facility, List.of(
                verifiedPoint("2024-01-01T00:00:00Z", 1.0, "reading-a"),
                verifiedPoint("2024-02-01T00:00:00Z", 2.0, "reading-b"),
                unverifiedPoint("2024-03-15T00:00:00Z", 3.5, "reading-unverifiable")));
    }

    private static MeasurementPoint verifiedPoint(String at, double metric, String refId) {
        return new MeasurementPoint(
                Instant.parse(at), metric, true,
                new EvidenceLink(SourceType.READING, new SourceRef(refId)));
    }

    private static MeasurementPoint unverifiedPoint(String at, double metric, String refId) {
        return new MeasurementPoint(
                Instant.parse(at), metric, false,
                new EvidenceLink(SourceType.READING, new SourceRef(refId)));
    }
}
