package com.terravow.capability.substitution;

/**
 * The regulatory admissibility of adopting a substitution {@link Candidate}.
 *
 * <p>Reported for each candidate alternative (Req 5.2): whether the alternative is permissible under
 * the applicable regulatory regime. {@link #UNKNOWN} is used when the available data is insufficient
 * to determine admissibility, in which case the {@link DossierField#ADMISSIBILITY} field is marked
 * an unquantified hypothesis with a recorded reason (Req 5.6).
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.2, 5.6</a>
 */
public enum RegulatoryAdmissibility {
    /** The alternative is permissible under the applicable regulatory regime. */
    ADMISSIBLE,
    /** The alternative is permissible subject to recorded conditions. */
    CONDITIONAL,
    /** The alternative is not permissible under the applicable regulatory regime. */
    INADMISSIBLE,
    /** Admissibility could not be determined from the available data (unquantified hypothesis). */
    UNKNOWN
}
