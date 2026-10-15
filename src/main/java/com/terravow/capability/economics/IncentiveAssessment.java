package com.terravow.capability.economics;

import com.terravow.domain.Currency;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of assessing incentive eligibility for an asset context across all four incentive
 * categories (Req 6.4).
 *
 * <p>The assessment covers PAT/ESCerts, Income Tax Act Section 32 depreciation, state
 * industrial-policy incentives, and avoided Environmental_Compensation exposure (Req 6.4). The
 * compact constructor enforces that an item is present for <em>every</em> {@link IncentiveKind}, so
 * full category coverage is structural. The {@link #totalIncentives()} subtotal — fed to the
 * net-position tool as the "total incentive value" (Req 6.5) — sums the eligible values of
 * everything <em>except</em> {@link IncentiveKind#AVOIDED_EC}, because avoided EC is supplied to the
 * net-position identity as its own term and must not be double-counted (Req 6.5).
 *
 * <p>Every assessment carries the {@link AdvisoryLabel#CITED_DRAFT_NOT_ADVICE} label (Req 6.8) and
 * an {@link EvidenceChain}.
 *
 * @param currency the single currency the incentive values are reported in; never {@code null}
 * @param items    the per-category eligibility items, one per {@link IncentiveKind}; never
 *                 {@code null}, defensively copied
 * @param evidence the Evidence_Chain supporting the eligibility conclusions (Req 6.7, 10.1); never
 *                 {@code null}
 * @param label    the advisory label (Req 6.8); never {@code null}
 *
 * @see EconomicsEngine
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.4, 6.5, 6.8</a>
 */
public record IncentiveAssessment(
        Currency currency,
        Map<IncentiveKind, IncentiveItem> items,
        EvidenceChain evidence,
        AdvisoryLabel label) {

    /**
     * Validates full category coverage, currency agreement, and reference presence (Req 6.4).
     *
     * @throws NullPointerException     if any argument is {@code null} or an item is {@code null}
     * @throws IllegalArgumentException if any {@link IncentiveKind} is missing, an item's kind does
     *                                  not match its key, or an eligible value's currency differs
     *                                  from {@code currency}
     */
    public IncentiveAssessment {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(label, "label must not be null");
        items = Map.copyOf(items);
        for (IncentiveKind kind : IncentiveKind.values()) {
            IncentiveItem item = items.get(kind);
            if (item == null) {
                throw new IllegalArgumentException(
                        "incentive assessment must cover every category; missing " + kind
                                + " (Req 6.4)");
            }
            if (item.kind() != kind) {
                throw new IllegalArgumentException(
                        "incentive item keyed under " + kind + " reports kind " + item.kind());
            }
            if (item.eligible() && item.value().orElseThrow().currency() != currency) {
                throw new IllegalArgumentException(
                        "incentive " + kind + " value currency "
                                + item.value().orElseThrow().currency()
                                + " must match assessment currency " + currency);
            }
        }
    }

    /**
     * Returns the item for a category (Req 6.4).
     *
     * @param kind the incentive category; never {@code null}
     * @return the item for the category
     */
    public IncentiveItem item(IncentiveKind kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        return items.get(kind);
    }

    /**
     * The total eligible incentive value fed to the net-position identity (Req 6.5), excluding
     * {@link IncentiveKind#AVOIDED_EC} to avoid double-counting the avoided-EC term.
     *
     * @return the summed eligible incentive values (excluding avoided EC) in {@link #currency()}
     */
    public Money totalIncentives() {
        Money total = Money.zero(currency);
        for (IncentiveKind kind : IncentiveKind.values()) {
            if (kind == IncentiveKind.AVOIDED_EC) {
                continue;
            }
            IncentiveItem item = items.get(kind);
            if (item.eligible()) {
                total = total.plus(item.value().orElseThrow());
            }
        }
        return total;
    }

    /**
     * The eligible avoided-EC value, if the avoided-EC category is eligible (Req 6.4, 6.5).
     *
     * @return the avoided-EC {@link Money}, or empty if not eligible
     */
    public Optional<Money> avoidedEc() {
        return items.get(IncentiveKind.AVOIDED_EC).value();
    }

    /**
     * Builds an assessment from the per-category items, keying them by kind (Req 6.4).
     *
     * @param currency the assessment currency; never {@code null}
     * @param items    the four per-category items; never {@code null}
     * @param evidence the Evidence_Chain; never {@code null}
     * @return a validated assessment carrying the advisory label
     */
    public static IncentiveAssessment of(
            Currency currency, List<IncentiveItem> items, EvidenceChain evidence) {
        Objects.requireNonNull(items, "items must not be null");
        Map<IncentiveKind, IncentiveItem> byKind = new EnumMap<>(IncentiveKind.class);
        for (IncentiveItem item : items) {
            Objects.requireNonNull(item, "incentive item must not be null");
            byKind.put(item.kind(), item);
        }
        return new IncentiveAssessment(
                currency, byKind, evidence, AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }
}
