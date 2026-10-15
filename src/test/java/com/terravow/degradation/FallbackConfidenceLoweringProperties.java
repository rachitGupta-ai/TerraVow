package com.terravow.degradation;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceType;

import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.Scale;

/**
 * Property-based test for Correctness Property 4 — fallback paths strictly lower confidence.
 *
 * <p>Validates that for <em>any</em> primary-path confidence strictly greater than {@code 0.0},
 * when the primary path is unavailable (it errors or times out) and the predefined safe fallback
 * succeeds, the {@link DegradationPolicy} assigns the fallback result a confidence that is
 * <em>strictly lower</em> than the primary confidence (Req 1.5, 12.4, 14.10), and records the
 * substituted path as a single {@link SourceType#FALLBACK_PATH} Evidence_Chain link encoding both
 * the fallback identifier and the replaced primary-path identifier (Req 7.4, 12.5). Degradation is
 * confidence-lowering, never guarantee-weakening.
 *
 * <p>The property holds across the real wired fallback table ({@link ManagedPath#fallback()}): the
 * primary path and its fallback are drawn as a wired primary/fallback pair, so the test exercises
 * exactly the managed-path substitutions the design mandates.
 *
 * <p><b>Validates: Requirements 1.5, 7.4, 12.4, 12.5, 14.10</b>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.5, 7.4, 12.4,
 *      12.5, 14.10</a>
 */
class FallbackConfidenceLoweringProperties {

    private final DegradationPolicy policy = new DefaultDegradationPolicy();

    // Feature: terravow, Property 4: Fallback paths strictly lower confidence.
    @Property(tries = 100)
    void fallbackSucceedsAtStrictlyLowerConfidenceRecordingSubstitutedPath(
            @ForAll("wiredPairs") ManagedPath primaryPath,
            @ForAll @DoubleRange(min = 0.0001, max = 1.0) @Scale(4) double primaryConfidenceValue,
            @ForAll String primaryValue,
            @ForAll String fallbackValue) {

        Confidence primaryConfidence = new Confidence(primaryConfidenceValue);
        ManagedPath fallbackPath = primaryPath.fallback().orElseThrow();

        PrimaryPath<String> primary = failingPrimary(primaryPath, primaryConfidence, primaryValue);
        FallbackPath<String> fallback = succeedingFallback(fallbackPath, fallbackValue);

        PathResult<String> result =
                policy.executeWithFallback(primary, Optional.of(fallback));

        // The fallback produced the value.
        assertThat(result.determined()).isTrue();
        assertThat(result.usedFallback()).isTrue();
        assertThat(result.value()).contains(fallbackValue);
        assertThat(result.routedToHumanReview()).isFalse();

        // Req 1.5/12.4/14.10 — strictly lower than the primary confidence.
        assertThat(result.confidence().value()).isLessThan(primaryConfidence.value());

        // Req 7.4/12.5 — exactly one FALLBACK_PATH link encoding fallback id + replaced primary id.
        assertThat(result.evidence().links()).hasSize(1);
        EvidenceLink link = result.evidence().links().get(0);
        assertThat(link.type()).isEqualTo(SourceType.FALLBACK_PATH);
        assertThat(link.ref().id())
                .contains(fallbackPath.id())
                .contains(primaryPath.id());
    }

    // ---- test doubles ----------------------------------------------------------------------------

    /** A primary path that is unavailable: it throws to signal error/timeout (Req 12.3). */
    private static PrimaryPath<String> failingPrimary(
            ManagedPath path, Confidence confidence, String value) {
        return new PrimaryPath<>() {
            @Override
            public ManagedPath path() {
                return path;
            }

            @Override
            public Confidence confidence() {
                return confidence;
            }

            @Override
            public String execute() {
                throw new PathUnavailableException(path, "simulated unavailability for " + value);
            }
        };
    }

    /** A fallback path that returns a fixed value. */
    private static FallbackPath<String> succeedingFallback(ManagedPath path, String value) {
        return new FallbackPath<>() {
            @Override
            public ManagedPath path() {
                return path;
            }

            @Override
            public String execute() {
                return value;
            }
        };
    }

    /** The managed paths that have a wired safe fallback (every non-terminal path). */
    @Provide
    Arbitrary<ManagedPath> wiredPairs() {
        List<ManagedPath> withFallback =
                java.util.Arrays.stream(ManagedPath.values())
                        .filter(p -> p.fallback().isPresent())
                        .toList();
        return Arbitraries.of(withFallback);
    }
}
