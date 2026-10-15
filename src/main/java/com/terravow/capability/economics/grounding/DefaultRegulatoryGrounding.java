package com.terravow.capability.economics.grounding;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link RegulatoryGrounding} implementation over the injected {@link RegulatoryCorpusPort}
 * seam (Req 6.1, 6.7, 6.9, 10.1).
 *
 * <p>For each of the four {@link FigureKind}s the service asks the corpus port for passages keyed by
 * the {@link ViolationFacts} fact pattern. The port abstracts both managed retrieval substrates —
 * Vertex AI Search semantic + keyword RAG and BigQuery embeddings + vector search — so this logic is
 * identical whether the live GCP clients or the controlled in-memory corpus back it.
 *
 * <p>Resolution rules:
 *
 * <ul>
 *   <li>When the port returns at least one passage for a figure, the first passage is taken as the
 *       grounded figure. Its citation (primary-source reference + snippet) is folded into the
 *       Evidence_Chain, so every regulatory figure carries a primary-source citation (Req 6.1, 6.7,
 *       10.1).</li>
 *   <li>When the port returns no passage for a figure, the service does not invent a value; it
 *       records a {@link CorpusGap} and that figure is reported undetermined (Req 6.9).</li>
 * </ul>
 *
 * <p>The service is deterministic (same facts + same corpus → same figures and gaps) and holds no
 * mutable state, so it is safe to share as a singleton bean.
 *
 * @see RegulatoryGrounding
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.7, 6.9,
 *      10.1</a>
 */
public class DefaultRegulatoryGrounding implements RegulatoryGrounding {

    private final RegulatoryCorpusPort corpus;

    /**
     * Creates the service over the managed-retrieval seam (constructor injection).
     *
     * @param corpus the regulatory-corpus retrieval port; never {@code null}
     * @throws NullPointerException if {@code corpus} is {@code null}
     */
    public DefaultRegulatoryGrounding(RegulatoryCorpusPort corpus) {
        this.corpus = Objects.requireNonNull(corpus, "corpus must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public GroundedFigures resolve(ViolationFacts facts) {
        Objects.requireNonNull(facts, "facts must not be null");

        Map<FigureKind, GroundedFigure> resolved = new EnumMap<>(FigureKind.class);
        List<CorpusGap> gaps = new java.util.ArrayList<>();

        for (FigureKind kind : FigureKind.values()) {
            List<CorpusPassage> passages = corpus.lookup(facts, kind);
            if (passages == null || passages.isEmpty()) {
                gaps.add(
                        new CorpusGap(
                                kind,
                                "figure "
                                        + kind
                                        + " not located in regulatory corpus for violation class '"
                                        + facts.violationClass()
                                        + "' ("
                                        + facts.pollutant()
                                        + "/"
                                        + facts.medium()
                                        + ")"));
                continue;
            }
            resolved.put(kind, GroundedFigure.fromPassage(passages.get(0)));
        }

        return GroundedFigures.of(facts, resolved, gaps);
    }
}
