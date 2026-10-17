package com.terravow.api;

import java.util.Map;

/**
 * Request body for the internal Decision_Gate evaluate endpoint.
 *
 * <p>Mirrors the inputs the Python front door's own gate uses, so the two implementations can be
 * compared on identical inputs: a per-medium map of emission-mass deltas in kg (negative means a
 * reduction, e.g. {@code {"air": -120.0, "water": 35.0}}), the recommendation confidence in
 * {@code [0,1]} (nullable to exercise the fail-closed path), and the upstream data-trust score.
 *
 * @param deltasKg    change in emitted mass per medium versus the do-nothing path; may be empty
 * @param confidence  recommendation confidence in [0,1], or {@code null} when absent/invalid
 * @param dataTrust   governing data-trust score in [0,1]; defaults to 1.0 when {@code null}
 */
public record GateEvaluateRequest(Map<String, Double> deltasKg, Double confidence, Double dataTrust) {
}
