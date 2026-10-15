package com.terravow.domain;

/**
 * The set of currencies TerraVow reports monetary values in.
 *
 * <p>{@link Money} is always expressed in exactly one declared currency (Req 2.1). The enum is kept
 * deliberately small; INR is the primary currency given the Indian regulatory-instrument focus of
 * the Economics Engine (Req 6).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 2.1</a>
 */
public enum Currency {
    /** Indian Rupee — minor unit: paise (1/100). */
    INR,
    /** United States Dollar — minor unit: cents (1/100). */
    USD,
    /** Euro — minor unit: cents (1/100). */
    EUR
}
