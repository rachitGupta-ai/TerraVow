package com.terravow.capability.erd;

import com.terravow.capability.eid.EidResult;
import com.terravow.capability.eid.EidStatus;
import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.MachineId;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.tool.ErdSummationRequest;
import com.terravow.reasoning.tool.ErdSummationTool;
import com.terravow.reasoning.tool.ToolResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic implementation of {@link ErdQuantificationService} (Req 2.1–2.6).
 *
 * <p>Determinism: the service holds an injected {@link Clock} and never calls
 * {@link Instant#now()} directly, so the elapsed-operating-hour measurement (Req 2.3) is a pure
 * function of the request and the injected clock — identical inputs yield an identical result.
 *
 * <h2>Accrual (Req 2.3)</h2>
 *
 * <p>Elapsed operating hours are the whole hours between the request's degraded onset and the
 * clock's current instant, floored to an integer and clamped at zero (an onset in the future or
 * exactly now accrues nothing). Each available component rate is multiplied by that elapsed-hour
 * count to produce its accrued amount, so the ERD increases by the per-hour total for each elapsed
 * operating hour the machine stays degraded.
 *
 * <h2>Sum of available components (Req 2.2, 2.6)</h2>
 *
 * <p>The accrued available components are summed by the verified {@link ErdSummationTool} (Req 2.2)
 * rather than free arithmetic: the deterministic tool adds only the available components and
 * verifies they share the declared currency. A component whose rate is unavailable is excluded from
 * the sum and reported {@link ComponentStatus#UNAVAILABLE} with its recorded reason (Req 2.6). If the
 * summation tool itself is unavailable (e.g. a currency mismatch or overflow), every component is
 * marked unavailable and the total is zero in the declared currency.
 *
 * <h2>Single currency (Req 2.1)</h2>
 *
 * <p>The ERD is a single {@link Money} total in the request's declared {@link Currency}; the
 * {@link ErdResult} compact constructor re-checks that the total and every available component share
 * that currency.
 *
 * <h2>Evidence_Chain (Req 2.4)</h2>
 *
 * <p>The chain opens with the machine's {@link SourceType#OPERATING_LOG} provenance spine and adds a
 * link per available component recording the source instrument/computed input the component derives
 * from.
 *
 * <h2>Confidence (Req 2.5)</h2>
 *
 * <p>The ERD confidence is the confidence of the EID result it was computed from. When the EID is not
 * available for the machine (Req 2.1 precondition unmet), no component can be computed: every
 * component is reported unavailable and the total is zero in the declared currency.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.1–2.6</a>
 */
public final class DefaultErdQuantificationService implements ErdQuantificationService {

    /** The injected clock the elapsed-operating-hour measurement is taken against (Req 2.3). */
    private final Clock clock;

    /** The verified tool that sums the available components into the ERD total (Req 2.2). */
    private final ErdSummationTool summationTool;

    /**
     * Creates a service with the given clock and summation tool (constructor injection).
     *
     * @param clock         the clock the accrual elapsed time is measured against; never
     *                      {@code null}
     * @param summationTool the verified ERD summation tool; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultErdQuantificationService(Clock clock, ErdSummationTool summationTool) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.summationTool = Objects.requireNonNull(summationTool, "summationTool must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public ErdResult quantify(ErdRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        MachineId machine = request.machine();
        Currency currency = request.currency();
        EidResult eid = request.eidResult();

        // Req 2.5 — the ERD confidence derives from the EID confidence.
        Confidence confidence = eid.confidence();

        // Req 2.1 — ERD is computed only when an EID result is available for the machine. When the
        // EID is undetermined, no component can be computed: all unavailable, total zero.
        if (eid.status() != EidStatus.COMPUTED) {
            String reason = "EID undetermined: " + eid.reason().orElse("no EID value available");
            return allUnavailable(machine, request.componentRates(), currency, confidence, reason);
        }

        // Req 2.3 — whole elapsed operating hours from the degraded onset, floored and clamped at 0.
        long elapsedHours = elapsedOperatingHours(request.degradedOnset());

        List<ErdComponent> components = new ArrayList<>(request.componentRates().size());
        List<com.terravow.reasoning.tool.ErdComponent> summands =
                new ArrayList<>(request.componentRates().size());
        EvidenceChain evidence = operatingLogEvidence(machine);

        for (ErdComponentRate rate : request.componentRates()) {
            if (!rate.isAvailable()) {
                // Req 2.6 — excluded from the sum, reported unavailable with its reason.
                components.add(
                        ErdComponent.unavailable(rate.type(), rate.unavailableReason().orElseThrow()));
                continue;
            }
            Money perHour = rate.amountPerHour().orElseThrow();
            if (perHour.currency() != currency) {
                // A component in a different currency cannot contribute to the single-currency total.
                components.add(
                        ErdComponent.unavailable(
                                rate.type(),
                                "component currency " + perHour.currency()
                                        + " does not match declared " + currency));
                continue;
            }
            long accrued;
            try {
                accrued = Math.multiplyExact(perHour.minorUnits(), elapsedHours); // Req 2.3
            } catch (ArithmeticException ex) {
                components.add(
                        ErdComponent.unavailable(
                                rate.type(), "accrued amount overflowed: " + ex.getMessage()));
                continue;
            }
            Money amount = new Money(accrued, currency);
            components.add(ErdComponent.available(rate.type(), amount)); // Req 2.2
            summands.add(
                    com.terravow.reasoning.tool.ErdComponent.available(rate.type().name(), amount));
            // Req 2.4 — link the component to its source instrument/computed input.
            evidence = evidence.with(componentSourceLink(rate.source().orElseThrow()));
        }

        // Req 2.2 — sum the available components via the verified tool.
        ToolResult<Money> sum =
                summationTool.execute(new ErdSummationRequest(currency, summands));
        if (!sum.isComputed()) {
            // Verified-tool failure: mark the dependent value unavailable (no unverified total).
            String reason = "ERD summation unavailable: " + sum.reason().orElse("unknown");
            return allUnavailable(machine, request.componentRates(), currency, confidence, reason);
        }

        return new ErdResult(sum.value().orElseThrow(), components, currency, confidence, evidence);
    }

    /**
     * Returns the whole elapsed operating hours between {@code degradedOnset} and the clock's current
     * instant, floored to an integer and clamped at zero (Req 2.3). An onset at or after the current
     * instant accrues nothing.
     */
    private long elapsedOperatingHours(Instant degradedOnset) {
        Instant now = clock.instant();
        Duration elapsed = Duration.between(degradedOnset, now);
        if (elapsed.isNegative() || elapsed.isZero()) {
            return 0L;
        }
        return elapsed.toHours();
    }

    /**
     * Builds a result in which every component is unavailable and the ERD total is zero in the
     * declared currency (Req 2.1, 2.6), preserving the EID-derived confidence (Req 2.5).
     */
    private static ErdResult allUnavailable(
            MachineId machine,
            List<ErdComponentRate> rates,
            Currency currency,
            Confidence confidence,
            String reason) {
        List<ErdComponent> components = new ArrayList<>(rates.size());
        for (ErdComponentRate rate : rates) {
            String componentReason =
                    rate.isAvailable() ? reason : rate.unavailableReason().orElse(reason);
            components.add(ErdComponent.unavailable(rate.type(), componentReason));
        }
        return new ErdResult(
                Money.zero(currency),
                components,
                currency,
                confidence,
                operatingLogEvidence(machine));
    }

    /**
     * Builds the base Evidence_Chain for a machine's ERD determination: a single
     * {@link SourceType#OPERATING_LOG} link to the machine's append-only log, the provenance spine
     * every ERD value traces to (Req 2.4, 14.12).
     */
    private static EvidenceChain operatingLogEvidence(MachineId machine) {
        return new EvidenceChain(
                List.of(
                        new EvidenceLink(
                                SourceType.OPERATING_LOG,
                                new SourceRef("operating-log:" + machine.value()))));
    }

    /**
     * Builds the Evidence_Chain link for an available component's source instrument/computed input
     * (Req 2.4).
     */
    private static EvidenceLink componentSourceLink(String source) {
        return new EvidenceLink(SourceType.OPERATING_LOG, new SourceRef("erd-component:" + source));
    }
}
