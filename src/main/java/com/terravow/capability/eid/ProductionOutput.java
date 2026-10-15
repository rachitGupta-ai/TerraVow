package com.terravow.capability.eid;

import java.util.Objects;
import java.util.Optional;

/**
 * The production-output input (the denominator) for an emissions-intensity computation.
 *
 * <p>Emissions intensity is emissions per unit of output (Req 1.1); this record carries the output
 * term. The value is {@link Optional} so an absent measurement is represented explicitly. An absent
 * value, a non-finite/negative value, or a zero output (which would make the ratio undefined) makes
 * the intensity unavailable with a recorded reason (Req 1.2); that validation is performed by the
 * service so the degradation rule is centralised there.
 *
 * @param quantity the produced output quantity over the window, or empty if not captured (Req 1.1,
 *                 1.2)
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.1, 1.2</a>
 */
public record ProductionOutput(Optional<Double> quantity) {

    /**
     * Validates that the optional container itself is present (its contents are validated by the
     * service, so an absent, invalid, or zero value degrades gracefully rather than throwing).
     *
     * @throws NullPointerException if {@code quantity} is {@code null}
     */
    public ProductionOutput {
        Objects.requireNonNull(quantity, "quantity must not be null");
    }

    /**
     * Convenience factory for an output carrying a captured quantity.
     *
     * @param quantity the produced output quantity
     * @return a production output wrapping the value
     */
    public static ProductionOutput of(double quantity) {
        return new ProductionOutput(Optional.of(quantity));
    }

    /**
     * Convenience factory for an output with no captured quantity.
     *
     * @return a production output with an absent quantity
     */
    public static ProductionOutput absent() {
        return new ProductionOutput(Optional.empty());
    }
}
