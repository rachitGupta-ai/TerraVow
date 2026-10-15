package com.terravow.capability.passport;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.AssetClass;
import com.terravow.domain.BatchId;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineId;
import com.terravow.evidence.SourceType;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Supplementary unit tests for the actuals-path usage of Passport creation (Req 7.3).
 *
 * <p>{@code DefaultPassportServiceTest} already confirms that, with no Fleet_Cohort_Baseline
 * supplied, {@code create} uses the machine-state-conditioned actual footprint, records no fallback
 * substitution, and attaches the actuals confidence. These tests cover the genuinely uncovered
 * case: when a Fleet_Cohort_Baseline (the industry-average-style substitute) is <em>also</em>
 * available, the service still uses the actual in preference to it — the actuals path takes
 * precedence over the cohort/industry-average alternative (Req 7.3). This is the discriminating
 * behaviour of Req 7.3: actuals are used <em>not</em> industry averages even when a substitute is
 * on hand.
 */
class ActualsPathUsageTest {

    private final PassportService service = new DefaultPassportService();
    private final MachineId machine = new MachineId("machine-actuals");
    private final AssetClass assetClass = new AssetClass("press-line");

    // Req 7.3 — with BOTH an actual and a Fleet_Cohort_Baseline present, the footprint equals the
    // actual (not the cohort), confidence is the full actuals confidence, and no fallback is
    // recorded. This distinguishes the actuals path from the industry-average fallback path.
    @Test
    void createPrefersActualOverFleetCohortBaselineWhenBothPresent() {
        double actualFootprint = 12.5;
        double cohortIntensity = 99.0; // Deliberately different from the actual.
        FleetCohortBaseline cohort = new FleetCohortBaseline(assetClass, cohortIntensity, 100.0);
        MachineStateReadings readings =
                new MachineStateReadings(
                        machine,
                        Optional.of(actualFootprint),
                        Optional.of(cohort),
                        List.of("reading-1", "reading-2"));

        Passport passport = service.create(new BatchId("batch-actuals"), readings);

        // Footprint is the machine-state-conditioned actual, NOT the cohort/industry-average value.
        assertThat(passport.embeddedFootprint()).isEqualTo(actualFootprint);
        assertThat(passport.embeddedFootprint()).isNotEqualTo(cohortIntensity);
        // Full actuals confidence, strictly higher than the cohort-fallback confidence.
        assertThat(passport.confidence()).isEqualTo(DefaultPassportService.ACTUALS_CONFIDENCE);
        assertThat(passport.confidence().value())
                .isGreaterThan(DefaultPassportService.FLEET_COHORT_CONFIDENCE.value());
        // No fallback substitution is recorded; the chain links only to contributing readings.
        assertThat(passport.evidence().links())
                .noneMatch(link -> link.type() == SourceType.FALLBACK_PATH);
        assertThat(passport.evidence().links())
                .allMatch(link -> link.type() == SourceType.READING)
                .hasSize(2);
    }

    // Req 7.3 — the actual is passed through byte-for-byte regardless of its magnitude, confirming
    // no industry-average blending/adjustment is applied to the actuals path.
    @Test
    void createPassesActualThroughWithoutAdjustment() {
        FleetCohortBaseline cohort = new FleetCohortBaseline(assetClass, 50.0, 100.0);
        MachineStateReadings readings =
                new MachineStateReadings(
                        machine, Optional.of(0.0), Optional.of(cohort), List.of("reading-zero"));

        Passport passport = service.create(new BatchId("batch-zero"), readings);

        assertThat(passport.embeddedFootprint()).isEqualTo(0.0);
        assertThat(passport.evidence().links())
                .noneMatch(link -> link.type() == SourceType.FALLBACK_PATH);
    }
}
