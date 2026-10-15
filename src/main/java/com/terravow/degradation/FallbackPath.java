package com.terravow.degradation;

/**
 * The predefined safe fallback executed when a {@link PrimaryPath} is unavailable (Req 12.3, 12.4).
 *
 * <p>A fallback produces a value of the same type as the primary path, so a caller consumes the
 * result uniformly whether or not degradation occurred. The confidence of a fallback result is
 * <em>not</em> supplied here: the {@link DegradationPolicy} derives it by lowering the primary-path
 * confidence, which structurally guarantees the fallback is strictly lower (Req 12.4). The fallback
 * identifier is taken from {@link #path()} and recorded in the Evidence_Chain alongside the
 * replaced primary id (Req 12.5).
 *
 * @param <T> the type of value this fallback produces (identical to the primary path's type)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.3, 12.4, 12.5</a>
 */
public interface FallbackPath<T> {

    /**
     * The managed path identifier of this fallback, recorded as the {@code fallbackPathId}
     * (Req 12.5).
     *
     * @return the fallback path; never {@code null}
     */
    ManagedPath path();

    /**
     * Executes the safe fallback.
     *
     * @return the produced value; never {@code null}
     * @throws PathUnavailableException if the fallback itself errors or times out (Req 12.3)
     * @throws RuntimeException         any other failure is also treated as the fallback being
     *                                  unavailable
     */
    T execute();
}
