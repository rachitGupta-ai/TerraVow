package com.terravow.degradation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceType;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultDegradationPolicy}, exercising the primary-success, fallback, and
 * undetermined paths with specific examples and boundary values (Req 12.3–12.6).
 */
class DefaultDegradationPolicyTest {

    private final DegradationPolicy policy = new DefaultDegradationPolicy();

    // ---- test doubles ----------------------------------------------------------------------------

    /** A primary path that returns a fixed value at a fixed confidence. */
    private static PrimaryPath<String> succeedingPrimary(
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
                return value;
            }
        };
    }

    /** A primary path that is unavailable: it throws to signal error/timeout (Req 12.3). */
    private static PrimaryPath<String> failingPrimary(ManagedPath path, Confidence confidence) {
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
                throw new PathUnavailableException(path, "simulated timeout");
            }
        };
    }

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

    private static FallbackPath<String> failingFallback(ManagedPath path) {
        return new FallbackPath<>() {
            @Override
            public ManagedPath path() {
                return path;
            }

            @Override
            public String execute() {
                throw new PathUnavailableException(path, "fallback also down");
            }
        };
    }

    // ---- Req 12.3/12.4: primary success passes through -------------------------------------------

    @Test
    void primarySuccessPassesThroughAtPrimaryConfidence() {
        PrimaryPath<String> primary =
                succeedingPrimary(ManagedPath.AGENT_ENGINE, new Confidence(0.9), "agent-answer");

        PathResult<String> result = policy.executeWithFallback(
                primary, Optional.of(succeedingFallback(ManagedPath.DIRECT_GEMINI, "fallback")));

        assertThat(result.determined()).isTrue();
        assertThat(result.usedFallback()).isFalse();
        assertThat(result.value()).contains("agent-answer");
        assertThat(result.confidence().value()).isEqualTo(0.9);
        assertThat(result.routedToHumanReview()).isFalse();
        assertThat(result.unavailablePath()).isEmpty();
        assertThat(result.evidence().links()).isEmpty();
    }

    // ---- Req 12.3/12.4/12.5: primary failure → fallback at strictly lower confidence -------------

    @Test
    void primaryFailureFallsBackAtStrictlyLowerConfidenceRecordingSubstitutedPath() {
        Confidence primaryConfidence = new Confidence(0.9);
        PrimaryPath<String> primary =
                failingPrimary(ManagedPath.AGENT_ENGINE, primaryConfidence);

        PathResult<String> result = policy.executeWithFallback(
                primary,
                Optional.of(succeedingFallback(ManagedPath.DIRECT_GEMINI, "gemini-answer")));

        assertThat(result.determined()).isTrue();
        assertThat(result.usedFallback()).isTrue();
        assertThat(result.value()).contains("gemini-answer");
        // Req 12.4 — strictly lower than the primary confidence.
        assertThat(result.confidence().value()).isLessThan(primaryConfidence.value());
        assertThat(result.routedToHumanReview()).isFalse();

        // Req 12.5 — a FALLBACK_PATH link records the fallback id and the replaced primary id.
        assertThat(result.evidence().links()).hasSize(1);
        EvidenceLink link = result.evidence().links().get(0);
        assertThat(link.type()).isEqualTo(SourceType.FALLBACK_PATH);
        assertThat(link.ref().id())
                .contains(ManagedPath.DIRECT_GEMINI.id())
                .contains(ManagedPath.AGENT_ENGINE.id());
    }

    @Test
    void nonUnavailableRuntimeExceptionIsAlsoTreatedAsUnavailable() {
        PrimaryPath<String> primary = new PrimaryPath<>() {
            @Override
            public ManagedPath path() {
                return ManagedPath.GEMINI_PRO;
            }

            @Override
            public Confidence confidence() {
                return new Confidence(0.8);
            }

            @Override
            public String execute() {
                throw new IllegalStateException("unexpected error, not a timeout");
            }
        };

        PathResult<String> result = policy.executeWithFallback(
                primary, Optional.of(succeedingFallback(ManagedPath.GEMINI_FLASH, "flash")));

        assertThat(result.usedFallback()).isTrue();
        assertThat(result.value()).contains("flash");
    }

    // ---- Req 12.6: no safe fallback → undetermined + human review --------------------------------

    @Test
    void primaryFailureWithNoFallbackYieldsUndeterminedRoutedToReview() {
        PrimaryPath<String> primary =
                failingPrimary(ManagedPath.CORPUS_SCAN, new Confidence(0.7));

        PathResult<String> result = policy.executeWithFallback(primary, Optional.empty());

        assertThat(result.determined()).isFalse();
        assertThat(result.value()).isEmpty();
        assertThat(result.usedFallback()).isFalse();
        assertThat(result.confidence()).isEqualTo(Confidence.NONE);
        assertThat(result.routedToHumanReview()).isTrue();
        // Req 12.6 — the unavailable path is recorded.
        assertThat(result.unavailablePath()).contains(ManagedPath.CORPUS_SCAN);
        assertThat(result.evidence().links()).hasSize(1);
        assertThat(result.evidence().links().get(0).type()).isEqualTo(SourceType.FALLBACK_PATH);
        assertThat(result.evidence().links().get(0).ref().id())
                .contains(ManagedPath.CORPUS_SCAN.id());
    }

    @Test
    void fallbackItselfUnavailableYieldsUndeterminedRecordingFallbackPath() {
        PrimaryPath<String> primary =
                failingPrimary(ManagedPath.VERTEX_AI_SEARCH, new Confidence(0.8));

        PathResult<String> result = policy.executeWithFallback(
                primary, Optional.of(failingFallback(ManagedPath.BIGQUERY_VECTOR)));

        assertThat(result.determined()).isFalse();
        assertThat(result.routedToHumanReview()).isTrue();
        assertThat(result.unavailablePath()).contains(ManagedPath.BIGQUERY_VECTOR);
    }

    @Test
    void primaryConfidenceOfZeroCannotBeLoweredSoFallbackYieldsUndetermined() {
        PrimaryPath<String> primary = failingPrimary(ManagedPath.MEMORY_BANK, Confidence.NONE);

        PathResult<String> result = policy.executeWithFallback(
                primary, Optional.of(succeedingFallback(ManagedPath.STATELESS, "stateless")));

        // A strictly-lower confidence than 0.0 is unrepresentable, so the policy declines to
        // produce an equal-or-higher fallback and falls through to undetermined (Req 12.4/12.6).
        assertThat(result.determined()).isFalse();
        assertThat(result.routedToHumanReview()).isTrue();
        assertThat(result.unavailablePath()).contains(ManagedPath.MEMORY_BANK);
    }

    // ---- registry wiring -------------------------------------------------------------------------

    @Test
    void managedPathRegistryWiresEveryKnownFallback() {
        assertThat(ManagedPath.AGENT_ENGINE.fallback()).contains(ManagedPath.DIRECT_GEMINI);
        assertThat(ManagedPath.VERTEX_AI_SEARCH.fallback()).contains(ManagedPath.BIGQUERY_VECTOR);
        assertThat(ManagedPath.BIGQUERY_VECTOR_SEARCH.fallback()).contains(ManagedPath.CORPUS_SCAN);
        assertThat(ManagedPath.CONTINUOUS_QUERY.fallback()).contains(ManagedPath.BATCH_MICRO_POLL);
        assertThat(ManagedPath.GEMINI_PRO.fallback()).contains(ManagedPath.GEMINI_FLASH);
        assertThat(ManagedPath.MEMORY_BANK.fallback()).contains(ManagedPath.STATELESS);

        // Terminal fallbacks have no further fallback (Req 12.6).
        assertThat(ManagedPath.DIRECT_GEMINI.fallback()).isEmpty();
        assertThat(ManagedPath.CORPUS_SCAN.fallback()).isEmpty();
        assertThat(ManagedPath.STATELESS.fallback()).isEmpty();
    }

    @Test
    void endToEndUsesRegistryFallbackForAgentEngine() {
        PrimaryPath<String> primary =
                failingPrimary(ManagedPath.AGENT_ENGINE, new Confidence(0.85));
        ManagedPath wiredFallback = ManagedPath.AGENT_ENGINE.fallback().orElseThrow();

        PathResult<String> result = policy.executeWithFallback(
                primary, Optional.of(succeedingFallback(wiredFallback, "direct-gemini-answer")));

        assertThat(result.usedFallback()).isTrue();
        assertThat(result.value()).contains("direct-gemini-answer");
        assertThat(result.evidence().links().get(0).ref().id())
                .contains(wiredFallback.id());
    }

    // ---- construction guards ---------------------------------------------------------------------

    @Test
    void nonPositivePenaltyIsRejected() {
        assertThatThrownBy(() -> new DefaultDegradationPolicy(0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefaultDegradationPolicy(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
