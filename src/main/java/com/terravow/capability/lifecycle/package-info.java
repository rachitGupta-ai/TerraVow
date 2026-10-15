/**
 * AI-Driven Emissions-Aware Maintenance Lifecycle (Req 15).
 *
 * <p>Prioritises maintenance by the pollution cost of deferral and verifies it by its measured
 * emissions outcome. The {@link com.terravow.capability.lifecycle.MaintenanceLifecycle} forecasts the
 * EID trajectory of a degraded asset over a configured horizon through a Reasoning_Agent (Req 15.1),
 * prices the forecast deferral cost through the ERD components via verified tools (Req 15.2),
 * computes an Emissions_Aware_Priority combining failure risk, downtime cost, and forecast pollution
 * cost — AEI-weighting the pollution-cost term where a Receptor_Map and met forecast exist (Req 15.3,
 * 15.4) — ranks the queue so higher environmental harm can outrank higher failure probability with an
 * Evidence_Chain (Req 15.5), records a Maintenance_Effectiveness_Record from the before/after
 * intensity delta on completion (Req 15.6, 15.7) surfaced to substitution/certification (Req 15.8),
 * routes the recommendation through the Decision_Gate (Req 15.9), and reports the deferral cost
 * undetermined when the data is insufficient (Req 15.10).
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 15.1–15.10</a>
 */
package com.terravow.capability.lifecycle;
