package com.terravow.capability.eid;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.AssetClass;
import com.terravow.domain.BaselineKind;
import com.terravow.domain.CommissioningBaseline;
import com.terravow.domain.Confidence;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.SourceType;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultEidAttributionService}, exercising the emissions-intensity ratio
 * (Req 1.1), the unavailable-input rule (Req 1.2), the EID baseline delta (Req 1.3), additive
 * attribution (Req 1.4), the Fleet_Cohort fallback and its strictly-lower confidence (Req 1.5), the
 * both-baselines-absent undetermined case (Req 1.6), and the confidence + Evidence_Chain invariant
 * on every result (Req 1.7).
 */
class DefaultEidAttributionServiceTest {

    private final EidAttributionService service = new DefaultEidAttributionService();
    private final MachineId machine = new MachineId("machine-1");
    private final AssetClass assetClass = new AssetClass("press-line");

    private EmissionsIntensityResult availableIntensity(double value) {
        return EmissionsIntensityResult.available(
                machine, value, new Confidence(0.9), EvidenceChain.EMPTY);
    }

    // ----- Emissions intensity (Req 1.1) -----

    // Req 1.1 — intensity is emissions / output.
    @Test
    void intensityIsEmissionsOverOutput() {
        EmissionsIntensityResult result =
                service.intensity(machine, TelemetryWindow.of(100.0), ProductionOutput.of(4.0));

        assertThat(result.status()).isEqualTo(IntensityStatus.AVAILABLE);
        assertThat(result.intensity()).contains(25.0);
    }

    // Req 1.7 — an available intensity carries a confidence in [0,1] and an Evidence_Chain.
    @Test
    void availableIntensityCarriesConfidenceAndEvidence() {
        EmissionsIntensityResult result =
                service.intensity(machine, TelemetryWindow.of(10.0), ProductionOutput.of(2.0));

        assertThat(result.confidence().value()).isBetween(0.0, 1.0);
        assertThat(result.evidence().links())
                .anyMatch(link -> link.type() == SourceType.OPERATING_LOG);
    }

    // ----- Intensity unavailable on bad input (Req 1.2) -----

