package com.terravow.capability.economics;

/**
 * The authority that issued a regulatory instrument relevant to a violation fact pattern (Req 6.3).
 *
 * <p>Conflicts between instruments issued by different authorities — a central rule, a state rule,
 * and a National Green Tribunal determination — must be reported with every conflicting instrument
 * enumerated in the Evidence_Chain (Req 6.3). The authority classifies each
 * {@link RegulatoryInstrument} so the conflict report can show which levels of the regulatory
 * hierarchy disagree.
 *
 * @see RegulatoryInstrument
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.3</a>
 */
public enum InstrumentAuthority {
    /** A central (national) regulatory instrument. */
    CENTRAL,
    /** A state regulatory instrument. */
    STATE,
    /** A National Green Tribunal determination. */
    NGT
}
