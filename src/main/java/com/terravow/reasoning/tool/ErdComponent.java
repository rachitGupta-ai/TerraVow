package com.terravow.reasoning.tool;

import com.terravow.domain.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * A single Environmental Regulatory Debt (ERD) component for the summation tool (Req 2.2, 2.6).
 *
 * <p>ERD is the sum of its <em>available</em> components only (Req 2.2). A component that cannot be
 * computed is excluded from the sum and reported unavailable with a reason (Req 2.6). This record
 * models both states: an available component carries a monetary {@link Money} amount; an
 * unavailable component carries a reason and no amount.
 *
 * @param name   a non-blank component name (e.g. {@code "excess-energy-cost"})
 * @param amount the component amount, present iff the component is available
 * @param reason the reason the component is unavailable, present iff {@code amount} is absent
 *
 * @see ErdSummationTool
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 2.6</a>
 */
public record ErdComponent(String name, Optional<Money> amount, Optional<String> reason) {

    /**
     * Validates the name and the available/unavailable consistency invariant.
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank, or amount/reason are not mutually
     *                                  exclusive
     */
    public ErdComponent {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("ERD component name must not be blank");
        }
        if (amount.isPresent() == reason.isPresent()) {
            throw new IllegalArgumentException(
                    "ERD component carries exactly one of amount or reason, got amount="
                            + amount + ", reason=" + reason);
        }
    }

    /**
     * Builds an available component carrying a monetary amount.
     *
     * @param name   the non-blank component name
     * @param amount the component amount
     * @return an available {@code ErdComponent}
     */
    public static ErdComponent available(String name, Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return new ErdComponent(name, Optional.of(amount), Optional.empty());
    }

    /**
     * Builds an unavailable component carrying only a reason; excluded from the ERD sum.
     *
     * @param name   the non-blank component name
     * @param reason a non-blank reason the component could not be computed
     * @return an unavailable {@code ErdComponent}
     */
    public static ErdComponent unavailable(String name, String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("ERD component reason must not be blank");
        }
        return new ErdComponent(name, Optional.empty(), Optional.of(reason));
    }

    /**
     * Whether this component contributes to the ERD sum.
     *
     * @return {@code true} iff the component carries an amount
     */
    public boolean isAvailable() {
        return amount.isPresent();
    }
}
