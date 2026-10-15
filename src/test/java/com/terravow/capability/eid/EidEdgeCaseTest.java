package com.terravow.capability.eid;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Confidence;
import com.terravow.domain.MachineId;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.SourceType;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Supplementary edge-case unit tests for {@link DefaultEidAttributionService}, covering intensity
 * input-validation branches and the both-baselines-absent undetermined case (Req 1.2, 1.6).
 *
 * <p>These cases are intentionally disjoint from {@link DefaultEidAttributionServiceTest}, which
 * already covers absent emissions, absent output, zero output, and negative emissions for Req 1.2,
 * and the both-baselines-absent and unavailable-current-intensity cases for Req 1.6. This class adds
 * the remaining uncovered validation branches — non-finite ({@code NaN}/infinity) emissions,
 * non-finite output, and negative output (as distinct from zero output) — each of which must yield
 * an {@link IntensityStatus#UNAVAILABLE} result with a recorded reason (Req 1.2), and asserts the
 * recorded reason content and Evidence_Chain of the both-baselines-absent undetermined case
 * (Req 1.6).
 */
class EidEdgeCaseTest {

    private final EidAttributionService service = new DefaultEidAttributionService();
    private final MachineId machine = new MachineId("machine-1");

    // ----- Intensity unavailable on invalid input (Req 1.2) -----

    // Req 1.2 — NaN emissions is non-finite → unavailable with reason.
    @Test
    void nanEmissionsYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(
                        machine, TelemetryWindow.of(Double.NaN), ProductionOutput.of(5.0));

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
        assertThat(result.intensity()).isEmpty();
        assertThat(result.reason()).get().asString().contains("non-finite");
    }

    // Req 1.2 — infinite emissions is non-finite → unavailable with reason.
    @Test
    void infiniteEmissionsYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(
                        machine,
                        TelemetryWindow.of(Double.POSITIVE_INFINITY),
                        ProductionOutput.of(5.0));

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
        assertThat(result.reason()).get().asString().contains("non-finite");
    }

    // Req 1.2 — NaN output is non-finite → unavailable with reason.
    @Test
    void nanOutputYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(
                        machine, TelemetryWindow.of(5.0), ProductionOutput.of(Double.NaN));

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
        assertThat(result.reason()).get().asString().contains("non-finite");
    }

    // Req 1.2 — infinite output is non-finite → unavailable with reason.
    @Test
    void infiniteOutputYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(
                        machine,
                        TelemetryWindow.of(5.0),
                        ProductionOutput.of(Double.NEGATIVE_INFINITY));

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
        assertThat(result.reason()).get().asString().contains("non-finite");
    }

    // Req 1.2 — negative output is invalid (distinct from zero output) → unavailable with reason.
    @Test
    void negativeOutputYieldsUnavailableWithReason() {
        EmissionsIntensityResult result =
                service.intensity(
                        machine, TelemetryWindow.of(5.0), ProductionOutput.of(-1.0));

        assertThat(result.status()).isEqualTo(IntensityStatus.UNAVAILABLE);
        assertThat(result.reason()).get().asString().contains("negative");
    }

    // ----- Both baselines absent → undetermined (Req 1.6) -----

    // Req 1.6 — with an available current intensity but no baselines, the EID is undetermined and
    // the recorded reason names the missing baselines (reason content, beyond the presence check in
    // DefaultEidAttributionServiceTest).
    @Test
    void bothBaselinesAbsentRecordsBaselineReason() {
        EidResult result =
                service.eid(
                        new EidRequest(
                                machine,
                                EmissionsIntensityResult.available(
                                        machine, 30.0, new Confidence(0.9), EvidenceChain.EMPTY),
                                Optional.empty(),
                                Optional.empty()));

        assertThat(result.status()).isEqualTo(EidStatus.UNDETERMINED);
        assertThat(result.reason()).get().asString().contains("Baseline");
        assertThat(result.evidence().links())
                .anyMatch(link -> link.type() == SourceType.OPERATING_LOG);
    }
}
