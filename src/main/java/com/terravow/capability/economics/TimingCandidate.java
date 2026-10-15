package com.terravow.capability.economics;

import com.terravow.domain.Money;

import java.util.Objects;

/**
 * A candidate replacement timing with its net-position value over the evaluated horizon (Req 6.6).
 *
 * <p>The Economics Engine recommends the replacement timing that maximises the net-position value
 * (Req 6.6). Each candidate pairs an {@link #offsetDays()} (how many days from the horizon start the
 * replacement would occur) with the {@link #netPosition()} value the net-position identity yields at
 * that timing. The net position is a verified-tool output (Req 6.5), so the candidate carries the
 * already-computed value rather than re-deriving it.
 *
 * @param offsetDays  the replacement offset from the horizon start, in days; must be {@code >= 0}
 * @param netPosition the net-position value at this timing (Req 6.5); never {@code null}
 *
 * @see TimingRecommendation
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.6</a>
 */
public record TimingCandidate(long offsetDays, Money netPosition) {

    /**
     * Validates a non-negative offset and the presence of a net-position value (Req 6.6).
     *
     * @throws NullPointerException     if {@code netPosition} is {@code null}
     * @throws IllegalArgumentException if {@code offsetDays} is negative
     */
    public TimingCandidate {
        Objects.requireNonNull(netPosition, "netPosition must not be null");
        if (offsetDays < 0L) {
            throw new IllegalArgumentException(
                    "offsetDays must be non-negative, got: " + offsetDays);
        }
    }
}
