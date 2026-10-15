package com.terravow.capability.erd;

import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;

import java.util.List;
import java.util.Objects;

/**
 * The result of an Environmental Regulatory Debt (ERD) determination for a machine (Req 2.1–2.6).
 *
 * <p>ERD is a single monetary amount in one declared currency (Req 2.1): {@link #erd()} and
 * {@link #currency()} always agree, and {@code erd} equals the sum of the <em>available</em>
 * components (Req 2.2) — unavailable components are excluded (Req 2.6). The full component list
 * (available and unavailable) is retained so a report can show what was excluded and why. Each
 * component's source instrument or computed input is linked in the {@link #evidence()} chain
 * (Req 2.4). The {@link #confidence()} is derived from the EID confidence the ERD was computed from
 * (Req 2.5).
 *
 * <p>The record enforces its invariants in the compact constructor so an inconsistent result is
 * unrepresentable: the declared currency matches the total's currency, every available component is
 * in the declared currency, and the sum of the available components equals {@code erd}.
 *
 * @param erd        the single ERD monetary total, the sum of the available components (Req 2.1,
 *                   2.2); never {@code null}
 * @param components the full component list, available and unavailable (Req 2.2, 2.6); never
 *                   {@code null}, defensively copied, may be empty
 * @param currency   the single declared currency the ERD is reported in (Req 2.1); never
 *                   {@code null}
 * @param confidence the confidence derived from the EID confidence (Req 2.5); never {@code null}
 * @param evidence   the Evidence_Chain linking each component to its source (Req 2.4); never
 *                   {@code null}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.1–2.6</a>
 */
public record ErdResult(
        Money erd,
        List<ErdComponent> components,
        Currency currency,
        Confidence confidence,
        EvidenceChain evidence) {

    /**
     * Validates presence of all references and the single-currency and available-sum invariants
     * (Req 2.1, 2.2, 2.6).
     *
     * @throws NullPointerException     if any argument is {@code null} or a component is
     *                                  {@code null}
     * @throws IllegalArgumentException if the total currency, declared currency, or any available
     *                                  component currency disagree, or the available components do
     *                                  not sum to {@code erd}
     */
    public ErdResult {
        Objects.requireNonNull(erd, "erd must not be null");
        Objects.requireNonNull(components, "components must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        components = List.copyOf(components); // rejects null elements, yields an immutable list

        // Req 2.1 — a single declared currency; the total agrees with it.
        if (erd.currency() != currency) {
            throw new IllegalArgumentException(
                    "ERD total currency " + erd.currency() + " must match declared currency "
                            + currency + " (Req 2.1)");
        }

        // Req 2.1, 2.2 — every available component shares the declared currency and the available
        // components sum exactly to the ERD total.
        Money expected = Money.zero(currency);
        for (ErdComponent component : components) {
            if (!component.isAvailable()) {
                continue;
            }
            Money amount = component.amount().orElseThrow();
            if (amount.currency() != currency) {
                throw new IllegalArgumentException(
                        "ERD component " + component.type() + " currency " + amount.currency()
                                + " must match declared currency " + currency + " (Req 2.1)");
            }
            expected = expected.plus(amount);
        }
        if (expected.minorUnits() != erd.minorUnits()) {
            throw new IllegalArgumentException(
                    "ERD total " + erd.minorUnits() + " must equal the sum of available components "
                            + expected.minorUnits() + " (Req 2.2)");
        }
    }
}
