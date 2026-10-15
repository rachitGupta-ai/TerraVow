package com.terravow.reasoning;

import java.util.Map;
import java.util.Objects;

/**
 * The prompt handed to a reasoning request, carrying <strong>only masked inputs</strong> (Req 11.1,
 * 13.3).
 *
 * <p>No raw PII or confidential supplier identifier may reach the managed Agent Engine runtime, its
 * Sessions/Memory Bank, or any grounding query (Req 13.3). The {@code masked} flag asserts that the
 * deterministic masker has already run; the canonical constructor rejects a spec that is not marked
 * masked so the masking invariant cannot be bypassed at the orchestrator boundary. The actual
 * masking is performed upstream (task 20.1); this type records and enforces the contract.
 *
 * @param instruction the natural-language instruction for the specialist agent; never
 *                    {@code null}, non-blank
 * @param maskedInputs the already-masked structured inputs, keyed by name; never {@code null},
 *                    defensively copied, may be empty
 * @param masked      {@code true} asserting the inputs have passed deterministic masking; must be
 *                    {@code true}
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 11.1, 13.3</a>
 */
public record PromptSpec(String instruction, Map<String, Object> maskedInputs, boolean masked) {

    /**
     * Validates presence of the instruction and inputs, defensively copies the input map, and
     * enforces the masking invariant (Req 13.3).
     *
     * @throws NullPointerException     if {@code instruction} or {@code maskedInputs} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code instruction} is blank or {@code masked} is
     *                                  {@code false}
     */
    public PromptSpec {
        Objects.requireNonNull(instruction, "PromptSpec instruction must not be null");
        Objects.requireNonNull(maskedInputs, "PromptSpec maskedInputs must not be null");
        if (instruction.isBlank()) {
            throw new IllegalArgumentException("PromptSpec instruction must not be blank");
        }
        if (!masked) {
            throw new IllegalArgumentException(
                    "PromptSpec inputs must be masked before reaching the reasoning layer (Req 13.3)");
        }
        maskedInputs = Map.copyOf(maskedInputs); // immutable copy; rejects null keys/values
    }

    /**
     * Creates a masked prompt spec from an instruction and already-masked inputs.
     *
     * @param instruction  the agent instruction; never {@code null}, non-blank
     * @param maskedInputs the already-masked inputs; never {@code null}
     * @return a {@code PromptSpec} marked masked
     */
    public static PromptSpec ofMasked(String instruction, Map<String, Object> maskedInputs) {
        return new PromptSpec(instruction, maskedInputs, true);
    }
}
