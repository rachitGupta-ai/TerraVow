package com.terravow.capability.economics;

import com.terravow.evidence.SourceRef;

import java.util.Objects;

/**
 * A single regulatory instrument — a central or state rule, or a National Green Tribunal
 * determination — that bears on a violation fact pattern (Req 6.3).
 *
 * <p>When two or more instruments conflict, the Economics Engine reports the conflict and
 * identifies every conflicting instrument in the Evidence_Chain (Req 6.3). Each instrument carries
 * its issuing {@link #authority()}, a non-blank {@link #citation()} identifying the instrument's
 * title/reference, and a resolvable {@link #source()} to its primary regulatory text, so a reported
 * conflict contributes one Evidence_Chain link per conflicting instrument.
 *
 * @param authority the issuing authority (central/state/NGT); never {@code null}
 * @param citation  a non-blank human-readable instrument reference (title / clause)
 * @param source    a resolvable reference to the instrument's primary regulatory text; never
 *                  {@code null}
 *
 * @see InstrumentConflict
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.3, 6.7, 10.1</a>
 */
public record RegulatoryInstrument(
        InstrumentAuthority authority, String citation, SourceRef source) {

    /**
     * Validates presence of the authority, a non-blank citation, and the primary-source reference
     * (Req 6.3, 6.7).
     *
     * @throws NullPointerException     if {@code authority}, {@code citation}, or {@code source} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code citation} is blank
     */
    public RegulatoryInstrument {
        Objects.requireNonNull(authority, "authority must not be null");
        Objects.requireNonNull(citation, "citation must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (citation.isBlank()) {
            throw new IllegalArgumentException("RegulatoryInstrument citation must not be blank");
        }
    }
}
