package com.terravow.capability.erd;

/**
 * The five constituent components of an Environmental Regulatory Debt (ERD) value (Req 2.2).
 *
 * <p>ERD is the compounding financial liability of operating degraded or legacy equipment, and the
 * ERD value is the sum of these components over those that are available (Req 2.2); a component that
 * cannot be computed is excluded from the sum and reported unavailable with a reason (Req 2.6). Each
 * constant names one of the monetary components the requirements enumerate.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 2.6</a>
 */
public enum ErdComponentType {
    /** Expected Environmental_Compensation exposure. */
    EC_EXPOSURE,
    /** Excess energy cost from running at degraded efficiency. */
    EXCESS_ENERGY,
    /** Forgone energy-saving-certificate (ESCerts) value. */
    FORGONE_ESCERTS,
    /** Forgone tax depreciation benefit. */
    FORGONE_TAX_DEPRECIATION,
    /** Cross-media treatment load cost. */
    CROSS_MEDIA_TREATMENT
}
