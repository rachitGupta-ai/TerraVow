package com.terravow.capability.economics;

import com.terravow.capability.economics.grounding.CorpusGap;
import com.terravow.capability.economics.grounding.FigureKind;
import com.terravow.capability.economics.grounding.GroundedFigure;
import com.terravow.capability.economics.grounding.GroundedFigures;
import com.terravow.capability.economics.grounding.RegulatoryGrounding;
import com.terravow.capability.economics.grounding.ViolationFacts;
import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.reasoning.tool.EnvironmentalCompensationRequest;
import com.terravow.reasoning.tool.EnvironmentalCompensationTool;
import com.terravow.reasoning.tool.NetPositionRequest;
import com.terravow.reasoning.tool.NetPositionTool;
import com.terravow.reasoning.tool.ToolResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link EconomicsEngine} implementation (Req 6.2–6.6, 6.8, 6.9).
 *
 * <p>Collaborators are injected (constructor injection): the {@link RegulatoryGrounding} layer
 * resolves and cites the EC figures (Req 6.1, 6.7, 6.9); the verified
 * {@link EnvironmentalCompensationTool} and {@link NetPositionTool} perform the EC and net-position
 * arithmetic via function calling, so the engine never free-generates those numbers (Req 6.2, 6.5);
 * the {@link InstrumentConflictPort} and {@link IncentivePort} resolve the applicable instruments
 * and per-category incentive eligibility (Req 6.3, 6.4). The engine holds no mutable state and is
 * deterministic — identical inputs and collaborators yield identical outputs — so it is safe to
 * share as a singleton and testable against controlled stubs.
 *
 * <p>Every output this engine returns carries {@link AdvisoryLabel#CITED_DRAFT_NOT_ADVICE} (Req
 * 6.8). The EC currency is derived from the asset/incentive currency supplied by the caller; EC
 * grounded figures are unit-less numerics, so the engine attaches the caller's currency to the
 * verified EC amount.
 *
 * @see EconomicsEngine
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.2–6.6, 6.8, 6.9</a>
 */
public class DefaultEconomicsEngine implements EconomicsEngine {

    private final RegulatoryGrounding grounding;
    private final EnvironmentalCompensationTool ecTool;
    private final NetPositionTool netPositionTool;
    private final InstrumentConflictPort instrumentPort;
    private final IncentivePort incentivePort;

