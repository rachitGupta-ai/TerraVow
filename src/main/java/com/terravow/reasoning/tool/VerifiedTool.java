package com.terravow.reasoning.tool;

import com.terravow.reasoning.ToolDeclaration;
import com.terravow.reasoning.ToolId;

/**
 * A pure, deterministic computation TerraVow executes on behalf of the reasoning layer via Gemini
 * function calling (Req 2.2, 4.5, 6.2, 6.5).
 *
 * <p>The model orchestrates by returning a structured call naming this tool's {@link ToolId}; the
 * verified tool performs the actual arithmetic and returns the number. The tool is a pure function
 * of its request — same request always yields the same {@link ToolResult} — and it never throws
 * across its boundary: a tool-execution failure (missing or invalid inputs, or an arithmetic error)
 * is reported as an {@link com.terravow.domain.DeterminationStatus#UNAVAILABLE} result with a
 * recorded reason.
 *
 * @param <R> the typed request accepted by this tool
 * @param <V> the type of the computed value
 *
 * @see VerifiedToolRegistry
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 4.5, 6.2,
 *      6.5</a>
 */
public interface VerifiedTool<R, V> {

    /**
     * The identifier under which this tool is registered and invoked.
     *
     * @return the tool identifier; never {@code null}
     */
    ToolId id();

    /**
     * The declaration (id + input/output schemas) exposed to the model for function calling.
     *
     * @return the tool declaration; never {@code null}
     */
    ToolDeclaration declaration();

    /**
     * Executes the deterministic computation over the given request.
     *
     * <p>Implementations must never throw for bad or missing inputs or for arithmetic error;
     * instead they return {@link ToolResult#unavailable(String)} with a recorded reason.
     *
     * @param request the typed input arguments; may be {@code null}, which yields an unavailable
     *                result
     * @return a computed or unavailable result; never {@code null}
     */
    ToolResult<V> execute(R request);
}
