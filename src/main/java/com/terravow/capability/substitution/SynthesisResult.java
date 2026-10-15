package com.terravow.capability.substitution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The raw output of a {@link SubstitutionSynthesizer}: the inferred functional role and the
 * synthesised candidate alternatives, before the {@link SubstitutionDossierService} applies the
 * dossier invariants.
 *
 * <p>The synthesizer infers the functional role of the subject (Req 5.1) and produces zero or more
 * raw candidates. An empty candidate list signals that no viable alternative could be synthesised;
 * the accompanying {@code noAlternativeReason} records why (Req 5.7). A non-empty list is trimmed by
 * the service to the {@code 1..5} bound (Req 5.1).
 *
 * @param inferredFunctionalRole the inferred functional role of the subject; never {@code null},
 *                              non-blank (Req 5.1)
 * @param candidates            the raw synthesised candidates; never {@code null}, defensively
 *                              copied, may be empty (Req 5.1, 5.7)
 * @param noAlternativeReason   the reason no alternative was synthesised, required when
 *                              {@code candidates} is empty; never {@code null} (Req 5.7)
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.1, 5.7</a>
 */
public record SynthesisResult(
        String inferredFunctionalRole,
        List<Candidate> candidates,
        Optional<String> noAlternativeReason) {

    /**
     * Validates the inferred role and the empty-candidate reason invariant (Req 5.1, 5.7).
     *
     * @throws NullPointerException     if any component or candidate element is {@code null}
     * @throws IllegalArgumentException if {@code inferredFunctionalRole} is blank, or if
     *                                  {@code candidates} is empty without a recorded reason
     */
    public SynthesisResult {
        Objects.requireNonNull(inferredFunctionalRole, "inferredFunctionalRole must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(noAlternativeReason, "noAlternativeReason must not be null");
        if (inferredFunctionalRole.isBlank()) {
            throw new IllegalArgumentException("inferredFunctionalRole must not be blank");
        }
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty() && noAlternativeReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "A synthesis with zero candidates must record the no-alternative reason");
        }
    }

    /**
     * Creates a result carrying one or more synthesised candidates (Req 5.1).
     *
     * @param inferredFunctionalRole the inferred functional role
     * @param candidates             the non-empty synthesised candidates
     * @return a populated synthesis result
     * @throws IllegalArgumentException if {@code candidates} is empty
     */
    public static SynthesisResult withCandidates(
            String inferredFunctionalRole, List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Use SynthesisResult.noAlternative(role, reason) for an empty result");
        }
        return new SynthesisResult(inferredFunctionalRole, candidates, Optional.empty());
    }

    /**
     * Creates a result signalling that no viable alternative could be synthesised (Req 5.7).
     *
     * @param inferredFunctionalRole the inferred functional role
     * @param reason                 the non-blank reason no alternative was synthesised
     * @return an empty synthesis result carrying the reason
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public static SynthesisResult noAlternative(String inferredFunctionalRole, String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("noAlternativeReason must not be blank");
        }
        return new SynthesisResult(inferredFunctionalRole, List.of(), Optional.of(reason));
    }
}
