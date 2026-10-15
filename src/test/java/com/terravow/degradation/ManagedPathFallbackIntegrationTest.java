package com.terravow.degradation;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Confidence;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.tool.DefaultVerifiedToolRegistry;
import com.terravow.reasoning.tool.EnvironmentalCompensationRequest;
import com.terravow.reasoning.tool.EnvironmentalCompensationTool;
import com.terravow.reasoning.tool.ToolResult;
import com.terravow.reasoning.tool.VerifiedToolRegistry;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for the managed-path graceful-degradation fallbacks (Req 12.3, 12.6).
 *
 * <p>These wire the <em>real</em> {@link DefaultDegradationPolicy} against the real
 * {@link ManagedPath} registry fallbacks and the real {@link DefaultVerifiedToolRegistry}, driving
 * end-to-end the four degradation behaviours the design mandates:
 *
 * <ul>
 *   <li>Agent-Engine error &rarr; direct-Gemini fallback lowers confidence and records the
 *       substituted path (Req 12.3–12.5).</li>
 *   <li>Vertex AI Search error &rarr; BigQuery-vector fallback lowers confidence and records the
 *       substituted path (Req 12.3–12.5).</li>
 *   <li>Verified-tool failure &rarr; {@link DeterminationStatus#UNAVAILABLE} with a recorded reason
 *       (Req 12.3), so the dependent value is marked unavailable rather than fabricated.</li>
 *   <li>No safe fallback &rarr; undetermined, routed to human review, unavailable path recorded
 *       (Req 12.6).</li>
 * </ul>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.3, 12.6</a>
 */
class ManagedPathFallbackIntegrationTest {

    private final DegradationPolicy policy = new DefaultDegradationPolicy();

    // ---- Agent-Engine error → direct-Gemini fallback (Req 12.3–12.5) -----------------------------

    @Test
    void agentEngineErrorDegradesToDirectGeminiLoweringConfidenceAndRecordingSubstitutedPath() {
        Confidence agentConfidence = new Confidence(0.9);
        ManagedPath wiredFallback = ManagedPath.AGENT_ENGINE.fallback().orElseThrow();
        assertThat(wiredFallback).isEqualTo(ManagedPath.DIRECT_GEMINI);

        PrimaryPath<String> agentEngine =
                failingPrimary(ManagedPath.AGENT_ENGINE, agentConfidence, "agent engine timed out");
        FallbackPath<String> directGemini =
                succeedingFallback(wiredFallback, "direct-gemini-answer");

        PathResult<String> result =
                policy.executeWithFallback(agentEngine, Optional.of(directGemini));

        assertThat(result.determined()).isTrue();
        assertThat(result.usedFallback()).isTrue();
        assertThat(result.value()).contains("direct-gemini-answer");
        assertThat(result.routedToHumanReview()).isFalse();
        // Req 12.4 — strictly lower confidence than the Agent-Engine primary.
        assertThat(result.confidence().value()).isLessThan(agentConfidence.value());
        // Req 12.5 — the substituted path is recorded (fallback id + replaced primary id).
        EvidenceLink link = singleFallbackLink(result);
        assertThat(link.ref().id())
                .contains(ManagedPath.DIRECT_GEMINI.id())
                .contains(ManagedPath.AGENT_ENGINE.id());
    }

    // ---- Vertex AI Search error → BigQuery-vector fallback (Req 12.3–12.5) -----------------------

    @Test
    void vertexAiSearchErrorDegradesToBigQueryVectorLoweringConfidenceAndRecordingSubstitutedPath() {
        Confidence groundingConfidence = new Confidence(0.8);
        ManagedPath wiredFallback = ManagedPath.VERTEX_AI_SEARCH.fallback().orElseThrow();
        assertThat(wiredFallback).isEqualTo(ManagedPath.BIGQUERY_VECTOR);

        PrimaryPath<String> vertexAiSearch = failingPrimary(
                ManagedPath.VERTEX_AI_SEARCH, groundingConfidence, "vertex ai search errored");
        FallbackPath<String> bigQueryVector =
                succeedingFallback(wiredFallback, "bigquery-vector-answer");

        PathResult<String> result =
                policy.executeWithFallback(vertexAiSearch, Optional.of(bigQueryVector));

        assertThat(result.determined()).isTrue();
        assertThat(result.usedFallback()).isTrue();
        assertThat(result.value()).contains("bigquery-vector-answer");
        assertThat(result.confidence().value()).isLessThan(groundingConfidence.value());
        EvidenceLink link = singleFallbackLink(result);
        assertThat(link.ref().id())
                .contains(ManagedPath.BIGQUERY_VECTOR.id())
                .contains(ManagedPath.VERTEX_AI_SEARCH.id());
    }

    // ---- Verified-tool failure → UNAVAILABLE (Req 12.3) ------------------------------------------

    @Test
    void verifiedToolFailureYieldsUnavailableResultWithRecordedReason() {
        VerifiedToolRegistry registry = DefaultVerifiedToolRegistry.withStandardTools();

        // A real EC request that cannot be computed (missing currency) → the real tool reports the
        // execution as UNAVAILABLE rather than throwing or fabricating a value.
        EnvironmentalCompensationRequest badRequest =
                new EnvironmentalCompensationRequest(1_000L, null, 10L, 1.5);

        ToolResult<?> result =
                registry.execute(EnvironmentalCompensationTool.TOOL_ID, badRequest);

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(result.value()).isEmpty();
        assertThat(result.reason()).isPresent();
        assertThat(result.isComputed()).isFalse();
    }

    @Test
    void verifiedToolSuccessIsComputedConfirmingTheUnavailablePathIsTheFailureBranch() {
        // Sanity sibling: the same real tool on valid inputs COMPUTES a value, so the UNAVAILABLE
        // result above is genuinely the tool-failure branch, not a tool that always fails.
        VerifiedToolRegistry registry = DefaultVerifiedToolRegistry.withStandardTools();
        EnvironmentalCompensationRequest goodRequest = new EnvironmentalCompensationRequest(
                1_000L, com.terravow.domain.Currency.INR, 10L, 1.5);

        ToolResult<?> result =
                registry.execute(EnvironmentalCompensationTool.TOOL_ID, goodRequest);

        assertThat(result.status()).isEqualTo(DeterminationStatus.COMPUTED);
        assertThat(result.isComputed()).isTrue();
        assertThat(result.value()).get().isInstanceOf(Money.class);
    }

    // ---- No safe fallback → undetermined, routed to human review (Req 12.6) ----------------------

    @Test
    void terminalPathWithNoSafeFallbackYieldsUndeterminedRoutedToHumanReview() {
        // A terminal fallback path has no further fallback wired (Req 12.6).
        assertThat(ManagedPath.DIRECT_GEMINI.fallback()).isEmpty();

        PrimaryPath<String> terminal =
                failingPrimary(ManagedPath.DIRECT_GEMINI, new Confidence(0.6), "no fallback left");

        PathResult<String> result = policy.executeWithFallback(
                terminal, ManagedPath.DIRECT_GEMINI.fallback().map(ManagedPathFallbackIntegrationTest::neverCalledFallback));

        assertThat(result.determined()).isFalse();
        assertThat(result.value()).isEmpty();
        assertThat(result.usedFallback()).isFalse();
        assertThat(result.confidence()).isEqualTo(Confidence.NONE);
        // Req 12.6 — routed to human review with the unavailable path recorded.
        assertThat(result.routedToHumanReview()).isTrue();
        assertThat(result.unavailablePath()).contains(ManagedPath.DIRECT_GEMINI);
        EvidenceLink link = singleFallbackLink(result);
        assertThat(link.ref().id()).contains(ManagedPath.DIRECT_GEMINI.id());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static EvidenceLink singleFallbackLink(PathResult<?> result) {
        assertThat(result.evidence().links()).hasSize(1);
        EvidenceLink link = result.evidence().links().get(0);
        assertThat(link.type()).isEqualTo(SourceType.FALLBACK_PATH);
        return link;
    }

    private static PrimaryPath<String> failingPrimary(
            ManagedPath path, Confidence confidence, String reason) {
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
                throw new PathUnavailableException(path, reason);
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

    /** A fallback that must never run (used only to satisfy types for a terminal path). */
    private static FallbackPath<String> neverCalledFallback(ManagedPath path) {
        return new FallbackPath<>() {
            @Override
            public ManagedPath path() {
                return path;
            }

            @Override
            public String execute() {
                throw new AssertionError("terminal path must not invoke a fallback");
            }
        };
    }
}
