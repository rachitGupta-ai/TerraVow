package com.terravow.capability.certification;

// Feature: terravow, Property 31: Gaming or contradictory evidence withholds certification

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
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for certification withholding on gaming or contradictory evidence
 * (Property 31).
 *
 * <p>When the supporting evidence is contradictory, unverifiable, or consistent with metric-gaming,
 * certification is withheld, the facility is omitted from the public registry, and the findings are
 * recorded in the Evidence_Chain (Req 9.6). This property constructs histories that each embed one
 * of two integrity faults — an unverifiable reading (a point flagged {@code verified = false}) or
 * contradictory same-instant points (two readings at the identical instant reporting different
 * improvement metrics) — otherwise spanning the minimum observation period with an improving,
 * verified series. For every such history the verdict is {@link VerdictStatus#WITHHELD_GAMING}
 * (never {@link VerdictStatus#ISSUED}), no certificate is produced, the evaluation is routed to
 * human review, the Evidence_Chain is non-empty, and nothing is persisted or published.
 *
 * <p><strong>Validates: Requirements 9.6</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.6</a>
 */
class GamingContradictionWithholdingProperties {

    private static final FacilityId FACILITY = new FacilityId("facility-prop-31");
    private static final Period MIN_OBSERVATION = Period.ofDays(90);
    private static final Duration VALIDITY = Duration.ofDays(365);
    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    /**
     * Property 31: a history carrying an unverifiable reading or contradictory same-instant points
     * yields {@link VerdictStatus#WITHHELD_GAMING}, is omitted from the registry, carries recorded
     * findings, and is routed to human review — never issued (Req 9.6).
     */
    @Property(tries = 100)
    void gamingOrContradictoryEvidenceWithholdsAndOmitsFromRegistry(
            @ForAll("faultyHistories") EffortHistory history) {
        InMemoryCertificateStore store = new InMemoryCertificateStore();
        InMemoryPublicRegistry registry = new InMemoryPublicRegistry();
        CertificationService service =
                new DefaultCertificationService(store, registry, VALIDITY);

        CertificationVerdict verdict = service.evaluate(FACILITY, history, MIN_OBSERVATION);

        // Withheld for gaming, never issued (Req 9.6).
        assertThat(verdict.status()).isEqualTo(VerdictStatus.WITHHELD_GAMING);
        assertThat(verdict.certificate()).isEmpty();
        // Routed to human review with recorded findings in the Evidence_Chain (Req 9.6).
        assertThat(verdict.routedToHumanReview()).isTrue();
        assertThat(verdict.evidence().links()).isNotEmpty();
        // Omitted from the registry, and nothing persisted (Req 9.6).
        assertThat(store.size()).isZero();
        assertThat(registry.size()).isZero();
    }

    /**
     * Histories that each embed exactly one integrity fault while otherwise forming an improving,
     * verified series spanning at least the minimum observation period. Half carry an unverifiable
     * reading; half carry contradictory same-instant points. Both faults are detected by the
     * integrity gate ahead of any sufficiency or trajectory check, so the span and improvement are
     * incidental — but keeping them well-formed ensures the withholding is attributable to the
     * fault rather than to a different gate.
     *
     * @return a generator of faulty {@link EffortHistory} for {@link #FACILITY}
     */
    @Provide
    Arbitrary<EffortHistory> faultyHistories() {
        Arbitrary<EffortHistory> unverifiable = unverifiableReadingHistories();
        Arbitrary<EffortHistory> contradictory = contradictoryPointHistories();
        return Arbitraries.oneOf(unverifiable, contradictory);
    }

    /**
     * Histories with a sound verified trajectory into which a single unverifiable reading is
     * injected at an interior position (Req 9.6).
     */
    private Arbitrary<EffortHistory> unverifiableReadingHistories() {
        // 3..6 points so there is always an interior slot to flag unverifiable.
        Arbitrary<Integer> counts = Arbitraries.integers().between(3, 6);
        return counts.flatMap(count ->
                Arbitraries.integers().between(1, count - 1).map(badIndex -> {
                    List<MeasurementPoint> points = improvingVerifiedSeries(count);
                    MeasurementPoint original = points.get(badIndex);
                    // Replace the interior point with an unverifiable version at the same instant.
                    points.set(badIndex, new MeasurementPoint(
                            original.observedAt(),
                            original.improvementMetric(),
                            false,
                            original.source()));
                    return new EffortHistory(FACILITY, points);
                }));
    }

    /**
     * Histories with a sound verified trajectory into which two contradictory same-instant points
     * (identical instant, different improvement metric, both verified) are injected (Req 9.6).
     */
    private Arbitrary<EffortHistory> contradictoryPointHistories() {
        Arbitrary<Integer> counts = Arbitraries.integers().between(2, 5);
        Arbitrary<Double> metricA = Arbitraries.doubles().between(0.0, 50.0).ofScale(3);
        Arbitrary<Double> gaps = Arbitraries.doubles().between(1.0, 50.0).ofScale(3);
        return Combinators.combine(counts, metricA, gaps).as((count, a, gap) -> {
            List<MeasurementPoint> points = improvingVerifiedSeries(count);
            Instant conflictInstant = T0.plus(Duration.ofDays(45));
            // Two verified readings at the same instant reporting different metrics (a vs a+gap).
            points.add(new MeasurementPoint(conflictInstant, a, true,
                    new EvidenceLink(SourceType.READING, new SourceRef("conflict-a"))));
            points.add(new MeasurementPoint(conflictInstant, a + gap, true,
                    new EvidenceLink(SourceType.READING, new SourceRef("conflict-b"))));
            return new EffortHistory(FACILITY, points);
        });
    }

    /**
     * Builds a strictly-improving, all-verified series of {@code count} points spread across a
     * 180-day window (which exceeds the 90-day minimum observation period).
     *
     * @param count the number of points (&gt;= 2)
     * @return a mutable list of verified, improving measurement points
     */
    private static List<MeasurementPoint> improvingVerifiedSeries(int count) {
        List<MeasurementPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long dayOffset = count <= 1 ? 0L : (long) (180.0 * i / (count - 1));
            points.add(new MeasurementPoint(
                    T0.plus(Duration.ofDays(dayOffset)),
                    0.10 + 0.10 * i,
                    true,
                    new EvidenceLink(SourceType.READING, new SourceRef("base-" + i))));
        }
        return points;
    }
}
