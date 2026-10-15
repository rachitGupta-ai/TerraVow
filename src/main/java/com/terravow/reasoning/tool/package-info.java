/**
 * Verified deterministic tools the reasoning layer invokes via Gemini function calling.
 *
 * <p>Gemini function calling lets the model orchestrate while TerraVow executes the actual
 * computation: the model returns a structured call naming a {@link com.terravow.reasoning.ToolId},
 * and TerraVow runs the matching verified tool and feeds the number back. The deterministic
 * numbers — Environmental_Compensation (EC), Environmental Regulatory Debt (ERD) summation,
 * mass-preservation check, and net-position arithmetic — are produced by these verified tools,
 * <em>not free-generated</em> by the model (design principle: deterministic-first).
 *
 * <p>Every tool is a pure deterministic function of its typed request. A tool-execution failure
 * (missing or invalid inputs, or an arithmetic error) does not throw out of the tool boundary;
 * instead the dependent value is marked {@link com.terravow.domain.DeterminationStatus#UNAVAILABLE}
 * with a recorded reason, so the reasoning layer never substitutes an unverified number for a
 * failed verified computation.
 *
 * @see com.terravow.reasoning.tool.VerifiedTool
 * @see com.terravow.reasoning.tool.VerifiedToolRegistry
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 4.5, 6.2,
 *      6.5</a>
 */
package com.terravow.reasoning.tool;
