package com.terravow.capability.aei;

/**
 * The outcome status of an AEI determination (Req 4.2, 4.3).
 *
 * <p>An AEI is {@link #COMPUTED} when both a meteorological forecast and a Receptor_Map are
 * available so the harm difference can be evaluated (Req 4.3); it is {@link #UNDETERMINED} when
 * either input is absent, carrying a recorded reason (Req 4.2). AEI uses this dedicated two-state
 * status (rather than the domain-wide {@code DeterminationStatus}) because the design's
 * {@code AeiResult} is specified as {@code COMPUTED | UNDETERMINED}.
 *
 * @see AeiResult
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.2, 4.3</a>
 */
public enum AeiStatus {
    /** The AEI harm difference was computed from the available forecast and receptor inputs. */
    COMPUTED,
    /** The AEI could not be determined because a required input was absent; a reason is recorded. */
    UNDETERMINED
}
