package com.terravow.capability.passport;

import com.terravow.domain.BatchId;

import java.util.List;

/**
 * Carries a machine-health-adjusted emissions footprint per batch and propagates it across
 * supply-chain tiers (Req 7).
 *
 * <p>The service produces five kinds of determination:
 *
 * <ul>
 *   <li>{@link #create(BatchId, MachineStateReadings)} builds a per-batch Embedded Pollution
 *       Passport from machine-state-conditioned actuals, carrying the footprint, a confidence, and an
 *       Evidence_Chain to the contributing readings (Req 7.1); the footprint uses actuals rather than
 *       industry-average factors (Req 7.3); a missing actual falls back to a Fleet_Cohort_Baseline at
 *       reduced confidence and records the substitution (Req 7.4).</li>
 *   <li>{@link #propagate(BatchId, List)} computes a downstream batch's Passport as the sum of each
 *       consumed input's embedded footprint in proportion to the quantity consumed (Req 7.2).</li>
 *   <li>{@link #airshedCoSchedule(List)} evaluates mass-preserving co-scheduling across facilities
 *       that share an airshed (Req 7.5).</li>
 *   <li>{@link #propagateCascade(SupplyGraph, DegradedCondition)} propagates cascade-risk from a
 *       degraded upstream tier to every dependent downstream tier (Req 7.6).</li>
 *   <li>{@link #supplyChainRipple(SupplyGraph, TierId, Substitution)} computes the delivery-requirement
 *       ripple of a tier substitution on every connected tier (Req 7.7).</li>
 * </ul>
 *
 * <p>All operations are deterministic pure functions of their inputs. Follows the TerraVow interface
 * + {@code Default...} implementation convention.
 *
 * @see DefaultPassportService
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.1–7.7</a>
 */
public interface PassportService {

    /**
     * Creates an Embedded Pollution Passport for a produced batch from machine-state-conditioned
     * actuals (Req 7.1, 7.3, 7.4).
     *
     * <p>When the readings carry a machine-state-conditioned actual footprint, the Passport uses it
     * directly at full-evidence confidence (Req 7.1, 7.3). When the actual is absent, the service
     * falls back to the readings' Fleet_Cohort_Baseline portion at a strictly lower confidence and
     * records the substitution as a {@code FALLBACK_PATH} link in the Evidence_Chain (Req 7.4). The
     * Evidence_Chain always links the footprint to its contributing machine-state readings (Req 7.1).
     *
     * @param batch    the produced batch; never {@code null}
     * @param readings the machine-state-conditioned inputs; never {@code null}
     * @return the batch's Embedded Pollution Passport
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if neither an actual footprint nor a Fleet_Cohort_Baseline is
     *                                  available (nothing to ground the footprint on)
     */
    Passport create(BatchId batch, MachineStateReadings readings);

    /**
     * Propagates embedded footprints downstream: the resulting batch's footprint is the sum over the
     * consumed inputs of each input's embedded footprint weighted by the proportion of that input's
     * quantity consumed (Req 7.2).
     *
     * @param downstream the downstream batch being produced; never {@code null}
     * @param inputs     the consumed input batches with quantities and Passports; never {@code null},
     *                   never empty
     * @return the downstream batch's Embedded Pollution Passport
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code inputs} is empty
     */
    Passport propagate(BatchId downstream, List<ConsumedInput> inputs);

    /**
     * Evaluates mass-preserving airshed co-scheduling across facilities that share an airshed
     * (Req 7.5). The total emission mass of the affected facilities is preserved by construction.
     *
     * @param sharedAirshed the facilities sharing an airshed; never {@code null}, at least two
     * @return a mass-preserving co-scheduling recommendation
     * @throws NullPointerException     if {@code sharedAirshed} is {@code null}
     * @throws IllegalArgumentException if fewer than two facilities are supplied (Req 7.5 applies when
     *                                  two or more facilities share an airshed)
     */
    CoScheduleResult airshedCoSchedule(List<AirshedFacility> sharedAirshed);

    /**
     * Propagates cascade-risk from a degraded upstream tier to every dependent downstream tier,
     * reporting each dependent tier and the cascade-risk attributed to it (Req 7.6).
     *
     * @param graph     the supply-chain dependency graph; never {@code null}
     * @param condition the degraded condition at the upstream tier; never {@code null}
     * @return the cascade-risk report
     * @throws NullPointerException if any argument is {@code null}
     */
    CascadeRisk propagateCascade(SupplyGraph graph, DegradedCondition condition);

    /**
     * Computes the supply-chain ripple of a substitution applied at a tier, reporting the
     * delivery-requirement change for every connected tier (Req 7.7).
     *
     * @param graph        the supply-chain dependency graph; never {@code null}
     * @param appliedTier  the tier the substitution is applied at; never {@code null}
     * @param substitution the applied substitution; never {@code null}
     * @return the ripple report
     * @throws NullPointerException if any argument is {@code null}
     */
    RippleResult supplyChainRipple(SupplyGraph graph, TierId appliedTier, Substitution substitution);
}
