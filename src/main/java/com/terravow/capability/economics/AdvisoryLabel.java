package com.terravow.capability.economics;

/**
 * The advisory label every Economics Engine output carries: a cited draft for qualified
 * professional review that is explicitly not legal or tax advice (Req 6.8).
 *
 * <p>The platform's economics outputs reason over real Indian regulatory and incentive instruments
 * and surface penalty exposure, incentive eligibility, net position, and replacement timing. Those
 * are interpretive drafts, never authoritative sign-off (Req 6.8, 8.6). Representing the label as a
 * dedicated value — carried on every result record this engine produces — makes the requirement
 * structural: an economics output cannot exist without it. The global "not legal, tax, or
 * engineering advice" disclaimer surfacing on export is a later cross-cutting concern (Req 13.5,
 * 13.6, task 20.2); this label satisfies the engine-local obligation (Req 6.8) and is kept
 * self-contained so the engine does not depend on that later wiring.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.8</a>
 */
public enum AdvisoryLabel {

    /** The single advisory label: a cited draft for qualified professional review, not advice. */
    CITED_DRAFT_NOT_ADVICE;

    /** The human-readable disclaimer text surfaced on every economics output (Req 6.8). */
    public static final String TEXT =
            "Cited draft for qualified professional review. This is not legal or tax advice.";

    /**
     * Returns the human-readable disclaimer text (Req 6.8).
     *
     * @return the disclaimer surfaced on every economics-engine output; never {@code null} or blank
     */
    public String text() {
        return TEXT;
    }
}
