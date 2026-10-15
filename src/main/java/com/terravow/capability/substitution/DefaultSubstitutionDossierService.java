package com.terravow.capability.substitution;

import java.util.List;
import java.util.Objects;

/**
 * Default {@link SubstitutionDossierService} that assembles, bounds, and validates the output of a
 * {@link SubstitutionSynthesizer} into a {@link SubstitutionDossier} (Req 5).
 *
 * <p>This implementation is deterministic: given the same synthesizer output for a request it
 * always produces the same dossier. It delegates the counterfactual reasoning (functional-role
 * inference and candidate synthesis) to the injected {@link SubstitutionSynthesizer} seam — the
 * Reasoning_Agent in production (Req 11.1), a deterministic stub under test — and is itself the
 * authority for the dossier policy:
 *
 * <ul>
 *   <li>trims a synthesis of more than {@value SubstitutionDossier#MAX_CANDIDATES} candidates to the
 *       {@code 1..5} bound (Req 5.1);
 *   <li>always sets the mandatory vendor-validation flag via the {@link SubstitutionDossier}
 *       factories (Req 5.4);
 *   <li>converts an empty synthesis into a zero-candidate dossier carrying the recorded
 *       no-alternative reason (Req 5.7).
 * </ul>
 *
 * <p>Per-value confidence (Req 5.3), reasoning chain and Evidence_Chain (Req 5.5), and
 * unquantified-hypothesis marking (Req 5.6) are structural invariants of each {@link Candidate} and
 * are therefore guaranteed for every candidate this service emits.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.1–5.7, 11.1</a>
 */
public final class DefaultSubstitutionDossierService implements SubstitutionDossierService {

    private final SubstitutionSynthesizer synthesizer;

    /**
     * Creates a service bound to the given candidate-synthesis seam (constructor injection).
     *
     * @param synthesizer the synthesizer that infers the functional role and produces raw
     *     candidates; never {@code null}
     * @throws NullPointerException if {@code synthesizer} is {@code null}
     */
    public DefaultSubstitutionDossierService(SubstitutionSynthesizer synthesizer) {
        this.synthesizer = Objects.requireNonNull(synthesizer, "synthesizer must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public SubstitutionDossier analyse(SubstitutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        SynthesisResult result = synthesizer.synthesise(request);
        Objects.requireNonNull(result, "synthesizer returned a null SynthesisResult");

        List<Candidate> synthesised = result.candidates();

        // No viable alternative: zero candidates with a recorded reason (Req 5.7). The flag is still
        // set by the factory (Req 5.4).
        if (synthesised.isEmpty()) {
            String reason =
                    result.noAlternativeReason()
                            .orElse(
                                    "No viable substitution alternative could be synthesised for "
                                            + request.subjectDescriptor());
            return SubstitutionDossier.noViableAlternative(reason);
        }

        // Bound the candidate count to 1..5 (Req 5.1). A synthesizer that over-produces is trimmed
        // deterministically to the first MAX_CANDIDATES, preserving synthesis order.
        List<Candidate> bounded =
                synthesised.size() <= SubstitutionDossier.MAX_CANDIDATES
                        ? synthesised
                        : List.copyOf(
                                synthesised.subList(0, SubstitutionDossier.MAX_CANDIDATES));

        // Factory sets the mandatory vendor-validation flag (Req 5.4) and asserts the bound.
        return SubstitutionDossier.of(bounded);
    }
}
