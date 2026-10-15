package com.terravow.domain.store;

import com.terravow.domain.BaselineKind;
import com.terravow.domain.Confidence;
import com.terravow.domain.Factor;
import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineHealthState;
import com.terravow.domain.MachineId;
import com.terravow.domain.MaintenanceEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Derives a {@link MachineHealthState} from telemetry and maintenance history, falling back to a
 * {@link FleetCohortBaseline} estimate at strictly lower confidence when history is insufficient
 * (Req 14.9, 14.10).
 *
 * <p>When at least {@code minTelemetrySamples} telemetry readings are available, the health score is
 * the mean telemetry indicator and the state is derived from the <em>full-history</em> path at
 * {@code fullHistoryConfidence}; the maintenance history refines the contributing factors (Req
 * 14.9). When telemetry is insufficient, the state is derived from the Fleet_Cohort_Baseline
 * estimate — a cohort-level health proxy — at a confidence <em>strictly lower</em> than
 * {@code fullHistoryConfidence}, exactly the degradation rule the design mandates (Req 14.10). When
 * telemetry is insufficient <em>and</em> no cohort baseline is supplied, the health state is
 * undetermined and {@link Optional#empty()} is returned, since there is nothing to derive from.
 *
 * <p>The derivation is deterministic: the assessment timestamp comes from an injected {@link Clock}
 * (never {@code Instant.now()}), the score is a plain mean, and no randomness or iteration-order
 * dependence enters. The strictly-lower-confidence ordering is enforced structurally in the
 * constructor, so a cohort-fallback state can never be constructed with a confidence equal to or
 * above the full-history confidence (Req 14.10).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 14.9, 14.10</a>
 */
public final class HealthStateDeriver {

    /** The default minimum telemetry sample count treated as sufficient history. */
    public static final int DEFAULT_MIN_TELEMETRY_SAMPLES = 3;

    private final Clock clock;
    private final int minTelemetrySamples;
    private final Confidence fullHistoryConfidence;
    private final Confidence cohortFallbackConfidence;

    /**
     * Creates a deriver with explicit sufficiency threshold and the two confidence levels (Req
     * 14.9, 14.10).
     *
     * <p>The cohort-fallback confidence must be <em>strictly lower</em> than the full-history
     * confidence; this is the structural guarantee behind Req 14.10 and is validated here so the
     * ordering can never be inverted by configuration.
     *
     * @param clock                    the clock supplying the assessment timestamp; never
     *                                 {@code null}
     * @param minTelemetrySamples      the minimum telemetry readings treated as sufficient history;
     *                                 must be {@code >= 1}
     * @param fullHistoryConfidence    the confidence assigned to a full-telemetry derivation; never
     *                                 {@code null}
     * @param cohortFallbackConfidence the confidence assigned to a Fleet_Cohort_Baseline fallback;
     *                                 never {@code null}, must be strictly lower than
     *                                 {@code fullHistoryConfidence}
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code minTelemetrySamples < 1}, or
     *                                  {@code cohortFallbackConfidence} is not strictly lower than
     *                                  {@code fullHistoryConfidence}
     */
    public HealthStateDeriver(
            Clock clock,
            int minTelemetrySamples,
            Confidence fullHistoryConfidence,
            Confidence cohortFallbackConfidence) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (minTelemetrySamples < 1) {
            throw new IllegalArgumentException(
                    "minTelemetrySamples must be >= 1, got: " + minTelemetrySamples);
        }
        this.fullHistoryConfidence =
                Objects.requireNonNull(
                        fullHistoryConfidence, "fullHistoryConfidence must not be null");
        this.cohortFallbackConfidence =
                Objects.requireNonNull(
                        cohortFallbackConfidence, "cohortFallbackConfidence must not be null");
        if (cohortFallbackConfidence.value() >= fullHistoryConfidence.value()) {
            throw new IllegalArgumentException(
                    "cohortFallbackConfidence ("
                            + cohortFallbackConfidence.value()
                            + ") must be strictly lower than fullHistoryConfidence ("
                            + fullHistoryConfidence.value()
                            + ") (Req 14.10)");
        }
        this.minTelemetrySamples = minTelemetrySamples;
    }

    /**
     * Creates a deriver with the default sufficiency threshold and conventional confidence levels
     * (full history {@code 0.9}, cohort fallback {@code 0.5}).
     *
     * @param clock the clock supplying the assessment timestamp; never {@code null}
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    public HealthStateDeriver(Clock clock) {
        this(clock, DEFAULT_MIN_TELEMETRY_SAMPLES, new Confidence(0.9), new Confidence(0.5));
    }

    /**
     * The confidence this deriver assigns to a full-telemetry derivation (Req 14.9).
     *
     * @return the full-history confidence
     */
    public Confidence fullHistoryConfidence() {
        return fullHistoryConfidence;
    }

    /**
     * The confidence this deriver assigns to a Fleet_Cohort_Baseline fallback — strictly lower than
     * {@link #fullHistoryConfidence()} (Req 14.10).
     *
     * @return the cohort-fallback confidence
     */
    public Confidence cohortFallbackConfidence() {
        return cohortFallbackConfidence;
    }

    /**
     * Derives a Machine_Health_State for a machine from the available telemetry and maintenance
     * history, with Fleet_Cohort_Baseline fallback at strictly lower confidence (Req 14.9, 14.10).
     *
     * <p>Behaviour:
     *
     * <ul>
     *   <li><b>Sufficient telemetry</b> ({@code telemetry.size() >= minTelemetrySamples}): the
     *       score is the mean indicator; confidence is {@link #fullHistoryConfidence()}; the baseline
     *       used is {@link BaselineKind#COMMISSIONING} (full-history path); contributing factors
     *       summarise the telemetry and maintenance history (Req 14.9).</li>
     *   <li><b>Insufficient telemetry but a cohort baseline is present:</b> the score is the cohort
     *       health proxy derived from the baseline; confidence is {@link #cohortFallbackConfidence()}
     *       — strictly lower; the baseline used is {@link BaselineKind#FLEET_COHORT} (Req
     *       14.10).</li>
     *   <li><b>Insufficient telemetry and no cohort baseline:</b> undetermined —
     *       {@link Optional#empty()}.</li>
     * </ul>
     *
     * @param machine        the machine to assess; never {@code null}
     * @param telemetry      the telemetry readings; never {@code null} (may be empty)
     * @param history        the maintenance-event history; never {@code null} (may be empty)
     * @param cohortBaseline the Fleet_Cohort_Baseline fallback, if available; never {@code null},
     *                       may be {@link Optional#empty()}
     * @param degradedOnset  the onset of the current degraded period (from the Deferral_Period
     *                       start, Req 14.6); never {@code null}
     * @return the derived Machine_Health_State and which baseline it used, or empty when
     *         undetermined
     * @throws NullPointerException if any argument is {@code null}
     */
    public Optional<Derivation> derive(
            MachineId machine,
            List<TelemetrySample> telemetry,
            List<MaintenanceEvent> history,
            Optional<FleetCohortBaseline> cohortBaseline,
            Instant degradedOnset) {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(telemetry, "telemetry must not be null");
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(cohortBaseline, "cohortBaseline must not be null");
        Objects.requireNonNull(degradedOnset, "degradedOnset must not be null");

        Instant assessedAt = Instant.now(clock);

        if (telemetry.size() >= minTelemetrySamples) {
            double score = meanIndicator(telemetry);
            List<Factor> factors = new ArrayList<>();
            factors.add(
                    new Factor(
                            "telemetry: " + telemetry.size() + " samples, mean indicator " + score));
            factors.add(new Factor("maintenance history: " + history.size() + " events"));
            MachineHealthState state =
                    new MachineHealthState(
                            machine,
                            assessedAt,
                            score,
                            factors,
                            degradedOnset,
                            fullHistoryConfidence);
            return Optional.of(new Derivation(state, BaselineKind.COMMISSIONING));
        }

        if (cohortBaseline.isPresent()) {
            double score = cohortHealthProxy(cohortBaseline.get());
            List<Factor> factors = new ArrayList<>();
            factors.add(
                    new Factor(
                            "fleet-cohort fallback for asset class "
                                    + cohortBaseline.get().assetClass().value()
                                    + " (insufficient telemetry: "
                                    + telemetry.size()
                                    + " < "
                                    + minTelemetrySamples
                                    + ")"));
            MachineHealthState state =
                    new MachineHealthState(
                            machine,
                            assessedAt,
                            score,
                            factors,
                            degradedOnset,
                            cohortFallbackConfidence);
            return Optional.of(new Derivation(state, BaselineKind.FLEET_COHORT));
        }

        // Insufficient telemetry and no cohort baseline: nothing to derive from.
        return Optional.empty();
    }

    private static double meanIndicator(List<TelemetrySample> telemetry) {
        double sum = 0.0;
        for (TelemetrySample sample : telemetry) {
            sum += sample.indicator();
        }
        return sum / telemetry.size();
    }

    /**
     * Maps a cohort baseline's intensity to a bounded health proxy in {@code [0, 1]}. A higher
     * cohort intensity means poorer expected health, so the proxy is {@code 1 / (1 + intensity)},
     * which is finite, strictly in {@code (0, 1]}, and monotonically decreasing in intensity.
     */
    private static double cohortHealthProxy(FleetCohortBaseline baseline) {
        return 1.0 / (1.0 + baseline.cohortIntensity());
    }

    /**
     * The outcome of a health-state derivation: the derived {@link MachineHealthState} plus the
     * {@link BaselineKind} that produced it (full-history {@code COMMISSIONING} vs cohort
     * {@code FLEET_COHORT}).
     *
     * <p>Reporting the baseline kind alongside the state lets callers (EID, ERD) record in the
     * Evidence_Chain whether the strictly-lower-confidence cohort fallback was taken (Req 14.10).
     *
     * @param state        the derived health state; never {@code null}
     * @param baselineUsed the baseline the derivation used; never {@code null}, never
     *                     {@link BaselineKind#NONE} (an undetermined derivation returns empty
     *                     instead)
     */
    public record Derivation(MachineHealthState state, BaselineKind baselineUsed) {

        /**
         * Validates that both components are present and the baseline kind is a real derivation path
         * (Req 14.9, 14.10).
         *
         * @throws NullPointerException     if either component is {@code null}
         * @throws IllegalArgumentException if {@code baselineUsed} is {@link BaselineKind#NONE}
         */
        public Derivation {
            Objects.requireNonNull(state, "Derivation state must not be null");
            Objects.requireNonNull(baselineUsed, "Derivation baselineUsed must not be null");
            if (baselineUsed == BaselineKind.NONE) {
                throw new IllegalArgumentException(
                        "A derived MachineHealthState must use COMMISSIONING or FLEET_COHORT, "
                                + "not NONE");
            }
        }
    }
}
