package com.terravow.capability.economics.grounding;

/**
 * The kinds of Environmental_Compensation (EC) figure resolved from the regulatory corpus (Req
 * 6.1).
 *
 * <p>When a violation fact pattern is submitted, the {@link RegulatoryGrounding} layer resolves the
 * applicable EC guideline chapter, base rate, days of violation, and deterrence factor from the
 * regulatory corpus (Req 6.1). These four figures are the inputs the Economics Engine's verified
 * {@code base × days × deterrence} tool consumes (Req 6.2, a separate task). Each kind that cannot
 * be located in the corpus is reported as a {@link CorpusGap} and its figure as undetermined (Req
 * 6.9).
 *
 * @see RegulatoryGrounding
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.1, 6.9</a>
 */
public enum FigureKind {
    /** The applicable per-violation-class EC guideline chapter identifier. */
    GUIDELINE_CHAPTER,
    /** The EC base rate for the violation class. */
    BASE_RATE,
    /** The number of days of violation. */
    DAYS_OF_VIOLATION,
    /** The deterrence factor applied to the EC computation. */
    DETERRENCE_FACTOR
}
