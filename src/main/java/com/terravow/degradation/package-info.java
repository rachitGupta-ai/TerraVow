/**
 * Graceful degradation policy (Req 12.3–12.6).
 *
 * <p>Executes managed paths with predefined safe fallbacks at strictly lower confidence, recording
 * the substituted path in the Evidence_Chain; routes to human review when no safe fallback exists.
 */
package com.terravow.degradation;
