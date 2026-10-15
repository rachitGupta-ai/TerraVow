package com.terravow.domain.store;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a validated store operation: either a successfully created record, or a rejection
 * that records the missing required field and creates nothing.
 *
 * <p>Requirement 14 specifies that a registration or Maintenance_Event submitted with a missing
 * required identity/field is <em>rejected</em>, the missing field recorded, and no record created
 * (Req 14.2, 14.4). A thrown exception would not let a caller enumerate the missing field as data,
 * nor continue processing the remaining records (Req 14.14), so the stores return this result
 * instead. Exactly one of {@link #created()} / {@link #missingField()} is present:
 *
 * <ul>
 *   <li>a {@link #created(Object) CREATED} result carries the constructed record and an empty
 *       missing-field; nothing was rejected;</li>
 *   <li>a {@link #rejected(String) REJECTED} result carries the name of the first missing required
 *       field and an empty record; nothing was created.</li>
 * </ul>
 *
 * <p>The type is an immutable value object; it holds no wall-clock time and no mutable state, so a
 * store operation on the same submission always yields an equal result.
 *
 * @param <T>          the domain-core record type produced on success
 * @param status       whether the operation created a record or rejected the submission
 * @param created      the created record, present iff {@code status == CREATED}
 * @param missingField the name of the first missing required field, present iff
 *                     {@code status == REJECTED}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.2, 14.4,
 *      14.14</a>
 */
public record StoreResult<T>(Status status, Optional<T> created, Optional<String> missingField) {

    /** Whether a store operation created a record or rejected the submission. */
    public enum Status {
        /** The submission was complete; the record was created and stored. */
        CREATED,
        /** The submission was missing a required field; nothing was created. */
        REJECTED
    }

    /**
     * Validates the result invariant: exactly one of {@code created} / {@code missingField} is
     * present, consistent with {@code status}.
     *
     * @throws NullPointerException     if any component is {@code null}
     * @throws IllegalArgumentException if the present component does not match {@code status}
     */
    public StoreResult {
        Objects.requireNonNull(status, "StoreResult status must not be null");
        Objects.requireNonNull(created, "StoreResult created must not be null");
        Objects.requireNonNull(missingField, "StoreResult missingField must not be null");
        if (status == Status.CREATED && (created.isEmpty() || missingField.isPresent())) {
            throw new IllegalArgumentException(
                    "A CREATED StoreResult must carry the created record and no missing field");
        }
        if (status == Status.REJECTED && (missingField.isEmpty() || created.isPresent())) {
            throw new IllegalArgumentException(
                    "A REJECTED StoreResult must carry a missing field and no created record");
        }
    }

    /**
     * Creates a successful result wrapping the constructed, stored record (Req 14.1, 14.3, 14.7).
     *
     * @param record the created record; never {@code null}
     * @param <T>    the record type
     * @return a {@link Status#CREATED} result
     * @throws NullPointerException if {@code record} is {@code null}
     */
    public static <T> StoreResult<T> created(T record) {
        return new StoreResult<>(
                Status.CREATED,
                Optional.of(Objects.requireNonNull(record, "created record must not be null")),
                Optional.empty());
    }

    /**
     * Creates a rejection result recording the missing required field (Req 14.2, 14.4). Nothing is
     * created.
     *
     * @param missingField the name of the first missing required field; never {@code null} or blank
     * @param <T>          the record type that would have been produced
     * @return a {@link Status#REJECTED} result
     * @throws NullPointerException     if {@code missingField} is {@code null}
     * @throws IllegalArgumentException if {@code missingField} is blank
     */
    public static <T> StoreResult<T> rejected(String missingField) {
        Objects.requireNonNull(missingField, "missingField must not be null");
        if (missingField.isBlank()) {
            throw new IllegalArgumentException("missingField must not be blank");
        }
        return new StoreResult<>(Status.REJECTED, Optional.empty(), Optional.of(missingField));
    }

    /**
     * Whether this result created a record.
     *
     * @return {@code true} iff {@code status == CREATED}
     */
    public boolean isCreated() {
        return status == Status.CREATED;
    }

    /**
     * Whether this result rejected the submission.
     *
     * @return {@code true} iff {@code status == REJECTED}
     */
    public boolean isRejected() {
        return status == Status.REJECTED;
    }
}
