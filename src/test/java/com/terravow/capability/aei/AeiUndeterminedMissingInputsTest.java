package com.terravow.capability.aei;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.FacilityLocation;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Supplementary unit tests for the AEI "undetermined on missing inputs" edge case (Req 4.2).
 *
 * <p>{@link DefaultAeiServiceTest} already covers the {@code aei(...)} undetermined outcomes for a
 * {@code null} meteorological forecast, a {@code null} Receptor_Map, and both {@code null}, and the
 * {@code reschedule(...)} undetermined outcome for a missing input. This class fills the genuinely
 * uncovered cases of Req 4.2:
 * <ul>
 *   <li>the {@link DefaultAeiService#forecast} method itself returns {@link java.util.Optional#empty()}
 *       when either input is absent — the seam the capability relies on to report undetermined;</li>
 *   <li>a meteorological forecast that is <em>present but empty</em> ({@code stepCount() == 0}) is
 *       treated as absent, distinct from the {@code null} case the existing test exercises;</li>
 *   <li>the {@code reschedule(...)} undetermined path names the specific missing input (both
 *       missing, and Receptor_Map-only missing) and retains the actual schedule.</li>
 * </ul>
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.2</a>
 */
class AeiUndeterminedMissingInputsTest {

    private static final FacilityLocation FACILITY = new FacilityLocation("plant-A");

    private final DefaultAeiService service = new DefaultAeiService();

    // ---- helpers -----------------------------------------------------------------------------

    private static MeteorologicalForecast flatMet(double capacity) {
        List<Double> caps = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            caps.add(capacity);
        }
        return new MeteorologicalForecast(FACILITY, caps);
    }

    private static MeteorologicalForecast emptyMet() {
        return new MeteorologicalForecast(FACILITY, List.of());
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

    // ---- forecast() seam returns empty on missing inputs -------------------------------------

    @Test
    void forecastEmptyWhenMeteorologicalForecastNull() {
        assertThat(service.forecast(null, receptorMap(100.0))).isEmpty();
    }

    @Test
    void forecastEmptyWhenReceptorMapNull() {
        assertThat(service.forecast(flatMet(10.0), null)).isEmpty();
    }

    @Test
    void forecastEmptyWhenBothInputsNull() {
        assertThat(service.forecast(null, null)).isEmpty();
    }

    @Test
    void forecastEmptyWhenMeteorologicalForecastPresentButEmpty() {
        // An empty (zero-step) forecast carries no dispersion data, so it is treated as absent.
        assertThat(service.forecast(emptyMet(), receptorMap(100.0))).isEmpty();
    }

    // ---- aei() undetermined on a present-but-empty meteorological forecast --------------------

    @Test
    void aeiUndeterminedWhenMeteorologicalForecastPresentButEmpty() {
        AeiResult result = service.aei(
                schedule(1.0, 1.0), constraints(5.0, 5.0), emptyMet(), receptorMap(100.0));

        assertThat(result.status()).isEqualTo(AeiStatus.UNDETERMINED);
        assertThat(result.aeiPersonUgM3Hours()).isEmpty();
        assertThat(result.reason()).isPresent();
        assertThat(result.reason().get()).contains("meteorological forecast");
        // An undetermined result carries no receptors and an empty Evidence_Chain (Req 4.2).
        assertThat(result.affectedReceptors()).isEmpty();
        assertThat(result.evidence().links()).isEmpty();
        assertThat(result.confidence().value()).isBetween(0.0, 1.0);
    }

    // ---- reschedule() undetermined naming the missing input, retaining the actual ------------

    @Test
    void rescheduleUndeterminedWhenReceptorMapMissingRetainsActualAndNamesInput() {
        EmissionSchedule actual = schedule(5.0, 5.0);

        RescheduleResult result =
                service.reschedule(actual, constraints(10.0, 10.0), flatMet(10.0), null);

        assertThat(result.status()).isEqualTo(AeiStatus.UNDETERMINED);
        assertThat(result.recommendationApplied()).isFalse();
        assertThat(result.recommendedSchedule()).isEqualTo(actual);
        assertThat(result.actualAei()).isEmpty();
        assertThat(result.recommendedAei()).isEmpty();
        assertThat(result.undeterminedReason()).isPresent();
        assertThat(result.undeterminedReason().get()).contains("Receptor_Map");
    }

    @Test
    void rescheduleUndeterminedWhenBothInputsMissingNamesBoth() {
        EmissionSchedule actual = schedule(5.0, 5.0);

        RescheduleResult result =
                service.reschedule(actual, constraints(10.0, 10.0), null, null);

        assertThat(result.status()).isEqualTo(AeiStatus.UNDETERMINED);
        assertThat(result.recommendedSchedule()).isEqualTo(actual);
        assertThat(result.undeterminedReason().get())
                .contains("meteorological forecast")
                .contains("Receptor_Map");
    }
}
