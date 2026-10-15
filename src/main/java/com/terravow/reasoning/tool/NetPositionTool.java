package com.terravow.reasoning.tool;

import com.terravow.domain.Money;
import com.terravow.reasoning.Schema;
import com.terravow.reasoning.ToolDeclaration;
import com.terravow.reasoning.ToolId;

/**
 * Verified tool computing the net-position arithmetic (Req 6.5).
 *
 * <p>Net position equals {@code incentives + avoided EC − penalty} (Req 6.5), returned as a single
 * {@link Money} in the shared currency. The subtraction negates the penalty via
 * {@link Math#negateExact(long)} and the additions use {@link Money#plus(Money)}, so overflow is
 * detected and surfaced as unavailable rather than wrapping silently. The result may be negative
 * (net cost) — {@code Money} permits negative minor units.
 *
 * <p>Deterministic-first: the model invokes this verified arithmetic via function calling rather
 * than free-generating the net position. On missing/invalid inputs (null request, any null amount,
 * a currency mismatch between amounts) or arithmetic overflow, the tool returns
 * {@link ToolResult#unavailable(String)}.
 *
 * @see NetPositionRequest
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.5</a>
 */
public final class NetPositionTool implements VerifiedTool<NetPositionRequest, Money> {

    /** The stable identifier under which this tool is registered. */
    public static final ToolId TOOL_ID = new ToolId("net.position");

    private static final Schema INPUT_SCHEMA =
            new Schema("{\"incentives\":\"Money\",\"avoidedEc\":\"Money\",\"penalty\":\"Money\"}");
    private static final Schema OUTPUT_SCHEMA =
            new Schema("{\"minorUnits\":\"long\",\"currency\":\"Currency\"}");
    private static final ToolDeclaration DECLARATION =
            new ToolDeclaration(TOOL_ID, INPUT_SCHEMA, OUTPUT_SCHEMA);

    @Override
    public ToolId id() {
        return TOOL_ID;
    }

    @Override
    public ToolDeclaration declaration() {
        return DECLARATION;
    }

    /**
     * Computes {@code incentives + avoided EC − penalty} as {@link Money}.
     *
     * @param request the net-position inputs; a {@code null} request yields an unavailable result
     * @return a {@code COMPUTED} net position, or {@code UNAVAILABLE} with a reason
     */
    @Override
    public ToolResult<Money> execute(NetPositionRequest request) {
        if (request == null) {
            return ToolResult.unavailable("net-position request is missing");
        }
        if (request.incentives() == null) {
            return ToolResult.unavailable("net-position incentives amount is missing");
        }
        if (request.avoidedEc() == null) {
            return ToolResult.unavailable("net-position avoided-EC amount is missing");
        }
        if (request.penalty() == null) {
            return ToolResult.unavailable("net-position penalty amount is missing");
        }
        Money incentives = request.incentives();
        Money avoidedEc = request.avoidedEc();
        Money penalty = request.penalty();
        if (incentives.currency() != avoidedEc.currency()
                || incentives.currency() != penalty.currency()) {
            return ToolResult.unavailable(
                    "net-position amounts span differing currencies: incentives="
                            + incentives.currency() + ", avoidedEc=" + avoidedEc.currency()
                            + ", penalty=" + penalty.currency());
        }
        try {
            Money negatedPenalty =
                    new Money(Math.negateExact(penalty.minorUnits()), penalty.currency());
            Money net = incentives.plus(avoidedEc).plus(negatedPenalty);
            return ToolResult.computed(net);
        } catch (ArithmeticException ex) {
            return ToolResult.unavailable("net-position computation overflowed: " + ex.getMessage());
        }
    }
}
