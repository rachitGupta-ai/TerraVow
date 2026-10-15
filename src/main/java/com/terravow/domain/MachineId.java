package com.terravow.domain;

import java.util.Objects;

/**
 * The stable identifier of a {@link MachineAsset}.
 *
 * <p>A Machine_Asset is registered with a stable identifier (Req 14.1) that serves as the unit to
 * which maintenance events, job runs, and health state are attributed. Wrapping the identifier in a
 * dedicated value type keeps machine references type-safe across the domain core and prevents them
 * being confused with other opaque identifiers (job, batch, action, event).
 *
 * @param value a non-blank, stable machine identifier
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.1</a>
 */
public record MachineId(String value) {

    /**
     * Validates that the identifier is present and non-blank (Req 14.1).
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public MachineId {
        Objects.requireNonNull(value, "MachineId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("MachineId value must not be blank");
        }
    }
}
