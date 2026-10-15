package com.terravow.capability.certification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultCertificationService}, exercising the ordered certification
 * algorithm with specific examples and edge cases (Req 9.1, 9.3, 9.4, 9.5, 9.6).
 */
class DefaultCertificationServiceTest {

    private static final FacilityId FACILITY = new FacilityId("facility-1");
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

    private static EvidenceLink reading(String id) {
        return new EvidenceLink(SourceType.READING, new SourceRef(id));
    }

    private static MeasurementPoint point(long dayOffset, double metric, boolean verified) {
        return new MeasurementPoint(
                T0.plus(Duration.ofDays(dayOffset)), metric, verified, reading("r-" + dayOffset));
    }

    // Req 9.1, 9.2, 9.4, 9.5 — a sustained verified trajectory over the minimum period is certified,
    // persisted, and published.
    @Test
    void sustainedTrajectoryIsIssuedPersistedAndPublished() {
        EffortHistory history = new EffortHistory(
                FACILITY,
                List.of(point(0, 0.10, true), point(90, 0.30, true), point(180, 0.55, true)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.ISSUED);
        assertThat(verdict.certificate()).isPresent();
        assertThat(verdict.routedToHumanReview()).isFalse();

        Certificate cert = verdict.certificate().orElseThrow();
        // Persisted with confidence + Evidence_Chain (Req 9.4).
        assertThat(store.findByReference(cert.reference())).contains(cert);
        assertThat(cert.evidence().links()).hasSize(3);
        assertThat(cert.confidence().value()).isGreaterThan(0.0);
        // Published to the public registry with the four published fields (Req 9.5).
        assertThat(registry.findByReference(cert.reference())).isPresent();
        RegistryEntry entry = registry.findByReference(cert.reference()).orElseThrow();
        assertThat(entry.facility()).isEqualTo(FACILITY);
        assertThat(entry.verdict()).isEqualTo(VerdictStatus.ISSUED);
        assertThat(entry.validFrom()).isEqualTo(cert.validFrom());
        assertThat(entry.validUntil()).isEqualTo(cert.validFrom().plus(VALIDITY));
    }

    // Req 9.3 — verified history shorter than the minimum period is withheld as insufficient
    // history and routed to human review; nothing is persisted or published.
    @Test
    void shortHistoryIsWithheldAsInsufficientAndRouted() {
        EffortHistory history = new EffortHistory(
                FACILITY, List.of(point(0, 0.10, true), point(30, 0.40, true)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_INSUFFICIENT_HISTORY);
        assertThat(verdict.certificate()).isEmpty();
        assertThat(verdict.routedToHumanReview()).isTrue();
        assertThat(store.size()).isZero();
        assertThat(registry.size()).isZero();
    }

    // Req 9.2, 9.3 — a single verified point (a snapshot) is insufficient regardless of span.
    @Test
    void singleVerifiedPointIsInsufficient() {
        EffortHistory history = new EffortHistory(FACILITY, List.of(point(0, 0.10, true)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_INSUFFICIENT_HISTORY);
        assertThat(verdict.routedToHumanReview()).isTrue();
    }

    // Req 9.6 — an unverifiable reading withholds certification for gaming and omits from registry.
    @Test
    void unverifiableReadingWithholdsForGaming() {
        EffortHistory history = new EffortHistory(
                FACILITY,
                List.of(point(0, 0.10, true), point(90, 0.30, false), point(180, 0.55, true)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_GAMING);
        assertThat(verdict.certificate()).isEmpty();
        assertThat(verdict.routedToHumanReview()).isTrue();
        assertThat(verdict.evidence().links()).isNotEmpty();
        assertThat(store.size()).isZero();
        assertThat(registry.size()).isZero();
    }

    // Req 9.6 — contradictory points (same instant, conflicting metric) withhold for gaming.
    @Test
    void contradictoryPointsWithholdForGaming() {
        MeasurementPoint a = new MeasurementPoint(T0, 0.20, true, reading("a"));
        MeasurementPoint b = new MeasurementPoint(T0, 0.80, true, reading("b"));
        MeasurementPoint later = point(180, 0.90, true);
        EffortHistory history = new EffortHistory(FACILITY, List.of(a, b, later));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_GAMING);
        assertThat(store.size()).isZero();
        assertThat(registry.size()).isZero();
    }

    // Req 9.6 — a late-jump gaming pattern (earlier regression, last-minute spike) is withheld.
    @Test
    void lateJumpGamingPatternIsWithheld() {
        EffortHistory history = new EffortHistory(
                FACILITY,
                List.of(point(0, 0.50, true), point(90, 0.20, true), point(180, 0.70, true)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_GAMING);
        assertThat(registry.size()).isZero();
    }

    // Req 9.1 — a verified trajectory with no net improvement is not certified.
    @Test
    void noNetImprovementIsNotCertified() {
        EffortHistory history = new EffortHistory(
                FACILITY,
                List.of(point(0, 0.50, true), point(90, 0.50, true), point(180, 0.50, true)));

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_GAMING);
        assertThat(store.size()).isZero();
    }

    // Req 9.5 — publication is idempotent: re-evaluating identical inputs does not duplicate entries.
    @Test
    void reEvaluationIsIdempotent() {
        EffortHistory history = new EffortHistory(
                FACILITY,
                List.of(point(0, 0.10, true), point(90, 0.30, true), point(180, 0.55, true)));

        service.evaluate(FACILITY, history, MIN_OBSERVATION);
        service.evaluate(FACILITY, history, MIN_OBSERVATION);

        assertThat(store.size()).isEqualTo(1);
        assertThat(registry.size()).isEqualTo(1);
    }

    // Input validation — a mismatched facility is rejected.
    @Test
    void mismatchedFacilityIsRejected() {
        EffortHistory history = new EffortHistory(
                new FacilityId("other"), List.of(point(0, 0.1, true), point(180, 0.5, true)));

        assertThatThrownBy(() -> service.evaluate(FACILITY, history, MIN_OBSERVATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different facility");
    }

    // Input validation — a non-positive minimum observation period is rejected.
    @Test
    void nonPositiveMinObservationIsRejected() {
        EffortHistory history = new EffortHistory(FACILITY, List.of(point(0, 0.1, true)));

        assertThatThrownBy(() -> service.evaluate(FACILITY, history, Period.ofDays(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minObservation must be positive");
    }
}
