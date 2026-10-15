package com.terravow.capability.economics.grounding;

import java.util.Objects;

/**
 * A recorded gap: a required Environmental_Compensation figure that could not be located in the
 * regulatory corpus (Req 6.9).
 *
 * <p>When the {@link RegulatoryCorpusPort} returns no passage for a figure, {@link RegulatoryGrounding}
 * does not invent a value; it reports the figure as undetermined and records the gap (Req 6.9). The
 * gap names the missing {@link #kind()} and carries a non-blank {@link #reason()} describing why it
 * could not be resolved, so the audit trail and the Economics Engine downstream can surface exactly
 * which figure is undetermined.
 *
 * @param kind   the kind of figure that could not be located; never {@code null}
 * @param reason a non-blank explanation of the gap
 *
 * @see GroundedFigures
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.9</a>
 */
public record CorpusGap(FigureKind kind, String reason) {

    /**
     * Validates that the missing kind and a non-blank reason are present (Req 6.9).
     *
     * @throws NullPointerException     if {@code kind} or {@code reason} is {@code null}
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public CorpusGap {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("CorpusGap reason must not be blank (Req 6.9)");
        }
    }
}
