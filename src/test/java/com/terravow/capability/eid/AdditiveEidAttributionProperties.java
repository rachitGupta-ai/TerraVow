package com.terravow.capability.eid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.terravow.domain.AssetClass;
import com.terravow.domain.CommissioningBaseline;
import com.terravow.domain.Confidence;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;

import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for the additive attribution of a computed EID result produced by
 * {@link DefaultEidAttributionService#eid}.
 *
 * <p>Validates Correctness Property 3: whenever an EID is computed, it is reported as two additive
 * attributed quantities — the portion attributable to machine health state and the portion
 * attributable to unavoidable process necessity — whose sum equals the EID (Req 1.4). The property
 * holds for any computed result, independent of which baseline (Commissioning or Fleet_Cohort) was
 * used, so both baseline paths are exercised. The sum is checked within floating-point tolerance.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.4</a>
 */
class AdditiveEidAttributionProperties {

    private final EidAttributionService service = new DefaultEidAttributionService();
    private final MachineId machine = new MachineId("machine-1");
    private final AssetClass assetClass = new AssetClass("press-line");

    private EmissionsIntensityResult availableIntensity(double value) {
        return EmissionsIntensityResult.available(
                machine, value, new Confidence(0.9), EvidenceChain.EMPTY);
    }

    // Feature: terravow, Property 3: EID attribution is additive
    @Property(tries = 100)
    void attributionSumsToEidForCommissioningBaseline(
            @ForAll("intensity") double current, @ForAll("intensity") double baseline) {
        EidResult result =
                service.eid(
                        new EidRequest(
                                machine,
                                availableIntensity(current),
                                Optional.of(new CommissioningBaseline(machine, baseline, 50.0)),
                                Optional.empty()));

        assertThat(result.status()).isEqualTo(EidStatus.COMPUTED);
        assertAttributionSumsToEid(result);
    }

    // Feature: terravow, Property 3: EID attribution is additive
    @Property(tries = 100)
    void attributionSumsToEidForFleetCohortBaseline(
            @ForAll("intensity") double current, @ForAll("intensity") double baseline) {
        EidResult result =
                service.eid(
                        new EidRequest(
                                machine,
                                availableIntensity(current),
                                Optional.empty(),
                                Optional.of(new FleetCohortBaseline(assetClass, baseline, 55.0))));

        assertThat(result.status()).isEqualTo(EidStatus.COMPUTED);
        assertAttributionSumsToEid(result);
    }

    /**
     * Asserts that the two additive attributed quantities of a computed result sum to its EID
     * within floating-point tolerance (Req 1.4).
     */
    private static void assertAttributionSumsToEid(EidResult result) {
        double health = result.healthAttributed().orElseThrow();
        double process = result.processNecessityAttributed().orElseThrow();
        double eid = result.eid().orElseThrow();
        assertThat(health + process)
                .isCloseTo(eid, within(Math.max(1.0e-9, Math.abs(eid) * 1.0e-9)));
    }

    /**
     * Finite, non-negative intensity values across a wide magnitude range, used for both the
     * current intensity and the baseline intensity.
     */
    @Provide
    Arbitrary<Double> intensity() {
        return Arbitraries.doubles().between(0.0, 1.0e9).filter(Double::isFinite);
    }
}
