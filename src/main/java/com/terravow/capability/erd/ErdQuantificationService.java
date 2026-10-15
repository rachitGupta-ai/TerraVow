package com.terravow.capability.erd;

/**
 * Quantifies the Environmental Regulatory Debt (ERD) of operating a degraded machine (Req 2).
 *
 * <p>ERD is the compounding financial liability of running degraded or legacy equipment, expressed
 * as a single accruing monetary amount so deferred maintenance surfaces on the balance sheet. The
 * service:
 *
 * <ul>
 *   <li>produces a single monetary amount in one declared currency when an EID result is available
 *       for the machine (Req 2.1);</li>
 *   <li>sets the ERD equal to the sum of its <em>available</em> components (Req 2.2), excluding and
 *       reporting any component that cannot be computed with a recorded reason (Req 2.6);</li>
 *   <li>accrues the component amounts per elapsed operating hour measured from the start of the
 *       current degraded-health-state period (Req 2.3);</li>
 *   <li>attaches an Evidence_Chain linking each component to its source instrument or computed input
 *       (Req 2.4);</li>
 *   <li>derives the ERD confidence from the confidence of the EID result it was computed from
 *       (Req 2.5).</li>
 * </ul>
 *
 * <p>Follows the TerraVow interface + {@code Default...} implementation convention. The elapsed-time
 * measurement is deterministic against an injected {@link java.time.Clock}.
 *
 * @see DefaultErdQuantificationService
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.1–2.6</a>
 */
public interface ErdQuantificationService {

    /**
     * Computes the ERD for a machine from its EID result and per-operating-hour component rates
     * (Req 2.1–2.6).
     *
     * <p>Each available component rate is multiplied by the elapsed operating hours from the request's
     * degraded onset to the current clock instant to produce its accrued amount (Req 2.3); the
     * available components are summed into a single total in the declared currency (Req 2.1, 2.2);
     * each component is linked to its source in the Evidence_Chain (Req 2.4); unavailable components
     * are excluded from the sum and reported with a reason (Req 2.6); and the result confidence is
     * derived from the EID confidence (Req 2.5). When the EID result is not available for the machine
     * no component can be computed, so every component is reported unavailable and the ERD total is
     * zero in the declared currency (Req 2.1, 2.6).
     *
     * @param request the machine, its EID result, the declared currency, the degraded onset, and the
     *                per-component rates; never {@code null}
     * @return the ERD result: a single monetary total, its components, the declared currency, the
     *         derived confidence, and the Evidence_Chain
     * @throws NullPointerException if {@code request} is {@code null}
     */
    ErdResult quantify(ErdRequest request);
}
