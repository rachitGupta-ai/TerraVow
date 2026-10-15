package com.terravow.capability.passport;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;

/**
 * The supply-chain ripple report produced when a substitution is applied at one tier (Req 7.7).
 *
 * <p>The report contains one {@link Entry} for <em>every</em> tier connected to the applied tier,
 * each naming the connected tier and the change to its delivery requirements (Req 7.7).
 *
 * @param appliedTier the tier the substitution was applied at; never {@code null}
 * @param entries     one entry per connected tier; never {@code null}, defensively copied, may be
 *                   empty when the applied tier is unconnected
 * @param confidence  the confidence attached to the report; never {@code null}
 * @param evidence    the Evidence_Chain supporting the report; never {@code null}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.7</a>
 */
public record RippleResult(
        TierId appliedTier, List<Entry> entries, Confidence confidence, EvidenceChain evidence) {

    /**
     * One connected tier and the change to its delivery requirements (Req 7.7).
     *
     * @param tier                     the identified connected tier; never {@code null}
     * @param deliveryRequirementChange the signed fractional change to the tier's delivery
     *                                 requirements; finite
     */
    public record Entry(TierId tier, double deliveryRequirementChange) {

        /**
         * Validates the tier reference and a finite change (Req 7.7).
         *
         * @throws NullPointerException     if {@code tier} is {@code null}
         * @throws IllegalArgumentException if {@code deliveryRequirementChange} is non-finite
         */
        public Entry {
            Objects.requireNonNull(tier, "tier must not be null");
            if (Double.isNaN(deliveryRequirementChange)
                    || Double.isInfinite(deliveryRequirementChange)) {
                throw new IllegalArgumentException(
                        "deliveryRequirementChange must be a finite number, got: "
                                + deliveryRequirementChange);
            }
        }
    }

    /**
     * Validates presence of all references and defensively copies the entries (Req 7.7).
     *
     * @throws NullPointerException if any argument is {@code null} or {@code entries} contains a
     *                              {@code null} element
     */
    public RippleResult {
        Objects.requireNonNull(appliedTier, "appliedTier must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        entries = List.copyOf(entries);
    }
}
