package com.terravow.capability.lifecycle;

import com.terravow.evidence.EvidenceChain;

import java.util.Objects;

/**
 * A queued maintenance action placed in the Emissions_Aware_Priority ranking, carrying its priority,
 * its zero-based rank position, and the Evidence_Chain supporting the ranking (Req 15.3, 15.5).
 *
 * <p>The maintenance queue is ranked by {@link EmissionsAwarePriority#score()} descending, so a
 * higher-environmental-harm action is placed above a lower-harm action that merely has a higher
 * failure probability (Req 15.5); {@link #rank()} is {@code 0} for the highest-priority action.
 * The {@link #evidence()} chain records the ranking basis — the priority terms and, where applied,
 * the AEI weighting — so the ranking is machine-checkable to primary sources (Req 15.5).
 *
 * @param action    the queued action being ranked; never {@code null}
 * @param priority  the Emissions_Aware_Priority of the action; never {@code null}
 * @param rank      the zero-based rank position ({@code 0} = highest priority); {@code >= 0}
 * @param evidence  the Evidence_Chain supporting the ranking (Req 15.5); never {@code null}
 *
 * @see MaintenanceLifecycle#prioritise(java.util.List)
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.3, 15.5</a>
 */
public record RankedAction(
        QueuedAction action, EmissionsAwarePriority priority, int rank, EvidenceChain evidence) {

    /**
     * Validates the references and the non-negative rank (Req 15.5).
     *
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code rank} is negative
     */
    public RankedAction {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        if (rank < 0) {
            throw new IllegalArgumentException("rank must not be negative, got: " + rank);
        }
    }

    /**
     * The Emissions_Aware_Priority score of this ranked action (Req 15.3).
     *
     * @return the priority score
     */
    public double score() {
        return priority.score();
    }
}
