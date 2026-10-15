package com.terravow.capability.eid;

import com.terravow.domain.CommissioningBaseline;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineId;

import java.util.Objects;
import java.util.Optional;

/**
 * The inputs required to compute an EID determination for a machine.
 *
 * <p>EID is the delta between a machine's current emissions intensity and a baseline intensity
 * (Req 1.3, 1.5). This request bundles the three inputs the computation reasons over: the
 * already-computed current {@link EmissionsIntensityResult}, and the two optional baselines. The
 * service selects the Commissioning_Baseline when present (Req 1.3), falls back to the
 * Fleet_Cohort_Baseline at strictly lower confidence when it is absent (Req 1.5), and reports the
 * EID as undetermined when both are absent (Req 1.6). Passing the inputs explicitly keeps the
 * service deterministic and free of hidden store dependencies.
 *
 * @param machine               the machine the EID is for; never {@code null}
 * @param currentIntensity      the machine's current emissions-intensity result; never
 *                              {@code null} (Req 1.3)
 * @param commissioningBaseline the Commissioning_Baseline if one exists (Req 1.3); never
 *                              {@code null}, may be empty
 * @param fleetCohortBaseline   the Fleet_Cohort_Baseline fallback if available (Req 1.5); never
 *                              {@code null}, may be empty
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.3, 1.5, 1.6</a>
 */
public record EidRequest(
        MachineId machine,
        EmissionsIntensityResult currentIntensity,
        Optional<CommissioningBaseline> commissioningBaseline,
        Optional<FleetCohortBaseline> fleetCohortBaseline) {

    /**
     * Validates that all references are present (Req 1.3). Absence of a baseline is modelled by an
     * empty {@link Optional}, not {@code null}.
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public EidRequest {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(currentIntensity, "currentIntensity must not be null");
        Objects.requireNonNull(commissioningBaseline, "commissioningBaseline must not be null");
        Objects.requireNonNull(fleetCohortBaseline, "fleetCohortBaseline must not be null");
    }
}
