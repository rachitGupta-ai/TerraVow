package com.terravow.capability.aei;

import java.util.Optional;

/**
 * The AEI (Avoidable Exposure Index) capability and harm-aware rescheduler (Req 4).
 *
 * <p>AEI measures the population-exposure harm attributable to the <em>timing</em> of an emission
 * rather than its mass. The service:
 * <ul>
 *   <li>computes an airshed assimilative-capacity forecast over a horizon of at least 24 hours in
 *       steps no coarser than 1 hour when a meteorological forecast and a Receptor_Map are both
 *       present, else reports the AEI undetermined with a reason (Req 4.1, 4.2);</li>
 *   <li>computes the AEI as the harm of the actual schedule minus the harm of the harm-minimising
 *       schedule of the same total mass over the horizon under the production constraints
 *       (Req 4.3);</li>
 *   <li>recommends a revised schedule whose AEI is no greater than the actual, preserving total
 *       emission mass within 0.1 percent and rejecting any mass- or cross-media-increasing
 *       candidate in favour of retaining the actual schedule (Req 4.4, 4.5, 4.6);</li>
 *   <li>reports the affected receptors and an Evidence_Chain on every result (Req 4.7).</li>
 * </ul>
 *
 * <p>Implementations are deterministic: the same inputs always produce the same forecast, AEI, and
 * recommendation.
 *
 * @see DefaultAeiService
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.1–4.7</a>
 */
public interface AeiService {

    /**
     * Computes the airshed assimilative-capacity forecast for a facility (Req 4.1, 4.2).
     *
     * <p>Returns a present {@link AssimilativeForecast} over the configured horizon (≥ 24h) at the
     * configured step (≤ 1h) when both a meteorological forecast and a Receptor_Map are available
     * for the facility; returns {@link Optional#empty()} when either input is absent (the caller
     * then reports the AEI undetermined with a reason per Req 4.2).
     *
     * @param meteorologicalForecast the facility's meteorological forecast, or {@code null} if
     *                               unavailable
     * @param receptorMap            the facility's Receptor_Map, or {@code null} if unavailable
     * @return the assimilative-capacity forecast, or empty when an input is unavailable
     */
    Optional<AssimilativeForecast> forecast(
            MeteorologicalForecast meteorologicalForecast, ReceptorMap receptorMap);

    /**
     * Computes the AEI of an actual emission schedule (Req 4.2, 4.3, 4.7).
     *
     * <p>When the forecast inputs are available, returns a {@link AeiStatus#COMPUTED} result whose
     * value is {@code harm(actual) − harm(harm-minimising same-mass schedule)} in
     * person-µg/m³-hours, reporting the affected receptors and an Evidence_Chain. When a required
     * input is absent, returns an {@link AeiStatus#UNDETERMINED} result with a recorded reason.
     *
     * @param actual                 the actual emission schedule; never {@code null}
     * @param constraints            the facility production constraints; never {@code null}
     * @param meteorologicalForecast the facility's meteorological forecast, or {@code null}
     * @param receptorMap            the facility's Receptor_Map, or {@code null}
     * @return the AEI result
     * @throws NullPointerException if {@code actual} or {@code constraints} is {@code null}
     */
    AeiResult aei(
            EmissionSchedule actual,
            FacilityConstraints constraints,
            MeteorologicalForecast meteorologicalForecast,
            ReceptorMap receptorMap);

    /**
     * Recommends a harm-aware revised schedule or retains the actual (Req 4.4, 4.5, 4.6, 4.7).
     *
     * <p>Searches for a revised schedule of the same total mass, feasible under the production
     * constraints, whose AEI is no greater than the actual (Req 4.4). The recommendation preserves
     * total emission mass within the configured tolerance (Req 4.5); a candidate that would exceed
     * that tolerance or increase cross-media load is rejected and the actual schedule retained
     * (Req 4.6). When the forecast inputs are absent, returns an undetermined result retaining the
     * actual schedule.
     *
     * @param actual                 the actual emission schedule; never {@code null}
     * @param constraints            the facility production constraints; never {@code null}
     * @param meteorologicalForecast the facility's meteorological forecast, or {@code null}
     * @param receptorMap            the facility's Receptor_Map, or {@code null}
     * @return the rescheduling result
     * @throws NullPointerException if {@code actual} or {@code constraints} is {@code null}
     */
    RescheduleResult reschedule(
            EmissionSchedule actual,
            FacilityConstraints constraints,
            MeteorologicalForecast meteorologicalForecast,
            ReceptorMap receptorMap);
}
