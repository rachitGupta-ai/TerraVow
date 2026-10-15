package com.terravow.pii;

import java.util.Objects;
import java.util.Set;

/**
 * An <em>advisory</em> result from the optional {@link MaskingTriage} seam (Req 13.3).
 *
 * <p>A Gemini Flash tier may inspect ingested fields and suggest additional field names it believes
 * carry PII or confidential supplier identifiers that the caller did not explicitly declare. This
 * hint is advisory only: the deterministic masker treats {@code additionalSensitiveFields} as a
 * <em>union-in</em> signal — it may mask more because of the hint, but it never masks less, and it
 * never allows a declared-sensitive field through on the strength of the triage. The deterministic
 * masker remains authoritative (design: "Deterministic masking remains authoritative; triage never
 * bypasses masking").
 *
 * @param additionalSensitiveFields extra field names the triage suggests masking; never
 *                                  {@code null}, defensively copied, may be empty
 *
 * @see MaskingTriage
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.3</a>
 */
public record TriageHint(Set<String> additionalSensitiveFields) {

    /** A hint that suggests nothing additional — the deterministic default. */
    public static final TriageHint NONE = new TriageHint(Set.of());

    /**
     * Validates and defensively copies the suggested field set.
     *
     * @throws NullPointerException if {@code additionalSensitiveFields} or any element is
     *                              {@code null}
     */
    public TriageHint {
        Objects.requireNonNull(
                additionalSensitiveFields, "TriageHint additionalSensitiveFields must not be null");
        additionalSensitiveFields = Set.copyOf(additionalSensitiveFields);
    }

    /**
     * Convenience factory for a hint suggesting the given field names.
     *
     * @param fields the field names to suggest masking; never {@code null}
     * @return a {@code TriageHint} carrying {@code fields}
     */
    public static TriageHint suggesting(String... fields) {
        return new TriageHint(Set.of(fields));
    }
}
