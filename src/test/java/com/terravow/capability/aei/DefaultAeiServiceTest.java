package com.terravow.capability.aei;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.domain.FacilityLocation;
import com.terravow.reasoning.tool.MassPreservationTool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultAeiService} covering the AEI happy path and the edge cases of
 * Requirement 4 (forecast horizon/granularity 4.1, undetermined on missing inputs 4.2, AEI as the
 * non-negative harm difference 4.3, rescheduling never worsening AEI 4.4, mass preservation 4.5,
 * rejection of mass/cross-media-increasing candidates 4.6, affected-receptor/Evidence_Chain
 * reporting 4.7).
 */
class DefaultAeiServiceTest {

    private static final FacilityLocation FACILITY = new FacilityLocation("plant-A");

    private final DefaultAeiService service = new DefaultAeiService();

    // ---- helpers -----------------------------------------------------------------------------

    private static MeteorologicalForecast met(double... capacities) {
        List<Double> list = new ArrayList<>();
        for (double c : capacities) {
            list.add(c);
        }
        return new MeteorologicalForecast(FACILITY, list);
    }

    /** A 24-step met forecast whose step {@code i} has the given capacity for all steps. */
    private static MeteorologicalForecast flatMet(double capacity) {
        double[] caps = new double[24];
        java.util.Arrays.fill(caps, capacity);
        return met(caps);
    }

