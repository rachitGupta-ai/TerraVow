package com.terravow.capability.economics.grounding;

import com.terravow.reasoning.GroundingCitation;
import com.terravow.reasoning.GroundingMode;

import java.util.Objects;
import java.util.Optional;

/**
 * A single Environmental_Compensation figure successfully resolved from the regulatory corpus,
 * paired with the citation that grounds it (Req 6.1, 6.7).
 *
 * <p>A grounded figure is never invented: its {@link #citation()} ties it to the primary regulatory
 * text the retrieval substrate returned, so it feeds the Evidence_Chain and every regulatory figure
 * carries a primary-source citation (Req 6.7, 10.1). The {@link #retrievedVia()} mode records which
 * managed substrate (Vertex AI Search or BigQuery vector search) produced the passage. For the
 * numeric figure kinds the parsed {@link #numericValue()} is present; for
 * {@link FigureKind#GUIDELINE_CHAPTER} the textual chapter identifier is the
 * {@link GroundingCitation#snippet()} of the citation and {@code numericValue} is empty.
 *
 * @param kind         the kind of figure resolved; never {@code null}
 * @param numericValue the parsed numeric value for numeric figures; never {@code null}, empty for a
 *                     textual figure such as the guideline chapter
 * @param citation     the grounding citation (primary-source ref + snippet) for the figure; never
 *                     {@code null}
 * @param retrievedVia the managed-retrieval substrate that produced the figure; never {@code null}
 *
 * @see GroundedFigures
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.7, 10.1</a>
 */
public record GroundedFigure(
        FigureKind kind,
        Optional<Double> numericValue,
        GroundingCitation citation,
        GroundingMode retrievedVia) {

    /**
     * Validates presence of all references and that a present numeric value is finite (Req 6.1).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if a present numeric value is non-finite
     */
    public GroundedFigure {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(numericValue, "numericValue must not be null");
        Objects.requireNonNull(citation, "citation must not be null");
        Objects.requireNonNull(retrievedVia, "retrievedVia must not be null");
        if (numericValue.isPresent()) {
            double v = numericValue.get();
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalArgumentException(
                        "GroundedFigure numericValue must be finite, got: " + v);
            }
        }
    }

    /**
     * Builds a grounded figure from a resolved {@link CorpusPassage}, folding the passage's citation
     * details into a {@link GroundingCitation} (Req 6.7, 10.1).
     *
     * @param passage the resolved passage; never {@code null}
     * @return a grounded figure carrying the passage's value, citation, and retrieval substrate
     * @throws NullPointerException if {@code passage} is {@code null}
     */
    public static GroundedFigure fromPassage(CorpusPassage passage) {
        Objects.requireNonNull(passage, "passage must not be null");
        return new GroundedFigure(
                passage.figure(),
                passage.numericValue(),
                new GroundingCitation(passage.source(), passage.snippet()),
                passage.retrievedVia());
    }
}
