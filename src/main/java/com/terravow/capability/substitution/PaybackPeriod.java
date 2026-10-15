package com.terravow.capability.substitution;

import java.util.Objects;
import java.util.Optional;

/**
 * The payback period reported for a substitution {@link Candidate}.
 *
 * <p>Payback is one of the eight reported values for each candidate (Req 5.2): the time over which
 * the candidate's cost is expected to be recovered. The period is expressed in whole months when
 * quantifiable; when the available data is insufficient to quantify it, {@code months} is
 * {@link Optional#empty()} and the enclosing candidate marks the {@link DossierField#PAYBACK} field
 * an unquantified hypothesis with a recorded reason (Req 5.6).
 *
 * @param months the payback period in whole months, empty when not quantifiable; never {@code null}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.2, 5.6</a>
 */
public record PaybackPeriod(Optional<Integer> months) {

    /**
     * Validates that the optional is present (possibly empty) and any value is non-negative
     * (Req 5.2).
     *
     * @throws NullPointerException     if {@code months} is {@code null}
     * @throws IllegalArgumentException if a present value is negative
     */
    public PaybackPeriod {
        Objects.requireNonNull(months, "months must not be null");
        if (months.isPresent() && months.get() < 0) {
            throw new IllegalArgumentException(
                    "PaybackPeriod months must be non-negative, got: " + months.get());
        }
    }

    /**
     * Creates a quantified payback period.
     *
     * @param months the non-negative payback period in whole months
     * @return a quantified payback period
     */
    public static PaybackPeriod ofMonths(int months) {
        return new PaybackPeriod(Optional.of(months));
    }

    /**
     * Creates a payback period that could not be quantified from the available data (Req 5.6).
     *
     * @return a payback period with an empty value
     */
    public static PaybackPeriod unquantified() {
        return new PaybackPeriod(Optional.empty());
    }
}
