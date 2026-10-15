package com.terravow.capability.erd;

import com.terravow.domain.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * The per-operating-hour accrual rate of a single ERD component, or the reason it cannot be
 * computed (Req 2.2, 2.3, 2.6).
 *
 * <p>ERD accrues per elapsed operating hour measured from the start of the current
 * degraded-health-state period (Req 2.3). Each component contributes a monetary amount per operating
 * hour; the service multiplies the rate by the elapsed operating hours and links the result to its
 * source (Req 2.4). A component whose rate cannot be computed from the available data is reported
 * unavailable with a reason and excluded from the sum (Req 2.6).
 *
 * <p>An available rate carries a {@link Money} amount <em>per operating hour</em> and a non-blank
 * {@code source} string recording the instrument or computed input it derives from (Req 2.4); an
 * unavailable rate carries only a reason. The two states are mutually exclusive and enforced in the
 * compact constructor.
 *
 * @param type              the component this rate is for; never {@code null}
 * @param amountPerHour     the monetary amount accrued per operating hour, present iff available
 *                          (Req 2.3)
 * @param source            the source instrument/computed input the rate derives from, present iff
 *                          available (Req 2.4)
 * @param unavailableReason the reason the component cannot be computed, present iff unavailable
 *                          (Req 2.6)
 *
 * @see ErdRequest
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 2.3, 2.4,
 *      2.6</a>
 */
public record ErdComponentRate(
        ErdComponentType type,
        Optional<Money> amountPerHour,
        Optional<String> source,
        Optional<String> unavailableReason) {

    /**
     * Validates presence of all references and the available/unavailable consistency invariant.
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the available amount/source and the unavailable reason are
     *                                  not mutually exclusive, or a present source/reason is blank,
     *                                  or an available per-hour amount is negative
     */
    public ErdComponentRate {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amountPerHour, "amountPerHour must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(unavailableReason, "unavailableReason must not be null");

        boolean available = amountPerHour.isPresent();
        if (available) {
            if (unavailableReason.isPresent()) {
                throw new IllegalArgumentException(
                        "an available ERD component rate must not carry an unavailable reason");
            }
            if (source.isEmpty() || source.get().isBlank()) {
                throw new IllegalArgumentException(
                        "an available ERD component rate must carry a non-blank source (Req 2.4)");
            }
            if (amountPerHour.get().minorUnits() < 0L) {
                throw new IllegalArgumentException(
                        "ERD component per-hour amount must not be negative, got: "
                                + amountPerHour.get().minorUnits());
            }
        } else { // unavailable
            if (source.isPresent()) {
                throw new IllegalArgumentException(
                        "an unavailable ERD component rate must not carry a source");
            }
            if (unavailableReason.isEmpty() || unavailableReason.get().isBlank()) {
                throw new IllegalArgumentException(
                        "an unavailable ERD component rate must carry a non-blank reason (Req 2.6)");
            }
        }
    }

    /**
     * Builds an available per-operating-hour rate linked to its source (Req 2.3, 2.4).
     *
     * @param type          the component type; never {@code null}
     * @param amountPerHour the monetary amount accrued per operating hour; never {@code null},
     *                      non-negative
     * @param source        a non-blank source instrument/computed input identifier
     * @return an available component rate
     */
    public static ErdComponentRate available(
            ErdComponentType type, Money amountPerHour, String source) {
        Objects.requireNonNull(amountPerHour, "amountPerHour must not be null");
        return new ErdComponentRate(
                type, Optional.of(amountPerHour), Optional.of(source), Optional.empty());
    }

    /**
     * Builds an unavailable component rate carrying only a reason (Req 2.6).
     *
     * @param type   the component type; never {@code null}
     * @param reason a non-blank reason the component could not be computed
     * @return an unavailable component rate
     */
    public static ErdComponentRate unavailable(ErdComponentType type, String reason) {
        return new ErdComponentRate(type, Optional.empty(), Optional.empty(), Optional.of(reason));
    }

    /**
     * Whether this rate can contribute an accrued amount to the ERD sum.
     *
     * @return {@code true} iff a per-hour amount is present
     */
    public boolean isAvailable() {
        return amountPerHour.isPresent();
    }
}
