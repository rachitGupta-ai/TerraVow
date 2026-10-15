package com.terravow.capability.substitution;

/**
 * The set of reported values a substitution {@link Candidate} carries.
 *
 * <p>Each enum constant names one of the eight reported values a candidate must report relative to
 * the incumbent machine or process step (Req 5.2). The field identity is used as the key for the
 * per-value confidence map (Req 5.3) and to mark a specific value an unquantified hypothesis when
 * the available data is insufficient to quantify it (Req 5.6).
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.2, 5.3, 5.6</a>
 */
public enum DossierField {
    /** Signed change in emissions relative to the incumbent. */
    DELTA_EMISSIONS,
    /** Signed change in energy relative to the incumbent. */
    DELTA_ENERGY,
    /** Signed change in cost relative to the incumbent. */
    DELTA_COST,
    /** Product-quality risk of adopting the candidate. */
    QUALITY_RISK,
    /** Applicable incentives for the candidate. */
    INCENTIVES,
    /** Payback period of the candidate. */
    PAYBACK,
    /** Regulatory admissibility of the candidate. */
    ADMISSIBILITY,
    /** Supply-chain ripple of adopting the candidate. */
    RIPPLE
}
