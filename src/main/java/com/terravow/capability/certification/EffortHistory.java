package com.terravow.capability.certification;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A facility's verified effort history: the time-ordered set of {@link MeasurementPoint}s over
 * which a Trajectory Certification verdict is computed (Req 9.1).
 *
 * <p>Certification is deliberately trajectory-based rather than snapshot-based: the verdict is
 * computed from the rate of improvement and verified effort history measured across the observation
 * period (Req 9.1) and must draw on at least two verified points separated across that period
 * (Req 9.2). This record is the input container for that computation. Points are defensively copied
 * and sorted chronologically on construction so the certification logic is deterministic and does
 * not depend on caller iteration order.
 *
 * @param facility the facility the history belongs to; never {@code null}
 * @param points   the measurement points; never {@code null}, defensively copied, may be empty,
 *                 held sorted by {@link MeasurementPoint#observedAt()}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.1, 9.2</a>
 */
public record EffortHistory(FacilityId facility, List<MeasurementPoint> points) {

    /**
     * Validates inputs, then defensively copies and chronologically sorts the points so downstream
     * logic is deterministic (Req 9.1, 9.2).
     *
     * @throws NullPointerException if {@code facility} or {@code points} is {@code null}, or a point
     *                              is {@code null}
     */
    public EffortHistory {
        Objects.requireNonNull(facility, "facility must not be null");
        Objects.requireNonNull(points, "points must not be null");
        points = points.stream()
                .map(p -> Objects.requireNonNull(p, "points must not contain a null element"))
                .sorted(Comparator.comparing(MeasurementPoint::observedAt))
                .toList();
    }

    /**
     * Returns the subset of points that were verified against their primary source, in chronological
     * order (Req 9.2).
     *
     * @return the verified measurement points; never {@code null}, possibly empty
     */
    public List<MeasurementPoint> verifiedPoints() {
        return points.stream().filter(MeasurementPoint::verified).toList();
    }

    /**
     * Returns the earliest observation timestamp across all points, or {@code null} when the history
     * is empty. Used to measure the span of the history against the minimum observation period
     * (Req 9.3).
     *
     * @return the earliest {@link MeasurementPoint#observedAt()}, or {@code null} if empty
     */
    public Instant earliestObservedAt() {
        return points.isEmpty() ? null : points.get(0).observedAt();
    }

    /**
     * Returns the latest observation timestamp across all points, or {@code null} when the history
     * is empty. Used to measure the span of the history against the minimum observation period
     * (Req 9.3).
     *
     * @return the latest {@link MeasurementPoint#observedAt()}, or {@code null} if empty
     */
    public Instant latestObservedAt() {
        return points.isEmpty() ? null : points.get(points.size() - 1).observedAt();
    }
}
