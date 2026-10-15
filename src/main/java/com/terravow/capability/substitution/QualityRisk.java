package com.terravow.capability.substitution;

/**
 * The product-quality risk level of adopting a substitution {@link Candidate}.
 *
 * <p>Reported for each candidate alternative (Req 5.2). The level is deliberately coarse-grained and
 * ordinal — adopting an alternative machine or process step carries a qualitative risk to product
 * quality that a vendor validation and process-safety review must confirm (Req 5.4). When the
 * available data is insufficient to assess the risk, the candidate marks the
 * {@link DossierField#QUALITY_RISK} field an unquantified hypothesis and reports {@link #UNKNOWN}
 * (Req 5.6).
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.2, 5.4, 5.6</a>
 */
public enum QualityRisk {
    /** Little or no expected impact on product quality. */
    LOW,
    /** A moderate, manageable impact on product quality. */
    MEDIUM,
    /** A significant impact on product quality that must be validated before adoption. */
    HIGH,
    /** The quality risk could not be assessed from the available data (unquantified hypothesis). */
    UNKNOWN
}
