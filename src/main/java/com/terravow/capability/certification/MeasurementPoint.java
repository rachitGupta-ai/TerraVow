package com.terravow.capability.certification;

import com.terravow.evidence.EvidenceLink;

import java.time.Instant;
import java.util.Objects;

/**
 * A single verified-or-unverified measurement of a facility's improvement metric at a point in
 * time, forming the raw material of an {@link EffortHistory}.
 *
 * <p>A Trajectory Certification verdict is derived from at least two <em>verified</em> measurement
 * points separated across the observation period, never from a single-point-in-time snapshot
 * (Req 9.2). Each point carries the primary-source {@link EvidenceLink} that backs it so the
 * verdict's Evidence_Chain resolves to primary sources (Req 9.4, 9.6). The {@code verified} flag
 * records whether the reading could be verified against its source: an unverifiable reading is
 * evidence of a gaming / integrity problem rather than of improvement (Req 9.6).
 *
 * <p>The {@code improvementMetric} is a facility improvement indicator where a <em>higher</em>
 * value means <em>more</em> improvement (for example, a cumulative emissions-intensity reduction).
 * The certification logic reads the sign of the change between the earliest and latest verified
 * points as the rate of improvement; it does not assign units here.
 *
 * @param observedAt        the instant the measurement was taken; never {@code null}
 * @param improvementMetric the facility improvement indicator (higher is better); must be finite
 * @param verified          whether the reading was verified against its primary source (Req 9.6)
 * @param source            the primary-source link backing this measurement; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.2, 9.4, 9.6</a>
 */
public record MeasurementPoint(
        Instant observedAt, double improvementMetric, boolean verified, EvidenceLink source) {

    /**
     * Validates that the timestamp and source are present and the metric is a finite number
     * (Req 9.2).
     *
     * @throws NullPointerException     if {@code observedAt} or {@code source} is {@code null}
     * @throws IllegalArgumentException if {@code improvementMetric} is non-finite
     */
    public MeasurementPoint {
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (Double.isNaN(improvementMetric) || Double.isInfinite(improvementMetric)) {
            throw new IllegalArgumentException(
                    "improvementMetric must be a finite number, got: " + improvementMetric);
        }
    }
}
