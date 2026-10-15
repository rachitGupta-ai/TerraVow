package com.terravow.capability.passport;

import java.util.Objects;

/**
 * A degraded machine condition detected at an upstream tier, with the severity that seeds
 * cascade-risk propagation (Req 7.6).
 *
 * <p>When a degraded machine condition is detected at an upstream tier, the resulting cascade-risk
 * is propagated to each dependent downstream tier (Req 7.6). The {@code severity} in {@code [0,1]} is
 * the magnitude of the degradation at the source; the attributed cascade-risk at each dependent tier
 * is derived deterministically from this severity and the tier's dependency distance.
 *
 * @param upstream the tier the degraded condition is detected at; never {@code null}
 * @param severity the degradation severity in {@code [0,1]}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.6</a>
 */
public record DegradedCondition(TierId upstream, double severity) {

    /**
     * Validates the tier reference and the {@code [0,1]} severity bound (Req 7.6).
     *
     * @throws NullPointerException     if {@code upstream} is {@code null}
     * @throws IllegalArgumentException if {@code severity} is non-finite or outside {@code [0,1]}
     */
    public DegradedCondition {
        Objects.requireNonNull(upstream, "upstream must not be null");
        if (Double.isNaN(severity) || Double.isInfinite(severity)) {
            throw new IllegalArgumentException(
                    "severity must be a finite number, got: " + severity);
        }
        if (severity < 0.0 || severity > 1.0) {
            throw new IllegalArgumentException(
                    "severity must be within [0.0, 1.0], got: " + severity);
        }
    }
}
