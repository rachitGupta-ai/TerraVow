package com.terravow.capability.passport;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;

/**
 * A mass-preserving airshed co-scheduling recommendation across the facilities that share an airshed
 * (Req 7.5).
 *
 * <p>The recommendation redistributes the facilities' emission mass across the airshed in proportion
 * to assimilative capacity while <em>preserving the total emission mass</em> of the affected
 * facilities (Req 7.5). The mass-preservation invariant — the sum of recommended masses equals the
 * sum of actual masses within a 0.1&nbsp;percent tolerance — is enforced in the compact constructor,
 * mirroring the AEI rescheduler's mass-preservation guarantee, so a non-preserving recommendation is
 * unrepresentable.
 *
 * @param entries        one line per facility, actual and recommended mass; never {@code null},
 *                      never empty, defensively copied
 * @param totalActualMass the sum of the facilities' actual emission masses; finite and non-negative
 * @param confidence     the confidence attached to the recommendation; never {@code null}
 * @param evidence       the Evidence_Chain supporting the recommendation; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.5</a>
 */
public record CoScheduleResult(
        List<CoScheduleEntry> entries,
        double totalActualMass,
        Confidence confidence,
        EvidenceChain evidence) {

    /** Mass-preservation tolerance: 0.1 percent of the actual total mass (design Property 15). */
    public static final double MASS_TOLERANCE_FRACTION = 0.001;

    /**
     * Validates presence of all references and the mass-preservation invariant (Req 7.5): the sum of
     * recommended masses equals {@code totalActualMass} within {@link #MASS_TOLERANCE_FRACTION}.
     *
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code entries} is empty, {@code totalActualMass} is
     *                                  non-finite or negative, or the recommended masses do not
     *                                  preserve the total within tolerance
     */
    public CoScheduleResult {
        Objects.requireNonNull(entries, "entries must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        entries = List.copyOf(entries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("co-scheduling result must contain at least one entry");
        }
        if (Double.isNaN(totalActualMass) || Double.isInfinite(totalActualMass)) {
            throw new IllegalArgumentException(
                    "totalActualMass must be a finite number, got: " + totalActualMass);
        }
        if (totalActualMass < 0.0) {
            throw new IllegalArgumentException(
                    "totalActualMass must be non-negative, got: " + totalActualMass);
        }
        double recommendedTotal = entries.stream().mapToDouble(CoScheduleEntry::recommendedMass).sum();
        double tolerance = Math.max(MASS_TOLERANCE_FRACTION * totalActualMass, 1.0e-9);
        if (Math.abs(recommendedTotal - totalActualMass) > tolerance) {
            throw new IllegalArgumentException(
                    "co-scheduling must preserve total emission mass within "
                            + (MASS_TOLERANCE_FRACTION * 100.0)
                            + "%: actual=" + totalActualMass + ", recommended=" + recommendedTotal);
        }
    }
}
