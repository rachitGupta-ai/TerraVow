package com.terravow.capability.passport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.capability.passport.SupplyGraph.Dependency;
import com.terravow.domain.AssetClass;
import com.terravow.domain.BatchId;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineId;
import com.terravow.evidence.SourceType;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultPassportService}, exercising per-batch Passport creation from
 * machine-state actuals with confidence + Evidence_Chain (Req 7.1), proportional downstream
 * propagation (Req 7.2), the actuals-not-industry-averages path (Req 7.3), the
 * Fleet_Cohort_Baseline fallback with reduced confidence + recorded substitution (Req 7.4),
 * mass-preserving airshed co-scheduling (Req 7.5), cascade-risk reaching every dependent downstream
 * tier (Req 7.6), and substitution ripple reaching every connected tier (Req 7.7).
 */
class DefaultPassportServiceTest {

    private final PassportService service = new DefaultPassportService();
    private final AssetClass assetClass = new AssetClass("press-line");
    private final MachineId machine = new MachineId("machine-1");

    private MachineStateReadings actuals(double footprint) {
        return new MachineStateReadings(
                machine, Optional.of(footprint), Optional.empty(), List.of("reading-1", "reading-2"));
    }

    // ----- create: Passport from actuals (Req 7.1, 7.3) -----

    // Req 7.1 — a produced batch gets a Passport carrying footprint, confidence, and an
    // Evidence_Chain linking to its contributing machine-state readings.
    @Test
    void createBuildsPassportFromActualsWithConfidenceAndEvidence() {
        Passport passport = service.create(new BatchId("batch-1"), actuals(42.0));

        assertThat(passport.batch()).isEqualTo(new BatchId("batch-1"));
        assertThat(passport.embeddedFootprint()).isEqualTo(42.0);
        assertThat(passport.confidence().value()).isBetween(0.0, 1.0);
        assertThat(passport.evidence().links())
                .allMatch(link -> link.type() == SourceType.READING)
                .hasSize(2);
    }

    // Req 7.3 — the footprint equals the machine-state-conditioned actual, NOT any
    // industry-average factor, and the actuals path does not record a fallback substitution.
    @Test
    void createUsesActualsNotIndustryAverages() {
        Passport passport = service.create(new BatchId("batch-1"), actuals(17.5));

        assertThat(passport.embeddedFootprint()).isEqualTo(17.5);
        assertThat(passport.evidence().links())
                .noneMatch(link -> link.type() == SourceType.FALLBACK_PATH);
        assertThat(passport.confidence()).isEqualTo(DefaultPassportService.ACTUALS_CONFIDENCE);
    }

    // ----- create: Fleet_Cohort_Baseline fallback (Req 7.4) -----

    // Req 7.4 — a missing actual uses the Fleet_Cohort_Baseline portion, assigns reduced
    // confidence, and records the substituted input in the Evidence_Chain.
    @Test
    void createFallsBackToFleetCohortWithReducedConfidenceAndRecordedSubstitution() {
        FleetCohortBaseline cohort = new FleetCohortBaseline(assetClass, 30.0, 100.0);
        MachineStateReadings readings =
                new MachineStateReadings(
                        machine, Optional.empty(), Optional.of(cohort), List.of("reading-1"));

        Passport passport = service.create(new BatchId("batch-1"), readings);

        assertThat(passport.embeddedFootprint()).isEqualTo(30.0);
        assertThat(passport.confidence().value())
                .isLessThan(DefaultPassportService.ACTUALS_CONFIDENCE.value());
        assertThat(passport.evidence().links())
                .anyMatch(link -> link.type() == SourceType.FALLBACK_PATH);
    }

