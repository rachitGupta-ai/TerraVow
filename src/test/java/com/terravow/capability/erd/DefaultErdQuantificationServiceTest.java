package com.terravow.capability.erd;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.eid.EidResult;
import com.terravow.domain.BaselineKind;
import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.MachineId;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.SourceType;
import com.terravow.reasoning.tool.ErdSummationTool;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultErdQuantificationService}, exercising the single-currency ERD total
 * (Req 2.1), the available-component sum (Req 2.2), per-operating-hour accrual from the degraded
 * onset (Req 2.3), the per-component Evidence_Chain source links (Req 2.4), the EID-derived
 * confidence (Req 2.5), and the exclude-and-report rule for uncomputable components (Req 2.6).
 */
class DefaultErdQuantificationServiceTest {

    private static final Currency INR = Currency.INR;
    private final MachineId machine = new MachineId("machine-1");
    private final Instant onset = Instant.parse("2026-01-01T00:00:00Z");

    private DefaultErdQuantificationService serviceAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new DefaultErdQuantificationService(clock, new ErdSummationTool());
    }

    private ErdRequest request(Instant now, Confidence eidConfidence, List<ErdComponentRate> rates) {
        return new ErdRequest(
                machine,
                EidResult.computed(
                        machine, 0.5, 0.5, 0.0, BaselineKind.COMMISSIONING,
                        eidConfidence, EvidenceChain.EMPTY),
                INR,
                onset,
                rates);
    }

    private ErdComponentRate ratePerHour(ErdComponentType type, long minorUnits) {
        return ErdComponentRate.available(type, new Money(minorUnits, INR), type.name() + "-source");
    }

    // ----- Req 2.1: single monetary amount in one declared currency -----

    @Test
    void erdIsASingleMoneyInTheDeclaredCurrency() {
        Instant now = onset.plus(Duration.ofHours(10));
        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        now,
                                        new Confidence(0.9),
                                        List.of(ratePerHour(ErdComponentType.EXCESS_ENERGY, 100))));

        assertThat(result.currency()).isEqualTo(INR);
        assertThat(result.erd().currency()).isEqualTo(INR);
    }

    // ----- Req 2.2: ERD equals the sum of available components -----

    @Test
    void erdEqualsSumOfAvailableComponents() {
        Instant now = onset.plus(Duration.ofHours(2));
        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        now,
                                        new Confidence(0.9),
                                        List.of(
                                                ratePerHour(ErdComponentType.EC_EXPOSURE, 100),
                                                ratePerHour(ErdComponentType.EXCESS_ENERGY, 50))));

        // 2 hours * (100 + 50) per hour = 300.
        assertThat(result.erd().minorUnits()).isEqualTo(300L);
    }

    // ----- Req 2.3: accrue per elapsed operating hour from degraded onset -----

    @Test
    void accruesPerElapsedOperatingHour() {
        List<ErdComponentRate> rates = List.of(ratePerHour(ErdComponentType.EXCESS_ENERGY, 100));

        ErdResult after5 =
                serviceAt(onset.plus(Duration.ofHours(5)))
                        .quantify(request(onset, new Confidence(0.9), rates));
        ErdResult after8 =
                serviceAt(onset.plus(Duration.ofHours(8)))
                        .quantify(request(onset, new Confidence(0.9), rates));

        assertThat(after5.erd().minorUnits()).isEqualTo(500L);
        assertThat(after8.erd().minorUnits()).isEqualTo(800L);
        assertThat(after8.erd().minorUnits()).isGreaterThan(after5.erd().minorUnits());
    }

    @Test
    void accrualFloorsToWholeOperatingHours() {
        ErdResult result =
                serviceAt(onset.plus(Duration.ofMinutes(150))) // 2h30m -> 2 whole hours
                        .quantify(
                                request(
                                        onset,
                                        new Confidence(0.9),
                                        List.of(ratePerHour(ErdComponentType.EXCESS_ENERGY, 100))));

        assertThat(result.erd().minorUnits()).isEqualTo(200L);
    }

    @Test
    void onsetAtOrAfterNowAccruesNothing() {
        ErdResult result =
                serviceAt(onset) // zero elapsed
                        .quantify(
                                request(
                                        onset,
                                        new Confidence(0.9),
                                        List.of(ratePerHour(ErdComponentType.EXCESS_ENERGY, 100))));

        assertThat(result.erd().minorUnits()).isZero();
    }

    @Test
    void futureOnsetAccruesNothing() {
        Instant now = onset.minus(Duration.ofHours(3)); // onset is in the future relative to now
        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        now,
                                        new Confidence(0.9),
                                        List.of(ratePerHour(ErdComponentType.EXCESS_ENERGY, 100))));

        assertThat(result.erd().minorUnits()).isZero();
    }

    // ----- Req 2.4: Evidence_Chain links each component to its source -----

    @Test
    void evidenceChainLinksOperatingLogAndEachComponentSource() {
        Instant now = onset.plus(Duration.ofHours(1));
        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        now,
                                        new Confidence(0.9),
                                        List.of(
                                                ratePerHour(ErdComponentType.EC_EXPOSURE, 10),
                                                ratePerHour(ErdComponentType.EXCESS_ENERGY, 20))));

        assertThat(result.evidence().links())
                .anyMatch(link -> link.type() == SourceType.OPERATING_LOG);
        // Operating-log spine + one source link per available component.
        assertThat(result.evidence().links()).hasSize(3);
        assertThat(result.evidence().links())
                .anyMatch(link -> link.ref().id().contains("EC_EXPOSURE-source"));
        assertThat(result.evidence().links())
                .anyMatch(link -> link.ref().id().contains("EXCESS_ENERGY-source"));
    }

    // ----- Req 2.5: confidence derived from EID confidence -----

    @Test
    void confidenceIsDerivedFromEidConfidence() {
        Instant now = onset.plus(Duration.ofHours(1));
        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        now,
                                        new Confidence(0.73),
                                        List.of(ratePerHour(ErdComponentType.EXCESS_ENERGY, 10))));

        assertThat(result.confidence().value()).isEqualTo(0.73);
    }

    // ----- Req 2.6: uncomputable component excluded + reported unavailable with reason -----

    @Test
    void uncomputableComponentIsExcludedAndReportedUnavailable() {
        Instant now = onset.plus(Duration.ofHours(3));
        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        now,
                                        new Confidence(0.9),
                                        List.of(
                                                ratePerHour(ErdComponentType.EXCESS_ENERGY, 100),
                                                ErdComponentRate.unavailable(
                                                        ErdComponentType.FORGONE_ESCERTS,
                                                        "ESCerts market price absent"))));

        // Only the available component contributes: 3h * 100 = 300.
        assertThat(result.erd().minorUnits()).isEqualTo(300L);

        ErdComponent escerts =
                result.components().stream()
                        .filter(c -> c.type() == ErdComponentType.FORGONE_ESCERTS)
                        .findFirst()
                        .orElseThrow();
        assertThat(escerts.isAvailable()).isFalse();
        assertThat(escerts.status()).isEqualTo(ComponentStatus.UNAVAILABLE);
        assertThat(escerts.reason()).contains("ESCerts market price absent");
    }

    @Test
    void componentInWrongCurrencyIsReportedUnavailable() {
        Instant now = onset.plus(Duration.ofHours(2));
        ErdComponentRate usdRate =
                ErdComponentRate.available(
                        ErdComponentType.EXCESS_ENERGY, new Money(100, Currency.USD), "usd-source");

        ErdResult result = serviceAt(now).quantify(request(now, new Confidence(0.9), List.of(usdRate)));

        assertThat(result.erd().minorUnits()).isZero();
        ErdComponent component = result.components().get(0);
        assertThat(component.isAvailable()).isFalse();
        assertThat(component.reason()).isPresent();
    }

    // ----- Req 2.1 precondition: EID undetermined -> all components unavailable, total zero -----

    @Test
    void undeterminedEidYieldsZeroTotalWithAllComponentsUnavailable() {
        Instant now = onset.plus(Duration.ofHours(5));
        EidResult undetermined =
                EidResult.undetermined(
                        machine, "no baseline available", Confidence.NONE, EvidenceChain.EMPTY);
        ErdRequest req =
                new ErdRequest(
                        machine,
                        undetermined,
                        INR,
                        onset,
                        List.of(ratePerHour(ErdComponentType.EXCESS_ENERGY, 100)));

        ErdResult result = serviceAt(now).quantify(req);

        assertThat(result.erd().minorUnits()).isZero();
        assertThat(result.components()).allMatch(c -> !c.isAvailable());
        assertThat(result.confidence()).isEqualTo(Confidence.NONE);
    }

    @Test
    void emptyComponentsYieldZeroErd() {
        Instant now = onset.plus(Duration.ofHours(5));
        ErdResult result = serviceAt(now).quantify(request(now, new Confidence(0.9), List.of()));

        assertThat(result.erd().minorUnits()).isZero();
        assertThat(result.components()).isEmpty();
    }
}
