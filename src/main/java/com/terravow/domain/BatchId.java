package com.terravow.domain;

import java.util.Objects;

/**
 * The identifier of a produced batch linked to a {@link JobRun}.
 *
 * <p>A Job_Run links to the batch it produced (Req 14.7); the batch is in turn the unit an Embedded
 * Pollution Passport is created for (Req 7.1). Wrapping the batch reference as a value type keeps it
 * type-safe and lets the Passport service key footprints by batch.
 *
 * @param value a non-blank batch identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.7</a>
 */
public record BatchId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 14.7).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public BatchId {
        Objects.requireNonNull(value, "BatchId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("BatchId value must not be blank");
        }
    }
}
