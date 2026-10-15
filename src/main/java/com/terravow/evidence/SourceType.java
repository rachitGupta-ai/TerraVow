package com.terravow.evidence;

/**
 * The kind of primary source an {@link EvidenceLink} resolves to.
 *
 * <p>Every link in an {@link EvidenceChain} must resolve to a primary source (Req 10.1). The source
 * type classifies that primary source so provenance can be audited by category.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.1</a>
 */
public enum SourceType {
    /** A stack / effluent / sensor reading. */
    READING,
    /** A regulatory or corporate filing. */
    FILING,
    /** A primary regulatory text (statute, rule, guideline chapter). */
    REGULATORY_TEXT,
    /** An append-only Operating_Log record (the domain provenance spine). */
    OPERATING_LOG,
    /** A degradation fallback path that was substituted for a primary path (Req 12.4). */
    FALLBACK_PATH
}
