package com.terravow.pipeline;

import com.terravow.gate.GateVerdict;
import com.terravow.pii.synthetic.DecisionOrientedOutput;

import java.util.Objects;

/**
 * The terminal outcome of routing a capability determination through the cross-cutting control flow
 * (design &rarr; Cross-Cutting Control Flow; Req 8.1, 10.3, 13.5).
 *
 * <p>It bundles the three cross-cutting results produced for a decision-oriented determination:
 * <ul>
 *   <li>the {@link GateVerdict} the recommendation received from the Decision_Gate (Req 8.1);</li>
 *   <li>the {@link DecisionOrientedOutput} returned to the caller — which always carries the
 *       advisory disclaimer (Req 13.5) and propagates the synthetic label (Req 13.2); and</li>
 *   <li>the {@link Determination} that was routed, preserved for traceability.</li>
 * </ul>
 *
 * <p>By the time an instance exists, the audit entry for the recommendation has already been
 * persisted synchronously (Req 10.3) — construction of this outcome is the control flow's final
 * step, performed only after {@code AuditService.persistDecision} has returned.
 *
 * @param <T>          the capability value type carried by the routed determination
 * @param verdict      the Decision_Gate verdict for the recommendation; never {@code null}
 * @param output       the decision-oriented output carrying the disclaimer; never {@code null}
 * @param determination the determination that was routed; never {@code null}
 *
 * @see CrossCuttingControlFlow
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 10.3, 13.5</a>
 */
public record ControlFlowOutcome<T>(
        GateVerdict verdict,
        DecisionOrientedOutput output,
        Determination<T> determination) {

    /**
     * Validates that the verdict, output, and determination are all present.
     *
     * @throws NullPointerException if {@code verdict}, {@code output}, or {@code determination} is
     *                              {@code null}
     */
    public ControlFlowOutcome {
        Objects.requireNonNull(verdict, "ControlFlowOutcome verdict must not be null");
        Objects.requireNonNull(output, "ControlFlowOutcome output must not be null");
        Objects.requireNonNull(determination, "ControlFlowOutcome determination must not be null");
    }

    /**
     * Convenience accessor for the surfaced output text, which always contains the advisory
     * disclaimer (Req 13.5).
     *
     * @return the surfaced decision-oriented output text
     */
    public String surfaced() {
        return output.surface();
    }
}
