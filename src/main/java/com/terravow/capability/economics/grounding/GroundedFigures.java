package com.terravow.capability.economics.grounding;

import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of resolving the Environmental_Compensation figures for a {@link ViolationFacts}
 * pattern from the regulatory corpus (Req 6.1, 6.7, 6.9, 10.1).
 *
 * <p>The bundle separates the figures that were grounded from those that were not:
 *
 * <ul>
 *   <li>{@link #resolved()} — the figures located in the corpus, each paired with its grounding
 *       citation (Req 6.1, 6.7).</li>
 *   <li>{@link #gaps()} — the figures that could not be located, each a recorded {@link CorpusGap}
 *       marking that figure undetermined (Req 6.9).</li>
 *   <li>{@link #evidence()} — an {@link EvidenceChain} whose links resolve to the primary regulatory
 *       texts of every resolved figure, ready to feed the consuming determination's Evidence_Chain
 *       (Req 6.7, 10.1).</li>
 * </ul>
 *
 * <p>The record is self-contained and immutable: both collections are defensively copied, and the
 * compact constructor rejects a figure that appears in both the resolved map and the gap set (a
 * figure is either grounded or undetermined, never both). The Economics Engine (a separate task)
 * consumes this bundle: it feeds the verified EC tool when all four figures are resolved, and
 * surfaces the gaps as undetermined figures otherwise.
 *
 * @param facts    the fact pattern these figures were resolved for; never {@code null}
 * @param resolved the grounded figures keyed by kind; never {@code null}, defensively copied
 * @param gaps     the recorded gaps for unresolvable figures; never {@code null}, defensively
 *                 copied
 * @param evidence the Evidence_Chain of primary-source links for the resolved figures; never
 *                 {@code null}
 *
 * @see RegulatoryGrounding
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.7, 6.9,
 *      10.1</a>
 */
public record GroundedFigures(
        ViolationFacts facts,
        Map<FigureKind, GroundedFigure> resolved,
        List<CorpusGap> gaps,
        EvidenceChain evidence) {

    /**
     * Validates and defensively copies the figures and gaps, and enforces that no figure is both
     * resolved and gapped (Req 6.1, 6.9).
     *
     * @throws NullPointerException     if any argument is {@code null} or any element is
     *                                  {@code null}
     * @throws IllegalArgumentException if a figure kind appears in both {@code resolved} and
     *                                  {@code gaps}
     */
    public GroundedFigures {
        Objects.requireNonNull(facts, "facts must not be null");
        Objects.requireNonNull(resolved, "resolved must not be null");
        Objects.requireNonNull(gaps, "gaps must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        resolved = Map.copyOf(resolved); // rejects null keys/values, immutable
        gaps = List.copyOf(gaps); // rejects null elements, immutable
        for (CorpusGap gap : gaps) {
            if (resolved.containsKey(gap.kind())) {
                throw new IllegalArgumentException(
                        "figure " + gap.kind() + " cannot be both resolved and a recorded gap");
            }
        }
    }

    /**
     * Returns the grounded figure for a kind, if it was resolved (Req 6.1).
     *
     * @param kind the figure kind; never {@code null}
     * @return the grounded figure, or empty if the figure is a recorded gap
     */
    public Optional<GroundedFigure> figure(FigureKind kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        return Optional.ofNullable(resolved.get(kind));
    }

    /**
     * Indicates whether any required figure is undetermined (a recorded gap) (Req 6.9).
     *
     * @return {@code true} iff at least one figure could not be located in the corpus
     */
    public boolean hasGaps() {
        return !gaps.isEmpty();
    }

    /**
     * Indicates whether all four EC figures were resolved from the corpus (Req 6.1).
     *
     * @return {@code true} iff every {@link FigureKind} is resolved and no gap is recorded
     */
    public boolean fullyResolved() {
        return gaps.isEmpty() && resolved.keySet().containsAll(List.of(FigureKind.values()));
    }

    /**
     * Builds a bundle from the per-kind resolution outcome, deriving the Evidence_Chain from the
     * resolved figures' citations (Req 6.7, 10.1).
     *
     * <p>Each resolved figure contributes one {@link EvidenceLink} of type
     * {@link SourceType#REGULATORY_TEXT} resolving to the figure's cited primary source, so the
     * returned bundle's {@link #evidence()} is ready to fold into the consuming determination's
     * Evidence_Chain. Links are added in the stable order the {@link FigureKind} enum declares, so
     * the chain is deterministic.
     *
     * @param facts    the fact pattern; never {@code null}
     * @param resolved the resolved figures keyed by kind; never {@code null}
     * @param gaps     the recorded gaps; never {@code null}
     * @return a grounded-figures bundle with a derived Evidence_Chain
     */
    public static GroundedFigures of(
            ViolationFacts facts,
            Map<FigureKind, GroundedFigure> resolved,
            List<CorpusGap> gaps) {
        Objects.requireNonNull(facts, "facts must not be null");
        Objects.requireNonNull(resolved, "resolved must not be null");
        Objects.requireNonNull(gaps, "gaps must not be null");

        // Preserve a deterministic, enum-declaration link order.
        Map<FigureKind, GroundedFigure> ordered = new EnumMap<>(FigureKind.class);
        ordered.putAll(resolved);

        EvidenceChain chain = EvidenceChain.EMPTY;
        for (FigureKind kind : FigureKind.values()) {
            GroundedFigure figure = ordered.get(kind);
            if (figure != null) {
                chain =
                        chain.with(
                                new EvidenceLink(
                                        SourceType.REGULATORY_TEXT, figure.citation().source()));
            }
        }
        return new GroundedFigures(facts, ordered, gaps, chain);
    }
}
