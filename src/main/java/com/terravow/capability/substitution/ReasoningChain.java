package com.terravow.capability.substitution;

import java.util.List;
import java.util.Objects;

/**
 * The ordered reasoning chain supporting a substitution {@link Candidate}.
 *
 * <p>Every Substitution Dossier attaches the reasoning chain supporting each reported value
 * (Req 5.5), alongside the machine-checkable Evidence_Chain. Where the Evidence_Chain links values
 * to primary sources, the reasoning chain records the ordered human-readable reasoning steps the
 * Reasoning_Agent followed to arrive at the candidate. The steps are defensively copied into an
 * immutable list.
 *
 * @param steps the ordered reasoning steps; never {@code null}, defensively copied, non-empty
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.5</a>
 */
public record ReasoningChain(List<String> steps) {

    /**
     * Validates and defensively copies the reasoning steps into a non-empty immutable list
     * (Req 5.5). A reasoning chain that supports reported values cannot be empty.
     *
     * @throws NullPointerException     if {@code steps} is {@code null} or contains a {@code null}
     *                                  element
     * @throws IllegalArgumentException if {@code steps} is empty
     */
    public ReasoningChain {
        Objects.requireNonNull(steps, "steps must not be null");
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("ReasoningChain must contain at least one step");
        }
    }
}
