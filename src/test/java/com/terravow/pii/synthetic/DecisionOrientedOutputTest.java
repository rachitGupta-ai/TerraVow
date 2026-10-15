package com.terravow.pii.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.SyntheticLabel;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DecisionOrientedOutput} and {@link AdvisoryDisclaimer}: the "not legal, tax,
 * or engineering advice" disclaimer is present on every decision-oriented output and on export, with
 * no claim of authoritative sign-off, and the synthetic label propagates onto the output (Req 13.2,
 * 13.5, 13.6, 14.15).
 */
class DecisionOrientedOutputTest {

    // Req 13.5 — a decision-oriented output surfaces the advisory disclaimer.
    @Test
    void surfaceCarriesAdvisoryDisclaimer() {
        DecisionOrientedOutput output =
                new DecisionOrientedOutput("ERD exposure: INR 4,20,000", SyntheticLabel.REAL);

        String surfaced = output.surface();

        assertThat(surfaced).contains("ERD exposure: INR 4,20,000");
        assertThat(AdvisoryDisclaimer.isPresentIn(surfaced)).isTrue();
        assertThat(surfaced).contains("not legal, tax, or engineering advice");
    }

    // Req 13.6 — exporting a decision-oriented output includes the advisory disclaimer.
    @Test
    void exportIncludesAdvisoryDisclaimer() {
        DecisionOrientedOutput output =
                new DecisionOrientedOutput("Substitution dossier: candidate A", SyntheticLabel.REAL);

        String exported = output.export();

        assertThat(exported).contains("Substitution dossier: candidate A");
        assertThat(AdvisoryDisclaimer.isPresentIn(exported)).isTrue();
    }

    // Req 8.6, 13.5 — the disclaimer makes no claim of authoritative sign-off; it explicitly denies
    // being one.
    @Test
    void disclaimerMakesNoAuthoritativeSignOffClaim() {
        String text = AdvisoryDisclaimer.TEXT;

        assertThat(text).contains("not legal, tax, or engineering advice");
        assertThat(text).contains("not an authoritative");
        assertThat(text).contains("qualified professional review");
    }

    // Req 13.2, 14.15 — a decision-oriented output built from a synthetic-derived value carries the
    // synthetic label AND the disclaimer: propagation and disclaimer co-exist.
    @Test
    void syntheticDerivedOutputCarriesLabelAndDisclaimer() {
        Labeled<String> derived =
                Labeled.synthetic("stack.so2 reading").map(reading -> reading + " → EID draft");

        DecisionOrientedOutput output = DecisionOrientedOutput.ofDerived(derived);

        assertThat(output.isSynthetic()).isTrue();
        assertThat(output.surface()).contains("[SYNTHETIC DATA]");
        assertThat(AdvisoryDisclaimer.isPresentIn(output.surface())).isTrue();
        assertThat(AdvisoryDisclaimer.isPresentIn(output.export())).isTrue();
    }

    // Req 13.5, 13.6 — the disclaimer is present on BOTH surface and export for the same output, so
    // it can never be stripped on the export path.
    @Test
    void disclaimerPresentOnBothSurfaceAndExport() {
        DecisionOrientedOutput output =
                new DecisionOrientedOutput("Net position: +INR 1,10,000", SyntheticLabel.SYNTHETIC);

        assertThat(AdvisoryDisclaimer.isPresentIn(output.surface())).isTrue();
        assertThat(AdvisoryDisclaimer.isPresentIn(output.export())).isTrue();
    }

    // Req 13.2 — a real-derived decision output is not marked synthetic but still carries the
    // disclaimer.
    @Test
    void realDerivedOutputIsNotSyntheticButStillDisclaimed() {
        DecisionOrientedOutput output = DecisionOrientedOutput.ofDerived(Labeled.real("verdict"));

        assertThat(output.isSynthetic()).isFalse();
        assertThat(output.surface()).doesNotContain("[SYNTHETIC DATA]");
        assertThat(AdvisoryDisclaimer.isPresentIn(output.surface())).isTrue();
    }
}
