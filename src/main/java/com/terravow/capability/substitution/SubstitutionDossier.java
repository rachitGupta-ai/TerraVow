package com.terravow.capability.substitution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of a substitution analysis: a set of candidate alternatives for replacing a machine or
 * process step, carrying the mandatory vendor-validation flag.
 *
 * <p>On request, the system infers the functional role of the machine or process step and generates
 * between 1 and 5 candidate alternatives (Req 5.1). Every dossier carries a mandatory "requires
 * vendor validation and process-safety review" flag that is always set (Req 5.4). If no viable
 * candidate alternative can be synthesised, the dossier is returned with zero candidates and the
 * reason no alternative was generated is recorded (Req 5.7).
 *
 * <p>The compact constructor enforces the structural invariants: the candidate count is bounded to
 * {@code 0..5} inclusive (Req 5.1, 5.7); the vendor-validation flag is required to be {@code true}
 * (Req 5.4); and the no-alternative reason is present exactly when there are zero candidates
 * (Req 5.7) so a non-empty dossier never carries a spurious reason and an empty dossier always
 * records why.
 *
 * @param candidates                   the generated candidate alternatives; never {@code null},
 *                                    defensively copied, size {@code 0..5} (Req 5.1, 5.7)
 * @param requiresVendorValidationFlag the mandatory vendor-validation flag, always {@code true}
 *                                    (Req 5.4)
 * @param noAlternativeReason          the recorded reason when there are zero candidates, otherwise
 *                                    empty; never {@code null} (Req 5.7)
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.1, 5.4, 5.7</a>
 */
public record SubstitutionDossier(
        List<Candidate> candidates,
        boolean requiresVendorValidationFlag,
        Optional<String> noAlternativeReason) {

    /** The maximum number of candidate alternatives a dossier may carry (Req 5.1). */
    public static final int MAX_CANDIDATES = 5;

    /**
     * Validates the candidate-count bound, the mandatory flag, and the reason-presence invariant
     * (Req 5.1, 5.4, 5.7).
     *
     * @throws NullPointerException     if {@code candidates} (or an element) or
     *                                  {@code noAlternativeReason} is {@code null}
     * @throws IllegalArgumentException if the candidate count exceeds {@link #MAX_CANDIDATES}, if
     *                                  the vendor-validation flag is not set, or if the
     *                                  no-alternative reason is not present exactly when there are
     *                                  zero candidates
     */
    public SubstitutionDossier {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(noAlternativeReason, "noAlternativeReason must not be null");
        candidates = List.copyOf(candidates);

        // Candidate count is bounded 0..5 (Req 5.1 upper bound; Req 5.7 permits zero).
        if (candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException(
                    "Substitution candidate count must be at most "
                            + MAX_CANDIDATES
                            + ", got: "
                            + candidates.size());
        }

        // The vendor-validation flag is mandatory and always set (Req 5.4).
        if (!requiresVendorValidationFlag) {
            throw new IllegalArgumentException(
                    "Every Substitution Dossier must set requiresVendorValidationFlag (Req 5.4)");
        }

        // The no-alternative reason is present exactly when there are zero candidates (Req 5.7).
        boolean empty = candidates.isEmpty();
        if (empty && noAlternativeReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "A dossier with zero candidates must record the no-alternative reason (Req 5.7)");
        }
        if (!empty && noAlternativeReason.isPresent()) {
            throw new IllegalArgumentException(
                    "A dossier with candidates must not carry a no-alternative reason (Req 5.7)");
        }
    }

    /**
     * Creates a dossier containing one or more candidate alternatives (Req 5.1). The
     * vendor-validation flag is set and no no-alternative reason is recorded.
     *
     * @param candidates the generated candidates; must be non-empty and size {@code <= 5}
     * @return a populated dossier
     * @throws IllegalArgumentException if {@code candidates} is empty or exceeds the bound
     */
    public static SubstitutionDossier of(List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Use SubstitutionDossier.noViableAlternative(reason) for an empty dossier");
        }
        return new SubstitutionDossier(candidates, true, Optional.empty());
    }

    /**
     * Creates a dossier with zero candidate alternatives and a recorded reason, for the case where
     * no viable alternative can be synthesised (Req 5.7). The vendor-validation flag is still set
     * (Req 5.4).
     *
     * @param reason the non-blank reason no alternative was generated
     * @return an empty dossier carrying the reason
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public static SubstitutionDossier noViableAlternative(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("noAlternativeReason must not be blank");
        }
        return new SubstitutionDossier(List.of(), true, Optional.of(reason));
    }
}
