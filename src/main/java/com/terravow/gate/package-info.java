/**
 * Decision_Gate (Req 8).
 *
 * <p>Applies the ordered hard-veto-before-confidence algorithm: hard veto first, then
 * confidence-validity, then ALLOW/ASK/BLOCK classification, then human-review routing. Never
 * claims authoritative sign-off.
 */
package com.terravow.gate;
