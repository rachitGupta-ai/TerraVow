package com.terravow.capability.passport;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;

/**
 * The cascade-risk report produced when a degraded condition at an upstream tier is propagated to
 * each dependent downstream tier (Req 7.6).
 *
 * <p>The report contains one {@link Entry} for <em>every</em> tier that transitively depends on the
 * degraded upstream tier, each naming the identified tier and the cascade-risk attributed to it
 * (Req 7.6).
 *
 * @param origin   the upstream tier the degraded condition was detected at; never {@code null}
 * @param entries  one entry per dependent downstream tier; never {@code null}, defensively copied,
 *                may be empty when nothing depends on the origin
 * @param confidence the confidence attached to the report; never {@code null}
 * @param evidence the Evidence_Chain supporting the report; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.6</a>
 */
public record CascadeRisk(
        TierId origin, List<Entry> entries, Confidence confidence, EvidenceChain evidence) {

    /**
     * One dependent downstream tier and the cascade-risk attributed to it (Req 7.6).
     *
     * @param tier          the identified dependent tier; never {@code null}
     * @param attributedRisk the cascade-risk attributed to the tier, in {@code [0,1]}
     */
    public record Entry(TierId tier, double attributedRisk) {

        /**
         * Validates the tier reference and the {@code [0,1]} attributed-risk bound (Req 7.6).
         *
         * @throws NullPointerException     if {@code tier} is {@code null}
         * @throws IllegalArgumentException if {@code attributedRisk} is non-finite or outside
         *                                  {@code [0,1]}
         */
        public Entry {
            Objects.requireNonNull(tier, "tier must not be null");
            if (Double.isNaN(attributedRisk) || Double.isInfinite(attributedRisk)) {
                throw new IllegalArgumentException(
                        "attributedRisk must be a finite number, got: " + attributedRisk);
            }
            if (attributedRisk < 0.0 || attributedRisk > 1.0) {
                throw new IllegalArgumentException(
                        "attributedRisk must be within [0.0, 1.0], got: " + attributedRisk);
            }
        }
    }

    /**
     * Validates presence of all references and defensively copies the entries (Req 7.6).
     *
     * @throws NullPointerException if any argument is {@code null} or {@code entries} contains a
     *                              {@code null} element
     */
    public CascadeRisk {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        entries = List.copyOf(entries);
    }
}
