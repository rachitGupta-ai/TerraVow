package com.terravow.degradation;

import java.util.Optional;

/**
 * The set of managed Google-platform paths TerraVow executes, each paired with its predefined safe
 * fallback (Req 12.3–12.6).
 *
 * <p>Every managed path is a <em>degradable enrichment path</em>: on error or timeout the primary
 * path is treated as unavailable (Req 12.3) and the {@linkplain #fallback() configured fallback} is
 * run instead, at strictly lower confidence (Req 12.4). The wiring below is the single source of
 * truth for the design's fallback table — it must stay in step with the "Platform &amp; Graceful
 * Degradation" section of the design document:
 *
 * <table>
 *   <caption>Managed path &rarr; safe fallback</caption>
 *   <tr><th>Primary managed path</th><th>Safe fallback</th></tr>
 *   <tr><td>Vertex AI Agent Engine</td><td>Direct Gemini call</td></tr>
 *   <tr><td>Grounding with Vertex AI Search</td><td>BigQuery embeddings + vector search</td></tr>
 *   <tr><td>BigQuery vector search</td><td>Keyword/corpus scan of Cloud Storage filings</td></tr>
 *   <tr><td>Pub/Sub + BigQuery continuous query</td><td>Batch micro-poll of the BigQuery table</td></tr>
 *   <tr><td>Gemini 3.1 Pro</td><td>Gemini Flash (reduced-scope prompt)</td></tr>
 *   <tr><td>Agent Engine Memory Bank</td><td>Stateless single-session reasoning</td></tr>
 * </table>
 *
 * <p>A terminal fallback path (the right-hand column above) has no further fallback of its own: if
 * it too is unavailable the result is undetermined and routed to human review, with the unavailable
 * path recorded (Req 12.6).
 *
 * <p>The {@link #id()} of each constant is the stable identifier recorded in the Evidence_Chain as
 * the {@code fallbackPathId} / replaced {@code primaryPathId} (Req 12.5). Identifiers are fixed
 * strings, never derived from wall-clock time or randomness, so provenance is deterministic.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.3, 12.4, 12.5,
 *      12.6</a>
 */
public enum ManagedPath {

    /** Vertex AI Agent Engine specialist agent &rarr; direct Gemini call. */
    AGENT_ENGINE("agent-engine", "direct Gemini call via orchestrator"),

    /** Grounding with Vertex AI Search &rarr; BigQuery embeddings + vector search. */
    VERTEX_AI_SEARCH("vertex-ai-search", "BigQuery embeddings + vector search"),

    /** BigQuery vector search &rarr; keyword/corpus scan of Cloud Storage filings. */
    BIGQUERY_VECTOR_SEARCH("bigquery-vector-search", "keyword/corpus scan of Cloud Storage"),

    /** Pub/Sub + BigQuery continuous query &rarr; batch micro-poll of the same table. */
    CONTINUOUS_QUERY("continuous-query", "batch micro-poll of BigQuery table"),

    /** Gemini 3.1 Pro deep reasoning &rarr; Gemini Flash reduced-scope prompt. */
    GEMINI_PRO("gemini-pro", "Gemini Flash reduced-scope prompt"),

    /** Agent Engine Memory Bank cross-session context &rarr; stateless single-session reasoning. */
    MEMORY_BANK("memory-bank", "stateless single-session reasoning"),

    // ---- Terminal fallback paths (no further fallback) -------------------------------------------

    /** Direct Gemini call; terminal fallback for {@link #AGENT_ENGINE}. */
    DIRECT_GEMINI("direct-gemini", null),

    /** BigQuery embeddings + vector search; terminal fallback for {@link #VERTEX_AI_SEARCH}. */
    BIGQUERY_VECTOR("bigquery-vector", null),

    /** Keyword/corpus scan of Cloud Storage; terminal fallback for {@link #BIGQUERY_VECTOR_SEARCH}. */
    CORPUS_SCAN("corpus-scan", null),

    /** Batch micro-poll of the BigQuery table; terminal fallback for {@link #CONTINUOUS_QUERY}. */
    BATCH_MICRO_POLL("batch-micro-poll", null),

    /** Gemini Flash reduced-scope prompt; terminal fallback for {@link #GEMINI_PRO}. */
    GEMINI_FLASH("gemini-flash", null),

    /** Stateless single-session reasoning; terminal fallback for {@link #MEMORY_BANK}. */
    STATELESS("stateless", null);

    private final String id;
    private final String fallbackName;

    ManagedPath(String id, String fallbackName) {
        this.id = id;
        this.fallbackName = fallbackName;
    }

    /**
     * The stable identifier recorded in the Evidence_Chain for this path (Req 12.5).
     *
     * @return a fixed, non-blank identifier
     */
    public String id() {
        return id;
    }

    /**
     * The predefined safe fallback for this path, if one exists (Req 12.3).
     *
     * <p>A primary managed path returns its configured fallback; a terminal fallback path returns
     * {@link Optional#empty()}, signalling that no further safe fallback is defined (Req 12.6).
     *
     * @return the fallback path, or empty if this path is terminal
     */
    public Optional<ManagedPath> fallback() {
        return switch (this) {
            case AGENT_ENGINE -> Optional.of(DIRECT_GEMINI);
            case VERTEX_AI_SEARCH -> Optional.of(BIGQUERY_VECTOR);
            case BIGQUERY_VECTOR_SEARCH -> Optional.of(CORPUS_SCAN);
            case CONTINUOUS_QUERY -> Optional.of(BATCH_MICRO_POLL);
            case GEMINI_PRO -> Optional.of(GEMINI_FLASH);
            case MEMORY_BANK -> Optional.of(STATELESS);
            default -> Optional.empty();
        };
    }

    /**
     * A short human-readable description of the fallback this path degrades to, or {@code null} for
     * a terminal path. Intended for logs and audit narration, not for provenance keying.
     *
     * @return the fallback description, or {@code null} if this path is terminal
     */
    public String fallbackDescription() {
        return fallbackName;
    }
}
