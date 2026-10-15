package com.terravow.reasoning.tool;

/**
 * Input arguments for the mass-preservation check tool (Req 4.5).
 *
 * <p>A rescheduling recommendation must preserve the total emission mass of the affected operations
 * within a tolerance (0.1 percent per Req 4.5). The check compares a candidate schedule's total
 * mass against the original total mass. Both masses are expressed in the same unit (e.g. kilograms)
 * as a double magnitude.
 *
 * <p>This record is a plain argument carrier; the tool ({@link MassPreservationTool}) performs all
 * validation so invalid inputs surface as an unavailable result rather than a construction-time
 * exception.
 *
 * @param originalMass      the original total emission mass; expected finite and {@code >= 0}
 * @param candidateMass     the candidate schedule's total emission mass; expected finite and
 *                          {@code >= 0}
 * @param toleranceFraction the permitted relative deviation as a fraction (e.g. {@code 0.001} for
 *                          0.1 percent); expected finite and {@code >= 0}
 *
 * @see MassPreservationTool
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.5</a>
 */
public record MassPreservationRequest(
        double originalMass,
        double candidateMass,
        double toleranceFraction) {
}
