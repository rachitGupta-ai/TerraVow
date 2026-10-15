package com.terravow.capability.economics;

import java.util.Objects;

/**
 * The recommended replacement timing: the candidate maximising net position over the horizon, with
 * the recorded basis for the recommendation (Req 6.6).
 *
 * <p>The engine recommends the timing whose net-position value is greater than or equal to that of
 * every other candidate (Req 6.6). The {@link #recommended()} candidate is that maximiser and the
 * {@link #basis()} records why — a non-blank human-readable explanation referencing the compared
 * candidates — satisfying the "record the basis for that timing" obligation (Req 6.6). Every
 * recommendation carries the {@link AdvisoryLabel#CITED_DRAFT_NOT_ADVICE} label (Req 6.8).
 *
 * @param recommended the net-position-maximising candidate; never {@code null}
 * @param basis       a non-blank recorded basis for the timing (Req 6.6)
 * @param label       the advisory label (Req 6.8); never {@code null}
 *
 * @see EconomicsEngine
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.6, 6.8</a>
 */
public record TimingRecommendation(
        TimingCandidate recommended, String basis, AdvisoryLabel label) {

    /**
     * Validates presence of the recommended candidate, a non-blank basis, and the label (Req 6.6,
     * 6.8).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code basis} is blank
     */
    public TimingRecommendation {
        Objects.requireNonNull(recommended, "recommended must not be null");
        Objects.requireNonNull(basis, "basis must not be null");
        Objects.requireNonNull(label, "label must not be null");
        if (basis.isBlank()) {
            throw new IllegalArgumentException("timing recommendation basis must not be blank (Req 6.6)");
        }
    }
}
