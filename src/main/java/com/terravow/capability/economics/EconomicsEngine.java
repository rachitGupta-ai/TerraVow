package com.terravow.capability.economics;

import com.terravow.capability.economics.grounding.ViolationFacts;

/**
 * The Economics Engine: reasons penalty exposure and incentive eligibility over real Indian
 * regulatory and incentive instruments to produce auditable cited drafts rather than invented
 * figures (Req 6).
 *
 * <p>The engine does not free-generate numbers. Environmental_Compensation exposure
 * ({@code base × days × deterrence}) and the net-position identity
 * ({@code incentives + avoided EC − penalty}) are computed by verified function-calling tools
 * (Req 6.2, 6.5), the EC figures are resolved and cited by the grounding layer (Req 6.1, 6.7), and
 * every output is labelled a cited draft for qualified professional review that is not legal or tax
 * advice (Req 6.8). Follows the TerraVow interface + {@code Default...} implementation convention.
 *
 * @see DefaultEconomicsEngine
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.2, 6.3, 6.4, 6.5,
 *      6.6, 6.8</a>
 */
public interface EconomicsEngine {

    /**
     * Computes the Environmental_Compensation exposure for a violation fact pattern via the verified
     * EC tool using the grounded figures (Req 6.2).
     *
     * <p>The engine resolves the EC guideline chapter, base rate, days, and deterrence from the
     * regulatory corpus and feeds the three numeric figures to the verified
     * {@code base × days × deterrence} tool (Req 6.1, 6.2). If a required figure is a grounding gap,
     * the exposure is reported undetermined naming that figure — the engine never invents it
     * (Req 6.9). Every exposure carries the advisory label (Req 6.8).
     *
     * @param facts the violation fact pattern; never {@code null}
     * @return the EC exposure determination; never {@code null}
     * @throws NullPointerException if {@code facts} is {@code null}
     */
    EcExposure computeEnvironmentalCompensation(ViolationFacts facts);

    /**
     * Reports whether the central / state / NGT instruments bearing on the fact pattern conflict,
     * enumerating every conflicting instrument in the Evidence_Chain (Req 6.3).
     *
     * @param facts the violation fact pattern; never {@code null}
     * @return the conflict report (possibly {@link InstrumentConflict#none()}); never {@code null}
     * @throws NullPointerException if {@code facts} is {@code null}
     */
    InstrumentConflict reportInstrumentConflicts(ViolationFacts facts);

    /**
     * Assesses incentive eligibility across all four categories — PAT/ESCerts, Income Tax Act
     * Section 32 depreciation, state industrial policy, and avoided EC (Req 6.4).
     *
     * @param context the asset context; never {@code null}
     * @return the incentive assessment covering every category; never {@code null}
     * @throws NullPointerException if {@code context} is {@code null}
     */
    IncentiveAssessment assessIncentives(AssetContext context);

    /**
     * Computes the net position {@code total incentives + avoided EC − penalty} via the verified
     * net-position tool (Req 6.5).
     *
     * @param penalty    the EC penalty exposure; never {@code null}
     * @param incentives the incentive assessment supplying the incentive total and avoided EC; never
     *                   {@code null}
     * @return the net-position determination; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    NetPosition netPosition(EcExposure penalty, IncentiveAssessment incentives);

    /**
     * Recommends the replacement timing maximising net position over the evaluated horizon, recording
     * the basis (Req 6.6).
     *
     * @param horizon the evaluated timing horizon with candidate net positions; never {@code null}
     * @return the net-position-maximising timing recommendation; never {@code null}
     * @throws NullPointerException if {@code horizon} is {@code null}
     */
    TimingRecommendation optimalReplacementTiming(TimingHorizon horizon);
}
