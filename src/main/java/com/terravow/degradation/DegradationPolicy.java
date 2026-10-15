package com.terravow.degradation;

import java.util.Optional;

/**
 * The uniform graceful-degradation mechanism applied to every managed Google path (Req 12.3–12.6).
 *
 * <p>A managed path (Agent Engine, Vertex AI Search grounding, BigQuery vector search, continuous
 * query, Gemini Pro, Memory Bank) is a <em>degradable enrichment path</em>: it is attempted first,
 * and if it errors or times out it is treated as unavailable (Req 12.3) and the predefined safe
 * fallback is run in its place at <em>strictly lower</em> confidence (Req 12.4), with the fallback
 * id and the replaced primary id recorded in the Evidence_Chain (Req 12.5). When no safe fallback
 * exists, the result is undetermined, routed to human review, with the unavailable path recorded
 * (Req 12.6).
 *
 * <p>Degradation is <b>confidence-lowering, never guarantee-weakening</b>: the policy only
 * substitutes a path and lowers confidence; it never relaxes masking, the Evidence_Chain, or the
 * Decision_Gate hard veto, which continue to apply around it.
 *
 * @see DefaultDegradationPolicy
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.3, 12.4, 12.5,
 *      12.6</a>
 */
public interface DegradationPolicy {

    /**
     * Executes a primary path, falling back to a predefined safe path on unavailability
     * (Req 12.3–12.6).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Attempt {@code primary}. If it returns, produce a primary-success result at the
     *       primary-path confidence.</li>
     *   <li>If {@code primary} errors or times out, treat it as unavailable (Req 12.3) and, if a
     *       {@code fallback} is supplied, attempt it. On success produce a fallback result at
     *       strictly-lower confidence (Req 12.4) with a {@code FALLBACK_PATH} Evidence_Chain link
     *       recording the fallback id and the replaced primary id (Req 12.5).</li>
     *   <li>If no {@code fallback} is supplied, or the fallback is itself unavailable, produce an
     *       undetermined result routed to human review with the unavailable path recorded
     *       (Req 12.6).</li>
     * </ol>
     *
     * @param primary  the primary managed path to attempt; never {@code null}
     * @param fallback the predefined safe fallback, or {@link Optional#empty()} if none is defined;
     *                 never {@code null}
     * @param <T>      the type of value the paths produce
     * @return the path result in one of the three states described on {@link PathResult}
     */
    <T> PathResult<T> executeWithFallback(PrimaryPath<T> primary, Optional<FallbackPath<T>> fallback);
}
