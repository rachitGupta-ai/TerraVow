package com.terravow.domain.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.terravow.domain.AssetClass;
import com.terravow.domain.BaselineKind;
import com.terravow.domain.Confidence;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HealthStateDeriver}: a full-telemetry derivation uses the full-history
 * confidence, an insufficient-telemetry derivation falls back to the Fleet_Cohort_Baseline at
 * strictly lower confidence, and with neither telemetry nor a cohort baseline the state is
 * undetermined (Req 14.9, 14.10).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.9, 14.10</a>
 */
class HealthStateDeriverTest {

    private static final Instant NOW = Instant.parse("2024-04-01T00:00:00Z");
    private static final MachineId MACHINE = new MachineId("machine-1");
    private static final Instant DEGRADED_ONSET = Instant.parse("2024-03-20T00:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final HealthStateDeriver deriver = new HealthStateDeriver(clock);

    private static TelemetrySample sample(double indicator) {
        return new TelemetrySample(MACHINE, NOW, indicator);
    }

    // Req 14.9 — sufficient telemetry derives from the full-history path at full-history confidence.
    @Test
    void sufficientTelemetryUsesFullHistoryConfidence() {
        List<TelemetrySample> telemetry = List.of(sample(0.6), sample(0.8), sample(1.0));
        List<MaintenanceEvent> history = List.of();

        HealthStateDeriver.Derivation derivation =
                deriver.derive(MACHINE, telemetry, history, Optional.empty(), DEGRADED_ONSET)
                        .orElseThrow();

        assertThat(derivation.baselineUsed()).isEqualTo(BaselineKind.COMMISSIONING);
        assertThat(derivation.state().confidence()).isEqualTo(deriver.fullHistoryConfidence());
        assertThat(derivation.state().healthScore()).isCloseTo(0.8, within(1e-9));
        assertThat(derivation.state().degradedOnset()).isEqualTo(DEGRADED_ONSET);
        assertThat(derivation.state().assessedAt()).isEqualTo(NOW);
        assertThat(derivation.state().contributingFactors()).isNotEmpty();
    }

    // Req 14.10 — insufficient telemetry falls back to the cohort baseline at strictly lower
    // confidence than the full-history path.
    @Test
    void insufficientTelemetryFallsBackToCohortAtStrictlyLowerConfidence() {
        List<TelemetrySample> telemetry = List.of(sample(0.5)); // below the default minimum of 3
        FleetCohortBaseline cohort = new FleetCohortBaseline(new AssetClass("press"), 1.0, 10.0);

        HealthStateDeriver.Derivation derivation =
                deriver.derive(
                                MACHINE,
                                telemetry,
                                List.of(),
                                Optional.of(cohort),
                                DEGRADED_ONSET)
                        .orElseThrow();

        assertThat(derivation.baselineUsed()).isEqualTo(BaselineKind.FLEET_COHORT);
        assertThat(derivation.state().confidence()).isEqualTo(deriver.cohortFallbackConfidence());
        assertThat(derivation.state().confidence().value())
                .isLessThan(deriver.fullHistoryConfidence().value());
    }

    // Req 14.10 — with no telemetry and no cohort baseline, the state is undetermined.
    @Test
    void noTelemetryAndNoCohortBaselineIsUndetermined() {
        Optional<HealthStateDeriver.Derivation> derivation =
                deriver.derive(
                        MACHINE, List.of(), List.of(), Optional.empty(), DEGRADED_ONSET);

        assertThat(derivation).isEmpty();
    }

    // Req 14.10 — the strictly-lower-confidence ordering is enforced structurally at construction.
    @Test
    void cohortConfidenceNotStrictlyLowerIsRejected() {
        assertThatThrownBy(
                        () ->
                                new HealthStateDeriver(
                                        clock, 3, new Confidence(0.5), new Confidence(0.5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // A derivation never reports BaselineKind.NONE — undetermined is signalled by empty instead.
    @Test
    void derivationRejectsNoneBaselineKind() {
        List<TelemetrySample> telemetry = List.of(sample(0.9), sample(0.9), sample(0.9));
        HealthStateDeriver.Derivation derivation =
                deriver.derive(MACHINE, telemetry, List.of(), Optional.empty(), DEGRADED_ONSET)
                        .orElseThrow();

        assertThat(derivation.baselineUsed()).isNotEqualTo(BaselineKind.NONE);
    }
}
