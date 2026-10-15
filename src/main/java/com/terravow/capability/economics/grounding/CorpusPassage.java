package com.terravow.capability.economics.grounding;

import com.terravow.evidence.SourceRef;
import com.terravow.reasoning.GroundingMode;

import java.util.Objects;
import java.util.Optional;

/**
 * A single passage returned by the {@link RegulatoryCorpusPort} for a figure lookup, carrying the
 * retrieved snippet, a resolvable citation to its primary source, and (when the passage encodes a
 * numeric figure) the parsed numeric value.
 *
 * <p>A passage models one grounding result from either managed retrieval substrate — Vertex AI
 * Search semantic + keyword RAG, or BigQuery embeddings + vector search for co-located similarity
 * (Req 6.1). The {@link #retrievedVia()} mode records which substrate produced it, so the Evidence_
 * Chain reflects whether the primary search path or the vector-search path grounded the figure. The
 * {@link #source()} citation ties the passage to a primary regulatory text so every grounded figure
 * resolves to a primary source (Req 6.7, 10.1).
 *
 * <p>For {@link FigureKind#GUIDELINE_CHAPTER} the figure is textual (the chapter identifier is the
 * {@link #snippet()}); for the numeric kinds ({@code BASE_RATE}, {@code DAYS_OF_VIOLATION},
 * {@code DETERRENCE_FACTOR}) the parsed {@link #numericValue()} is present.
 *
 * @param figure       the kind of figure this passage resolves; never {@code null}
 * @param snippet      the retrieved passage text supporting the figure; non-blank
 * @param numericValue the parsed numeric value when the figure is numeric; never {@code null},
 *                     empty for a textual figure
 * @param source       the resolvable citation to the primary regulatory text; never {@code null}
 * @param retrievedVia the managed-retrieval substrate that produced this passage; never
 *                     {@code null}
 *
 * @see RegulatoryCorpusPort
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.7, 10.1</a>
 */
public record CorpusPassage(
        FigureKind figure,
        String snippet,
        Optional<Double> numericValue,
        SourceRef source,
        GroundingMode retrievedVia) {

    /**
     * Validates presence of all references and that a numeric figure carries a finite value (Req
     * 6.1, 6.7).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code snippet} is blank or a present numeric value is
     *                                  non-finite
     */
    public CorpusPassage {
        Objects.requireNonNull(figure, "figure must not be null");
        Objects.requireNonNull(snippet, "snippet must not be null");
        Objects.requireNonNull(numericValue, "numericValue must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(retrievedVia, "retrievedVia must not be null");
        if (snippet.isBlank()) {
            throw new IllegalArgumentException("CorpusPassage snippet must not be blank");
        }
        if (numericValue.isPresent()) {
            double v = numericValue.get();
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalArgumentException(
                        "CorpusPassage numericValue must be finite, got: " + v);
            }
        }
    }

    /**
     * Builds a textual passage (used for {@link FigureKind#GUIDELINE_CHAPTER}).
     *
     * @param figure       the figure kind; never {@code null}
     * @param snippet      the retrieved chapter text; non-blank
     * @param source       the primary-source citation; never {@code null}
     * @param retrievedVia the retrieval substrate; never {@code null}
     * @return a textual corpus passage with no numeric value
     */
    public static CorpusPassage textual(
            FigureKind figure, String snippet, SourceRef source, GroundingMode retrievedVia) {
        return new CorpusPassage(figure, snippet, Optional.empty(), source, retrievedVia);
    }

    /**
     * Builds a numeric passage (used for base rate, days of violation, deterrence factor).
     *
     * @param figure       the figure kind; never {@code null}
     * @param value        the parsed numeric value; must be finite
     * @param snippet      the retrieved supporting text; non-blank
     * @param source       the primary-source citation; never {@code null}
     * @param retrievedVia the retrieval substrate; never {@code null}
     * @return a numeric corpus passage
     */
    public static CorpusPassage numeric(
            FigureKind figure,
            double value,
            String snippet,
            SourceRef source,
            GroundingMode retrievedVia) {
        return new CorpusPassage(figure, snippet, Optional.of(value), source, retrievedVia);
    }
}
