package com.terravow.reasoning.tool;

import com.terravow.domain.Money;
import com.terravow.reasoning.Schema;
import com.terravow.reasoning.ToolDeclaration;
import com.terravow.reasoning.ToolId;

/**
 * Verified tool summing the available Environmental Regulatory Debt (ERD) components (Req 2.2).
 *
 * <p>ERD equals the sum of its available components only (Req 2.2); unavailable components are
 * excluded from the sum (Req 2.6). The tool returns the total as a single {@link Money} in the
 * request's declared currency. A sum over zero available components is {@code Money.zero} in the
 * declared currency — a valid, computed total.
 *
 * <p>Deterministic-first: the model invokes this verified summation via function calling rather
 * than free-generating the total. On missing/invalid inputs (null request, null currency, null
 * component list, a currency-mismatched available component) or arithmetic overflow, the tool
 * returns {@link ToolResult#unavailable(String)}.
 *
 * @see ErdSummationRequest
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.1, 2.2, 2.6</a>
 */
public final class ErdSummationTool implements VerifiedTool<ErdSummationRequest, Money> {

    /** The stable identifier under which this tool is registered. */
    public static final ToolId TOOL_ID = new ToolId("erd.summation");

    private static final Schema INPUT_SCHEMA =
            new Schema("{\"currency\":\"Currency\",\"components\":"
                    + "[{\"name\":\"string\",\"amount\":\"Money?\",\"reason\":\"string?\"}]}");
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
     * Sums the available ERD components into a single {@link Money} total.
     *
     * @param request the ERD summation inputs; a {@code null} request yields an unavailable result
     * @return a {@code COMPUTED} total, or {@code UNAVAILABLE} with a reason
     */
    @Override
    public ToolResult<Money> execute(ErdSummationRequest request) {
        if (request == null) {
            return ToolResult.unavailable("ERD summation request is missing");
        }
        if (request.currency() == null) {
            return ToolResult.unavailable("ERD summation currency is missing");
        }
        if (request.components() == null) {
            return ToolResult.unavailable("ERD summation components are missing");
        }
        try {
            Money total = Money.zero(request.currency());
            for (ErdComponent component : request.components()) {
                if (!component.isAvailable()) {
                    continue;
                }
                Money amount = component.amount().orElseThrow();
                if (amount.currency() != request.currency()) {
                    return ToolResult.unavailable(
                            "ERD component '" + component.name() + "' currency " + amount.currency()
                                    + " does not match declared " + request.currency());
                }
                total = total.plus(amount);
            }
            return ToolResult.computed(total);
        } catch (ArithmeticException ex) {
            return ToolResult.unavailable("ERD summation overflowed: " + ex.getMessage());
        }
    }
}
