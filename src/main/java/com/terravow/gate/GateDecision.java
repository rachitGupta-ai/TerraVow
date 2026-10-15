package com.terravow.gate;

/**
 * The classification a {@code Decision_Gate} assigns to a recommendation.
 *
 * <p>This is the single source of truth for the gate decision across TerraVow: the Decision_Gate
 * (Req 8) emits it, and the Evidence/Audit service records it in every audit entry (Req 10.2). An
 * {@code ALLOW} may be applied automatically; an {@code ASK} or {@code BLOCK} withholds automatic
 * application and routes the recommendation to human review (Req 8.2). A hard-veto always forces
 * {@code BLOCK} regardless of confidence (Req 8.4).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 8.1, 8.2, 10.2</a>
 */
public enum GateDecision {
    /** The recommendation cleared the gate and may be applied automatically. */
    ALLOW,
    /** The recommendation is withheld from automatic application and routed to human review. */
    ASK,
    /** The recommendation is blocked (by hard veto or low/invalid confidence) and routed to review. */
    BLOCK
}
