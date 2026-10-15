package com.terravow.degradation;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic implementation of {@link DegradationPolicy} (Req 12.3–12.6).
 *
 * <p>The policy applies a fixed, deterministic algorithm: it attempts the primary path, and on any
 * failure — the canonical {@link PathUnavailableException} or any other {@link RuntimeException} —
 * treats the path as unavailable (Req 12.3) and substitutes the predefined safe fallback. A
 * fallback result's confidence is derived from the primary-path confidence by
 * {@link Confidence#loweredBy(double) lowering} it a fixed amount, which structurally guarantees it
 * is strictly lower (Req 12.4). The substituted path is recorded in the Evidence_Chain as a single
 * {@link SourceType#FALLBACK_PATH} link whose reference encodes both the fallback id and the
 * replaced primary id (Req 12.5). When no fallback is supplied — or the fallback is itself
 * unavailable, or a strictly-lower confidence cannot be represented — the result is undetermined
 * and routed to human review with the unavailable path recorded (Req 12.6).
 *
 * <p>The logic reads no wall-clock time, uses no randomness, and depends on no iteration order, so
 * identical inputs always produce an identical {@link PathResult}.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.3, 12.4, 12.5,
 *      12.6</a>
 */
public final class DefaultDegradationPolicy implements DegradationPolicy {

    /**
     * Default confidence reduction applied to a fallback result (Req 12.4). Any positive value
     * yields a strictly-lower confidence; the fixed amount keeps the reduction deterministic.
     */
    static final double DEFAULT_CONFIDENCE_PENALTY = 0.2;

    private final double confidencePenalty;

    /** Creates a policy using the {@link #DEFAULT_CONFIDENCE_PENALTY default fallback penalty}. */
    public DefaultDegradationPolicy() {
        this(DEFAULT_CONFIDENCE_PENALTY);
    }

    /**
     * Creates a policy with an explicit fallback confidence penalty (Req 12.4).
     *
     * @param confidencePenalty the positive amount subtracted from the primary confidence to derive
     *                          the strictly-lower fallback confidence
     * @throws IllegalArgumentException if {@code confidencePenalty} is not a positive finite number
     */
    public DefaultDegradationPolicy(double confidencePenalty) {
        if (Double.isNaN(confidencePenalty)
                || Double.isInfinite(confidencePenalty)
                || confidencePenalty <= 0.0) {
            throw new IllegalArgumentException(
                    "confidencePenalty must be a positive finite number, got: " + confidencePenalty);
        }
        this.confidencePenalty = confidencePenalty;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Executes the ordered algorithm described on {@link DegradationPolicy#executeWithFallback}.
     */
    @Override
    public <T> PathResult<T> executeWithFallback(
            PrimaryPath<T> primary, Optional<FallbackPath<T>> fallback) {
        Objects.requireNonNull(primary, "primary must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");

        // Step 1 — attempt the primary path.
        try {
            T value = primary.execute();
            Objects.requireNonNull(value, "primary path produced a null value");
            return PathResult.primary(value, primary.confidence(), EvidenceChain.EMPTY);
        } catch (RuntimeException primaryFailure) {
            // Primary errored or timed out: it is unavailable (Req 12.3). Fall through to the
            // fallback handling below.
        }

        // Step 2 — no safe fallback defined → undetermined, human review, record path (Req 12.6).
        if (fallback.isEmpty()) {
            return undetermined(primary.path());
        }

        // Step 3 — a strictly-lower confidence must be representable (Req 12.4). If the primary
        // confidence is already so low that lowering it would collapse to the primary value (only
        // possible at 0.0, where loweredBy clamps to 0.0), no honest fallback confidence exists, so
        // the result is undetermined rather than silently equal-or-higher.
        Confidence primaryConfidence = primary.confidence();
        Confidence fallbackConfidence = primaryConfidence.loweredBy(confidencePenalty);
        if (fallbackConfidence.value() >= primaryConfidence.value()) {
            return undetermined(primary.path());
        }

        // Step 4 — attempt the predefined safe fallback.
        FallbackPath<T> fallbackPath = fallback.get();
        try {
            T value = fallbackPath.execute();
            Objects.requireNonNull(value, "fallback path produced a null value");
            EvidenceChain evidence =
                    EvidenceChain.EMPTY.with(fallbackLink(primary.path(), fallbackPath.path()));
            return PathResult.fallback(value, fallbackConfidence, evidence);
        } catch (RuntimeException fallbackFailure) {
            // The fallback is itself unavailable: undetermined, record the fallback path (Req 12.6).
            return undetermined(fallbackPath.path());
        }
    }

    /**
     * Builds an undetermined result for an unavailable path, recording that path in both the result
     * and its Evidence_Chain (Req 12.6).
     */
    private <T> PathResult<T> undetermined(ManagedPath unavailable) {
        EvidenceChain evidence = EvidenceChain.EMPTY.with(unavailableLink(unavailable));
        return PathResult.undetermined(unavailable, evidence);
    }

    /**
     * A {@link SourceType#FALLBACK_PATH} link recording the fallback id and the replaced primary id
     * (Req 12.5). The reference encodes both ids as {@code "<fallbackId> replaced:<primaryId>"} so
     * the single link is a complete, resolvable record of the substitution.
     */
    private static EvidenceLink fallbackLink(ManagedPath replacedPrimary, ManagedPath fallback) {
        String ref = fallback.id() + " replaced:" + replacedPrimary.id();
        return new EvidenceLink(SourceType.FALLBACK_PATH, new SourceRef(ref));
    }

    /**
     * A {@link SourceType#FALLBACK_PATH} link recording an unavailable path for which no safe
     * fallback could serve (Req 12.6).
     */
    private static EvidenceLink unavailableLink(ManagedPath unavailable) {
        String ref = "unavailable:" + unavailable.id();
        return new EvidenceLink(SourceType.FALLBACK_PATH, new SourceRef(ref));
    }
}
