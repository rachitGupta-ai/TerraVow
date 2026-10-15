package com.terravow.capability.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.AssetClass;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEventId;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;
import com.terravow.gate.DefaultDecisionGate;
import com.terravow.reasoning.tool.ErdSummationTool;

import org.assertj.core.data.Offset;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based test for Correctness Property 49 of the Emissions-Aware Maintenance Lifecycle
 * (Req 15.6): maintenance effectiveness is the intensity delta.
 *
 * <p>For any completed Maintenance_Event with an emissions intensity before and after, the
 * attributable change recorded in the {@link MaintenanceEffectivenessRecord} equals the before
 * intensity minus the after intensity. The property generates arbitrary finite, non-negative
 * before/after intensities, records effectiveness through
 * {@link DefaultMaintenanceLifecycle#recordEffectiveness}, and asserts the recorded
 * {@code intensityDelta} equals {@code before − after} — matching the observation's own
 * {@link IntensityObservation#attributableChange()} — so an improvement is positive, a regression is
 * negative, and no change is zero.
 *
 * <p>Validates: Requirements 15.6
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 15.6</a>
 */
class MaintenanceEffectivenessDeltaProperties {

    private final MachineId machine = new MachineId("machine-1");
    private final AssetClass assetClass = new AssetClass("boiler-class-A");

    private DefaultMaintenanceLifecycle lifecycle() {
        return new DefaultMaintenanceLifecycle(
                (m, h) -> {
                    throw new UnsupportedOperationException("forecaster unused in recording");
                },
                new VerifiedToolDeferralCostPricer(new ErdSummationTool()),
                new DefaultDecisionGate(),
                new InMemoryMaintenanceEffectivenessStore(),
                MaintenanceLifecycleConfig.defaults());
    }

    // Feature: terravow, Property 49: Maintenance effectiveness is the intensity delta
    @Property(tries = 100)
    void recordedEffectivenessIsBeforeMinusAfter(
            @ForAll @DoubleRange(min = 0.0, max = 1_000_000.0) double before,
            @ForAll @DoubleRange(min = 0.0, max = 1_000_000.0) double after) {
        IntensityObservation observation =
                new IntensityObservation(
                        new MaintenanceEventId("event-1"),
                        machine,
                        assetClass,
                        "replace-filter",
                        before,
                        after,
                        new EvidenceLink(SourceType.READING, new SourceRef("before-reading")),
                        new EvidenceLink(SourceType.READING, new SourceRef("after-reading")));

        MaintenanceEffectivenessRecord record = lifecycle().recordEffectiveness(observation);

        // Req 15.6 — the recorded delta is the before-minus-after emissions-intensity change.
        double expectedDelta = before - after;
        assertThat(record.intensityDelta())
                .isCloseTo(expectedDelta, Offset.offset(1e-9));
        // It is exactly the observation's own attributable change (the source of truth).
        assertThat(record.intensityDelta())
                .isCloseTo(observation.attributableChange(), Offset.offset(1e-12));
        // Sign semantics: improvement positive, regression negative, no change zero.
        assertThat(record.intensityDelta() > 0.0).isEqualTo(before > after);
        assertThat(record.intensityDelta() < 0.0).isEqualTo(before < after);
    }
}
