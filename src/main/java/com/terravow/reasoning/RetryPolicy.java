package com.terravow.reasoning;

/**
 * The bounded retry limit governing the schema-validate &rarr; retry &rarr; escalate loop (Req
 * 11.4).
 *
 * <p>On schema-validation failure the orchestrator retries the reasoning request up to a configured
 * limit <strong>between 1 and 5 attempts inclusive, defaulting to 3</strong> (Req 11.4). This
 * record enforces that invariant: {@code retryLimit} is always in {@code [1, 5]}. Rather than
 * reject an out-of-range configuration, the {@link #clamped(int)} factory coerces any value into
 * range (and {@link #defaultPolicy()} supplies the default of 3 when a limit is absent), so a
 * mis-set configuration degrades safely to a valid bound instead of failing the determination.
 *
 * <p>The canonical constructor is strict (it rejects out-of-range values) so an invariant violation
 * cannot be constructed silently; callers holding a possibly-out-of-range or absent configuration
 * value should go through {@link #clamped(int)} or {@link #defaultPolicy()}.
 *
 * @param retryLimit the number of attempts, guaranteed to be in {@code [1, 5]}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.4</a>
 */
public record RetryPolicy(int retryLimit) {

    /** The lowest permitted retry limit (Req 11.4). */
    public static final int MIN_RETRY_LIMIT = 1;

    /** The highest permitted retry limit (Req 11.4). */
    public static final int MAX_RETRY_LIMIT = 5;

    /** The default retry limit applied when none is configured (Req 11.4). */
    public static final int DEFAULT_RETRY_LIMIT = 3;

    /**
     * Validates the invariant that {@code retryLimit} lies within {@code [1, 5]} (Req 11.4).
     *
     * @throws IllegalArgumentException if {@code retryLimit} is outside {@code [1, 5]}
     */
    public RetryPolicy {
        if (retryLimit < MIN_RETRY_LIMIT || retryLimit > MAX_RETRY_LIMIT) {
            throw new IllegalArgumentException(
                    "retryLimit must be within [" + MIN_RETRY_LIMIT + ", " + MAX_RETRY_LIMIT
                            + "] but was " + retryLimit);
        }
    }

    /**
     * Returns a policy using the default retry limit of {@value #DEFAULT_RETRY_LIMIT} (Req 11.4).
     *
     * @return a policy with the default retry limit
     */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(DEFAULT_RETRY_LIMIT);
    }

    /**
     * Returns a policy whose retry limit is {@code requested} clamped into the permitted range
     * {@code [1, 5]} (Req 11.4). A value below {@value #MIN_RETRY_LIMIT} becomes
     * {@value #MIN_RETRY_LIMIT}; a value above {@value #MAX_RETRY_LIMIT} becomes
     * {@value #MAX_RETRY_LIMIT}; an in-range value is used unchanged.
     *
     * @param requested the requested retry limit, which may be out of range
     * @return a policy with the clamped retry limit
     */
    public static RetryPolicy clamped(int requested) {
        int bounded = Math.max(MIN_RETRY_LIMIT, Math.min(MAX_RETRY_LIMIT, requested));
        return new RetryPolicy(bounded);
    }
}
