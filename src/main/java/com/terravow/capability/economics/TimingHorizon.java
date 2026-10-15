package com.terravow.capability.economics;

import com.terravow.domain.Currency;

import java.util.List;
import java.util.Objects;

/**
 * The evaluated timing horizon submitted to the Economics Engine's timing optimiser: the set of
 * candidate replacement timings, each with its net-position value, over which the engine recommends
 * the net-position-maximising timing (Req 6.6).
 *
 * <p>Candidate net positions are pre-computed by the verified net-position tool (Req 6.5) and
 * supplied here; the optimiser is a pure selection over them (Req 6.6). All candidate net positions
 * must share the {@link #currency()} so they are comparable, and at least one candidate must be
 * supplied so a recommendation exists.
 *
 * @param currency   the single currency all candidate net positions are expressed in; never
 *                   {@code null}
 * @param candidates the candidate timings with their net positions; never {@code null}, non-empty,
 *                   defensively copied
 *
 * @see TimingRecommendation
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.6</a>
 */
public record TimingHorizon(Currency currency, List<TimingCandidate> candidates) {

    /**
     * Validates presence, non-emptiness, and single-currency agreement of the candidates (Req 6.6).
     *
     * @throws NullPointerException     if any argument is {@code null} or a candidate is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code candidates} is empty or a candidate's net-position
     *                                  currency differs from {@code currency}
     */
    public TimingHorizon {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "timing horizon must carry at least one candidate (Req 6.6)");
        }
        for (TimingCandidate candidate : candidates) {
            if (candidate.netPosition().currency() != currency) {
                throw new IllegalArgumentException(
                        "candidate net-position currency " + candidate.netPosition().currency()
                                + " must match horizon currency " + currency + " (Req 6.6)");
            }
        }
    }
}
