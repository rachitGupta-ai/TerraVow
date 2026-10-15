package com.terravow.capability.certification;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Supplementary example-based tests for {@link DefaultCertificationService} covering the
 * certification happy path, certificate persistence/publication, and insufficient-history
 * handling (Req 9.1, 9.3, 9.4, 9.5).
 *
 * <p>The broader behaviour matrix is already covered by {@link DefaultCertificationServiceTest}
 * (issue/persist/publish, single-point insufficiency, idempotency, and the gaming/contradiction
 * withholding modes). This class adds focused boundary cases that the existing suite does not
 * exercise:
 *
 * <ul>
 *   <li>the exact two-verified-point boundary that spans precisely the minimum observation period
 *       — the smallest trajectory that is certifiable rather than a snapshot (Req 9.1, 9.2);</li>
 *   <li>that the published {@link RegistryEntry} projects exactly the four public fields and
 *       carries the stored certificate reference (Req 9.5);</li>
 *   <li>that the insufficient-history path publishes <em>nothing</em> to the registry and persists
 *       nothing, while still routing to human review (Req 9.3).</li>
 * </ul>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.1, 9.3, 9.4, 9.5</a>
 */
class CertificationHappyPathAndPersistenceTest {

    private static final FacilityId FACILITY = new FacilityId("facility-supp");
    private static final Period MIN_OBSERVATION = Period.ofDays(90);
    private static final Duration VALIDITY = Duration.ofDays(365);
    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    private InMemoryCertificateStore store;
    private InMemoryPublicRegistry registry;
    private CertificationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryCertificateStore();
        registry = new InMemoryPublicRegistry();
        service = new DefaultCertificationService(store, registry, VALIDITY);
    }

    private static MeasurementPoint verifiedPoint(long dayOffset, double metric) {
        return new MeasurementPoint(
                T0.plus(Duration.ofDays(dayOffset)),
                metric,
                true,
                new EvidenceLink(SourceType.READING, new SourceRef("r-" + dayOffset)));
    }

    // Req 9.1, 9.2 — exactly two verified points spanning precisely the 90-day minimum period form
    // the smallest certifiable trajectory: issued, persisted, and published.
    @Test
    void exactlyTwoPointsSpanningMinimumPeriodIsIssued() {
        EffortHistory history = new EffortHistory(
                FACILITY, List.of(verifiedPoint(0, 0.10), verifiedPoint(90, 0.40)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.ISSUED);
        Certificate cert = verdict.certificate().orElseThrow();
        assertThat(cert.evidence().links()).hasSize(2);
        assertThat(store.size()).isEqualTo(1);
        assertThat(registry.size()).isEqualTo(1);
    }

    // Req 9.4, 9.5 — the published registry entry projects exactly the four public fields and keys
    // on the persisted certificate reference; confidence and Evidence_Chain stay on the stored
    // certificate and are not part of the public projection.
    @Test
    void publishedEntryProjectsPublicFieldsFromStoredCertificate() {
        EffortHistory history = new EffortHistory(
                FACILITY,
                List.of(verifiedPoint(0, 0.10), verifiedPoint(90, 0.30), verifiedPoint(180, 0.60)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        Certificate cert = verdict.certificate().orElseThrow();
        Optional<Certificate> stored = store.findByReference(cert.reference());
        assertThat(stored).contains(cert);
        assertThat(cert.confidence().value()).isBetween(0.0, 1.0);
        assertThat(cert.evidence().links()).isNotEmpty();

        RegistryEntry entry = registry.findByReference(cert.reference()).orElseThrow();
        assertThat(entry.facility()).isEqualTo(FACILITY);
        assertThat(entry.certificateReference()).isEqualTo(cert.reference());
        assertThat(entry.verdict()).isEqualTo(VerdictStatus.ISSUED);
        assertThat(entry.validFrom()).isEqualTo(cert.validFrom());
        assertThat(entry.validUntil()).isEqualTo(cert.validUntil());
    }

    // Req 9.3 — insufficient verified history withholds, routes to human review, and publishes /
    // persists nothing (the facility stays out of the public registry).
    @Test
    void insufficientHistoryPublishesNothingAndRoutesToHumanReview() {
        EffortHistory history = new EffortHistory(
                FACILITY, List.of(verifiedPoint(0, 0.10), verifiedPoint(30, 0.50)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_INSUFFICIENT_HISTORY);
        assertThat(verdict.routedToHumanReview()).isTrue();
        assertThat(verdict.certificate()).isEmpty();
        assertThat(store.size()).isZero();
        assertThat(registry.size()).isZero();
    }
}
