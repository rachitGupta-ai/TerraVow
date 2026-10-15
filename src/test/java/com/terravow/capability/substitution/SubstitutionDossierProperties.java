package com.terravow.capability.substitution;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.MachineId;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for the {@link DefaultSubstitutionDossierService} dossier invariants.
 *
 * <p>Validates Correctness Property 17 (candidate count bounded to {@code 1..5} when a viable
 * alternative exists, Req 5.1), Property 18 (every candidate reports all eight values each with a
 * per-value confidence, Req 5.2, 5.3), and Property 19 (every dossier carries the mandatory
 * vendor-validation flag, Req 5.4).
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.1, 5.2, 5.3,
 *     5.4</a>
 */
class SubstitutionDossierProperties {

    private final SubstitutionRequest request =
            SubstitutionRequest.forMachine(new MachineId("asset-1"));

    private static SubstitutionDossierService serviceReturning(SynthesisResult result) {
        return new DefaultSubstitutionDossierService(req -> result);
    }

    // Feature: terravow, Property 17: Substitution candidate count is bounded.
    @Property(tries = 100)
    void candidateCountIsBetweenOneAndFiveWhenAlternativesExist(
            @ForAll @IntRange(min = 1, max = 20) int synthesisedCount) {
        SynthesisResult result =
                SynthesisResult.withCandidates(
                        "role", SubstitutionFixtures.candidates(synthesisedCount));

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.candidates().size())
                .isBetween(1, SubstitutionDossier.MAX_CANDIDATES);
        // When at least one alternative was synthesised, the dossier is non-empty and carries no
        // no-alternative reason (Req 5.1 vs 5.7).
        assertThat(dossier.candidates()).isNotEmpty();
        assertThat(dossier.noAlternativeReason()).isEmpty();
    }

    // Feature: terravow, Property 18: Each substitution candidate is complete.
    @Property(tries = 100)
    void everyCandidateReportsAllValuesEachWithConfidence(
            @ForAll @IntRange(min = 1, max = 5) int count,
            @ForAll("confidence") double confidence) {
        List<Candidate> synthesised =
                SubstitutionFixtures.candidatesWithConfidence(count, confidence);
        SubstitutionDossier dossier =
                serviceReturning(SynthesisResult.withCandidates("role", synthesised))
                        .analyse(request);

        for (Candidate candidate : dossier.candidates()) {
            // All eight signed/structured reported values are present and non-null (Req 5.2).
            assertThat(candidate.deltaEmissions()).isNotNull();
            assertThat(candidate.deltaEnergy()).isNotNull();
            assertThat(candidate.deltaCost()).isNotNull();
            assertThat(candidate.qualityRisk()).isNotNull();
            assertThat(candidate.incentives()).isNotNull();
            assertThat(candidate.payback()).isNotNull();
            assertThat(candidate.admissibility()).isNotNull();
            assertThat(candidate.ripple()).isNotNull();
            // A per-value confidence exists for every reported field (Req 5.3).
            assertThat(candidate.perValueConfidence()).containsOnlyKeys(DossierField.values());
            // Reasoning chain and Evidence_Chain support the reported values (Req 5.5).
            assertThat(candidate.reasoning().steps()).isNotEmpty();
            assertThat(candidate.evidence().links()).isNotEmpty();
        }
    }

    // Feature: terravow, Property 19: Every dossier carries the vendor-validation flag.
    @Property(tries = 100)
    void everyDossierCarriesTheVendorValidationFlag(
            @ForAll @IntRange(min = 0, max = 7) int synthesisedCount) {
        SynthesisResult result =
                synthesisedCount == 0
                        ? SynthesisResult.noAlternative("role", "no viable alternative")
                        : SynthesisResult.withCandidates(
                                "role", SubstitutionFixtures.candidates(synthesisedCount));

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        // The mandatory flag is set regardless of whether candidates were generated (Req 5.4).
        assertThat(dossier.requiresVendorValidationFlag()).isTrue();
    }

    /** Confidence values across the closed unit interval {@code [0.0, 1.0]}. */
    @Provide
    Arbitrary<Double> confidence() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }
}
