package com.terravow.reasoning.tool;

import com.terravow.domain.Money;
import com.terravow.reasoning.Schema;
import com.terravow.reasoning.ToolDeclaration;
import com.terravow.reasoning.ToolId;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Verified tool computing an Environmental_Compensation (EC) exposure (Req 6.2).
 *
 * <p>Applies the documented formula shape {@code base rate × days of violation × deterrence factor}
 * and returns the exposure as {@link Money} in the request currency. The multiplication is carried
 * out in {@link BigDecimal} to avoid overflow and floating-point drift on the deterrence factor,
 * then rounded half-up to whole minor units.
 *
 * <p>Deterministic-first: this exact arithmetic is what the model invokes via function calling —
 * the model never free-generates the EC number. On missing/invalid inputs (null currency, negative
 * base rate, negative days, non-finite or negative deterrence) or any arithmetic error, the tool
 * returns {@link ToolResult#unavailable(String)} so the dependent value is marked unavailable.
 *
 * @see EnvironmentalCompensationRequest
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.2</a>
 */
public final class EnvironmentalCompensationTool
        implements VerifiedTool<EnvironmentalCompensationRequest, Money> {

    /** The stable identifier under which this tool is registered. */
    public static final ToolId TOOL_ID = new ToolId("ec.formula");

    private static final Schema INPUT_SCHEMA =
            new Schema("{\"baseRateMinorUnits\":\"long\",\"currency\":\"Currency\","
                    + "\"daysOfViolation\":\"long\",\"deterrenceFactor\":\"double\"}");
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
     * Computes {@code base rate × days × deterrence} as {@link Money} in the request currency.
     *
     * @param request the EC inputs; a {@code null} request yields an unavailable result
     * @return a {@code COMPUTED} result with the EC exposure, or {@code UNAVAILABLE} with a reason
     */
    @Override
    public ToolResult<Money> execute(EnvironmentalCompensationRequest request) {
        if (request == null) {
            return ToolResult.unavailable("EC request is missing");
        }
        if (request.currency() == null) {
            return ToolResult.unavailable("EC currency is missing");
        }
        if (request.baseRateMinorUnits() < 0L) {
            return ToolResult.unavailable(
                    "EC base rate must be non-negative, got: " + request.baseRateMinorUnits());
        }
        if (request.daysOfViolation() < 0L) {
            return ToolResult.unavailable(
                    "EC days of violation must be non-negative, got: " + request.daysOfViolation());
        }
        double deterrence = request.deterrenceFactor();
        if (Double.isNaN(deterrence) || Double.isInfinite(deterrence)) {
            return ToolResult.unavailable(
                    "EC deterrence factor must be finite, got: " + deterrence);
        }
        if (deterrence < 0.0) {
            return ToolResult.unavailable(
                    "EC deterrence factor must be non-negative, got: " + deterrence);
        }
        try {
            BigDecimal exposure = BigDecimal.valueOf(request.baseRateMinorUnits())
                    .multiply(BigDecimal.valueOf(request.daysOfViolation()))
                    .multiply(BigDecimal.valueOf(deterrence))
                    .setScale(0, RoundingMode.HALF_UP);
            long minorUnits = toLongExact(exposure);
            return ToolResult.computed(new Money(minorUnits, request.currency()));
        } catch (ArithmeticException ex) {
            return ToolResult.unavailable("EC computation overflowed: " + ex.getMessage());
        }
    }

    /**
     * Converts a whole-valued {@link BigDecimal} to {@code long}, throwing on overflow.
     *
     * @param value a {@code BigDecimal} already rounded to scale 0
     * @return the exact {@code long}
     * @throws ArithmeticException if the value does not fit in a {@code long}
     */
    private static long toLongExact(BigDecimal value) {
        BigInteger integer = value.toBigIntegerExact();
        return integer.longValueExact();
    }
}
