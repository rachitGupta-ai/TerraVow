package com.terravow.capability.substitution;

import com.terravow.domain.Confidence;
import com.terravow.domain.Currency;
import com.terravow.domain.Money;
import com.terravow.domain.Sign;
import com.terravow.domain.SignedValue;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared test fixtures for the substitution capability: builders for fully-quantified and
 * partially-unquantified {@link Candidate}s and the dossier invariants they must satisfy (Req 5.2,
 * 5.3, 5.5, 5.6).
 */
final class SubstitutionFixtures {

    private SubstitutionFixtures() {}

    static EvidenceChain readingChain(String id) {
        return EvidenceChain.EMPTY.with(new EvidenceLink(SourceType.READING, new SourceRef(id)));
    }

    static Map<DossierField, Confidence> uniformConfidence(double value) {
        Map<DossierField, Confidence> map = new EnumMap<>(DossierField.class);
        for (DossierField field : DossierField.values()) {
            map.put(field, new Confidence(value));
        }
        return map;
    }

    /** A candidate with every reported value quantified and no unquantified hypotheses. */
    static Candidate fullyQuantifiedCandidate(String evidenceId) {
        return new Candidate(
                new SignedValue(12.5, Sign.NEGATIVE),
                new SignedValue(8.0, Sign.NEGATIVE),
                new SignedValue(250000L, Sign.POSITIVE),
                QualityRisk.LOW,
                List.of(Incentive.quantified("PAT-ESCerts", new Money(500000L, Currency.INR))),
                PaybackPeriod.ofMonths(18),
                RegulatoryAdmissibility.ADMISSIBLE,
                new SupplyChainRipple(List.of("Tier-2 supplier lead time +3 days")),
                uniformConfidence(0.72),
                List.of(),
                new ReasoningChain(
                        List.of(
                                "Inferred role: high-temperature drying",
                                "Candidate: heat-pump dryer",
                                "Δemissions estimated from fleet-cohort baseline")),
                readingChain(evidenceId));
    }

    /**
     * A candidate with the payback and incentives fields marked unquantified hypotheses with a
     * recorded reason (Req 5.6).
     */
    static Candidate candidateWithUnquantifiedPayback(String evidenceId) {
        return new Candidate(
                new SignedValue(10.0, Sign.NEGATIVE),
                new SignedValue(5.0, Sign.NEGATIVE),
                new SignedValue(0.0, Sign.POSITIVE),
                QualityRisk.UNKNOWN,
                List.of(Incentive.unquantified("state-industrial-policy")),
                PaybackPeriod.unquantified(),
                RegulatoryAdmissibility.UNKNOWN,
                SupplyChainRipple.NONE,
                uniformConfidence(0.35),
                List.of(
                        new ValueQuality(
                                DossierField.PAYBACK,
                                "No vendor price quote available to estimate payback"),
                        new ValueQuality(
                                DossierField.INCENTIVES,
                                "State policy eligibility pending corpus retrieval")),
                new ReasoningChain(List.of("Insufficient cost data; payback left as hypothesis")),
                readingChain(evidenceId));
    }

    /** Builds {@code count} fully-quantified candidates with distinct evidence ids. */
    static List<Candidate> candidates(int count) {
        List<Candidate> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(fullyQuantifiedCandidate("reading-" + i));
        }
        return list;
    }

    /**
     * Builds {@code count} fully-quantified candidates whose every per-value confidence is set to
     * {@code confidenceValue}, for property tests over the confidence range (Req 5.3).
     */
    static List<Candidate> candidatesWithConfidence(int count, double confidenceValue) {
        List<Candidate> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Candidate base = fullyQuantifiedCandidate("reading-" + i);
            list.add(
                    new Candidate(
                            base.deltaEmissions(),
                            base.deltaEnergy(),
                            base.deltaCost(),
                            base.qualityRisk(),
                            base.incentives(),
                            base.payback(),
                            base.admissibility(),
                            base.ripple(),
                            uniformConfidence(confidenceValue),
                            base.unquantified(),
                            base.reasoning(),
                            base.evidence()));
        }
        return list;
    }
}
