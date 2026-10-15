/**
 * The cross-cutting control flow that ties the structural safety components end to end (design
 * &rarr; Cross-Cutting Control Flow; Req 8.1, 10.3, 11.1, 13.5).
 *
 * <p>Defines the generic {@link com.terravow.pipeline.Determination} abstraction every capability
 * result conforms to (value + {@link com.terravow.domain.Confidence} +
 * {@link com.terravow.evidence.EvidenceChain}) and the
 * {@link com.terravow.pipeline.CrossCuttingControlFlow} that composes — without modifying — the
 * masking gate, the {@code Reasoning_Agent} orchestrator, the Decision_Gate, the Evidence/Audit
 * service, and the advisory disclaimer: capabilities reason with masked inputs, recommendations
 * route through the gate, and decision-oriented outputs trigger synchronous audit persistence
 * before returning with the disclaimer attached.
 */
package com.terravow.pipeline;
