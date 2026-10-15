package com.terravow.reasoning.tool;

/**
 * The computed outcome of a mass-preservation check (Req 4.5).
 *
 * <p>Reports whether the candidate schedule preserves the original total emission mass within the
 * requested tolerance, alongside the relative deviation that was measured and the tolerance it was
 * compared against. A rescheduling candidate that is not preserved must be rejected and the actual
 * schedule retained (Req 4.6).
 *
 * @param preserved         {@code true} iff the relative deviation is within tolerance
 * @param relativeDeviation the measured relative deviation {@code |candidate - original| / original}
 * @param toleranceFraction the tolerance the deviation was compared against
 *
 * @see MassPreservationTool
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.5, 4.6</a>
 */
public record MassPreservationResult(
        boolean preserved,
        double relativeDeviation,
        double toleranceFraction) {
}
