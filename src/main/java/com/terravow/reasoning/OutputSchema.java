package com.terravow.reasoning;

import java.util.Optional;

/**
 * The declared output schema a reasoning determination is validated against (Req 11.2, 11.3, 11.4).
 *
 * <p>When a Reasoning_Agent returns a determination, the orchestrator validates it against this
 * schema (Req 11.2). A valid determination is accepted and passed to the requesting capability (Req
 * 11.3); an invalid determination triggers a bounded retry (Req 11.4). The schema both decides
 * validity ({@link #validate(Object)}) and, for a valid payload, materialises the typed value
 * ({@link #coerce(Object)}) the capability consumes.
 *
 * <p>Implementations must be deterministic and side-effect free: the same payload must always
 * produce the same validity result, so the bounded-retry loop in task 6.2 can rely on it. The
 * schema is parameterised by {@code T}, the capability's determination type.
 *
 * @param <T> the typed determination this schema validates and produces
 *
 * @see ReasoningResult
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.2, 11.3, 11.4</a>
 */
public interface OutputSchema<T> {

    /**
     * Reports whether the raw agent output conforms to this schema (Req 11.2).
     *
     * <p>Must be deterministic: identical input always yields the same result. A {@code null}
     * payload is not schema-valid and must return {@code false} rather than throwing.
     *
     * @param rawOutput the raw determination returned by the Reasoning_Agent; may be {@code null}
     * @return {@code true} iff {@code rawOutput} satisfies this schema
     */
    boolean validate(Object rawOutput);

    /**
     * Materialises the typed determination from a schema-valid raw output (Req 11.3).
     *
     * <p>Called only for a payload that {@link #validate(Object)} has accepted; implementations may
     * assume validity. Returns {@link Optional#empty()} if the typed value cannot be produced from
     * an otherwise-valid payload, so the caller treats it as a validation failure rather than an
     * accepted determination.
     *
     * @param rawOutput the raw, already-validated determination; never {@code null}
     * @return the typed determination, or empty if it cannot be materialised
     */
    Optional<T> coerce(Object rawOutput);

    /**
     * A short, human-readable name of this schema used in failure records routed to human review
     * (Req 11.5).
     *
     * @return a non-blank schema name
     */
    String name();
}
