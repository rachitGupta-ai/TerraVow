package com.terravow.capability.eid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.terravow.domain.BaselineKind;
import com.terravow.domain.CommissioningBaseline;
import com.terravow.domain.Confidence;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;

import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for the EID baseline-delta computation of
 * {@link DefaultEidAttributionService#eid}.
 *
 * <p>Validates Correctness Property 2: when a Commissioning_Baseline exists, the EID equals the
 * delta between the machine's current emissions intensity and the commissioning baseline intensity
 * (Req 1.3). For arbitrary valid current intensity and baseline intensity the result is
 * {@link EidStatus#COMPUTED}, uses {@link BaselineKind#COMMISSIONING}, and reports
 * {@code eid == current − baseline} within floating-point tolerance — regardless of whether the
 * machine improved (negative delta) or degraded (positive delta). The computation is a pure
 * function of its inputs.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.3</a>
 */
class EidBaselineDeltaProperties {

    private final EidAttributionService service = new DefaultEidAttributionService();
    private final MachineId machine = new MachineId("machine-1");

    private EmissionsIntensityResult availableIntensity(double value) {
        return EmissionsIntensityResult.available(
                machine, value, new Confidence(0.9), EvidenceChain.EMPTY);
    }

    // Feature: terravow, Property 2: EID equals the baseline delta
    @Property(tries = 100)
    void eidEqualsCurrentMinusCommissioningBaseline(
            @ForAll("intensity") double current, @ForAll("intensity") double baseline) {
        EidRequest request =
                new EidRequest(
                        machine,
                        availableIntensity(current),
                        Optional.of(new CommissioningBaseline(machine, baseline, 50.0)),
                        Optional.empty());

        EidResult result = service.eid(request);

        assertThat(result.status()).isEqualTo(EidStatus.COMPUTED);
        assertThat(result.baselineUsed()).isEqualTo(BaselineKind.COMMISSIONING);
        double expected = current - baseline;
        assertThat(result.eid().orElseThrow())
                .isCloseTo(expected, within(Math.max(1.0e-9, Math.abs(expected) * 1.0e-9)));
    }

    /**
     * Finite, non-negative intensity values across a wide magnitude range. Both the current
     * intensity and the baseline intensity are drawn from this range so the delta can be positive
     * (degradation), negative (improvement), or zero.
     */
    @Provide
    Arbitrary<Double> intensity() {
        return Arbitraries.doubles().between(0.0, 1.0e9).filter(Double::isFinite);
    }
}
