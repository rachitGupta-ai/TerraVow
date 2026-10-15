package com.terravow.capability.pollution;

/**
 * An environmental medium into which a pollutant load is released (Req 3).
 *
 * <p>A reading's pollutant and medium together select the applicable regulatory threshold
 * (Req 3.1), and the Cross_Media_Guardrail detects when a remediation reduces load in one medium
 * while increasing it in another (Req 3.6, 3.7). Modelling the medium as a closed enum makes the
 * cross-media trade-off reasoning exhaustive and deterministic.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.6, 3.7</a>
 */
public enum EnvironmentalMedium {
    /** Air (stack emissions, ambient sensors). */
    AIR,
    /** Water (effluent discharge). */
    WATER,
    /** Solid waste. */
    SOLID_WASTE
}
