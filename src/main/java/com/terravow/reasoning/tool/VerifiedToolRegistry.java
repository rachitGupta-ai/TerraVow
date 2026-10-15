package com.terravow.reasoning.tool;

import com.terravow.reasoning.ToolDeclaration;
import com.terravow.reasoning.ToolId;

import java.util.List;

/**
 * Registry of the verified deterministic tools the reasoning layer invokes via Gemini function
 * calling (Req 2.2, 4.5, 6.2, 6.5).
 *
 * <p>The registry exposes the {@link ToolDeclaration}s passed to the model and executes a tool by
 * {@link ToolId} when the model returns a structured call. Because the number comes from the
 * verified tool and not from the model, this is the enforcement point for the deterministic-first
 * principle.
 *
 * <p>Execution is <em>fail-safe</em>: an unknown tool id, or a tool-execution failure (bad or
 * missing inputs, or an arithmetic error — including an uncontrolled exception escaping a tool),
 * yields an {@link com.terravow.domain.DeterminationStatus#UNAVAILABLE} result carrying a reason
 * rather than throwing. The caller marks the dependent value unavailable.
 *
 * @see VerifiedTool
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 4.5, 6.2,
 *      6.5</a>
 */
public interface VerifiedToolRegistry {

    /**
     * The declarations of all registered tools, for presentation to the model as function-calling
     * tools.
     *
     * @return an immutable list of declarations; never {@code null}
     */
    List<ToolDeclaration> declarations();

    /**
     * Whether a tool is registered under the given id.
     *
     * @param id the tool identifier; may be {@code null}
     * @return {@code true} iff a tool is registered under {@code id}
     */
    boolean isRegistered(ToolId id);

    /**
     * Executes the tool registered under {@code id} with the given request.
     *
     * <p>Never throws for an unknown id, a request of the wrong type, bad inputs, or an uncontrolled
     * exception escaping the tool: all of these yield an {@code UNAVAILABLE} result with a recorded
     * reason.
     *
     * @param id      the identifier of the tool to execute; may be {@code null}
     * @param request the tool's typed request; its runtime type must match the tool's request type
     * @return a computed or unavailable result; never {@code null}
     */
    ToolResult<?> execute(ToolId id, Object request);
}
