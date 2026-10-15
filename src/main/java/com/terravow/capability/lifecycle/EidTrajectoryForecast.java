package com.terravow.capability.lifecycle;

import com.terravow.domain.Confidence;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of forecasting the EID trajectory of a degraded Machine_Asset over a configured
 * deferral horizon through a Reasoning_Agent (Req 15.1, 15.10).
 *
 * <p>When the agent produces a trajectory, the status is {@link DeterminationStatus#COMPUTED} and
 * the forecast carries one {@link EidTrajectoryPoint} per step of the {@link Horizon}, a confidence,
 * and an Evidence_Chain (Req 15.1). When the data required to forecast the trajectory is
 * insufficient — the agent cannot produce a schema-valid trajectory, or the asset lacks the inputs a
 * forecast needs — the status is {@link DeterminationStatus#UNDETERMINED} with a recorded reason and
 * no points; the lifecycle then reports the forecast pollution cost of deferral as undetermined
 * (Req 15.10).
 *
 * <p>The compact constructor makes an inconsistent forecast unrepresentable: a COMPUTED forecast
 * carries a non-empty point list and no reason; an UNDETERMINED forecast carries no points and a
 * non-blank reason.
 *
 * @param machine    the degraded asset the trajectory is for; never {@code null}
 * @param status     whether the trajectory was computed or is undetermined; never {@code null}
 * @param horizon    the deferral horizon the trajectory spans; never {@code null}
 * @param points     the per-step forecast points; non-empty iff {@code status == COMPUTED}
 * @param confidence the confidence attached to the forecast; never {@code null}
 * @param evidence   the Evidence_Chain for the forecast; never {@code null}
 * @param reason     the reason the trajectory is undetermined; present iff
 *                   {@code status == UNDETERMINED} (Req 15.10)
 *
 * @see EidTrajectoryForecaster
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.1, 15.10</a>
 */
public record EidTrajectoryForecast(
        MachineId machine,
        DeterminationStatus status,
        Horizon horizon,
        List<EidTrajectoryPoint> points,
        Confidence confidence,
        EvidenceChain evidence,
        Optional<String> reason) {

    /**
     * Validates presence of all references and the status-dependent invariants (Req 15.1, 15.10).
     *
     * @throws NullPointerException     if any argument is {@code null} or a point is {@code null}
     * @throws IllegalArgumentException if the point list or reason is inconsistent with the status
     */
    public EidTrajectoryForecast {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(horizon, "horizon must not be null");
        Objects.requireNonNull(points, "points must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        points = List.copyOf(points); // rejects null elements, immutable

        if (status == DeterminationStatus.COMPUTED) {
            if (points.isEmpty()) {
                throw new IllegalArgumentException(
                        "a COMPUTED trajectory must carry at least one point (Req 15.1)");
            }
            if (reason.isPresent()) {
                throw new IllegalArgumentException(
                        "a COMPUTED trajectory must not carry an undetermined reason");
            }
        } else { // UNDETERMINED or UNAVAILABLE both treated as no-trajectory
            if (!points.isEmpty()) {
                throw new IllegalArgumentException(
                        "a non-computed trajectory must not carry points");
            }
            if (reason.isEmpty() || reason.get().isBlank()) {
                throw new IllegalArgumentException(
                        "a non-computed trajectory must carry a non-blank reason (Req 15.10)");
            }
        }
    }

    /** Whether a trajectory was produced over the horizon. */
    public boolean isComputed() {
        return status == DeterminationStatus.COMPUTED;
    }

    /**
     * Builds a {@link DeterminationStatus#COMPUTED} forecast over the horizon (Req 15.1).
     *
     * @param machine    the asset; never {@code null}
     * @param horizon    the deferral horizon; never {@code null}
     * @param points     the per-step forecast points; must be non-empty
     * @param confidence the confidence; never {@code null}
     * @param evidence   the Evidence_Chain; never {@code null}
     * @return a computed trajectory forecast
     */
    public static EidTrajectoryForecast computed(
            MachineId machine,
            Horizon horizon,
            List<EidTrajectoryPoint> points,
            Confidence confidence,
            EvidenceChain evidence) {
        return new EidTrajectoryForecast(
                machine,
                DeterminationStatus.COMPUTED,
                horizon,
                points,
                confidence,
                evidence,
                Optional.empty());
    }

    /**
     * Builds an {@link DeterminationStatus#UNDETERMINED} forecast with a recorded reason (Req 15.10).
     *
     * @param machine    the asset; never {@code null}
     * @param horizon    the deferral horizon; never {@code null}
     * @param reason     the non-blank reason the trajectory could not be forecast
     * @param confidence the confidence; never {@code null}
     * @param evidence   the Evidence_Chain; never {@code null}
     * @return an undetermined trajectory forecast
     */
    public static EidTrajectoryForecast undetermined(
            MachineId machine,
            Horizon horizon,
            String reason,
            Confidence confidence,
            EvidenceChain evidence) {
        return new EidTrajectoryForecast(
                machine,
                DeterminationStatus.UNDETERMINED,
                horizon,
                List.of(),
                confidence,
                evidence,
                Optional.of(reason));
    }
}
