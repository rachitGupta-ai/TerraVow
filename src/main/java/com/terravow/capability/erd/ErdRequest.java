package com.terravow.capability.erd;

import com.terravow.capability.eid.EidResult;
import com.terravow.domain.Currency;
import com.terravow.domain.MachineId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The inputs required to compute an ERD determination for a machine (Req 2.1–2.6).
 *
 * <p>An ERD is computed only when an EID result is available for the machine (Req 2.1); the
 * {@link #eidResult()} supplies both that precondition and the confidence the ERD confidence is
 * derived from (Req 2.5). The ERD is reported in a single declared {@link #currency()} (Req 2.1).
 * The {@link #componentRates()} give each component's per-operating-hour accrual rate (or the reason
 * it is unavailable); the service multiplies each available rate by the elapsed operating hours from
 * the {@link #degradedOnset()} to produce the accrued component amounts (Req 2.3) and links each to
 * its source (Req 2.4).
 *
 * <p>The {@code degradedOnset} is the start of the current degraded-health-state period — the
 * Deferral_Period start the domain core anchors ERD accrual to (Req 2.3, 14.6). Passing the inputs
 * explicitly keeps the service deterministic and free of hidden store dependencies; the elapsed time
 * is measured against an injected {@link java.time.Clock}, never the wall clock.
 *
 * @param machine        the machine the ERD is for; never {@code null}
 * @param eidResult      the EID result the ERD precondition and confidence derive from (Req 2.1,
 *                       2.5); never {@code null}
 * @param currency       the single declared result currency (Req 2.1); never {@code null}
 * @param degradedOnset  the start of the current degraded-health-state period accrual is measured
 *                       from (Req 2.3); never {@code null}
 * @param componentRates the per-operating-hour accrual rate (or unavailable reason) for each
 *                       component (Req 2.2, 2.3, 2.6); never {@code null}, defensively copied
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.1–2.6, 14.6</a>
 */
public record ErdRequest(
        MachineId machine,
        EidResult eidResult,
        Currency currency,
        Instant degradedOnset,
        List<ErdComponentRate> componentRates) {

    /**
     * Validates that all references are present and defensively copies the component rates.
     *
     * @throws NullPointerException if any argument is {@code null} or a component rate is
     *                              {@code null}
     */
    public ErdRequest {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(eidResult, "eidResult must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(degradedOnset, "degradedOnset must not be null");
        Objects.requireNonNull(componentRates, "componentRates must not be null");
        componentRates = List.copyOf(componentRates); // rejects null elements, immutable
    }
}
