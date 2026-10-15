package com.terravow.capability.pollution;

import java.util.Objects;

/**
 * A remediation whose cross-media effect is assessed by the {@link CrossMediaGuardrail} (Req 3.6,
 * 3.7).
 *
 * <p>A remediation is intended to reduce a pollutant load in some environmental medium; the
 * guardrail evaluates whether, in doing so, it increased the load in another medium (Req 3.6) and
 * flags cross-media displacement when it did (Req 3.7). The identifier is a non-blank string used
 * to attribute the assessment back to the remediation.
 *
 * @param id a non-blank remediation identifier
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.6, 3.7</a>
 */
public record Remediation(String id) {

    /**
     * Validates that the remediation identifier is present and non-blank.
     *
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public Remediation {
        Objects.requireNonNull(id, "Remediation id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Remediation id must not be blank");
        }
    }
}
