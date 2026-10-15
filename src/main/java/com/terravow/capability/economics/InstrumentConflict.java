package com.terravow.capability.economics;

import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceType;

import java.util.List;
import java.util.Objects;

/**
 * A report that two or more regulatory instruments conflict for a fact pattern, enumerating every
 * conflicting instrument in the Evidence_Chain (Req 6.3).
 *
 * <p>When central and state instruments or National Green Tribunal determinations disagree, the
 * conflict must be reported and <em>every</em> conflicting instrument identified in the
 * Evidence_Chain (Req 6.3). This record holds the full list of {@link #instruments()} and derives an
 * {@link #evidence()} chain with one {@link SourceType#REGULATORY_TEXT} link per instrument, in the
 * stable order the instruments were supplied — so the "every conflicting instrument" guarantee is
 * structural: the chain's links correspond one-to-one with the enumerated instruments.
 *
 * <p>A conflict exists only when at least two instruments are present; the {@link #of(List)} factory
 * and the empty-conflict sentinel ({@link #none()}) make the "no conflict" case explicit rather than
 * an empty-but-valid conflict.
 *
 * @param conflicting whether a conflict was detected ({@code true} iff two or more instruments)
 * @param instruments every conflicting instrument; never {@code null}, defensively copied
 * @param evidence    the Evidence_Chain with one link per instrument (Req 6.3); never {@code null}
 *
 * @see RegulatoryInstrument
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.3</a>
 */
public record InstrumentConflict(
        boolean conflicting, List<RegulatoryInstrument> instruments, EvidenceChain evidence) {

    /**
     * Validates the conflict invariant and that the Evidence_Chain enumerates every instrument
     * (Req 6.3).
     *
     * @throws NullPointerException     if any argument is {@code null} or an instrument is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code conflicting} disagrees with the instrument count or
     *                                  the Evidence_Chain does not have one link per instrument
     */
    public InstrumentConflict {
        Objects.requireNonNull(instruments, "instruments must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        instruments = List.copyOf(instruments);
        if (conflicting && instruments.size() < 2) {
            throw new IllegalArgumentException(
                    "a conflict requires at least two instruments, got: " + instruments.size()
                            + " (Req 6.3)");
        }
        if (!conflicting && !instruments.isEmpty()) {
            throw new IllegalArgumentException(
                    "a non-conflict must enumerate no instruments, got: " + instruments.size());
        }
        // Req 6.3 — every conflicting instrument is identified in the Evidence_Chain.
        if (evidence.links().size() != instruments.size()) {
            throw new IllegalArgumentException(
                    "Evidence_Chain must enumerate every conflicting instrument: "
                            + evidence.links().size() + " links for " + instruments.size()
                            + " instruments (Req 6.3)");
        }
    }

    /**
     * The sentinel for no conflict: no instruments and an empty Evidence_Chain.
     *
     * @return a non-conflicting report
     */
    public static InstrumentConflict none() {
        return new InstrumentConflict(false, List.of(), EvidenceChain.EMPTY);
    }

    /**
     * Builds a conflict report from the conflicting instruments, deriving the Evidence_Chain with
     * one primary-source link per instrument in the supplied order (Req 6.3).
     *
     * <p>If fewer than two instruments are supplied there is no conflict and {@link #none()} is
     * returned, so a caller cannot construct a spurious single-instrument "conflict".
     *
     * @param instruments the conflicting instruments; never {@code null}
     * @return a conflict report enumerating every instrument, or {@link #none()} if fewer than two
     * @throws NullPointerException if {@code instruments} or any element is {@code null}
     */
    public static InstrumentConflict of(List<RegulatoryInstrument> instruments) {
        Objects.requireNonNull(instruments, "instruments must not be null");
        List<RegulatoryInstrument> copy = List.copyOf(instruments);
        if (copy.size() < 2) {
            return none();
        }
        EvidenceChain chain = EvidenceChain.EMPTY;
        for (RegulatoryInstrument instrument : copy) {
            chain = chain.with(new EvidenceLink(SourceType.REGULATORY_TEXT, instrument.source()));
        }
        return new InstrumentConflict(true, copy, chain);
    }
}
