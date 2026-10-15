package com.terravow.capability.pollution;

import com.terravow.evidence.SourceRef;

import java.util.Objects;

/**
 * A single ingested stack, effluent, or sensor reading (Req 3.1).
 *
 * <p>A reading carries the {@link StreamId} it belongs to, the {@link Pollutant} measured, the
 * {@link EnvironmentalMedium} it was measured in, the measured numeric value, and a resolvable
 * {@link SourceRef} to the reading as a primary source. The pollutant and medium together select
 * the applicable regulatory threshold (Req 3.1); the {@link SourceRef} is what a raised
 * threshold-violation or anomaly condition links back to in its Evidence_Chain (Req 3.3). The
 * measured value must be finite.
 *
 * @param streamId      the stream this reading belongs to; never {@code null}
 * @param pollutant     the measured pollutant; never {@code null}
 * @param medium        the environmental medium measured; never {@code null}
 * @param measuredValue the finite measured value
 * @param source        the resolvable primary-source reference to this reading; never {@code null}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3</a>
 */
public record Reading(
        StreamId streamId,
        Pollutant pollutant,
        EnvironmentalMedium medium,
        double measuredValue,
        SourceRef source) {

    /**
     * Validates that all references are present and the measured value is finite (Req 3.1).
     *
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code measuredValue} is non-finite
     */
    public Reading {
        Objects.requireNonNull(streamId, "Reading streamId must not be null");
        Objects.requireNonNull(pollutant, "Reading pollutant must not be null");
        Objects.requireNonNull(medium, "Reading medium must not be null");
        Objects.requireNonNull(source, "Reading source must not be null");
        if (Double.isNaN(measuredValue) || Double.isInfinite(measuredValue)) {
            throw new IllegalArgumentException(
                    "Reading measuredValue must be a finite number, got: " + measuredValue);
        }
    }
}
