/**
 * Reasoning_Agent orchestrator over Vertex AI Agent Engine + ADK (Req 11).
 *
 * <p>TerraVow-owned contract wrapping every Agent Engine invocation with the
 * schema-validate &rarr; bounded-retry &rarr; escalate loop, passing only masked inputs and
 * folding grounding citations into the Evidence_Chain.
 */
package com.terravow.reasoning;
