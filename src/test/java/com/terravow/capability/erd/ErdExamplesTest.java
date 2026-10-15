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

import org.junit.jupiter.api.Test;

/**
 * Supplementary example-based unit tests for {@link DefaultErdQuantificationService} covering ERD
 * examples not already exercised by {@link DefaultErdQuantificationServiceTest}: the single-currency
 * guarantee for a mixed available/unavailable component set reported in a non-INR currency (Req 2.1),
 * and the EID-confidence derivation across the boundary confidence values and an alternate EID
 * baseline (Req 2.5).
 *
 * <p>{@link DefaultErdQuantificationServiceTest} already covers the single-currency INR total, the
 * mid-range confidence (0.73) derivation, and the undetermined-EID {@link Confidence#NONE} case;
 * these examples add the complementary cases without duplicating that coverage.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 2.1, 2.5</a>
 */
class ErdExamplesTest {

    private final MachineId machine = new MachineId("machine-1");
    private final Instant onset = Instant.parse("2026-01-01T00:00:00Z");

    private DefaultErdQuantificationService serviceAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new DefaultErdQuantificationService(clock, new ErdSummationTool());
    }

    private ErdRequest request(
            Currency currency, Confidence eidConfidence, List<ErdComponentRate> rates) {
        return new ErdRequest(
                machine,
                EidResult.computed(
                        machine, 0.5, 0.5, 0.0, BaselineKind.COMMISSIONING,
                        eidConfidence, EvidenceChain.EMPTY),
                currency,
                onset,
                rates);
    }

    // ----- Req 2.1: single monetary amount in one declared currency (non-INR, mixed components) -----

    @Test
    void erdTotalAndEveryAvailableComponentShareTheDeclaredNonInrCurrency() {
        Instant now = onset.plus(Duration.ofHours(4));
        ErdComponentRate availableEur =
                ErdComponentRate.available(
                        ErdComponentType.EXCESS_ENERGY, new Money(100, Currency.EUR), "eur-source");
        ErdComponentRate unavailable =
                ErdComponentRate.unavailable(
                        ErdComponentType.FORGONE_ESCERTS, "ESCerts market price absent");

        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        Currency.EUR,
                                        new Confidence(0.9),
                                        List.of(availableEur, unavailable)));

        // Req 2.1 — a single declared currency; the total and every available component agree.
        assertThat(result.currency()).isEqualTo(Currency.EUR);
        assertThat(result.erd().currency()).isEqualTo(Currency.EUR);
        assertThat(result.components())
                .filteredOn(ErdComponent::isAvailable)
                .allSatisfy(c -> assertThat(c.amount().orElseThrow().currency()).isEqualTo(Currency.EUR));
        // 4 hours * 100/hour = 400, in EUR.
        assertThat(result.erd().minorUnits()).isEqualTo(400L);
    }

    // ----- Req 2.5: ERD confidence derives from the EID confidence (boundary + alternate baseline) -----

    @Test
    void erdConfidenceEqualsFullEidConfidence() {
        Instant now = onset.plus(Duration.ofHours(1));
        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        Currency.INR,
                                        Confidence.FULL,
                                        List.of(
                                                ErdComponentRate.available(
                                                        ErdComponentType.EXCESS_ENERGY,
                                                        new Money(10, Currency.INR),
                                                        "src"))));

        assertThat(result.confidence()).isEqualTo(Confidence.FULL);
        assertThat(result.confidence().value()).isEqualTo(1.0);
    }

    @Test
    void erdConfidenceEqualsZeroEidConfidence() {
        Instant now = onset.plus(Duration.ofHours(1));
        ErdResult result =
                serviceAt(now)
                        .quantify(
                                request(
                                        Currency.INR,
                                        Confidence.NONE,
                                        List.of(
                                                ErdComponentRate.available(
                                                        ErdComponentType.EXCESS_ENERGY,
                                                        new Money(10, Currency.INR),
                                                        "src"))));

        assertThat(result.confidence()).isEqualTo(Confidence.NONE);
        assertThat(result.confidence().value()).isEqualTo(0.0);
    }

    @Test
    void erdConfidenceDerivesFromFleetCohortEidConfidence() {
        Instant now = onset.plus(Duration.ofHours(1));
        Confidence fleetConfidence = new Confidence(0.42);
        ErdRequest req =
                new ErdRequest(
                        machine,
                        EidResult.computed(
                                machine, 0.5, 0.5, 0.0, BaselineKind.FLEET_COHORT,
                                fleetConfidence, EvidenceChain.EMPTY),
                        Currency.INR,
                        onset,
                        List.of(
                                ErdComponentRate.available(
                                        ErdComponentType.EXCESS_ENERGY,
                                        new Money(10, Currency.INR),
                                        "src")));

        ErdResult result = serviceAt(now).quantify(req);

        // Req 2.5 — the ERD confidence is exactly the EID confidence, regardless of which baseline
        // the EID used.
        assertThat(result.confidence()).isEqualTo(fleetConfidence);
        assertThat(result.confidence().value()).isEqualTo(0.42);
    }
}
