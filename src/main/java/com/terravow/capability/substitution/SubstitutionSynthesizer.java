package com.terravow.capability.substitution;

/**
 * The seam through which substitution candidate alternatives are synthesised.
 *
 * <p>A substitution dossier is a counterfactual that does not exist in any database; it is produced
 * by a Reasoning_Agent on Gemini on Vertex AI with schema-validated, bounded-retry structured
 * output (Req 11.1). This interface is the port abstracting that reasoning: production wiring binds
 * it to the Reasoning_Agent orchestrator, while tests bind a deterministic stub so the service's
 * own policy — the candidate-count bound, the mandatory vendor-validation flag, per-value
 * confidence, unquantified-hypothesis marking, and the no-alternative path — is exercised in
 * isolation and without non-determinism.
 *
 * <p>The synthesizer returns the raw synthesised candidates and the inferred functional role; it
 * does <em>not</em> enforce the dossier invariants. The {@link SubstitutionDossierService} is the
 * authority that assembles, validates, and bounds the result into a {@link SubstitutionDossier}.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.1, 11.1</a>
 */
@FunctionalInterface
public interface SubstitutionSynthesizer {

    /**
     * Synthesises candidate alternatives for the subject of the request, inferring its functional
     * role (Req 5.1).
     *
     * <p>Implementations return zero or more raw candidates. Returning zero candidates signals that
     * no viable alternative could be synthesised (Req 5.7); the service converts that into a dossier
     * with a recorded no-alternative reason. Returning more candidates than the dossier bound
     * permits is tolerated here — the service trims the result to the {@code 0..5} bound (Req 5.1).
     *
     * @param request the substitution request identifying the subject; never {@code null}
     * @return the synthesis result carrying the inferred role and raw candidates; never
     *     {@code null}
     */
    SynthesisResult synthesise(SubstitutionRequest request);
}
