package com.terravow.reasoning;

import java.util.Objects;

/**
 * An opaque, declarative structure definition for a verified tool's input or output (Req 11.2).
 *
 * <p>A {@link ToolDeclaration} pairs an input and output {@code Schema} describing the shape of the
 * arguments a verified tool accepts and the result it returns via Gemini function calling. The
 * definition is carried as an opaque, non-blank specification string (e.g. a JSON Schema document);
 * TerraVow executes the tool and validates against this shape — the model does not free-generate
 * the result.
 *
 * <p>This differs from {@link OutputSchema}, which validates the <em>reasoning determination</em>
 * itself and is the subject of the schema-validate &rarr; retry &rarr; escalate loop (Req 11.2).
 *
 * @param definition a non-blank specification of the structure (e.g. a JSON Schema document)
 *
 * @see ToolDeclaration
 * @see OutputSchema
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.2</a>
 */
public record Schema(String definition) {

    /**
     * Validates that the structure definition is present and non-blank.
     *
     * @throws NullPointerException     if {@code definition} is {@code null}
     * @throws IllegalArgumentException if {@code definition} is blank
     */
    public Schema {
        Objects.requireNonNull(definition, "Schema definition must not be null");
        if (definition.isBlank()) {
            throw new IllegalArgumentException("Schema definition must not be blank");
        }
    }
}
