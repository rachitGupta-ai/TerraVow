package com.terravow.capability.aei;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.FacilityLocation;

import java.time.Duration;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the AEI assimilative-capacity forecast horizon and granularity bounds of
 * {@link DefaultAeiService} (Req 4.1).
 *
 * <p>Validates Correctness Property 12: when a meteorological forecast and a Receptor_Map are both
 * present for a facility, the forecast the service produces spans a horizon of <em>at least</em> 24
 * hours in time steps <em>no coarser than</em> 1 hour (Req 4.1). For any non-empty meteorological
 * forecast and any Receptor_Map, the returned {@link AssimilativeForecast} must therefore report
 * {@code horizon >= 24h}, {@code step <= 1h}, and consequently {@code stepCount >= 24}. The bounds
 * hold regardless of how many steps the raw meteorological forecast carries, because the service
 * maps the provided capacities onto its configured step grid.
 *
 * <p><strong>Validates: Requirements 4.1</strong>
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.1</a>
 */
class ForecastHorizonGranularityProperties {

    private static final FacilityLocation FACILITY = new FacilityLocation("plant-A");

    private final DefaultAeiService service = new DefaultAeiService();

    // Feature: terravow, Property 12: AEI forecast horizon and granularity bounds
    @Property(tries = 100)
    void forecastSpansAtLeast24HoursAtNoCoarserThanOneHourStep(
            @ForAll("meteorologicalForecasts") MeteorologicalForecast met,
            @ForAll("receptorMaps") ReceptorMap receptors) {

        AssimilativeForecast forecast = service.forecast(met, receptors).orElseThrow();

        assertThat(forecast.horizon()).isGreaterThanOrEqualTo(Duration.ofHours(24));
        assertThat(forecast.step()).isLessThanOrEqualTo(Duration.ofHours(1));
        assertThat(forecast.step()).isGreaterThan(Duration.ZERO);
        assertThat(forecast.stepCount()).isGreaterThanOrEqualTo(24);
        assertThat(forecast.harmCoefficientPerStep()).hasSize(forecast.stepCount());
    }

    /**
     * Non-empty meteorological forecasts with 1..48 strictly-positive, finite dispersion
     * capacities — spanning raw forecasts shorter than, equal to, and longer than the 24-step grid.
     */
    @Provide
    Arbitrary<MeteorologicalForecast> meteorologicalForecasts() {
        Arbitrary<Double> capacities =
                Arbitraries.doubles().between(0.01, 1_000.0).filter(Double::isFinite);
        return capacities
                .list()
                .ofMinSize(1)
                .ofMaxSize(48)
                .map(caps -> new MeteorologicalForecast(FACILITY, caps));
    }

    /** Receptor maps with 0..6 receptors of finite, non-negative population weight. */
    @Provide
    Arbitrary<ReceptorMap> receptorMaps() {
        Arbitrary<Double> weights =
                Arbitraries.doubles().between(0.0, 10_000.0).filter(Double::isFinite);
        Arbitrary<List<Receptor>> receptors = weights
                .list()
                .ofMinSize(0)
                .ofMaxSize(6)
                .map(ws -> {
                    List<Receptor> list = new java.util.ArrayList<>();
                    for (int i = 0; i < ws.size(); i++) {
                        list.add(new Receptor("r" + i, ws.get(i)));
                    }
                    return list;
                });
        return receptors.map(rs -> new ReceptorMap(FACILITY, rs));
    }
}
