package com.terravow.pipeline;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import java.util.Objects;

/**
 * The generic cross-cutting abstraction every capability result conforms to: a value plus a
 * {@link Confidence} in {@code [0,1]} plus an {@link EvidenceChain} (design &rarr; Cross-Cutting
 * Control Flow; Req 1.7, 10.1).
 *
 * <p>Each capability (EID, ERD, pollution, AEI, substitution, economics, passport, certification,
 * maintenance lifecycle) owns a richer, status-bearing result type of its own. {@code
 * Determination<T>} is the uniform shape those results share and the common currency the
 * cross-cutting control flow operates on: it carries whatever {@code value} the capability produced,
 * the confidence attached to it, and the Evidence_Chain whose links resolve to primary sources
 * (Req 10.1). Modelling the triple explicitly lets the {@link CrossCuttingControlFlow} route any
 * capability's output through the Decision_Gate, audit persistence, and disclaimer attachment
 * without knowing the capability's concrete result type.
 *
 * <p>The record is immutable and validates its invariants in the compact constructor, so an
 * ill-formed determination (missing value, confidence, or evidence) is unrepresentable.
 *
 * @param <T>        the capability-specific value type carried by this determination
 * @param value      the determined value; never {@code null}
 * @param confidence the confidence attached to the value; never {@code null} (Req 1.7)
 * @param evidence   the Evidence_Chain supporting the value; never {@code null} (Req 10.1)
 *
 * @see CrossCuttingControlFlow
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 1.7, 10.1</a>
 */
public record Determination<T>(T value, Confidence confidence, EvidenceChain evidence) {

    /**
     * Validates that the value, confidence, and evidence references are all present (Req 1.7,
     * 10.1).
     *
     * @throws NullPointerException if {@code value}, {@code confidence}, or {@code evidence} is
     *                              {@code null}
     */
    public Determination {
        Objects.requireNonNull(value, "Determination value must not be null");
        Objects.requireNonNull(confidence, "Determination confidence must not be null");
        Objects.requireNonNull(evidence, "Determination evidence must not be null");
    }

    /**
     * Convenience factory for a determination from a value, confidence, and Evidence_Chain.
     *
     * @param <T>        the value type
     * @param value      the determined value; never {@code null}
     * @param confidence the confidence; never {@code null}
     * @param evidence   the supporting Evidence_Chain; never {@code null}
     * @return a determination carrying the three components
     */
    public static <T> Determination<T> of(T value, Confidence confidence, EvidenceChain evidence) {
        return new Determination<>(value, confidence, evidence);
    }
}
