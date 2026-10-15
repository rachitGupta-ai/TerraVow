package com.terravow.capability.certification;

// Feature: terravow, Property 30: Issued certification uses at least two measurement points

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for trajectory certification's two-measurement-point rule (Property 30).
 *
 * <p>Certification is deliberately trajectory-based, never a single-point-in-time snapshot: an
 * issued verdict must be derived from at least two verified measurement points separated across the
 * minimum observation period (Req 9.2). This property drives {@link DefaultCertificationService}
 * with randomly generated verified effort histories and asserts the structural guarantee: whenever
 * the service returns an {@link VerdictStatus#ISSUED} verdict, the history it was derived from
 * contained at least two verified measurement points, and the issued certificate's Evidence_Chain
 * links at least two primary sources (one per verified point). The contrapositive is also checked:
 * a history with fewer than two verified points is never issued.
 *
 * <p><strong>Validates: Requirements 9.2</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.2</a>
 */
class TwoMeasurementPointCertificationProperties {

    private static final FacilityId FACILITY = new FacilityId("facility-prop-30");
    private static final Period MIN_OBSERVATION = Period.ofDays(90);
    private static final Duration VALIDITY = Duration.ofDays(365);
    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    /**
     * Property 30: any {@link VerdictStatus#ISSUED} verdict was derived from at least two verified
     * measurement points. For every generated history, if the service issues a certificate then the
     * history held two or more verified points, and the certificate's Evidence_Chain carries one
     * link per verified point (so at least two primary-source links). Conversely a history with
     * fewer than two verified points is never issued (Req 9.2).
     */
    @Property(tries = 100)
    void issuedVerdictUsesAtLeastTwoVerifiedPoints(@ForAll("histories") EffortHistory history) {
        InMemoryCertificateStore store = new InMemoryCertificateStore();
        InMemoryPublicRegistry registry = new InMemoryPublicRegistry();
        CertificationService service =
                new DefaultCertificationService(store, registry, VALIDITY);

        long verifiedCount = history.points().stream().filter(MeasurementPoint::verified).count();

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        if (verdict.status() == VerdictStatus.ISSUED) {
            // Derived from >= 2 verified measurement points, never a single snapshot (Req 9.2).
            assertThat(verifiedCount).isGreaterThanOrEqualTo(2L);

            Certificate certificate = verdict.certificate().orElseThrow();
            // The Evidence_Chain links one primary source per verified point, hence >= 2 links.
            assertThat(certificate.evidence().links())
                    .hasSize((int) verifiedCount)
                    .hasSizeGreaterThanOrEqualTo(2);
        } else {
            // Fewer than two verified points can never yield an issued certificate (Req 9.2).
            if (verifiedCount < 2L) {
                assertThat(verdict.status()).isNotEqualTo(VerdictStatus.ISSUED);
                assertThat(verdict.certificate()).isEmpty();
            }
        }
    }

    /**
     * Arbitrary effort histories spanning the observation period. Points are all verified and
     * strictly monotonically improving, so when at least two exist the service reaches the issue
     * path; the generator also produces single-point and empty histories to exercise the
     * insufficient-history branch. All points share a single instant per index so no contradictory
     * same-instant conflict is generated.
     *
     * @param count the number of measurement points to generate (0..5)
     * @return a generator of {@link EffortHistory} for {@link #FACILITY}
     */
    @Provide
    Arbitrary<EffortHistory> histories(@ForAll @IntRange(min = 0, max = 5) int count) {
        // Spread points evenly across >= the minimum observation window so, when there are two or
        // more, the verified series spans at least minObservation (90 days). We use a 180-day span.
        Arbitrary<List<Double>> metricLists =
                Arbitraries.doubles().between(0.0, 100.0).ofScale(3).list()
                        .ofSize(Math.max(count, 0));

        return metricLists.map(metrics -> {
            List<MeasurementPoint> points = new ArrayList<>(count);
            // Sort ascending so the verified trajectory is strictly improving when count >= 2,
            // guaranteeing a positive, sustained rate of improvement on the issue path.
            List<Double> sorted = new ArrayList<>(metrics);
            sorted.sort(Double::compareTo);
            for (int i = 0; i < count; i++) {
                long dayOffset = count <= 1 ? 0L : (long) (180.0 * i / (count - 1));
                // Nudge each metric strictly upward by its index so equal draws still improve,
                // avoiding the no-net-improvement and late-jump gaming branches.
                double metric = sorted.get(i) + i;
                EvidenceLink source =
                        new EvidenceLink(SourceType.READING, new SourceRef("r-" + i));
                points.add(new MeasurementPoint(
                        T0.plus(Duration.ofDays(dayOffset)), metric, true, source));
            }
            return new EffortHistory(FACILITY, points);
        });
    }
}
