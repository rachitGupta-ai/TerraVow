package com.terravow.capability.economics.grounding;

import java.util.List;

/**
 * The injected seam over managed regulatory-corpus retrieval (Req 6.1).
 *
 * <p>This port abstracts the two managed retrieval substrates named in the design — Grounding with
 * Vertex AI Search (semantic + keyword RAG over the Cloud Storage corpus with a BigQuery connector)
 * and BigQuery embeddings + vector search for co-located similarity. {@link RegulatoryGrounding}
 * depends only on this interface, so the live Google Cloud clients are a later, config-gated concern
 * and the grounding logic is exercised against a controlled in-memory corpus in tests. A production
 * adapter and the degradation fallback (Vertex AI Search → BigQuery vector search) wire in behind
 * this same seam (Req 12.3–12.6, separate tasks).
 *
 * <p>Each lookup returns the passages the retrieval substrate found for a single
 * {@link FigureKind}, keyed by the {@link ViolationFacts} fact pattern. An empty list means the
 * figure could not be located in the corpus, which the grounding layer reports as undetermined with
 * a recorded gap (Req 6.9). Implementations must never fabricate a passage; absence is an empty
 * result, not an invented figure (Req 6.1, 6.7).
 *
 * @see RegulatoryGrounding
 * @see CorpusPassage
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.7, 6.9</a>
 */
public interface RegulatoryCorpusPort {

    /**
     * Retrieves the passages the managed corpus holds for a single figure of a violation fact
     * pattern (Req 6.1).
     *
     * <p>The returned passages carry their retrieval substrate ({@link CorpusPassage#retrievedVia()})
     * and a resolvable primary-source citation ({@link CorpusPassage#source()}), so grounded figures
     * feed the Evidence_Chain (Req 6.7, 10.1). An empty list signals the figure is not retrievable
     * from the corpus (Req 6.9).
     *
     * @param facts the violation fact pattern used as the retrieval key; never {@code null}
     * @param figure the figure kind to resolve; never {@code null}
     * @return the passages found for the figure; never {@code null}, possibly empty
     */
    List<CorpusPassage> lookup(ViolationFacts facts, FigureKind figure);
}