    // Req 1.2 — absent emissions → unavailable with reason.
    @Test
    void absentEmissionsYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(machine, TelemetryWindow.absent(), ProductionOutput.of(5.0));

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
        assertThat(result.intensity()).isEmpty();
        assertThat(result.reason()).isPresent();
    }

    // Req 1.2 — absent output → unavailable with reason.
    @Test
    void absentOutputYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(machine, TelemetryWindow.of(5.0), ProductionOutput.absent());

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
        assertThat(result.reason()).isPresent();
    }

    // Req 1.2 — zero output makes the ratio undefined → unavailable with reason.
    @Test
    void zeroOutputYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(machine, TelemetryWindow.of(5.0), ProductionOutput.of(0.0));

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
        assertThat(result.reason()).get().asString().contains("zero");
    }

    // Req 1.2 — negative emissions is invalid → unavailable with reason.
    @Test
    void negativeEmissionsYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(machine, TelemetryWindow.of(-1.0), ProductionOutput.of(5.0));

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
    }

    // Req 1.2, 1.7 — an unavailable intensity still carries an Evidence_Chain.
    @Test
    void unavailableIntensityStillCarriesEvidence() {
        EmissionsIntensityResult result =
                service.intensity(machine, TelemetryWindow.absent(), ProductionOutput.absent());

        assertThat(result.evidence().links())
                .anyMatch(link -> link.type() == SourceType.OPERATING_LOG);
    }

    // ----- EID baseline delta and attribution (Req 1.3, 1.4) -----

    // Req 1.3 — EID equals current − commissioning baseline intensity.
    @Test
    void eidEqualsBaselineDeltaAgainstCommissioning() {
        EidRequest request =
                new EidRequest(
                        machine,
                        availableIntensity(30.0),
                        Optional.of(new CommissioningBaseline(machine, 20.0, 50.0)),
                        Optional.empty());

        EidResult result = service.eid(request);

        assertThat(result.status()).isEqualTo(EidStatus.COMPUTED);
        assertThat(result.eid()).contains(10.0);
        assertThat(result.baselineUsed()).isEqualTo(BaselineKind.COMMISSIONING);
    }

    // Req 1.4 — the two additive attributed quantities sum to the EID.
    @Test
    void additiveAttributionSumsToEid() {
        EidRequest request =
                new EidRequest(
                        machine,
                        availableIntensity(30.0),
                        Optional.of(new CommissioningBaseline(machine, 20.0, 50.0)),
                        Optional.empty());

        EidResult result = service.eid(request);

        double sum =
                result.healthAttributed().orElseThrow()
                        + result.processNecessityAttributed().orElseThrow();
        assertThat(sum).isEqualTo(result.eid().orElseThrow());
    }

    // Req 1.7 — a computed EID carries a confidence in [0,1] and an Evidence_Chain.
    @Test
    void computedEidCarriesConfidenceAndEvidence() {
        EidRequest request =
                new EidRequest(
                        machine,
                        availableIntensity(30.0),
                        Optional.of(new CommissioningBaseline(machine, 20.0, 50.0)),
                        Optional.empty());

        EidResult result = service.eid(request);

        assertThat(result.confidence().value()).isBetween(0.0, 1.0);
        assertThat(result.evidence().links()).isNotEmpty();
    }

    // ----- Fleet_Cohort fallback (Req 1.5) -----

    // Req 1.5 — with no commissioning baseline, the fleet-cohort baseline is used.
    @Test
    void fleetCohortFallbackUsedWhenCommissioningAbsent() {
        EidRequest request =
                new EidRequest(
                        machine,
                        availableIntensity(30.0),
                        Optional.empty(),
                        Optional.of(new FleetCohortBaseline(assetClass, 22.0, 55.0)));

        EidResult result = service.eid(request);

        assertThat(result.status()).isEqualTo(EidStatus.COMPUTED);
        assertThat(result.eid()).contains(8.0);
        assertThat(result.baselineUsed()).isEqualTo(BaselineKind.FLEET_COHORT);
    }

    // Req 1.5 — the fleet-cohort confidence is strictly lower than the commissioning confidence for
    // the same result.
    @Test
    void fleetCohortConfidenceStrictlyLowerThanCommissioning() {
        EmissionsIntensityResult current = availableIntensity(30.0);

        EidResult commissioning =
                service.eid(
                        new EidRequest(
                                machine,
                                current,
                                Optional.of(new CommissioningBaseline(machine, 20.0, 50.0)),
                                Optional.empty()));
        EidResult cohort =
                service.eid(
                        new EidRequest(
                                machine,
                                current,
                                Optional.empty(),
                                Optional.of(new FleetCohortBaseline(assetClass, 20.0, 50.0))));

        assertThat(cohort.confidence().value())
                .isLessThan(commissioning.confidence().value());
    }

    // Req 1.5, 12.5 — the fallback is recorded in the Evidence_Chain as a FALLBACK_PATH link.
    @Test
    void fleetCohortFallbackRecordedInEvidenceChain() {
        EidResult result =
                service.eid(
                        new EidRequest(
                                machine,
                                availableIntensity(30.0),
                                Optional.empty(),
                                Optional.of(new FleetCohortBaseline(assetClass, 22.0, 55.0))));

        assertThat(result.evidence().links())
                .anyMatch(link -> link.type() == SourceType.FALLBACK_PATH);
    }

    // Req 1.3 — the commissioning baseline is preferred when both baselines are present.
    @Test
    void commissioningPreferredWhenBothBaselinesPresent() {
        EidResult result =
                service.eid(
                        new EidRequest(
                                machine,
                                availableIntensity(30.0),
                                Optional.of(new CommissioningBaseline(machine, 20.0, 50.0)),
                                Optional.of(new FleetCohortBaseline(assetClass, 22.0, 55.0))));

        assertThat(result.baselineUsed()).isEqualTo(BaselineKind.COMMISSIONING);
        assertThat(result.eid()).contains(10.0);
    }

    // ----- Both baselines absent → undetermined (Req 1.6) -----

    // Req 1.6 — both baselines absent → undetermined with reason.
    @Test
    void bothBaselinesAbsentYieldsUndeterminedWithReason() {
        EidResult result =
                service.eid(
                        new EidRequest(
                                machine,
                                availableIntensity(30.0),
                                Optional.empty(),
                                Optional.empty()));

        assertThat(result.status()).isEqualTo(EidStatus.UNDETERMINED);
        assertThat(result.baselineUsed()).isEqualTo(BaselineKind.NONE);
        assertThat(result.reason()).isPresent();
        assertThat(result.eid()).isEmpty();
    }

    // Req 1.6 — an unavailable current intensity makes the EID undetermined even with a baseline.
    @Test
    void unavailableCurrentIntensityYieldsUndetermined() {
        EmissionsIntensityResult unavailable =
                EmissionsIntensityResult.unavailable(
                        machine, "output absent", Confidence.NONE, EvidenceChain.EMPTY);

        EidResult result =
                service.eid(
                        new EidRequest(
                                machine,
                                unavailable,
                                Optional.of(new CommissioningBaseline(machine, 20.0, 50.0)),
                                Optional.empty()));

        assertThat(result.status()).isEqualTo(EidStatus.UNDETERMINED);
        assertThat(result.reason()).isPresent();
    }

    // Req 1.7 — an undetermined EID still carries a confidence in [0,1] and an Evidence_Chain.
    @Test
    void undeterminedEidStillCarriesConfidenceAndEvidence() {
        EidResult result =
                service.eid(
                        new EidRequest(
                                machine,
                                availableIntensity(30.0),
                                Optional.empty(),
                                Optional.empty()));

        assertThat(result.confidence().value()).isBetween(0.0, 1.0);
        assertThat(result.evidence().links()).isNotEmpty();
    }
}
