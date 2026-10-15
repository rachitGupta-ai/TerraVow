package com.terravow.degradation;

import java.util.Objects;

/**
 * Signals that a managed path is unavailable — it errored or timed out — and must be treated as
 * such by the {@link DegradationPolicy} (Req 12.3).
 *
 * <p>A path attempt throws this (directly or wrapped as the cause of any thrown exception) to
 * declare itself unavailable. The policy responds by substituting the predefined safe fallback at
 * strictly lower confidence (Req 12.4), or — when no fallback is defined — by returning an
 * undetermined result routed to human review with the unavailable path recorded (Req 12.6). From
 * the policy's perspective a timeout and an error are indistinguishable: both mean "treat the path
 * as unavailable".
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.3</a>
 */
public final class PathUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ManagedPath path;

    /**
     * Creates an exception marking the given path unavailable.
     *
     * @param path    the path that errored or timed out; never {@code null}
     * @param message a human-readable explanation; never {@code null}
     */
    public PathUnavailableException(ManagedPath path, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.path = Objects.requireNonNull(path, "path must not be null");
    }

    /**
     * Creates an exception marking the given path unavailable, carrying the underlying cause.
     *
     * @param path    the path that errored or timed out; never {@code null}
     * @param message a human-readable explanation; never {@code null}
     * @param cause   the underlying error or timeout; may be {@code null}
     */
    public PathUnavailableException(ManagedPath path, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.path = Objects.requireNonNull(path, "path must not be null");
    }

    /**
     * The managed path that was found unavailable (Req 12.3, recorded per Req 12.5/12.6).
     *
     * @return the unavailable path; never {@code null}
     */
    public ManagedPath path() {
        return path;
    }
}