    /**
     * Creates the engine over its injected collaborators (constructor injection).
     *
     * @param grounding       the EC-figure grounding layer; never {@code null}
     * @param ecTool          the verified EC-formula tool (Req 6.2); never {@code null}
     * @param netPositionTool the verified net-position tool (Req 6.5); never {@code null}
     * @param instrumentPort  the instrument-conflict retrieval seam (Req 6.3); never {@code null}
     * @param incentivePort   the per-category incentive retrieval seam (Req 6.4); never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultEconomicsEngine(
            RegulatoryGrounding grounding,
            EnvironmentalCompensationTool ecTool,
            NetPositionTool netPositionTool,
            InstrumentConflictPort instrumentPort,
            IncentivePort incentivePort) {
        this.grounding = Objects.requireNonNull(grounding, "grounding must not be null");
        this.ecTool = Objects.requireNonNull(ecTool, "ecTool must not be null");
        this.netPositionTool = Objects.requireNonNull(netPositionTool, "netPositionTool must not be null");
        this.instrumentPort = Objects.requireNonNull(instrumentPort, "instrumentPort must not be null");
        this.incentivePort = Objects.requireNonNull(incentivePort, "incentivePort must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public EcExposure computeEnvironmentalCompensation(ViolationFacts facts) {
        return computeEnvironmentalCompensation(facts, Currency.INR);
    }

    /**
     * Computes EC exposure in a caller-chosen currency (Req 6.2, 6.9).
     *
     * <p>Resolves the four EC figures from the grounding layer; if the base rate, days, or
     * deterrence factor is a grounding gap, the exposure is reported undetermined naming the gaps —
     * the engine never invents a figure (Req 6.9). When all three numeric figures are present the
     * verified EC tool computes {@code base × days × deterrence} (Req 6.2); a tool failure surfaces
     * as unavailable. Days of violation is taken from the fact pattern (a measured operating fact);
     * the grounded days figure, when present, feeds the Evidence_Chain.
     *
     * @param facts    the violation fact pattern; never {@code null}
     * @param currency the currency the EC amount is reported in; never {@code null}
     * @return the EC exposure determination carrying the advisory label (Req 6.8)
     * @throws NullPointerException if {@code facts} or {@code currency} is {@code null}
     */
    public EcExposure computeEnvironmentalCompensation(ViolationFacts facts, Currency currency) {
        Objects.requireNonNull(facts, "facts must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        GroundedFigures figures = grounding.resolve(facts);

        // Req 6.9 — a required numeric figure the corpus could not return is a recorded gap; the
        // exposure is undetermined naming those gaps rather than inventing a figure. The guideline
        // chapter is textual context, not a formula input, so its gap alone does not block the
        // arithmetic.
        List<CorpusGap> blockingGaps = new ArrayList<>();
        for (CorpusGap gap : figures.gaps()) {
            if (gap.kind() != FigureKind.GUIDELINE_CHAPTER) {
                blockingGaps.add(gap);
            }
        }
        if (!blockingGaps.isEmpty()) {
            String undetermined = String.join(
                    ", ", blockingGaps.stream().map(g -> g.kind().name()).toList());
            return new EcExposure(
                    DeterminationStatus.UNDETERMINED,
                    Optional.empty(),
                    blockingGaps,
                    Optional.of(
                            "EC exposure undetermined: required figure(s) not located in corpus: "
                                    + undetermined + " (Req 6.9)"),
                    figures.evidence(),
                    AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        }

        long baseRate = numericOf(figures, FigureKind.BASE_RATE);
        long days = facts.daysOfViolation();
        double deterrence = doubleOf(figures, FigureKind.DETERRENCE_FACTOR);

        ToolResult<Money> result =
                ecTool.execute(
                        new EnvironmentalCompensationRequest(baseRate, currency, days, deterrence));
        if (!result.isComputed()) {
            return new EcExposure(
                    DeterminationStatus.UNAVAILABLE,
                    Optional.empty(),
                    List.of(),
                    Optional.of("EC verified tool unavailable: " + result.reason().orElseThrow()),
                    figures.evidence(),
                    AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        }
        return new EcExposure(
                DeterminationStatus.COMPUTED,
                Optional.of(result.value().orElseThrow()),
                List.of(),
                Optional.empty(),
                figures.evidence(),
                AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    /** {@inheritDoc} */
    @Override
    public InstrumentConflict reportInstrumentConflicts(ViolationFacts facts) {
        Objects.requireNonNull(facts, "facts must not be null");
        List<RegulatoryInstrument> instruments = instrumentPort.instrumentsFor(facts);
        // Req 6.3 — a conflict exists only with two or more instruments; InstrumentConflict.of
        // enumerates every one of them in the Evidence_Chain.
        return InstrumentConflict.of(instruments == null ? List.of() : instruments);
    }

    /** {@inheritDoc} */
    @Override
    public IncentiveAssessment assessIncentives(AssetContext context) {
        Objects.requireNonNull(context, "context must not be null");
        // Req 6.4 — assess every incentive category; the assessment's constructor enforces coverage.
        List<IncentiveItem> items = new ArrayList<>();
        for (IncentiveKind kind : IncentiveKind.values()) {
            IncentiveItem item = incentivePort.assess(kind, context);
            Objects.requireNonNull(item, "incentive port returned null for " + kind);
            if (item.kind() != kind) {
                throw new IllegalStateException(
                        "incentive port returned item for " + item.kind() + " when asked for " + kind);
            }
            items.add(item);
        }
        return IncentiveAssessment.of(context.currency(), items, EvidenceChain.EMPTY);
    }

    /** {@inheritDoc} */
    @Override
    public NetPosition netPosition(EcExposure penalty, IncentiveAssessment incentives) {
        Objects.requireNonNull(penalty, "penalty must not be null");
        Objects.requireNonNull(incentives, "incentives must not be null");

        EvidenceChain combined = merge(penalty.evidence(), incentives.evidence());

        // The penalty must be a computed amount to run the identity; otherwise net position is
        // unavailable (the dependent value cannot be computed).
        if (!penalty.isComputed()) {
            return new NetPosition(
                    DeterminationStatus.UNAVAILABLE,
                    Optional.empty(),
                    Optional.of(
                            "net position unavailable: EC penalty is "
                                    + penalty.status() + " ("
                                    + penalty.reason().orElse("no reason recorded") + ")"),
                    combined,
                    AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        }

        Currency currency = incentives.currency();
        Money totalIncentives = incentives.totalIncentives();
        Money avoidedEc = incentives.avoidedEc().orElse(Money.zero(currency));
        Money penaltyAmount = penalty.amount().orElseThrow();

        // Req 6.5 — net position = total incentives + avoided EC − penalty, via the verified tool.
        ToolResult<Money> result =
                netPositionTool.execute(
                        new NetPositionRequest(totalIncentives, avoidedEc, penaltyAmount));
        if (!result.isComputed()) {
            return new NetPosition(
                    DeterminationStatus.UNAVAILABLE,
                    Optional.empty(),
                    Optional.of(
                            "net-position verified tool unavailable: "
                                    + result.reason().orElseThrow()),
                    combined,
                    AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
        }
        return new NetPosition(
                DeterminationStatus.COMPUTED,
                Optional.of(result.value().orElseThrow()),
                Optional.empty(),
                combined,
                AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    /** {@inheritDoc} */
    @Override
    public TimingRecommendation optimalReplacementTiming(TimingHorizon horizon) {
        Objects.requireNonNull(horizon, "horizon must not be null");

        // Req 6.6 — recommend the candidate whose net position is >= every other candidate's. Ties
        // are broken deterministically by the earliest offset so the recommendation is stable.
        TimingCandidate best = null;
        for (TimingCandidate candidate : horizon.candidates()) {
            if (best == null) {
                best = candidate;
                continue;
            }
            long cmp = candidate.netPosition().minorUnits() - best.netPosition().minorUnits();
            if (cmp > 0L
                    || (cmp == 0L && candidate.offsetDays() < best.offsetDays())) {
                best = candidate;
            }
        }

        String basis =
                "selected offset " + best.offsetDays() + " days maximising net position at "
                        + best.netPosition().minorUnits() + " "
                        + best.netPosition().currency() + " minor units over "
                        + horizon.candidates().size() + " evaluated candidate timing(s) (Req 6.6)";
        return new TimingRecommendation(best, basis, AdvisoryLabel.CITED_DRAFT_NOT_ADVICE);
    }

    /**
     * Extracts a grounded numeric figure as a whole {@code long}, used for the EC base rate.
     *
     * @param figures the resolved figures; never {@code null}
     * @param kind    the numeric figure kind
     * @return the figure's value truncated to a whole {@code long} minor-unit base rate
     */
    private static long numericOf(GroundedFigures figures, FigureKind kind) {
        GroundedFigure figure = figures.figure(kind).orElseThrow();
        double value = figure.numericValue().orElseThrow();
        return BigDecimal.valueOf(value).longValue();
    }

    /**
     * Extracts a grounded numeric figure as a {@code double}, used for the deterrence factor.
     *
     * @param figures the resolved figures; never {@code null}
     * @param kind    the numeric figure kind
     * @return the figure's value as a {@code double}
     */
    private static double doubleOf(GroundedFigures figures, FigureKind kind) {
        return figures.figure(kind).orElseThrow().numericValue().orElseThrow();
    }

    /**
     * Merges two Evidence_Chains, preserving order and the left chain first.
     *
     * @param left  the first chain; never {@code null}
     * @param right the second chain; never {@code null}
     * @return a chain with {@code left}'s links followed by {@code right}'s
     */
    private static EvidenceChain merge(EvidenceChain left, EvidenceChain right) {
        EvidenceChain merged = left;
        for (EvidenceLink link : right.links()) {
            merged = merged.with(link);
        }
        return merged;
    }
}
