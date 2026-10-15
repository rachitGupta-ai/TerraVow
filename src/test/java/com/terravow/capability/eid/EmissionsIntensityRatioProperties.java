package com.terravow.capability.eid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.terravow.domain.MachineId;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for the emissions-intensity ratio computed by
 * {@link DefaultEidAttributionService#intensity}.
 *
 * <p>Validates Correctness Property 1: for arbitrary valid (finite, non-negative) emissions and a
 * strictly-positive output, the computed emissions intensity is exactly the ratio
 * {@code emissions / output} (Req 1.1). The result is always {@link IntensityStatus#AVAILABLE} and
 * the reported value equals the ratio within floating-point tolerance; the inverse relationship
 * ({@code intensity * output == emissions}) is also checked to guard against an accidental
 * reciprocal. The computation is a pure function of its inputs, so the same inputs always yield the
 * same ratio.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.1</a>
 */
class EmissionsIntensityRatioProperties {

    private final EidAttributionService service = new DefaultEidAttributionService();
    private final MachineId machine = new MachineId("machine-1");

    // Feature: terravow, Property 1: Emissions intensity is the output ratio
    @Property(tries = 100)
    void intensityIsEmissionsOverOutput(
            @ForAll("emissions") double emissions, @ForAll("positiveOutput") double output) {
        EmissionsIntensityResult result =
                service.intensity(
                        machine, TelemetryWindow.of(emissions), ProductionOutput.of(output));

        assertThat(result.status()).isEqualTo(IntensityStatus.AVAILABLE);
        assertThat(result.intensity()).isPresent();
        double expected = emissions / output;
        assertThat(result.intensity().orElseThrow())
                .isCloseTo(expected, within(Math.max(1.0e-9, Math.abs(expected) * 1.0e-9)));
    }

    // Feature: terravow, Property 1: Emissions intensity is the output ratio
    @Property(tries = 100)
    void intensityTimesOutputRecoversEmissions(
            @ForAll("emissions") double emissions, @ForAll("positiveOutput") double output) {
        // Guards against a reciprocal/transposed ratio: intensity * output must recover emissions.
        EmissionsIntensityResult result =
                service.intensity(
                        machine, TelemetryWindow.of(emissions), ProductionOutput.of(output));

        double recovered = result.intensity().orElseThrow() * output;
        assertThat(recovered)
                .isCloseTo(emissions, within(Math.max(1.0e-6, Math.abs(emissions) * 1.0e-9)));
    }

    /** Finite, non-negative emissions values across a wide magnitude range. */
    @Provide
    Arbitrary<Double> emissions() {
        return Arbitraries.doubles().between(0.0, 1.0e9).ofScale(6).filter(Double::isFinite);
    }

    /** Strictly-positive, finite output values (a zero output makes the ratio undefined). */
    @Provide
    Arbitrary<Double> positiveOutput() {
        return Arbitraries.doubles()
                .greaterOrEqual(1.0e-3)
                .lessOrEqual(1.0e9)
                .ofScale(6)
                .filter(Double::isFinite);
    }
}
