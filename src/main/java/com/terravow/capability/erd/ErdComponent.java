package com.terravow.capability.erd;

import com.terravow.domain.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * A single component of an Environmental Regulatory Debt (ERD) value (Req 2.2, 2.4, 2.6).
 *
 * <p>Each component is one of the five {@link ErdComponentType}s. An {@link ComponentStatus#AVAILABLE}
 * component carries a monetary {@link Money} amount and contributes to the ERD sum (Req 2.2); an
 * {@link ComponentStatus#UNAVAILABLE} component carries a recorded reason, no amount, and is excluded
 * from the sum (Req 2.6). The source instrument or computed input each component links to is recorded
 * in the enclosing {@link ErdResult}'s Evidence_Chain (Req 2.4).
 *
 * <p>The record makes an inconsistent component unrepresentable: the compact constructor requires an
 * amount present exactly when {@code status == AVAILABLE} and a reason present exactly when
 * {@code status == UNAVAILABLE}, and rejects a non-finite / wrong-shape value.
 *
 * @param type   the component this represents; never {@code null}
 * @param amount the monetary amount, present iff {@code status == AVAILABLE} (Req 2.2)
 * @param status whether the component is available or unavailable; never {@code null}
 * @param reason the reason the component is unavailable, present iff {@code status == UNAVAILABLE}
 *               (Req 2.6)
 *
 * @see ErdResult
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 2.4, 2.6</a>
 */
public record ErdComponent(
        ErdComponentType type,
        Optional<Money> amount,
        ComponentStatus status,
        Optional<String> reason) {

    /**
     * Validates presence of all references and the status/amount/reason consistency invariant
     * (Req 2.2, 2.6).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the status is inconsistent with the presence of
     *                                  amount/reason, or an UNAVAILABLE reason is blank
     */
    public ErdComponent {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        if (status == ComponentStatus.AVAILABLE) {
            if (amount.isEmpty()) {
                throw new IllegalArgumentException(
                        "AVAILABLE ERD component must carry a monetary amount (Req 2.2)");
            }
            if (reason.isPresent()) {
                throw new IllegalArgumentException(
                        "AVAILABLE ERD component must not carry an unavailability reason");
            }
        } else { // UNAVAILABLE
            if (amount.isPresent()) {
                throw new IllegalArgumentException(
                        "UNAVAILABLE ERD component must not carry a monetary amount (Req 2.6)");
            }
            if (reason.isEmpty() || reason.get().isBlank()) {
                throw new IllegalArgumentException(
                        "UNAVAILABLE ERD component must carry a non-blank reason (Req 2.6)");
            }
        }
    }

    /**
     * Builds an {@link ComponentStatus#AVAILABLE} component carrying a monetary amount (Req 2.2).
     *
     * @param type   the component type; never {@code null}
     * @param amount the component amount; never {@code null}
     * @return an available component that contributes to the ERD sum
     */
    public static ErdComponent available(ErdComponentType type, Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return new ErdComponent(type, Optional.of(amount), ComponentStatus.AVAILABLE, Optional.empty());
    }

    /**
     * Builds an {@link ComponentStatus#UNAVAILABLE} component carrying only a reason (Req 2.6).
     *
     * @param type   the component type; never {@code null}
     * @param reason a non-blank reason the component could not be computed
     * @return an unavailable component, excluded from the ERD sum
     */
    public static ErdComponent unavailable(ErdComponentType type, String reason) {
        return new ErdComponent(
                type, Optional.empty(), ComponentStatus.UNAVAILABLE, Optional.of(reason));
    }

    /**
     * Whether this component contributes to the ERD sum.
     *
     * @return {@code true} iff {@code status == AVAILABLE}
     */
    public boolean isAvailable() {
        return status == ComponentStatus.AVAILABLE;
    }
}
