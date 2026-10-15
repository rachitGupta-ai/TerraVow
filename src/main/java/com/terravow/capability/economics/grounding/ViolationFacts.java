package com.terravow.capability.economics.grounding;

import java.util.Objects;

/**
 * The violation fact pattern submitted to the {@link RegulatoryGrounding} layer to resolve the
 * applicable Environmental_Compensation (EC) figures from the regulatory corpus (Req 6.1).
 *
 * <p>The fact pattern is the retrieval key: the pollutant and environmental medium select the
 * applicable per-violation-class guideline chapter, and the number of days of violation is the one
 * figure supplied directly by the caller (it is a measured operating fact, not a corpus figure) so
 * the grounding layer can surface it alongside the corpus-resolved base rate and deterrence factor.
 * All inputs are retained verbatim; the grounding layer never invents figures (Req 6.1, 6.7).
 *
 * @param pollutant       the pollutant the violation concerns; non-blank
 * @param medium          the environmental medium (e.g. {@code "air"}, {@code "water"}); non-blank
 * @param violationClass  the per-violation-class identifier used to select the guideline chapter;
 *                        non-blank
 * @param daysOfViolation the number of days of violation; must be {@code >= 0}
 *
 * @see RegulatoryGrounding
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.7</a>
 */
public record ViolationFacts(
        String pollutant, String medium, String violationClass, long daysOfViolation) {

    /**
     * Validates that the retrieval-key fields are present and that the day count is non-negative
     * (Req 6.1).
     *
     * @throws NullPointerException     if any string field is {@code null}
     * @throws IllegalArgumentException if any string field is blank or {@code daysOfViolation} is
     *                                  negative
     */
    public ViolationFacts {
        Objects.requireNonNull(pollutant, "pollutant must not be null");
        Objects.requireNonNull(medium, "medium must not be null");
        Objects.requireNonNull(violationClass, "violationClass must not be null");
        requireNonBlank(pollutant, "pollutant");
        requireNonBlank(medium, "medium");
        requireNonBlank(violationClass, "violationClass");
        if (daysOfViolation < 0L) {
            throw new IllegalArgumentException(
                    "daysOfViolation must be non-negative, got: " + daysOfViolation);
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
