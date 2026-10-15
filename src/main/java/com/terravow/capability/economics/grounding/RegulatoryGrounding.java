package com.terravow.capability.economics.grounding;

/**
 * Managed retrieval over the regulatory corpus: resolves the Environmental_Compensation guideline
 * chapter, base rate, days of violation, and deterrence factor for a violation fact pattern, and
 * returns the passages + citations that feed the Evidence_Chain (Req 6.1, 6.7, 10.1).
 *
 * <p>The design grounds EC figures with <strong>Vertex AI Search</strong> (semantic + keyword RAG
 * over the Cloud Storage corpus with a BigQuery connector) and <strong>BigQuery embeddings + vector
 * search</strong> for co-located similarity. Those managed substrates sit behind the injected
 * {@link RegulatoryCorpusPort} seam, so this service is deterministic and testable against a
 * controlled in-memory corpus while the live Google Cloud clients remain a later, config-gated
 * concern. The service never free-generates a figure: a figure the corpus cannot return is reported
 * as undetermined with a recorded {@link CorpusGap} (Req 6.9). Follows the TerraVow interface +
 * {@code Default...} implementation convention.
 *
 * @see DefaultRegulatoryGrounding
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.7, 6.9,
 *      10.1</a>
 */
public interface RegulatoryGrounding {

    /**
     * Resolves the four EC figures for a violation fact pattern from the regulatory corpus (Req
     * 6.1).
     *
     * <p>Each figure that the corpus returns is grounded with its primary-source citation and
     * contributes a link to the result's Evidence_Chain (Req 6.7, 10.1); each figure the corpus
     * cannot return is reported as a {@link CorpusGap} and marked undetermined (Req 6.9). The
     * returned {@link GroundedFigures} bundle carries both outcomes and the derived Evidence_Chain.
     *
     * @param facts the violation fact pattern used as the retrieval key; never {@code null}
     * @return the resolved figures, recorded gaps, and the Evidence_Chain of primary-source links
     * @throws NullPointerException if {@code facts} is {@code null}
     */
    GroundedFigures resolve(ViolationFacts facts);
}