    private static ReceptorMap receptorMap(double... weights) {
        List<Receptor> receptors = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            receptors.add(new Receptor("r" + i, weights[i]));
        }
        return new ReceptorMap(FACILITY, receptors);
    }

    private static EmissionSchedule schedule(double... masses) {
        List<Double> list = new ArrayList<>();
        for (double m : masses) {
            list.add(m);
        }
        return new EmissionSchedule(list);
    }

    private static FacilityConstraints constraints(double... caps) {
        List<Double> list = new ArrayList<>();
        for (double c : caps) {
            list.add(c);
        }
        return new FacilityConstraints(list);
    }

    // ---- Req 4.1: forecast horizon and granularity -------------------------------------------

    @Test
    void forecastCoversAtLeast24HoursAtNoCoarserThanOneHourStep() {
        var forecast = service.forecast(flatMet(10.0), receptorMap(100.0));

        assertThat(forecast).isPresent();
        AssimilativeForecast f = forecast.get();
        assertThat(f.horizon()).isGreaterThanOrEqualTo(Duration.ofHours(24));
        assertThat(f.step()).isLessThanOrEqualTo(Duration.ofHours(1));
        assertThat(f.stepCount()).isGreaterThanOrEqualTo(24);
        assertThat(f.facility()).isEqualTo(FACILITY);
    }

    @Test
    void forecastHarmCoefficientReflectsPopulationOverDispersion() {
        // coefficient = totalPopulationWeight / dispersionCapacity = 200 / 10 = 20
        var forecast = service.forecast(flatMet(10.0), receptorMap(120.0, 80.0)).orElseThrow();

        assertThat(forecast.harmCoefficientPerStep().get(0)).isEqualTo(20.0);
    }

    // ---- Req 4.2: undetermined on missing inputs ---------------------------------------------

    @Test
    void aeiUndeterminedWhenMeteorologicalForecastMissing() {
        AeiResult result = service.aei(
                schedule(1.0, 1.0), constraints(5.0, 5.0), null, receptorMap(100.0));

        assertThat(result.status()).isEqualTo(AeiStatus.UNDETERMINED);
        assertThat(result.aeiPersonUgM3Hours()).isEmpty();
        assertThat(result.reason()).isPresent();
        assertThat(result.reason().get()).contains("meteorological forecast");
    }

    @Test
    void aeiUndeterminedWhenReceptorMapMissing() {
        AeiResult result = service.aei(
                schedule(1.0, 1.0), constraints(5.0, 5.0), flatMet(10.0), null);

        assertThat(result.status()).isEqualTo(AeiStatus.UNDETERMINED);
        assertThat(result.reason().get()).contains("Receptor_Map");
    }

    @Test
    void aeiUndeterminedNamesBothMissingInputs() {
        AeiResult result = service.aei(schedule(1.0), constraints(5.0), null, null);

        assertThat(result.status()).isEqualTo(AeiStatus.UNDETERMINED);
        assertThat(result.reason().get()).contains("meteorological forecast").contains("Receptor_Map");
    }

    // ---- Req 4.3: AEI is the non-negative harm difference -------------------------------------

    @Test
    void aeiIsNonNegativeHarmDifferenceForBadlyTimedSchedule() {
        // Step 0 has low capacity (high harm), step 1 high capacity (low harm). Emitting all mass
        // in step 0 is the worst timing; the harm-minimising schedule shifts it to step 1.
        // capacities: step0 = 1 (coeff 100), step1 = 100 (coeff 1); population = 100
        double[] caps = new double[24];
        java.util.Arrays.fill(caps, 100.0);
        caps[0] = 1.0;
        MeteorologicalForecast met = met(caps);
        ReceptorMap receptors = receptorMap(100.0);

        // actual: all 10 units in step 0 -> harm = 10 * 100 = 1000
        List<Double> actualMasses = new ArrayList<>(java.util.Collections.nCopies(24, 0.0));
        actualMasses.set(0, 10.0);
        EmissionSchedule actual = new EmissionSchedule(actualMasses);

        // caps allow shifting all 10 units to step 1 (cap 50)
        double[] capsArr = new double[24];
        java.util.Arrays.fill(capsArr, 50.0);
        FacilityConstraints constraints = constraints(capsArr);

        AeiResult result = service.aei(actual, constraints, met, receptors);

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        double aei = result.aeiPersonUgM3Hours().orElseThrow();
        assertThat(aei).isGreaterThan(0.0);
        // minimising harm: 10 units in a coeff-1 step = 10; AEI = 1000 - 10 = 990
        assertThat(aei).isEqualTo(990.0, org.assertj.core.api.Assertions.within(1e-6));
    }

    @Test
    void aeiIsZeroWhenActualIsAlreadyHarmMinimising() {
        // Flat capacities -> every step equal; any same-mass distribution has equal harm, so AEI=0.
        AeiResult result = service.aei(
                schedule(fill(24, 1.0)),
                constraints(fill(24, 10.0)),
                flatMet(10.0),
                receptorMap(100.0));

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        assertThat(result.aeiPersonUgM3Hours().orElseThrow())
                .isEqualTo(0.0, org.assertj.core.api.Assertions.within(1e-6));
    }

    // ---- Req 4.4: rescheduling never worsens AEI ----------------------------------------------

    @Test
    void rescheduleRecommendsLowerHarmScheduleAndNeverWorsensAei() {
        double[] caps = new double[24];
        java.util.Arrays.fill(caps, 100.0);
        caps[0] = 1.0; // step 0 is the stagnant, high-harm step
        MeteorologicalForecast met = met(caps);
        ReceptorMap receptors = receptorMap(100.0);

        List<Double> actualMasses = new ArrayList<>(java.util.Collections.nCopies(24, 0.0));
        actualMasses.set(0, 10.0);
        EmissionSchedule actual = new EmissionSchedule(actualMasses);
        FacilityConstraints constraints = constraints(fill(24, 50.0));

        RescheduleResult result = service.reschedule(actual, constraints, met, receptors);

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        assertThat(result.recommendationApplied()).isTrue();
        assertThat(result.recommendedAei().orElseThrow())
                .isLessThanOrEqualTo(result.actualAei().orElseThrow());
        // mass preserved
        assertThat(result.recommendedSchedule().totalMass())
                .isEqualTo(actual.totalMass(), org.assertj.core.api.Assertions.within(1e-6));
    }

    @Test
    void rescheduleRetainsActualWhenAlreadyOptimal() {
        RescheduleResult result = service.reschedule(
                schedule(fill(24, 1.0)),
                constraints(fill(24, 10.0)),
                flatMet(10.0),
                receptorMap(100.0));

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        assertThat(result.recommendationApplied()).isFalse();
        assertThat(result.recommendedSchedule()).isEqualTo(result.actualSchedule());
        assertThat(result.recommendedAei().orElseThrow())
                .isLessThanOrEqualTo(result.actualAei().orElseThrow());
    }

    // ---- Req 4.5 / 4.6: mass preservation and candidate rejection -----------------------------

    @Test
    void recommendedSchedulePreservesTotalMassWithinTolerance() {
        double[] caps = new double[24];
        java.util.Arrays.fill(caps, 100.0);
        caps[0] = 1.0;
        MeteorologicalForecast met = met(caps);

        List<Double> actualMasses = new ArrayList<>(java.util.Collections.nCopies(24, 0.0));
        actualMasses.set(0, 37.5);
        EmissionSchedule actual = new EmissionSchedule(actualMasses);
        FacilityConstraints constraints = constraints(fill(24, 100.0));

        RescheduleResult result =
                service.reschedule(actual, constraints, met, receptorMap(100.0));

        double tolerance = AeiConfig.DEFAULT_TOLERANCE_FRACTION;
        double relativeDeviation = Math.abs(
                result.recommendedSchedule().totalMass() - actual.totalMass()) / actual.totalMass();
        assertThat(relativeDeviation).isLessThanOrEqualTo(tolerance);
    }

    @Test
    void rescheduleRejectsInfeasibleCandidateAndRetainsActual() {
        // All mass is in the high-harm step 0, but every other step has zero capacity, so the mass
        // cannot be shifted anywhere. The only same-mass schedule is the actual; capacity shortfall
        // makes the greedy candidate infeasible, so the actual is retained.
        double[] caps = new double[24];
        java.util.Arrays.fill(caps, 100.0);
        caps[0] = 1.0;
        MeteorologicalForecast met = met(caps);

        List<Double> actualMasses = new ArrayList<>(java.util.Collections.nCopies(24, 0.0));
        actualMasses.set(0, 10.0);
        EmissionSchedule actual = new EmissionSchedule(actualMasses);

        // only step 0 has capacity (10); all other steps cap 0 -> cannot move mass out
        List<Double> capList = new ArrayList<>(java.util.Collections.nCopies(24, 0.0));
        capList.set(0, 10.0);
        FacilityConstraints constraints = new FacilityConstraints(capList);

        RescheduleResult result =
                service.reschedule(actual, constraints, met, receptorMap(100.0));

        assertThat(result.status()).isEqualTo(AeiStatus.COMPUTED);
        assertThat(result.recommendationApplied()).isFalse();
        assertThat(result.recommendedSchedule()).isEqualTo(actual);
    }

    @Test
    void rescheduleUndeterminedWhenInputsMissingRetainsActual() {
        EmissionSchedule actual = schedule(5.0, 5.0);
        RescheduleResult result =
                service.reschedule(actual, constraints(10.0, 10.0), null, receptorMap(100.0));

        assertThat(result.status()).isEqualTo(AeiStatus.UNDETERMINED);
        assertThat(result.recommendationApplied()).isFalse();
        assertThat(result.recommendedSchedule()).isEqualTo(actual);
        assertThat(result.undeterminedReason()).isPresent();
    }

    // ---- Req 4.7: affected receptors + Evidence_Chain -----------------------------------------

    @Test
    void aeiReportsAffectedReceptorsAndEvidenceChain() {
        ReceptorMap receptors = receptorMap(60.0, 40.0);
        AeiResult result = service.aei(
                schedule(fill(24, 1.0)), constraints(fill(24, 10.0)), flatMet(10.0), receptors);

        assertThat(result.affectedReceptors()).hasSize(2);
        assertThat(result.affectedReceptors()).extracting(Receptor::id).containsExactly("r0", "r1");
        assertThat(result.evidence().links()).hasSize(2);
        assertThat(result.evidence().links())
                .anyMatch(l -> l.ref().id().startsWith("met-forecast:"))
                .anyMatch(l -> l.ref().id().startsWith("receptor-map:"));
        assertThat(result.confidence().value()).isBetween(0.0, 1.0);
    }

    // ---- config clamping ----------------------------------------------------------------------

    @Test
    void configClampsOutOfRangeHorizonStepAndTolerance() {
        AeiConfig config = AeiConfig.of(Duration.ofHours(1), Duration.ofHours(6), 5.0);

        assertThat(config.horizon()).isGreaterThanOrEqualTo(Duration.ofHours(24));
        assertThat(config.step()).isLessThanOrEqualTo(Duration.ofHours(1));
        assertThat(config.toleranceFraction()).isBetween(0.0, 1.0);
    }

    @Test
    void nullDependenciesRejected() {
        assertThatThrownBy(() -> new DefaultAeiService(null, new MassPreservationTool()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultAeiService(AeiConfig.defaults(), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static double[] fill(int length, double value) {
        double[] a = new double[length];
        java.util.Arrays.fill(a, value);
        return a;
    }
}