    // Edge case — neither an actual nor a cohort baseline leaves nothing to ground the footprint on.
    @Test
    void createRejectsWhenNoActualAndNoFleetCohortBaseline() {
        MachineStateReadings readings =
                new MachineStateReadings(
                        machine, Optional.empty(), Optional.empty(), List.of("reading-1"));

        assertThatThrownBy(() -> service.create(new BatchId("batch-1"), readings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no machine-state actual");
    }

    // ----- propagate: proportional footprint sum (Req 7.2) -----

    // Req 7.2 — the downstream footprint is each input's footprint weighted by the proportion of
    // its quantity consumed (quantity-weighted mean).
    @Test
    void propagateSumsFootprintsInProportionToQuantityConsumed() {
        Passport inputA = service.create(new BatchId("in-A"), actuals(10.0));
        Passport inputB = service.create(new BatchId("in-B"), actuals(40.0));
        // 3 units of A (footprint 10) + 1 unit of B (footprint 40):
        // (10*3 + 40*1) / 4 = 70/4 = 17.5
        List<ConsumedInput> inputs =
                List.of(
                        new ConsumedInput(new BatchId("in-A"), 3.0, inputA),
                        new ConsumedInput(new BatchId("in-B"), 1.0, inputB));

        Passport downstream = service.propagate(new BatchId("out"), inputs);

        assertThat(downstream.embeddedFootprint()).isEqualTo(17.5);
    }

    // Req 7.2 — a single consumed input propagates its footprint unchanged (proportion = 1).
    @Test
    void propagateWithSingleInputCarriesFootprintThrough() {
        Passport inputA = service.create(new BatchId("in-A"), actuals(25.0));

        Passport downstream =
                service.propagate(
                        new BatchId("out"),
                        List.of(new ConsumedInput(new BatchId("in-A"), 5.0, inputA)));

        assertThat(downstream.embeddedFootprint()).isEqualTo(25.0);
        // Confidence of a chain is no higher than its weakest input.
        assertThat(downstream.confidence().value())
                .isLessThanOrEqualTo(inputA.confidence().value());
    }

    @Test
    void propagateRejectsEmptyInputs() {
        assertThatThrownBy(() -> service.propagate(new BatchId("out"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----- airshed co-scheduling: mass preservation (Req 7.5) -----

    // Req 7.5 — co-scheduling across shared-airshed facilities preserves the total emission mass.
    @Test
    void airshedCoSchedulePreservesTotalMass() {
        List<AirshedFacility> airshed =
                List.of(
                        new AirshedFacility(new FacilityId("fac-1"), 100.0, 1.0),
                        new AirshedFacility(new FacilityId("fac-2"), 60.0, 3.0),
                        new AirshedFacility(new FacilityId("fac-3"), 40.0, 1.0));

        CoScheduleResult result = service.airshedCoSchedule(airshed);

        double actualTotal = 200.0;
        double recommendedTotal =
                result.entries().stream().mapToDouble(CoScheduleEntry::recommendedMass).sum();
        assertThat(recommendedTotal)
                .isCloseTo(actualTotal, org.assertj.core.data.Offset.offset(1.0e-6));
        assertThat(result.totalActualMass()).isEqualTo(actualTotal);
        // Higher-capacity facility receives a larger recommended share.
        assertThat(result.entries().get(1).recommendedMass())
                .isGreaterThan(result.entries().get(0).recommendedMass());
    }

    @Test
    void airshedCoScheduleRejectsFewerThanTwoFacilities() {
        assertThatThrownBy(
                        () ->
                                service.airshedCoSchedule(
                                        List.of(
                                                new AirshedFacility(
                                                        new FacilityId("fac-1"), 10.0, 1.0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two or more facilities");
    }

    // ----- cascade-risk: reaches every dependent downstream tier (Req 7.6) -----

    // Req 7.6 — a degraded upstream tier propagates cascade-risk to every transitively dependent
    // downstream tier, each with an attributed risk.
    @Test
    void propagateCascadeReachesEveryTransitivelyDependentTier() {
        TierId t1 = new TierId("tier-1");
        TierId t2 = new TierId("tier-2");
        TierId t3 = new TierId("tier-3");
        TierId t4 = new TierId("tier-4");
        // t1 -> t2 -> t3; t1 -> t4
        SupplyGraph graph =
                SupplyGraph.of(
                        List.of(
                                new Dependency(t1, t2),
                                new Dependency(t2, t3),
                                new Dependency(t1, t4)));

        CascadeRisk result = service.propagateCascade(graph, new DegradedCondition(t1, 0.8));

        assertThat(result.origin()).isEqualTo(t1);
        assertThat(result.entries()).extracting(CascadeRisk.Entry::tier)
                .containsExactlyInAnyOrder(t2, t3, t4);
        assertThat(result.entries()).allMatch(e -> e.attributedRisk() == 0.8);
    }

    // Edge case — a leaf upstream tier with no dependents produces an empty cascade report.
    @Test
    void propagateCascadeWithNoDependentsReportsNoEntries() {
        TierId t1 = new TierId("tier-1");
        SupplyGraph graph = SupplyGraph.of(List.of());

        CascadeRisk result = service.propagateCascade(graph, new DegradedCondition(t1, 0.5));

        assertThat(result.entries()).isEmpty();
    }

    // ----- substitution ripple: reaches every connected tier (Req 7.7) -----

    // Req 7.7 — a substitution at a tier ripples to every connected tier (either direction,
    // transitively), reporting each tier's delivery-requirement change.
    @Test
    void supplyChainRippleReachesEveryConnectedTier() {
        TierId t1 = new TierId("tier-1");
        TierId t2 = new TierId("tier-2");
        TierId t3 = new TierId("tier-3");
        // t1 -> t2 (applied at t2); t2 -> t3. t2 connects to both t1 (upstream) and t3 (downstream).
        SupplyGraph graph =
                SupplyGraph.of(List.of(new Dependency(t1, t2), new Dependency(t2, t3)));

        RippleResult result =
                service.supplyChainRipple(graph, t2, new Substitution("low-carbon alloy", 0.1));

        assertThat(result.appliedTier()).isEqualTo(t2);
        assertThat(result.entries()).extracting(RippleResult.Entry::tier)
                .containsExactlyInAnyOrder(t1, t3);
        assertThat(result.entries())
                .allMatch(e -> e.deliveryRequirementChange() == 0.1);
    }
}
