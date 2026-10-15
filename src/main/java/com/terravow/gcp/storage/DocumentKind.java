package com.terravow.gcp.storage;

/**
 * The document families TerraVow routes to Cloud Storage (Req 12.2).
 *
 * <p>The design maps Cloud Storage to the regulatory corpus, filings, and document primary sources
 * that Evidence_Chains reference and Vertex AI Search indexes. This enum names those families so a
 * {@link StoredDocument} declares its kind while sharing one object store seam.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 12.2</a>
 */
public enum DocumentKind {

    /** Regulatory corpus text (guidelines, statutes, standards). */
    REGULATORY_CORPUS,

    /** Submitted filings and returns. */
    FILING,

    /** Any other document primary source referenced by an Evidence_Chain. */
    PRIMARY_SOURCE
}
