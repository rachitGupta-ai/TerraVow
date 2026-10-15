package com.terravow.capability.erd;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.eid.EidResult;
import com.terravow.domain.BaselineKind;
import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.MachineId;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;
import com.terravow.reasoning.tool.ErdSummationTool;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the monotonic accrual of an ERD determination produced by
 * {@link DefaultErdQuantificationService#quantify} as the clock advances.
 *
 * <p>Validates Correctness Property 7: for a fixed component rate set and a fixed degraded onset,
 * ERD accrues per elapsed operating hour and is therefore non-decreasing in time — the ERD measured
 * at a later clock instant is greater than or equal to the ERD measured at an earlier instant
 * (Req 2.3). The two determinations are taken with {@link Clock#fixed} at two instants that differ
 * only in how much time has elapsed since the same onset; everything else (machine, EID, currency,
 * rates) is held constant.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 2.3</a>
 */
class MonotonicErdAccrualProperties {

    private static final Currency INR = Currency.INR;
    private final MachineId machine = new MachineId("machine-1");
    private final Instant onset = Instant.parse("2026-01-01T00:00:00Z");

    private DefaultErdQuantificationService serviceAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new DefaultErdQuantificationService(clock, new ErdSummationTool());
    }

    private ErdRequest request(List<ErdComponentRate> rates) {
        return new ErdRequest(
                machine,
                EidResult.computed(
                        machine, 0.5, 0.5, 0.0, BaselineKind.COMMISSIONING,
                        new Confidence(0.9), EvidenceChain.EMPTY),
                INR,
                onset,
                rates);
    }

    // Feature: terravow, Property 7: ERD accrues monotonically while degraded
    @Property(tries = 100)
    void erdIsNonDecreasingAsTheClockAdvances(
            @ForAll("componentRates") List<ErdComponentRate> rates,
            @ForAll("elapsedHours") int earlierHours,
            @ForAll("hourGap") int gap) {
        int laterHours = earlierHours + gap; // laterHours >= earlierHours

        Instant earlierNow = onset.plus(Duration.ofHours(earlierHours));
        Instant laterNow = onset.plus(Duration.ofHours(laterHours));

        ErdResult earlier = serviceAt(earlierNow).quantify(request(rates));
        ErdResult later = serviceAt(laterNow).quantify(request(rates));

        // Req 2.3 — accrual is per elapsed operating hour from the (fixed) degraded onset, so a
        // later observation can only be >= an earlier one for the same inputs.
        assertThat(later.erd().minorUnits())
                .isGreaterThanOrEqualTo(earlier.erd().minorUnits());

        // Both determinations share the single declared currency (Req 2.1) and the EID-derived
        // confidence (Req 2.5) — the only thing that changed is elapsed time.
        assertThat(later.currency()).isEqualTo(earlier.currency());
        assertThat(later.confidence()).isEqualTo(earlier.confidence());
    }

    /**
     * A fixed, non-empty set of available INR per-hour rates. Monotonicity must hold for any such
     * set; a few generated rates across distinct component types suffice to exercise the sum.
     */
    @Provide
    Arbitrary<List<ErdComponentRate>> componentRates() {
        Arbitrary<Long> units = Arbitraries.longs().between(0L, 1_000_000L);
        return units.list()
                .ofMinSize(1)
                .ofMaxSize(ErdComponentType.values().length)
                .map(
                        amounts -> {
                            ErdComponentType[] types = ErdComponentType.values();
                            java.util.List<ErdComponentRate> rates =
                                    new java.util.ArrayList<>(amounts.size());
                            for (int i = 0; i < amounts.size(); i++) {
                                ErdComponentType type = types[i];
                                rates.add(
                                        ErdComponentRate.available(
                                                type, new Money(amounts.get(i), INR),
                                                type.name() + "-source"));
                            }
                            return List.copyOf(rates);
                        });
    }

    /** Non-negative elapsed whole operating hours for the earlier observation. */
    @Provide
    Arbitrary<Integer> elapsedHours() {
        return Arbitraries.integers().between(0, 10_000);
    }

    /** Non-negative additional whole hours between the earlier and the later observation. */
    @Provide
    Arbitrary<Integer> hourGap() {
        return Arbitraries.integers().between(0, 10_000);
    }
}
