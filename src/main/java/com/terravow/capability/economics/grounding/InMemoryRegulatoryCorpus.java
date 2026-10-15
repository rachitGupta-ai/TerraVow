package com.terravow.capability.economics.grounding;

import com.terravow.evidence.SourceRef;
import com.terravow.reasoning.GroundingMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic, in-memory {@link RegulatoryCorpusPort} backed by an explicit set of corpus
 * passages (Req 6.1, 6.9).
 *
 * <p>This stub stands in for the managed retrieval substrates (Vertex AI Search semantic + keyword
 * RAG and BigQuery embeddings + vector search) in tests and local runs. Passages are keyed by the
 * exact {@link ViolationFacts} fact pattern and {@link FigureKind}, so lookups are fully
 * deterministic: the same facts and figure always return the same passages, and an unseeded figure
 * returns an empty list — which the grounding layer reports as a {@link CorpusGap} / undetermined
 * figure (Req 6.9). The live GCP adapter wires in behind the same {@link RegulatoryCorpusPort} seam.
 *
 * <p>Build an instance with {@link #builder()}, seeding whichever figures a scenario needs; omit a
 * figure to simulate a corpus gap.
 *
 * @see RegulatoryCorpusPort
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.9</a>
 */
public final class InMemoryRegulatoryCorpus implements RegulatoryCorpusPort {

    private final Map<Key, List<CorpusPassage>> passages;

    private InMemoryRegulatoryCorpus(Map<Key, List<CorpusPassage>> passages) {
        // Deep, immutable copy so the corpus cannot be mutated after construction.
        Map<Key, List<CorpusPassage>> copy = new HashMap<>();
        for (Map.Entry<Key, List<CorpusPassage>> entry : passages.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.passages = Map.copyOf(copy);
    }

    /** {@inheritDoc} */
    @Override
    public List<CorpusPassage> lookup(ViolationFacts facts, FigureKind figure) {
        Objects.requireNonNull(facts, "facts must not be null");
        Objects.requireNonNull(figure, "figure must not be null");
        return passages.getOrDefault(new Key(facts, figure), List.of());
    }

    /**
     * Creates a new builder for assembling a controlled corpus.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** The composite lookup key: a fact pattern paired with a figure kind. */
    private record Key(ViolationFacts facts, FigureKind figure) {}

    /**
     * Fluent builder for an {@link InMemoryRegulatoryCorpus}. Seeding a figure makes it retrievable;
     * omitting a figure makes it a corpus gap (Req 6.9).
     */
    public static final class Builder {

        private final Map<Key, List<CorpusPassage>> passages = new HashMap<>();

        private Builder() {}

        /**
         * Seeds a textual figure (the guideline chapter) for a fact pattern (Req 6.1, 6.7).
         *
         * @param facts        the fact pattern this figure answers; never {@code null}
         * @param figure       the figure kind; never {@code null}
         * @param snippet      the chapter text; non-blank
         * @param sourceId     the primary-source identifier (e.g. a Cloud Storage object id);
         *                     non-blank
         * @param retrievedVia the retrieval substrate; never {@code null}
         * @return this builder
         */
        public Builder withTextualFigure(
                ViolationFacts facts,
                FigureKind figure,
                String snippet,
                String sourceId,
                GroundingMode retrievedVia) {
            add(
                    facts,
                    figure,
                    CorpusPassage.textual(
                            figure, snippet, new SourceRef(sourceId), retrievedVia));
            return this;
        }

        /**
         * Seeds a numeric figure (base rate, days of violation, deterrence factor) for a fact
         * pattern (Req 6.1, 6.7).
         *
         * @param facts        the fact pattern this figure answers; never {@code null}
         * @param figure       the figure kind; never {@code null}
         * @param value        the numeric value; must be finite
         * @param snippet      the supporting text; non-blank
         * @param sourceId     the primary-source identifier; non-blank
         * @param retrievedVia the retrieval substrate; never {@code null}
         * @return this builder
         */
        public Builder withNumericFigure(
                ViolationFacts facts,
                FigureKind figure,
                double value,
                String snippet,
                String sourceId,
                GroundingMode retrievedVia) {
            add(
                    facts,
                    figure,
                    CorpusPassage.numeric(
                            figure, value, snippet, new SourceRef(sourceId), retrievedVia));
            return this;
        }

        private void add(ViolationFacts facts, FigureKind figure, CorpusPassage passage) {
            Objects.requireNonNull(facts, "facts must not be null");
            Objects.requireNonNull(figure, "figure must not be null");
            passages.computeIfAbsent(new Key(facts, figure), k -> new ArrayList<>()).add(passage);
        }

        /**
         * Builds the immutable corpus.
         *
         * @return a new {@link InMemoryRegulatoryCorpus} holding the seeded passages
         */
        public InMemoryRegulatoryCorpus build() {
            return new InMemoryRegulatoryCorpus(passages);
        }
    }
}
