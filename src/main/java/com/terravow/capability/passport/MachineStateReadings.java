package com.terravow.capability.passport;

import com.terravow.domain.FleetCohortBaseline;
import com.terravow.domain.MachineId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The machine-state-conditioned inputs from which a per-batch Passport footprint is computed
 * (Req 7.1, 7.3).
 *
 * <p>The {@code actualFootprint} is the machine-state-conditioned actual used in preference to any
 * industry-average factor (Req 7.3). When it is present the Passport footprint is computed from it
 * directly at full-evidence confidence. When it is {@link Optional#empty() absent} the service falls
 * back to the {@code fleetCohortBaseline} portion, reduces the confidence, and records the
 * substitution in the Evidence_Chain (Req 7.4); if the fallback is also absent the create call is
 * rejected because no footprint can be grounded.
 *
 * <p>{@code readingRefs} are the identifiers of the contributing machine-state readings that the
 * Passport's Evidence_Chain links to (Req 7.1); at least one is required so every Passport is
 * grounded.
 *
 * @param machine             the machine the batch was produced on; never {@code null}
 * @param actualFootprint     the machine-state-conditioned actual footprint, if measured; never
 *                            {@code null} (may be empty, triggering the fallback)
 * @param fleetCohortBaseline the cohort fallback used when the actual is absent; never {@code null}
 *                            (may be empty)
 * @param readingRefs         the identifiers of the contributing machine-state readings; never
 *                            {@code null}, never empty, defensively copied
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.1, 7.3, 7.4</a>
 */
public record MachineStateReadings(
        MachineId machine,
        Optional<Double> actualFootprint,
        Optional<FleetCohortBaseline> fleetCohortBaseline,
        List<String> readingRefs) {

    /**
     * Validates presence of all references, the finite/non-negative actual (when present), and that
     * at least one contributing reading identifier is supplied (Req 7.1, 7.3).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if a present {@code actualFootprint} is non-finite or
     *                                  negative, or if {@code readingRefs} is empty or contains a
     *                                  blank identifier
     */
    public MachineStateReadings {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(actualFootprint, "actualFootprint must not be null");
        Objects.requireNonNull(fleetCohortBaseline, "fleetCohortBaseline must not be null");
        Objects.requireNonNull(readingRefs, "readingRefs must not be null");
        if (actualFootprint.isPresent()) {
            double value = actualFootprint.get();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException(
                        "actualFootprint must be a finite number, got: " + value);
            }
            if (value < 0.0) {
                throw new IllegalArgumentException(
                        "actualFootprint must be non-negative, got: " + value);
            }
        }
        readingRefs = List.copyOf(readingRefs);
        if (readingRefs.isEmpty()) {
            throw new IllegalArgumentException(
                    "readingRefs must link at least one contributing machine-state reading "
                            + "(Req 7.1)");
        }
        for (String ref : readingRefs) {
            if (ref.isBlank()) {
                throw new IllegalArgumentException("readingRefs must not contain a blank reference");
            }
        }
    }
}
