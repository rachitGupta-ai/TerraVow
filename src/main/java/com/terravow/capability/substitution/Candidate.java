package com.terravow.capability.substitution;

import com.terravow.domain.Confidence;
import com.terravow.domain.SignedValue;
import com.terravow.evidence.EvidenceChain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single candidate alternative within a {@link SubstitutionDossier}.
 *
 * <p>Each candidate reports, relative to the incumbent machine or process step, a signed change in
 * emissions, a signed change in energy, and a signed change in cost, together with the
 * product-quality risk, applicable incentives, payback period, regulatory admissibility, and
 * supply-chain ripple (Req 5.2). A confidence value is attached to every one of those reported
 * values (Req 5.3), keyed by {@link DossierField}. The candidate also carries the reasoning chain
 * and Evidence_Chain supporting its reported values (Req 5.5), and the set of fields that could not
 * be quantified from the available data, each marked an unquantified hypothesis with a reason
 * (Req 5.6).
 *
 * <p>The compact constructor enforces two structural invariants so completeness is unrepresentably
 * absent: every {@link DossierField} has a per-value confidence (Req 5.3), and every unquantified
 * marker names a distinct field drawn from {@link DossierField}.
 *
 * @param deltaEmissions     signed change in emissions relative to the incumbent (Req 5.2)
 * @param deltaEnergy        signed change in energy relative to the incumbent (Req 5.2)
 * @param deltaCost          signed change in cost relative to the incumbent (Req 5.2)
 * @param qualityRisk        product-quality risk (Req 5.2)
 * @param incentives         applicable incentives; defensively copied, may be empty (Req 5.2)
 * @param payback            payback period (Req 5.2)
 * @param admissibility      regulatory admissibility (Req 5.2)
 * @param ripple             supply-chain ripple (Req 5.2)
 * @param perValueConfidence confidence attached to each reported value, keyed by field (Req 5.3)
 * @param unquantified       the fields marked unquantified hypotheses with reasons (Req 5.6)
 * @param reasoning          the reasoning chain supporting the reported values (Req 5.5)
 * @param evidence           the Evidence_Chain supporting the reported values (Req 5.5)
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.2, 5.3, 5.5,
 *     5.6</a>
 */
public record Candidate(
        SignedValue deltaEmissions,
        SignedValue deltaEnergy,
        SignedValue deltaCost,
        QualityRisk qualityRisk,
        List<Incentive> incentives,
        PaybackPeriod payback,
        RegulatoryAdmissibility admissibility,
        SupplyChainRipple ripple,
        Map<DossierField, Confidence> perValueConfidence,
        List<ValueQuality> unquantified,
        ReasoningChain reasoning,
        EvidenceChain evidence) {

    /**
     * Validates completeness and the per-value-confidence invariant (Req 5.2, 5.3, 5.5, 5.6).
     *
     * <p>Rejects any {@code null} reported value; defensively copies the collections into immutable
     * views; requires a per-value confidence for every {@link DossierField} (Req 5.3); and requires
     * every {@link ValueQuality} to name a field drawn from {@link DossierField} without duplicates
     * (Req 5.6). The Evidence_Chain must be non-empty so every candidate links its reported values
     * to primary sources (Req 5.5, 10.1).
     *
     * @throws NullPointerException     if any component or collection element is {@code null}
     * @throws IllegalArgumentException if a per-value confidence is missing for any field, if an
     *                                  unquantified marker duplicates a field, or if the evidence
     *                                  chain is empty
     */
    public Candidate {
        Objects.requireNonNull(deltaEmissions, "deltaEmissions must not be null");
        Objects.requireNonNull(deltaEnergy, "deltaEnergy must not be null");
        Objects.requireNonNull(deltaCost, "deltaCost must not be null");
        Objects.requireNonNull(qualityRisk, "qualityRisk must not be null");
        Objects.requireNonNull(incentives, "incentives must not be null");
        Objects.requireNonNull(payback, "payback must not be null");
        Objects.requireNonNull(admissibility, "admissibility must not be null");
        Objects.requireNonNull(ripple, "ripple must not be null");
        Objects.requireNonNull(perValueConfidence, "perValueConfidence must not be null");
        Objects.requireNonNull(unquantified, "unquantified must not be null");
        Objects.requireNonNull(reasoning, "reasoning must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");

        incentives = List.copyOf(incentives);
        unquantified = List.copyOf(unquantified);

        // Defensive, type-safe copy of the per-value confidence map (rejects null keys/values).
        Map<DossierField, Confidence> copiedConfidence = new EnumMap<>(DossierField.class);
        for (Map.Entry<DossierField, Confidence> entry : perValueConfidence.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "perValueConfidence must not have a null field");
            Objects.requireNonNull(
                    entry.getValue(), "perValueConfidence must not have a null confidence");
            copiedConfidence.put(entry.getKey(), entry.getValue());
        }
        // Every reported value carries a confidence (Req 5.3).
        for (DossierField field : DossierField.values()) {
            if (!copiedConfidence.containsKey(field)) {
                throw new IllegalArgumentException(
                        "Missing per-value confidence for reported field: " + field);
            }
        }
        perValueConfidence = java.util.Collections.unmodifiableMap(copiedConfidence);

        // Each unquantified marker names a distinct field (Req 5.6).
        var seenFields = java.util.EnumSet.noneOf(DossierField.class);
        for (ValueQuality quality : unquantified) {
            Objects.requireNonNull(quality, "unquantified must not contain a null entry");
            if (!seenFields.add(quality.field())) {
                throw new IllegalArgumentException(
                        "Duplicate unquantified marker for field: " + quality.field());
            }
        }

        // Every candidate links its reported values to primary sources (Req 5.5, 10.1).
        if (evidence.links().isEmpty()) {
            throw new IllegalArgumentException(
                    "Candidate evidence chain must contain at least one link (Req 5.5)");
        }
    }

    /**
     * Returns whether the given reported field is an unquantified hypothesis for this candidate
     * (Req 5.6).
     *
     * @param field the reported field to test; never {@code null}
     * @return {@code true} if the field is marked an unquantified hypothesis
     */
    public boolean isUnquantified(DossierField field) {
        Objects.requireNonNull(field, "field must not be null");
        return unquantified.stream().anyMatch(q -> q.field() == field);
    }
}
