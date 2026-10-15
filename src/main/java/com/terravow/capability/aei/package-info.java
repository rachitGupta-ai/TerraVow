/**
 * AEI (Avoidable Exposure Index) service and harm-aware rescheduler (Req 4).
 *
 * <p>Measures the population-exposure harm attributable to the timing of an emission rather than
 * its mass, computes an airshed assimilative-capacity forecast over a ≥ 24h / ≤ 1h-step horizon,
 * and recommends mass-preserving, harm-reducing schedules while rejecting any candidate that would
 * increase total mass or cross-media load.
 */
package com.terravow.capability.aei;
