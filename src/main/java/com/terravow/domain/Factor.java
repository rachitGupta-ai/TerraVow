package com.terravow.domain;

import java.util.Objects;

/**
 * A factor contributing to a {@link MachineHealthState} assessment.
 *
 * <p>A Machine_Health_State carries the contributing factors behind its health score (Req 14.9),
 * making the assessment explainable rather than an opaque number. Each factor is identified by a
 * non-blank description.
 *
 * @param description a non-blank description of the contributing factor
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.9</a>
 */
public record Factor(String description) {

    /**
     * Validates that the factor description is present and non-blank (Req 14.9).
     *
     * @throws NullPointerException     if {@code description} is {@code null}
     * @throws IllegalArgumentException if {@code description} is blank
     */
    public Factor {
        Objects.requireNonNull(description, "Factor description must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Factor description must not be blank");
        }
    }
}
