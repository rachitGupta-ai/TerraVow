package com.terravow.capability.substitution;

/**
 * Synthesises Sustainable Substitution Dossiers — AI-synthesised counterfactuals for replacing a
 * machine or process step (Req 5).
 *
 * <p>On request, the service infers the functional role of the subject and generates between 1 and 5
 * candidate alternatives (Req 5.1). For each candidate it reports the signed change in emissions,
 * energy, and cost relative to the incumbent, together with product-quality risk, applicable
 * incentives, payback period, regulatory admissibility, and supply-chain ripple (Req 5.2), and
 * attaches a confidence to every reported value (Req 5.3). Every dossier carries the mandatory
 * "requires vendor validation and process-safety review" flag (Req 5.4) and the reasoning chain and
 * Evidence_Chain supporting each reported value (Req 5.5). Where the available data is insufficient
 * to quantify a value, the dossier is still produced and the value is marked an unquantified
 * hypothesis with a reason (Req 5.6). Where no viable alternative can be synthesised, the dossier is
 * returned with zero candidates and a recorded reason (Req 5.7).
 *
 * @see DefaultSubstitutionDossierService
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.1–5.7</a>
 */
public interface SubstitutionDossierService {

    /**
     * Produces a Substitution Dossier for the subject of the given request.
     *
     * <p>The returned dossier always carries the mandatory vendor-validation flag (Req 5.4) and a
     * candidate count bounded to {@code 0..5}: {@code 1..5} when at least one viable alternative is
     * synthesised (Req 5.1), or zero with a recorded reason when none is (Req 5.7).
     *
     * @param request the substitution request identifying the machine or process step; never
     *     {@code null}
     * @return the produced Substitution Dossier; never {@code null}
     * @throws NullPointerException if {@code request} is {@code null}
     */
    SubstitutionDossier analyse(SubstitutionRequest request);
}
