package com.terravow.capability.economics;

import com.terravow.capability.economics.grounding.CorpusGap;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Environmental_Compensation (EC) exposure determination for a violation fact pattern (Req 6.2,
 * 6.9).
 *
 * <p>EC exposure is {@code base rate × days of violation × deterrence factor} computed by the
 * verified function-calling tool, never free-generated (Req 6.2). The three states this record can
 * take mirror the system's determination model:
 *
 * <ul>
 *   <li>{@link DeterminationStatus#COMPUTED} — all four grounded figures were present and the
 *       verified tool produced the exposure {@link #amount()} (Req 6.2); the {@link #evidence()}
 *       carries the primary-source citations for the figures and {@link #gaps()} is empty.</li>
 *   <li>{@link DeterminationStatus#UNDETERMINED} — a required figure was a grounding gap, so the
 *       exposure is undetermined; {@link #amount()} is empty, {@link #gaps()} names the undetermined
 *       figures, and the {@link #reason()} records why (Req 6.9).</li>
 *   <li>{@link DeterminationStatus#UNAVAILABLE} — the verified tool itself failed (e.g. arithmetic
 *       overflow), so the dependent value is unavailable with a recorded {@link #reason()}.</li>
 * </ul>
 *
 * <p>Every exposure, whatever its status, carries the {@link AdvisoryLabel#CITED_DRAFT_NOT_ADVICE}
 * label (Req 6.8) and an {@link EvidenceChain}. The record enforces the state/field consistency in
 * its compact constructor so an inconsistent exposure is unrepresentable.
 *
 * @param status   the determination status; never {@code null}
 * @param amount   the computed exposure, present iff {@code status == COMPUTED}
 * @param gaps     the figures reported undetermined; never {@code null}, non-empty iff
 *                 {@code status == UNDETERMINED}
 * @param reason   the recorded reason, present iff {@code status != COMPUTED}
 * @param evidence the Evidence_Chain of figure citations (Req 6.7, 10.1); never {@code null}
 * @param label    the advisory label (Req 6.8); never {@code null}
 *
 * @see EconomicsEngine
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.2, 6.8, 6.9</a>
 */
public record EcExposure(
        DeterminationStatus status,
        Optional<Money> amount,
        List<CorpusGap> gaps,
        Optional<String> reason,
        EvidenceChain evidence,
        AdvisoryLabel label) {

    /**
     * Validates the status/field-presence invariants and defensively copies the gaps (Req 6.2, 6.9).
     *
     * @throws NullPointerException     if any argument is {@code null} or a gap is {@code null}
     * @throws IllegalArgumentException if the fields are inconsistent with {@code status}
     */
    public EcExposure {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(gaps, "gaps must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(label, "label must not be null");
        gaps = List.copyOf(gaps);
        switch (status) {
            case COMPUTED -> {
                if (amount.isEmpty()) {
                    throw new IllegalArgumentException("COMPUTED EC exposure must carry an amount");
                }
                if (!gaps.isEmpty()) {
                    throw new IllegalArgumentException("COMPUTED EC exposure must have no gaps");
                }
                if (reason.isPresent()) {
                    throw new IllegalArgumentException("COMPUTED EC exposure must carry no reason");
                }
            }
            case UNDETERMINED -> {
                if (amount.isPresent()) {
                    throw new IllegalArgumentException(
                            "UNDETERMINED EC exposure must not carry an amount (Req 6.9)");
                }
                if (gaps.isEmpty()) {
                    throw new IllegalArgumentException(
                            "UNDETERMINED EC exposure must name at least one gap (Req 6.9)");
                }
                if (reason.isEmpty()) {
                    throw new IllegalArgumentException(
                            "UNDETERMINED EC exposure must carry a reason (Req 6.9)");
                }
            }
            case UNAVAILABLE -> {
                if (amount.isPresent()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE EC exposure must not carry an amount");
                }
                if (reason.isEmpty()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE EC exposure must carry a reason");
                }
            }
            default -> throw new IllegalArgumentException("Unexpected EC status: " + status);
        }
    }

    /**
     * Returns whether the exposure was computed by the verified tool (Req 6.2).
     *
     * @return {@code true} iff {@code status == COMPUTED}
     */
    public boolean isComputed() {
        return status == DeterminationStatus.COMPUTED;
    }
}
