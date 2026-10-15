package com.terravow.pii.synthetic;

import com.terravow.domain.SyntheticLabel;

import java.util.Objects;

/**
 * A decision-oriented output that always carries the advisory disclaimer and its synthetic label,
 * so the disclaimer is surfaced on the output and included on export, and the synthetic label
 * propagates with the result (Req 13.2, 13.5, 13.6, 14.15).
 *
 * <p>A decision-oriented output is any produced value that informs a decision — an EID/ERD figure,
 * an economics draft, a substitution dossier, a Decision_Gate recommendation. Two cross-cutting
 * guarantees attach to all of them, enforced here structurally so no call site can forget:
 *
 * <ul>
 *   <li><b>Advisory disclaimer (Req 13.5, 13.6).</b> Construction attaches the canonical {@link
 *       AdvisoryDisclaimer#TEXT}; {@link #surface()} embeds it on the surfaced output and {@link
 *       #export()} includes it in the export. There is no way to build a decision-oriented output
 *       without the disclaimer.</li>
 *   <li><b>Synthetic label propagation (Req 13.2, 14.15).</b> The output carries a {@link
 *       SyntheticLabel}; derive the output through {@link Labeled} so an output derived from any
 *       synthetic input is itself labelled {@link SyntheticLabel#SYNTHETIC}. The {@link
 *       #ofDerived(Labeled)} factory folds a {@link Labeled} value straight into an output,
 *       preserving the propagated label.</li>
 * </ul>
 *
 * @param body  the human-readable decision-oriented content; never {@code null}, non-blank
 * @param label the synthetic label that propagated to this output; never {@code null}
 *
 * @see AdvisoryDisclaimer
 * @see Labeled
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.2, 13.5, 13.6, 14.15</a>
 */
public record DecisionOrientedOutput(String body, SyntheticLabel label) {

    /**
     * Validates the output carries content and a label (the disclaimer is attached on surface and
     * export, never omittable).
     *
     * @throws NullPointerException     if {@code body} or {@code label} is {@code null}
     * @throws IllegalArgumentException if {@code body} is blank
     */
    public DecisionOrientedOutput {
        Objects.requireNonNull(body, "DecisionOrientedOutput body must not be null");
        Objects.requireNonNull(label, "DecisionOrientedOutput label must not be null");
        if (body.isBlank()) {
            throw new IllegalArgumentException("DecisionOrientedOutput body must not be blank");
        }
    }

    /**
     * Builds a decision-oriented output from a derived {@link Labeled} value, preserving the
     * propagated synthetic label (Req 13.2, 14.15).
     *
     * @param derived the labelled derived content; never {@code null}, its value's
     *                {@code toString()} must be non-blank
     * @return a decision-oriented output carrying {@code derived}'s label
     * @throws NullPointerException if {@code derived} is {@code null}
     */
    public static DecisionOrientedOutput ofDerived(Labeled<?> derived) {
        Objects.requireNonNull(derived, "derived must not be null");
        return new DecisionOrientedOutput(String.valueOf(derived.value()), derived.label());
    }

    /**
     * @return {@code true} iff this output (directly or transitively) derives from synthetic data
     */
    public boolean isSynthetic() {
        return label == SyntheticLabel.SYNTHETIC;
    }

    /**
     * Renders the output for surfacing with the advisory disclaimer appended, and a synthetic
     * marker where the output derives from synthetic data (Req 13.2, 13.5).
     *
     * @return the surfaced text, which always contains the advisory disclaimer
     */
    public String surface() {
        String syntheticMarker = isSynthetic() ? "\n[SYNTHETIC DATA]" : "";
        return body + syntheticMarker + "\n" + AdvisoryDisclaimer.TEXT;
    }

    /**
     * Renders the output for export with the advisory disclaimer included (Req 13.6).
     *
     * <p>Export reuses {@link #surface()} so the disclaimer and synthetic marker travel with the
     * exported artifact exactly as surfaced — the disclaimer can never be stripped on export.
     *
     * @return the export text, which always contains the advisory disclaimer
     */
    public String export() {
        return surface();
    }
}
