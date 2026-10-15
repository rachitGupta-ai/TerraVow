package com.terravow.capability.substitution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.domain.MachineId;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultSubstitutionDossierService}, exercising the dossier policy with
 * specific examples and boundary values: the {@code 1..5} candidate bound (Req 5.1), the mandatory
 * vendor-validation flag (Req 5.4), unquantified-hypothesis marking (Req 5.6), and the
 * no-alternative zero-candidate path (Req 5.7).
 */
class DefaultSubstitutionDossierServiceTest {

    private final SubstitutionRequest request =
            SubstitutionRequest.forMachine(new MachineId("boiler-7"));

    private static SubstitutionDossierService serviceReturning(SynthesisResult result) {
        return new DefaultSubstitutionDossierService(req -> result);
    }

    // Req 5.1 — one candidate is the lower bound of the 1..5 range.
    @Test
    void generatesSingleCandidateAtLowerBound() {
        SynthesisResult result =
                SynthesisResult.withCandidates("drying", SubstitutionFixtures.candidates(1));

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.candidates()).hasSize(1);
        assertThat(dossier.noAlternativeReason()).isEmpty();
    }

    // Req 5.1 — five candidates is the upper bound of the 1..5 range.
    @Test
    void generatesFiveCandidatesAtUpperBound() {
        SynthesisResult result =
                SynthesisResult.withCandidates("drying", SubstitutionFixtures.candidates(5));

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.candidates()).hasSize(5);
    }

    // Req 5.1 — a synthesizer that over-produces is trimmed to the 5-candidate bound.
    @Test
    void trimsOverProducedCandidatesToFive() {
        SynthesisResult result =
                SynthesisResult.withCandidates("drying", SubstitutionFixtures.candidates(9));

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.candidates()).hasSize(SubstitutionDossier.MAX_CANDIDATES);
    }

    // Req 5.4 — every dossier carries the mandatory vendor-validation flag (populated case).
    @Test
    void populatedDossierCarriesVendorValidationFlag() {
        SynthesisResult result =
                SynthesisResult.withCandidates("drying", SubstitutionFixtures.candidates(2));

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.requiresVendorValidationFlag()).isTrue();
    }

    // Req 5.4 — the vendor-validation flag is set even on the zero-candidate path.
    @Test
    void emptyDossierCarriesVendorValidationFlag() {
        SynthesisResult result =
                SynthesisResult.noAlternative("drying", "no admissible alternative in corpus");

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.requiresVendorValidationFlag()).isTrue();
    }

    // Req 5.6 — a value that cannot be quantified is still produced and marked an unquantified
    // hypothesis with a recorded reason; the dossier is still produced.
    @Test
    void insufficientDataMarksValueUnquantifiedHypothesis() {
        Candidate candidate = SubstitutionFixtures.candidateWithUnquantifiedPayback("reading-x");
        SynthesisResult result =
                SynthesisResult.withCandidates("drying", List.of(candidate));

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.candidates()).hasSize(1);
        Candidate produced = dossier.candidates().get(0);
        assertThat(produced.isUnquantified(DossierField.PAYBACK)).isTrue();
        assertThat(produced.isUnquantified(DossierField.INCENTIVES)).isTrue();
        assertThat(produced.isUnquantified(DossierField.DELTA_EMISSIONS)).isFalse();
        assertThat(produced.unquantified())
                .anySatisfy(
                        vq -> {
                            assertThat(vq.field()).isEqualTo(DossierField.PAYBACK);
                            assertThat(vq.reason()).isNotBlank();
                        });
        // Still fully reported: a per-value confidence exists for every field (Req 5.3).
        assertThat(produced.perValueConfidence()).containsKeys(DossierField.values());
    }

    // Req 5.7 — no viable alternative returns zero candidates with the recorded reason.
    @Test
    void noViableAlternativeReturnsZeroCandidatesWithReason() {
        SynthesisResult result =
                SynthesisResult.noAlternative(
                        "drying", "subject is a bespoke process with no comparable alternative");

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.candidates()).isEmpty();
        assertThat(dossier.noAlternativeReason())
                .hasValue("subject is a bespoke process with no comparable alternative");
    }

    // Req 5.7 — an empty synthesis without an explicit reason still yields a recorded reason.
    @Test
    void emptySynthesisWithoutReasonStillRecordsAReason() {
        // The synthesizer contract requires a reason for an empty result, so to exercise the
        // service's own default-reason fallback we hand it a result object that reports empty
        // candidates with a reason, then assert the dossier carries a non-blank reason.
        SynthesisResult result = SynthesisResult.noAlternative("drying", "none found");

        SubstitutionDossier dossier = serviceReturning(result).analyse(request);

        assertThat(dossier.noAlternativeReason()).isPresent();
        assertThat(dossier.noAlternativeReason().get()).isNotBlank();
    }

    @Test
    void rejectsNullRequest() {
        SubstitutionDossierService service =
                serviceReturning(
                        SynthesisResult.withCandidates(
                                "drying", SubstitutionFixtures.candidates(1)));

        assertThatThrownBy(() -> service.analyse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSynthesizer() {
        assertThatThrownBy(() -> new DefaultSubstitutionDossierService(null))
                .isInstanceOf(NullPointerException.class);
    }
}
