package com.terravow.capability.pollution;

/**
 * Configurable parameters for the {@link PollutionMonitor} anomaly detection (Req 3.4, 3.5).
 *
 * <p>The minimum history is the number of prior readings a stream must accumulate before an
 * expected profile is considered established; below it, anomaly evaluation is deferred as
 * insufficient-history (Req 3.5). The anomaly-deviation threshold is the minimum deviation from the
 * expected profile (measured in standard deviations of the stream's history, i.e. a robust,
 * unit-free z-score) at or above which a reading raises an anomaly condition (Req 3.4). Both are
 * behaviour-gating configuration so operators can tune sensitivity per environment without code
 * changes.
 *
 * @param minHistory               the minimum number of prior readings required to establish an
 *                                 expected profile; must be {@code >= 1}
 * @param anomalyDeviationThreshold the minimum (inclusive) deviation at which an anomaly is raised;
 *                                 must be finite and {@code >= 0}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.4, 3.5</a>
 */
public record PollutionMonitorConfig(int minHistory, double anomalyDeviationThreshold) {

    /**
     * Validates the configuration bounds (Req 3.4, 3.5).
     *
     * @throws IllegalArgumentException if {@code minHistory < 1}, or
     *                                  {@code anomalyDeviationThreshold} is non-finite or negative
     */
    public PollutionMonitorConfig {
        if (minHistory < 1) {
            throw new IllegalArgumentException("minHistory must be >= 1, got: " + minHistory);
        }
        if (Double.isNaN(anomalyDeviationThreshold)
                || Double.isInfinite(anomalyDeviationThreshold)
                || anomalyDeviationThreshold < 0.0) {
            throw new IllegalArgumentException(
                    "anomalyDeviationThreshold must be a finite non-negative number, got: "
                            + anomalyDeviationThreshold);
        }
    }

    /** A behaviour-preserving default: 5 readings of history, 3-sigma deviation threshold. */
    public static final PollutionMonitorConfig DEFAULT = new PollutionMonitorConfig(5, 3.0);
}
