package com.terravow.pii.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.SyntheticLabel;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests that every decision-oriented output carries the advisory disclaimer on both
 * its surfaced form and its exported form (Req 6.8, 8.6, 13.5, 13.6).
 *
 * <p>Validates Correctness Property 39: for any decision-oriented output, built with any body and
 * any synthetic label and whether derived from real or synthetic inputs, {@link
 * DecisionOrientedOutput#surface()} and {@link DecisionOrientedOutput#export()} both contain the
 * canonical "not legal, tax, or engineering advice" {@link AdvisoryDisclaimer} and make no claim of
 * authoritative sign-off. The disclaimer is attached structurally at render time, so it can never
 * be omitted on surface or stripped on export.
 *
 * @see DecisionOrientedOutput
 * @see AdvisoryDisclaimer
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.8, 8.6, 13.5, 13.6</a>
 */
class AdvisoryDisclaimerPresenceProperties {

    // Feature: terravow, Property 39: Decision-oriented outputs carry the advisory disclaimer.
    //
    // For any directly-constructed decision-oriented output, both the surfaced and exported forms
    // contain the canonical advisory phrase and preserve the body.
    @Property(tries = 100)
    void surfaceAndExportCarryDisclaimerForAnyOutput(
            @ForAll("body") String body, @ForAll("label") SyntheticLabel label) {
        DecisionOrientedOutput output = new DecisionOrientedOutput(body, label);

        String surfaced = output.surface();
        String exported = output.export();

        assertThat(AdvisoryDisclaimer.isPresentIn(surfaced)).isTrue();
        assertThat(AdvisoryDisclaimer.isPresentIn(exported)).isTrue();
        assertThat(surfaced).contains("not legal, tax, or engineering advice");
        assertThat(exported).contains("not legal, tax, or engineering advice");
        // The body is preserved; the disclaimer augments rather than replaces the content.
        assertThat(surfaced).contains(body);
        assertThat(exported).contains(body);
    }

    // Feature: terravow, Property 39: Decision-oriented outputs carry the advisory disclaimer.
    //
    // An output derived through the Labeled carrier — from either a real or a synthetic value —
    // still carries the disclaimer on both forms, so derivation never drops it.
    @Property(tries = 100)
    void derivedOutputCarriesDisclaimerRegardlessOfProvenance(
            @ForAll("body") String body, @ForAll("label") SyntheticLabel label) {
        Labeled<String> derived = new Labeled<>(body, label);

        DecisionOrientedOutput output = DecisionOrientedOutput.ofDerived(derived);

        assertThat(AdvisoryDisclaimer.isPresentIn(output.surface())).isTrue();
        assertThat(AdvisoryDisclaimer.isPresentIn(output.export())).isTrue();
    }

    // Feature: terravow, Property 39: Decision-oriented outputs carry the advisory disclaimer.
    //
    // The disclaimer makes no claim of authoritative sign-off and frames the output as a cited draft
    // for qualified professional review (Req 6.8, 8.6), on every output's surfaced form.
    @Property(tries = 100)
    void disclaimerNeverClaimsAuthoritativeSignOff(
            @ForAll("body") String body, @ForAll("label") SyntheticLabel label) {
        DecisionOrientedOutput output = new DecisionOrientedOutput(body, label);

        String surfaced = output.surface();

        assertThat(surfaced).contains("not an authoritative");
        assertThat(surfaced).contains("qualified professional review");
    }

    // Feature: terravow, Property 39: Decision-oriented outputs carry the advisory disclaimer.
    //
    // The disclaimer travels identically with surface and export: export reuses the surfaced form,
    // so the disclaimer present on surface is exactly the one that reaches the export boundary.
    @Property(tries = 100)
    void exportMatchesSurfaceSoDisclaimerIsNeverStripped(
            @ForAll("body") String body, @ForAll("label") SyntheticLabel label) {
        DecisionOrientedOutput output = new DecisionOrientedOutput(body, label);

        assertThat(output.export()).isEqualTo(output.surface());
    }

    /** Non-blank decision-oriented bodies, resembling figures/drafts the platform surfaces. */
    @Provide
    Arbitrary<String> body() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', ':', ',', '+', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                .ofMinLength(1)
                .ofMaxLength(60)
                .filter(s -> !s.isBlank());
    }

    /** Either synthetic provenance, with equal frequency. */
    @Provide
    Arbitrary<SyntheticLabel> label() {
        return Arbitraries.of(SyntheticLabel.REAL, SyntheticLabel.SYNTHETIC);
    }
}
