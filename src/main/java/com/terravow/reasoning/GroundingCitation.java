package com.terravow.reasoning;

import com.terravow.evidence.SourceRef;

import java.util.Objects;

/**
 * A citation returned by managed grounding (Vertex AI Search / BigQuery vector search) that is
 * folded into the Evidence_Chain of a reasoning determination (Req 6.1, 10.1, 11.1).
 *
 * <p>Each citation ties a retrieved passage back to a resolvable primary source, so a grounded
 * interpretive value satisfies the Evidence_Chain invariant that every link resolves to a primary
 * source (Req 10.1). The orchestrator collects the citations a specialist agent returns and surfaces
 * them on {@link ReasoningResult} for the capability to merge into its Evidence_Chain.
 *
 * @param source  the resolvable reference to the cited primary source; never {@code null}
 * @param snippet the retrieved passage text supporting the determination; never {@code null},
 *                may be empty
 *
 * @see ReasoningResult
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 10.1, 11.1</a>
 */
public record GroundingCitation(SourceRef source, String snippet) {

    /**
     * Validates that the cited source and snippet are present (Req 10.1).
     *
     * @throws NullPointerException if {@code source} or {@code snippet} is {@code null}
     */
    public GroundingCitation {
        Objects.requireNonNull(source, "GroundingCitation source must not be null");
        Objects.requireNonNull(snippet, "GroundingCitation snippet must not be null");
    }
}
