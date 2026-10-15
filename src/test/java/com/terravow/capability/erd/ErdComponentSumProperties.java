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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the available-component sum of an ERD determination produced by
 * {@link DefaultErdQuantificationService#quantify}.
 *
 * <p>Validates Correctness Property 6: the ERD total equals the sum of only its
 * {@link ComponentStatus#AVAILABLE} accrued components, and every {@link ComponentStatus#UNAVAILABLE}
 * component is excluded from the total yet still reported with a recorded reason (Req 2.2, 2.6). The
 * generator produces an arbitrary set of distinct component rates mixing available and unavailable
 * states, so the property exercises every combination of included and excluded components.
 *
 * <p>The expected total is re-derived independently of the service — elapsed whole operating hours
 * times the sum of the available per-hour rates — so the assertion checks the service's arithmetic
 * rather than restating it.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 2.6</a>
 */
class ErdComponentSumProperties {

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

    // Feature: terravow, Property 6: ERD equals the sum of its available components
    @Property(tries = 100)
    void erdEqualsSumOfAvailableAccruedComponentsOnly(
            @ForAll("elapsedHours") int elapsedHours,
            @ForAll("componentRates") List<ErdComponentRate> rates) {
        Instant now = onset.plus(Duration.ofHours(elapsedHours));
        ErdResult result = serviceAt(now).quantify(request(rates));

        // Independently re-derive the expected total from the AVAILABLE rates only (Req 2.2):
        // elapsed whole operating hours times the sum of the available per-hour amounts.
        long expectedTotal = 0L;
        for (ErdComponentRate rate : rates) {
            if (rate.isAvailable()) {
                expectedTotal += rate.amountPerHour().orElseThrow().minorUnits() * (long) elapsedHours;
            }
        }
        assertThat(result.erd().minorUnits()).isEqualTo(expectedTotal);

        // Req 2.2 — the ERD total equals the sum of the available RESULT components (what the
        // service emitted as available), independent of the elapsed-hours re-derivation above.
        long sumOfAvailableComponents =
                result.components().stream()
                        .filter(ErdComponent::isAvailable)
                        .mapToLong(c -> c.amount().orElseThrow().minorUnits())
                        .sum();
        assertThat(result.erd().minorUnits()).isEqualTo(sumOfAvailableComponents);

        // Req 2.6 — every unavailable input is excluded from the sum yet still reported, carrying a
        // recorded reason and contributing nothing to the total.
        for (ErdComponentRate rate : rates) {
            if (rate.isAvailable()) {
                continue;
            }
            ErdComponent reported =
                    result.components().stream()
                            .filter(c -> c.type() == rate.type())
                            .findFirst()
                            .orElseThrow();
            assertThat(reported.isAvailable()).isFalse();
            assertThat(reported.status()).isEqualTo(ComponentStatus.UNAVAILABLE);
            assertThat(reported.amount()).isEmpty();
            assertThat(reported.reason()).isPresent();
        }

        // Every input rate is reported exactly once (nothing silently dropped).
        assertThat(result.components()).hasSameSizeAs(rates);
    }

    /** Non-negative elapsed whole operating hours, bounded to keep the accrual arithmetic in range. */
    @Provide
    Arbitrary<Integer> elapsedHours() {
        return Arbitraries.integers().between(0, 10_000);
    }

    /**
     * An arbitrary set of distinct component rates mixing available and unavailable states. Each of
     * the five {@link ErdComponentType}s appears at most once; a per-type choice decides whether it
     * is absent, an available INR per-hour rate, or an unavailable rate with a reason. The resulting
     * list may be empty.
     */
    @Provide
    Arbitrary<List<ErdComponentRate>> componentRates() {
        List<Arbitrary<Optional<ErdComponentRate>>> perType = new ArrayList<>();
        for (ErdComponentType type : ErdComponentType.values()) {
            perType.add(maybeRate(type));
        }
        return Combinators.combine(perType)
                .as(optionals -> optionals.stream().flatMap(Optional::stream).toList());
    }

    /**
     * For one component type, produces either {@code empty} (the type is absent from the set), an
     * available INR per-hour rate, or an unavailable rate carrying a reason.
     */
    private Arbitrary<Optional<ErdComponentRate>> maybeRate(ErdComponentType type) {
        Arbitrary<ErdComponentRate> available =
                Arbitraries.longs()
                        .between(0L, 1_000_000L)
                        .map(
                                units ->
                                        ErdComponentRate.available(
                                                type, new Money(units, INR), type.name() + "-source"));
        Arbitrary<ErdComponentRate> unavailable =
                Arbitraries.just(ErdComponentRate.unavailable(type, type.name() + " input missing"));
        Arbitrary<ErdComponentRate> present = Arbitraries.oneOf(List.of(available, unavailable));
        return Arbitraries.oneOf(
                List.of(Arbitraries.just(Optional.empty()), present.map(Optional::of)));
    }
}
