package com.terravow.capability.economics;

/**
 * The incentive categories the Economics Engine assesses for eligibility (Req 6.4).
 *
 * <p>When incentive eligibility is assessed, the system evaluates PAT/ESCerts eligibility, Income
 * Tax Act Section 32 depreciation eligibility, state industrial-policy incentives, and avoided
 * Environmental_Compensation exposure (Req 6.4). Each category is a distinct {@link IncentiveKind}
 * so an {@link IncentiveAssessment} can report an {@link IncentiveItem} for every category and the
 * engine can prove it covered all four.
 *
 * @see IncentiveAssessment
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.4</a>
 */
public enum IncentiveKind {
    /** PAT scheme / Energy Saving Certificates (ESCerts) eligibility. */
    PAT_ESCERTS,
    /** Income Tax Act Section 32 depreciation eligibility. */
    IT_ACT_SECTION_32_DEPRECIATION,
    /** State industrial-policy incentives. */
    STATE_INDUSTRIAL_POLICY,
    /** Avoided Environmental_Compensation exposure (treated as an incentive-side value). */
    AVOIDED_EC
}
