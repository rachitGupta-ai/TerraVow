package com.terravow.reasoning.tool;

import com.terravow.domain.DeterminationStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * The controlled outcome of executing a {@link VerifiedTool} (Req 2.2, 4.5, 6.2, 6.5).
 *
 * <p>A verified tool never throws across its boundary. On success it returns a {@code COMPUTED}
 * result carrying the deterministic value; on a tool-execution failure — missing or invalid inputs,
 * or an arithmetic error — it returns an {@link DeterminationStatus#UNAVAILABLE} result carrying a
 * recorded reason and no value. This lets the reasoning layer mark the dependent value unavailable
 * rather than letting the model substitute an unverified number.
 *
 * <p>The two states are mutually exclusive and enforced in the compact constructor:
 * {@code COMPUTED} carries a present value and no reason; {@code UNAVAILABLE} carries a present
 * reason and no value. {@link DeterminationStatus#UNDETERMINED} is not a tool-level outcome — a tool
 * either computes its value or is unavailable.
 *
 * @param <T>    the type of the computed value
 * @param status the outcome status; {@code COMPUTED} or {@code UNAVAILABLE}
 * @param value  the computed value, present iff {@code status == COMPUTED}
 * @param reason the failure reason, present iff {@code status == UNAVAILABLE}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 4.5, 6.2,
 *      6.5</a>
 */
public record ToolResult<T>(DeterminationStatus status, Optional<T> value, Optional<String> reason) {

    /**
     * Validates the status/value/reason consistency invariant.
     *
     * @throws NullPointerException     if {@code status}, {@code value}, or {@code reason} is
     *                                  {@code null}
     * @throws IllegalArgumentException if the status does not match the presence of value/reason,
     *                                  or the status is {@link DeterminationStatus#UNDETERMINED}
     */
    public ToolResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        switch (status) {
            case COMPUTED -> {
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("COMPUTED result must carry a value");
                }
                if (reason.isPresent()) {
                    throw new IllegalArgumentException("COMPUTED result must not carry a reason");
                }
            }
            case UNAVAILABLE -> {
                if (reason.isEmpty()) {
                    throw new IllegalArgumentException("UNAVAILABLE result must carry a reason");
                }
                if (value.isPresent()) {
                    throw new IllegalArgumentException("UNAVAILABLE result must not carry a value");
                }
            }
            default -> throw new IllegalArgumentException(
                    "A tool result is COMPUTED or UNAVAILABLE, not " + status);
        }
    }

    /**
     * Builds a successful result carrying the computed value.
     *
     * @param value the computed value; never {@code null}
     * @param <T>   the value type
     * @return a {@code COMPUTED} result
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static <T> ToolResult<T> computed(T value) {
        Objects.requireNonNull(value, "computed value must not be null");
        return new ToolResult<>(DeterminationStatus.COMPUTED, Optional.of(value), Optional.empty());
    }

    /**
     * Builds an unavailable result marking the dependent value unavailable with a reason.
     *
     * @param reason a non-blank explanation of why the tool could not produce a value
     * @param <T>    the value type
     * @return an {@link DeterminationStatus#UNAVAILABLE} result
     * @throws NullPointerException     if {@code reason} is {@code null}
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public static <T> ToolResult<T> unavailable(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("UNAVAILABLE reason must not be blank");
        }
        return new ToolResult<>(DeterminationStatus.UNAVAILABLE, Optional.empty(), Optional.of(reason));
    }

    /**
     * Returns whether this result carries a computed value.
     *
     * @return {@code true} iff {@code status == COMPUTED}
     */
    public boolean isComputed() {
        return status == DeterminationStatus.COMPUTED;
    }
}
