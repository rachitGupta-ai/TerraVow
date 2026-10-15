/**
 * Regulatory grounding for the Economics Engine (Req 6.1, 6.7, 6.9, 10.1).
 *
 * <p>Resolves Environmental_Compensation figures (guideline chapter, base rate, days of violation,
 * deterrence factor) from the regulatory corpus via managed retrieval — Vertex AI Search (semantic
 * + keyword) and BigQuery embeddings + vector search — behind the injected
 * {@link com.terravow.capability.economics.grounding.RegulatoryCorpusPort} seam. Returned passages
 * and citations feed the Evidence_Chain; a figure the corpus cannot return is reported undetermined
 * with a recorded gap.
 */
package com.terravow.capability.economics.grounding;
