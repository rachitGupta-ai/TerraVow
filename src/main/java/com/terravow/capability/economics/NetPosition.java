package com.terravow.capability.economics;

import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;
import java.util.Optional;

/**
 * The net-position determination: {@code total incentives + avoided EC − penalty} (Req 6.5).
 *
 * <p>The identity is computed by the verified net-position function-calling tool, never
 * free-generated (Req 6.5). On success the {@link #value()} is present and {@code status} is
 * {@link DeterminationStatus#COMPUTED}; if the verified tool is unavailable (e.g. a currency
 * mismatch or overflow) the value is absent, {@code status} is {@link DeterminationStatus#UNAVAILABLE},
 * and the {@link #reason()} is recorded. The value may be negative (a net cost).
 *
 * <p>Every net-position output carries the {@link AdvisoryLabel#CITED_DRAFT_NOT_ADVICE} label
 * (Req 6.8) and an {@link EvidenceChain} folding together the EC-figure and incentive citations.
 *
 * @param status   the determination status; {@code COMPUTED} or {@code UNAVAILABLE}; never
 *                 {@code null}
 * @param value    the computed net position, present iff {@code status == COMPUTED}
 * @param reason   the recorded reason, present iff {@code status == UNAVAILABLE}
 * @param evidence the combined Evidence_Chain (Req 6.7, 10.1); never {@code null}
 * @param label    the advisory label (Req 6.8); never {@code null}
 *
 * @see EconomicsEngine
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.5, 6.8</a>
 */
public record NetPosition(
        DeterminationStatus status,
        Optional<Money> value,
        Optional<String> reason,
        EvidenceChain evidence,
        AdvisoryLabel label) {

    /**
     * Validates the status/field-presence invariant (Req 6.5).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the fields are inconsistent with {@code status}, or
     *                                  {@code status} is {@link DeterminationStatus#UNDETERMINED}
     */
    public NetPosition {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(label, "label must not be null");
        switch (status) {
            case COMPUTED -> {
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("COMPUTED net position must carry a value");
                }
                if (reason.isPresent()) {
                    throw new IllegalArgumentException("COMPUTED net position must carry no reason");
                }
            }
            case UNAVAILABLE -> {
                if (value.isPresent()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE net position must not carry a value");
                }
                if (reason.isEmpty()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE net position must carry a reason");
                }
            }
            default -> throw new IllegalArgumentException(
                    "net position is COMPUTED or UNAVAILABLE, not " + status);
        }
    }

    /**
     * Returns whether the net position was computed by the verified tool (Req 6.5).
     *
     * @return {@code true} iff {@code status == COMPUTED}
     */
    public boolean isComputed() {
        return status == DeterminationStatus.COMPUTED;
    }
}
