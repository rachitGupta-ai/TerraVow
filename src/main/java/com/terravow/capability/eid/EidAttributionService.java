package com.terravow.capability.eid;

import com.terravow.domain.MachineId;

/**
 * Attributes excess emissions-per-unit-output to a machine's health state (Req 1).
 *
 * <p>The service produces two kinds of determination:
 *
 * <ul>
 *   <li>{@link #intensity(MachineId, TelemetryWindow, ProductionOutput)} computes the emissions
 *       intensity as emissions per unit of output (Req 1.1), reporting it as unavailable with a
 *       recorded reason when a required input is absent or invalid (Req 1.2).</li>
 *   <li>{@link #eid(EidRequest)} computes the EID (Emissions-Intensity Degradation) as the delta
 *       between the current intensity and a baseline intensity (Req 1.3), reporting two additive
 *       attributed quantities that sum to the EID (Req 1.4), falling back to a Fleet_Cohort_Baseline
 *       at strictly lower confidence when the Commissioning_Baseline is absent (Req 1.5), and
 *       reporting the EID as undetermined when both baselines are absent (Req 1.6).</li>
 * </ul>
 *
 * <p>Every result carries a confidence in {@code [0,1]} and an Evidence_Chain (Req 1.7). Follows the
 * TerraVow interface + {@code Default...} implementation convention.
 *
 * @see DefaultEidAttributionService
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.1–1.7</a>
 */
public interface EidAttributionService {

    /**
     * Computes a machine's emissions intensity as emissions per unit of output (Req 1.1).
     *
     * <p>When telemetry emissions and production output are both present and valid, the result is
     * {@link IntensityStatus#AVAILABLE} with {@code intensity = emissions / output}. When a required
     * input is absent, non-finite, negative, or the output is zero (making the ratio undefined), the
     * result is {@link IntensityStatus#UNAVAILABLE} with a recorded reason (Req 1.2). Every result
     * carries a confidence and an Evidence_Chain (Req 1.7).
     *
     * @param machine the machine to compute intensity for; never {@code null}
     * @param window  the telemetry (emissions) input; never {@code null}
     * @param output  the production-output (denominator) input; never {@code null}
     * @return an emissions-intensity result, available or unavailable
     * @throws NullPointerException if any argument is {@code null}
     */
    EmissionsIntensityResult intensity(
            MachineId machine, TelemetryWindow window, ProductionOutput output);

    /**
     * Computes the EID for a machine from its current intensity and available baselines (Req 1.3,
     * 1.4, 1.5, 1.6).
     *
     * <p>Baseline selection: a Commissioning_Baseline is preferred (Req 1.3); if absent, a
     * Fleet_Cohort_Baseline is used at strictly lower confidence (Req 1.5); if both are absent — or
     * the current intensity is itself unavailable — the EID is undetermined with a recorded reason
     * (Req 1.6). A computed result reports two additive attributed quantities (health state and
     * process necessity) that sum to the EID (Req 1.4). Every result carries a confidence and an
     * Evidence_Chain (Req 1.7).
     *
     * @param request the machine, its current intensity, and the optional baselines; never
     *                {@code null}
     * @return an EID result, computed or undetermined
     * @throws NullPointerException if {@code request} is {@code null}
     */
    EidResult eid(EidRequest request);
}
