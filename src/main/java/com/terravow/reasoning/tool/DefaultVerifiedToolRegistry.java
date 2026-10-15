package com.terravow.reasoning.tool;

import com.terravow.reasoning.ToolDeclaration;
import com.terravow.reasoning.ToolId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link VerifiedToolRegistry} holding the four verified deterministic tools (Req 2.2, 4.5,
 * 6.2, 6.5): the EC formula, ERD summation, mass-preservation check, and net-position arithmetic.
 *
 * <p>Tools are supplied by constructor injection and indexed by {@link ToolId}; duplicate ids are
 * rejected at construction so dispatch is unambiguous. Execution is fail-safe — an unknown id, a
 * request whose runtime type does not match the tool, or any uncontrolled exception escaping a tool
 * is converted into an {@link com.terravow.domain.DeterminationStatus#UNAVAILABLE} result with a
 * reason, never a thrown exception.
 *
 * @see VerifiedTool
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.2, 4.5, 6.2,
 *      6.5</a>
 */
public final class DefaultVerifiedToolRegistry implements VerifiedToolRegistry {

    private final Map<ToolId, VerifiedTool<?, ?>> toolsById;

    /**
     * Constructs a registry over the given tools, indexing each by its {@link ToolId}.
     *
     * @param tools the verified tools to register; must be non-null with no null elements and no
     *              duplicate ids
     * @throws NullPointerException     if {@code tools} or any element is {@code null}
     * @throws IllegalArgumentException if two tools share a {@link ToolId}
     */
    public DefaultVerifiedToolRegistry(List<? extends VerifiedTool<?, ?>> tools) {
        Objects.requireNonNull(tools, "tools must not be null");
        Map<ToolId, VerifiedTool<?, ?>> index = new LinkedHashMap<>();
        for (VerifiedTool<?, ?> tool : tools) {
            Objects.requireNonNull(tool, "tool must not be null");
            ToolId id = Objects.requireNonNull(tool.id(), "tool id must not be null");
            VerifiedTool<?, ?> previous = index.putIfAbsent(id, tool);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate tool id: " + id.value());
            }
        }
        this.toolsById = Map.copyOf(index);
    }

    /**
     * Builds a registry pre-populated with the four standard verified tools.
     *
     * @return a registry over EC, ERD summation, mass-preservation, and net-position tools
     */
    public static DefaultVerifiedToolRegistry withStandardTools() {
        return new DefaultVerifiedToolRegistry(List.of(
                new EnvironmentalCompensationTool(),
                new ErdSummationTool(),
                new MassPreservationTool(),
                new NetPositionTool()));
    }

    @Override
    public List<ToolDeclaration> declarations() {
        return toolsById.values().stream().map(VerifiedTool::declaration).toList();
    }

    @Override
    public boolean isRegistered(ToolId id) {
        return id != null && toolsById.containsKey(id);
    }

    @Override
    public ToolResult<?> execute(ToolId id, Object request) {
        if (id == null) {
            return ToolResult.unavailable("tool id is missing");
        }
        VerifiedTool<?, ?> tool = toolsById.get(id);
        if (tool == null) {
            return ToolResult.unavailable("no verified tool registered for id: " + id.value());
        }
        try {
            return invoke(tool, request);
        } catch (ClassCastException ex) {
            return ToolResult.unavailable(
                    "request type does not match tool '" + id.value() + "': " + ex.getMessage());
        } catch (RuntimeException ex) {
            return ToolResult.unavailable(
                    "verified tool '" + id.value() + "' failed: " + ex.getMessage());
        }
    }

    /**
     * Casts the request to the tool's request type and executes it. The unchecked cast is contained
     * here; a mismatch raises {@link ClassCastException}, converted to an unavailable result by the
     * caller.
     *
     * @param tool    the tool to execute
     * @param request the raw request object
     * @param <R>     the tool's request type
     * @param <V>     the tool's value type
     * @return the tool's result
     */
    @SuppressWarnings("unchecked")
    private static <R, V> ToolResult<V> invoke(VerifiedTool<R, V> tool, Object request) {
        return tool.execute((R) request);
    }
}
