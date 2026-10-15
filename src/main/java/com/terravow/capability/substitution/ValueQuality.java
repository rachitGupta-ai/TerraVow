package com.terravow.capability.substitution;

import java.util.Objects;

/**
 * A record that a specific reported {@link DossierField} of a {@link Candidate} is an unquantified
 * hypothesis.
 *
 * <p>When the available data is insufficient to quantify a reported value, the dossier is still
 * produced, the value is marked an unquantified hypothesis, and the reason it could not be
 * quantified is recorded (Req 5.6). A {@code ValueQuality} is that marker: it names the field and
 * carries the recorded reason. A candidate's set of {@code ValueQuality} entries is the complete
 * list of fields that could not be quantified; a field absent from the set is a quantified value.
 *
 * @param field  the reported field that could not be quantified; never {@code null}
 * @param reason the non-blank recorded reason the value could not be quantified; never {@code null}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.6</a>
 */
public record ValueQuality(DossierField field, String reason) {

    /**
     * Validates the field is present and the reason is present and non-blank (Req 5.6).
     *
     * @throws NullPointerException     if {@code field} or {@code reason} is {@code null}
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public ValueQuality {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("ValueQuality reason must not be blank");
        }
    }
}
