package com.terravow.reasoning.tool;

import com.terravow.reasoning.Schema;
import com.terravow.reasoning.ToolDeclaration;
import com.terravow.reasoning.ToolId;

/**
 * Verified tool checking that a candidate schedule preserves total emission mass (Req 4.5).
 *
 * <p>Computes the relative deviation {@code |candidate - original| / original} and reports it
 * {@link MassPreservationResult#preserved() preserved} iff that deviation is within the requested
 * tolerance fraction (0.1 percent per Req 4.5). The comparison is {@code <=} so a deviation exactly
 * at the tolerance is preserved.
 *
 * <p>Zero original mass is handled explicitly: a zero candidate against a zero original is a zero
 * deviation (preserved); any positive candidate against a zero original is an undefined ratio and
 * is reported not preserved with a deviation of {@link Double#POSITIVE_INFINITY}.
 *
 * <p>Deterministic-first: the model invokes this verified check via function calling rather than
 * judging mass preservation itself. On missing/invalid inputs (null request, non-finite or negative
 * masses, non-finite or negative tolerance) the tool returns {@link ToolResult#unavailable(String)}.
 *
 * @see MassPreservationRequest
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 4.5, 4.6</a>
 */
public final class MassPreservationTool
        implements VerifiedTool<MassPreservationRequest, MassPreservationResult> {

    /** The stable identifier under which this tool is registered. */
    public static final ToolId TOOL_ID = new ToolId("mass.preservation.check");

    private static final Schema INPUT_SCHEMA =
            new Schema("{\"originalMass\":\"double\",\"candidateMass\":\"double\","
                    + "\"toleranceFraction\":\"double\"}");
    private static final Schema OUTPUT_SCHEMA =
            new Schema("{\"preserved\":\"boolean\",\"relativeDeviation\":\"double\","
                    + "\"toleranceFraction\":\"double\"}");
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
     * Checks whether the candidate mass is within tolerance of the original mass.
     *
     * @param request the mass-preservation inputs; a {@code null} request yields an unavailable
     *                result
     * @return a {@code COMPUTED} {@link MassPreservationResult}, or {@code UNAVAILABLE} with a
     *         reason
     */
    @Override
    public ToolResult<MassPreservationResult> execute(MassPreservationRequest request) {
        if (request == null) {
            return ToolResult.unavailable("mass-preservation request is missing");
        }
        String badMagnitude = validateMagnitude("original mass", request.originalMass());
        if (badMagnitude == null) {
            badMagnitude = validateMagnitude("candidate mass", request.candidateMass());
        }
        if (badMagnitude != null) {
            return ToolResult.unavailable(badMagnitude);
        }
        double tolerance = request.toleranceFraction();
        if (Double.isNaN(tolerance) || Double.isInfinite(tolerance)) {
            return ToolResult.unavailable("mass tolerance must be finite, got: " + tolerance);
        }
        if (tolerance < 0.0) {
            return ToolResult.unavailable("mass tolerance must be non-negative, got: " + tolerance);
        }

        double original = request.originalMass();
        double candidate = request.candidateMass();
        double absoluteDelta = Math.abs(candidate - original);
        double relativeDeviation;
        if (original == 0.0) {
            relativeDeviation = (absoluteDelta == 0.0) ? 0.0 : Double.POSITIVE_INFINITY;
        } else {
            relativeDeviation = absoluteDelta / original;
        }
        boolean preserved = relativeDeviation <= tolerance;
        return ToolResult.computed(
                new MassPreservationResult(preserved, relativeDeviation, tolerance));
    }

    /**
     * Validates a mass magnitude is finite and non-negative.
     *
     * @param label the field name for the reason message
     * @param value the magnitude
     * @return {@code null} if valid, otherwise an unavailable reason
     */
    private static String validateMagnitude(String label, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return label + " must be finite, got: " + value;
        }
        if (value < 0.0) {
            return label + " must be non-negative, got: " + value;
        }
        return null;
    }
}
