package com.terravow.capability.lifecycle;

import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.MachineId;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;
import java.util.Optional;

/**
 * The forecast pollution cost of continued deferral: the forecast EID trajectory priced through the
 * ERD components over the deferral horizon (Req 15.2), or an undetermined result when the data to
 * forecast the trajectory is insufficient (Req 15.10).
 *
 * <p>When the trajectory is available, the status is {@link DeterminationStatus#COMPUTED} and the
 * result carries a single {@link Money} {@link #cost()} in one declared {@link #currency()} — the
 * ERD-component pricing of the trajectory summed over the horizon (Req 15.2) — with a confidence and
 * an Evidence_Chain. When the EID trajectory could not be forecast, the status is
 * {@link DeterminationStatus#UNDETERMINED}, the cost is absent, and a reason is recorded (Req 15.10).
 *
 * <p>The compact constructor makes an inconsistent result unrepresentable: a COMPUTED result carries
 * a cost in the declared currency and no reason; an UNDETERMINED result carries no cost and a
 * non-blank reason.
 *
 * @param machine    the asset the deferral cost is for; never {@code null}
 * @param status     whether the cost was computed or is undetermined; never {@code null}
 * @param cost       the single monetary forecast pollution cost; present iff
 *                   {@code status == COMPUTED} (Req 15.2)
 * @param currency   the single declared currency the cost is reported in; never {@code null}
 * @param confidence the confidence attached to the result; never {@code null}
 * @param evidence   the Evidence_Chain for the result; never {@code null}
 * @param reason     the reason the cost is undetermined; present iff
 *                   {@code status == UNDETERMINED} (Req 15.10)
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.2, 15.10</a>
 */
public record DeferralCostResult(
        MachineId machine,
        DeterminationStatus status,
        Optional<Money> cost,
        Currency currency,
        Confidence confidence,
        EvidenceChain evidence,
        Optional<String> reason) {

    /**
     * Validates presence of all references and the status-dependent invariants (Req 15.2, 15.10).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the cost or reason is inconsistent with the status, or a
     *                                  present cost is in a different currency than declared
     */
    public DeferralCostResult {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(cost, "cost must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        if (status == DeterminationStatus.COMPUTED) {
            if (cost.isEmpty()) {
                throw new IllegalArgumentException(
                        "a COMPUTED deferral cost must carry a cost (Req 15.2)");
            }
            if (cost.get().currency() != currency) {
                throw new IllegalArgumentException(
                        "deferral cost currency " + cost.get().currency()
                                + " must match declared currency " + currency);
            }
            if (reason.isPresent()) {
                throw new IllegalArgumentException(
                        "a COMPUTED deferral cost must not carry an undetermined reason");
            }
        } else { // UNDETERMINED / UNAVAILABLE
            if (cost.isPresent()) {
                throw new IllegalArgumentException(
                        "a non-computed deferral cost must not carry a cost");
            }
            if (reason.isEmpty() || reason.get().isBlank()) {
                throw new IllegalArgumentException(
                        "a non-computed deferral cost must carry a non-blank reason (Req 15.10)");
            }
        }
    }

    /** Whether the forecast pollution cost of deferral was computed. */
    public boolean isComputed() {
        return status == DeterminationStatus.COMPUTED;
    }

    /**
     * The computed cost in minor units, or {@code 0} when the cost is undetermined.
     *
     * @return the cost's minor units, or {@code 0} when undetermined
     */
    public long costMinorUnitsOrZero() {
        return cost.map(Money::minorUnits).orElse(0L);
    }

    /**
     * Builds a {@link DeterminationStatus#COMPUTED} deferral cost (Req 15.2).
     *
     * @param machine    the asset; never {@code null}
     * @param cost       the single monetary cost; never {@code null}, in {@code currency}
     * @param currency   the declared currency; never {@code null}
     * @param confidence the confidence; never {@code null}
     * @param evidence   the Evidence_Chain; never {@code null}
     * @return a computed deferral cost result
     */
    public static DeferralCostResult computed(
            MachineId machine,
            Money cost,
            Currency currency,
            Confidence confidence,
            EvidenceChain evidence) {
        return new DeferralCostResult(
                machine,
                DeterminationStatus.COMPUTED,
                Optional.of(cost),
                currency,
                confidence,
                evidence,
                Optional.empty());
    }

    /**
     * Builds an {@link DeterminationStatus#UNDETERMINED} deferral cost with a recorded reason
     * (Req 15.10).
     *
     * @param machine    the asset; never {@code null}
     * @param currency   the declared currency; never {@code null}
     * @param reason     the non-blank reason the cost could not be determined
     * @param confidence the confidence; never {@code null}
     * @param evidence   the Evidence_Chain; never {@code null}
     * @return an undetermined deferral cost result
     */
    public static DeferralCostResult undetermined(
            MachineId machine,
            Currency currency,
            String reason,
            Confidence confidence,
            EvidenceChain evidence) {
        return new DeferralCostResult(
                machine,
                DeterminationStatus.UNDETERMINED,
                Optional.empty(),
                currency,
                confidence,
                evidence,
                Optional.of(reason));
    }
}
