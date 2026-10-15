package com.terravow.pii.synthetic;

/**
 * The canonical "not legal, tax, or engineering advice" advisory disclaimer that every
 * decision-oriented output must surface and every export must include (Req 13.5, 13.6).
 *
 * <p>TerraVow produces counterfactuals and legal/engineering interpretations that are cited drafts
 * for qualified professional review — never authoritative sign-off (Req 8.6, 6.8). To ensure the
 * platform is never mistaken for professional advice, every decision-oriented output surfaces this
 * disclaimer (Req 13.5) and every export of such an output includes it (Req 13.6). Keeping the
 * canonical text in one place means the wording cannot drift between surfaces and exports.
 *
 * @see DecisionOrientedOutput
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 13.5, 13.6, 6.8, 8.6</a>
 */
public final class AdvisoryDisclaimer {

    /**
     * The canonical disclaimer text. Contains the exact phrase "not legal, tax, or engineering
     * advice" and frames the output as a cited draft for qualified professional review, making no
     * claim of authoritative sign-off (Req 13.5, 13.6, 6.8, 8.6).
     */
    public static final String TEXT =
            "This output is a cited draft for qualified professional review and is "
                    + "not legal, tax, or engineering advice. It is not an authoritative "
                    + "legal, tax, or engineering sign-off.";

    private AdvisoryDisclaimer() {
        // Non-instantiable: a holder for the canonical disclaimer text.
    }

    /**
     * Reports whether some text already carries the advisory disclaimer, so a surface or export can
     * verify disclaimer presence (Req 13.5, 13.6).
     *
     * <p>Presence is detected on the canonical phrase "not legal, tax, or engineering advice" so a
     * reformatted surface that embeds the disclaimer is still recognised.
     *
     * @param text the text to inspect; may be {@code null}
     * @return {@code true} iff {@code text} contains the canonical advisory phrase
     */
    public static boolean isPresentIn(String text) {
        return text != null && text.contains("not legal, tax, or engineering advice");
    }
}
