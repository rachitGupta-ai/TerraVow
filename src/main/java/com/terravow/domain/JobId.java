package com.terravow.domain;

import java.util.Objects;

/**
 * The identifier of a {@link JobRun}.
 *
 * <p>A Job_Run is a single production job executed on a {@link MachineAsset} (Req 14.7). Its
 * identifier is a dedicated value type so job references stay type-safe and distinct from machine,
 * batch, event, and action identifiers across the domain core.
 *
 * @param value a non-blank job identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.7</a>
 */
public record JobId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 14.7).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public JobId {
        Objects.requireNonNull(value, "JobId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("JobId value must not be blank");
        }
    }
}
