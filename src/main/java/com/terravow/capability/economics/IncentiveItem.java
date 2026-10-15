package com.terravow.capability.economics;

import com.terravow.domain.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * The eligibility assessment of a single incentive category for an asset context (Req 6.4).
 *
 * <p>Each of the four incentive categories (Req 6.4) is assessed into one item recording whether the
 * asset is {@link #eligible()} and, when eligible, the monetary {@link #value()} of the incentive.
 * An ineligible item carries no value but a non-blank {@link #reason()} explaining why, so the
 * assessment is auditable. An eligible item carries a present value in the assessment currency.
 *
 * <p>{@link IncentiveKind#AVOIDED_EC} is modelled here too so avoided Environmental_Compensation
 * exposure is assessed alongside the other incentives (Req 6.4); the Economics Engine passes the
 * avoided-EC value separately to the net-position tool (Req 6.5), so this item documents the
 * category without being double-counted in the incentive subtotal.
 *
 * @param kind     the incentive category; never {@code null}
 * @param eligible whether the asset is eligible for this incentive
 * @param value    the incentive value, present iff {@code eligible}
 * @param reason   a non-blank explanation when not eligible; present iff {@code !eligible}
 *
 * @see IncentiveAssessment
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.4</a>
 */
public record IncentiveItem(
        IncentiveKind kind, boolean eligible, Optional<Money> value, Optional<String> reason) {

    /**
     * Validates the eligibility/value/reason consistency invariant (Req 6.4).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if an eligible item lacks a value or carries a reason, or an
     *                                  ineligible item carries a value or lacks a non-blank reason
     */
    public IncentiveItem {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (eligible) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException(
                        "eligible incentive " + kind + " must carry a value");
            }
            if (reason.isPresent()) {
                throw new IllegalArgumentException(
                        "eligible incentive " + kind + " must not carry a reason");
            }
        } else {
            if (value.isPresent()) {
                throw new IllegalArgumentException(
                        "ineligible incentive " + kind + " must not carry a value");
            }
            if (reason.isEmpty() || reason.get().isBlank()) {
                throw new IllegalArgumentException(
                        "ineligible incentive " + kind + " must carry a non-blank reason");
            }
        }
    }

    /**
     * Builds an eligible item carrying the incentive value.
     *
     * @param kind  the incentive category; never {@code null}
     * @param value the incentive value; never {@code null}
     * @return an eligible item
     */
    public static IncentiveItem eligible(IncentiveKind kind, Money value) {
        Objects.requireNonNull(value, "value must not be null");
        return new IncentiveItem(kind, true, Optional.of(value), Optional.empty());
    }

    /**
     * Builds an ineligible item carrying the reason.
     *
     * @param kind   the incentive category; never {@code null}
     * @param reason a non-blank explanation of ineligibility
     * @return an ineligible item
     */
    public static IncentiveItem ineligible(IncentiveKind kind, String reason) {
        return new IncentiveItem(kind, false, Optional.empty(), Optional.of(reason));
    }
}
