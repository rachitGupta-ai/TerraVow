package com.terravow.degradation;

import com.terravow.domain.Confidence;

/**
 * A primary managed path to attempt, together with the confidence a successful result would carry
 * (Req 12.3, 12.4).
 *
 * <p>The {@link #execute()} method performs the real managed call (an Agent Engine invocation, a
 * Vertex AI Search grounding, a BigQuery vector search, and so on). When the path is unavailable it
 * signals so by throwing — any {@link RuntimeException}, with {@link PathUnavailableException} the
 * canonical marker for an error or timeout (Req 12.3). The {@link #confidence()} is the confidence
 * the result deserves <em>on the primary path</em>; a fallback must come in strictly below it
 * (Req 12.4).
 *
 * @param <T> the type of value this path produces
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.3, 12.4</a>
 */
public interface PrimaryPath<T> {

    /**
     * The managed path identifier this attempt represents (Req 12.5).
     *
     * @return the path; never {@code null}
     */
    ManagedPath path();

    /**
     * The confidence a successful primary result carries (Req 12.4).
     *
     * @return the primary-path confidence; never {@code null}
     */
    Confidence confidence();

    /**
     * Attempts the managed call.
     *
     * @return the produced value; never {@code null}
     * @throws PathUnavailableException if the path errors or times out (Req 12.3)
     * @throws RuntimeException         any other failure is also treated as the path being
     *                                  unavailable
     */
    T execute();
}
