"""Deterministic cost/emissions model. NO LLM math lives here or anywhere else:
Gemini reads the machine's condition and explains results; this module computes.

All coefficients are ILLUSTRATIVE demo assumptions (see ASSUMPTIONS) and are
returned to the UI so nothing is hidden from the user.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

HORIZON_DAYS = 90

# Efficiency loss (absolute fraction of baseline efficiency) at day 0, by severity 1..5
LOSS_BY_SEVERITY = {1: 0.005, 2: 0.015, 3: 0.03, 4: 0.055, 5: 0.085}
# Daily compounding growth of that loss while the defect is left unrepaired
GROWTH_BY_SEVERITY = {1: 0.004, 2: 0.008, 3: 0.012, 4: 0.018, 5: 0.025}
LOSS_CAP = 0.25

# Illustrative cost parameters (INR)
CARBON_RATE_INR_PER_TCO2E = 1500.0   # exposure proxy, replace with a cited figure
NOX_RATE_INR_PER_KG = 40.0           # exposure proxy, replace with a cited figure

ASSUMPTIONS = {
    "efficiency_loss_at_day0_by_severity": LOSS_BY_SEVERITY,
    "daily_loss_growth_by_severity": GROWTH_BY_SEVERITY,
    "loss_cap": LOSS_CAP,
    "carbon_exposure_inr_per_tco2e": CARBON_RATE_INR_PER_TCO2E,
    "nox_exposure_inr_per_kg": NOX_RATE_INR_PER_KG,
    "note": "Illustrative demo coefficients. Replace with plant-specific and "
            "regulator-sourced values before any real use.",
}


@dataclass
class Machine:
    id: str
    name: str
    kind: str
    fuel: str
    fuel_unit: str
    base_fuel_per_day: float      # fuel units/day at healthy efficiency
    fuel_price: float             # INR per fuel unit
    ef_co2: float                 # kg CO2 per fuel unit
    ef_nox: float                 # kg NOx per fuel unit
    repair_cost: float            # INR to repair now
    downtime_cost_now: float      # INR lost if stopped now (unplanned)
    downtime_cost_planned: float  # INR lost if repaired in the planned window
    planned_window_day: int       # days until next planned shutdown
    repair_escalation_per_day: float  # repair cost growth/day if deferred
    health: dict = field(default_factory=dict)  # severity, confidence, component...
    output_per_day: float = 0.0           # units of product made per day (illustrative)
    output_label: str = "tonne of product"
    # Prototype extension tagging (design E1). Trailing optional fields only.
    industry: str = ""
    manufacturer: str = ""
    model: str = ""
    install_year: Optional[int] = None
    catalogue_id: Optional[str] = None


def loss_at(severity: int, day: int) -> float:
    sev = max(1, min(5, int(severity)))
    return min(LOSS_CAP, LOSS_BY_SEVERITY[sev] * (1 + GROWTH_BY_SEVERITY[sev]) ** day)


def excess_from_loss(m: Machine, loss: float) -> dict:
    """Excess fuel/emissions/cost for an efficiency loss, versus a healthy machine.
    Efficiency falls by `loss` relative to baseline, and fuel needed scales as
    1/efficiency, so excess fuel = base * (1/(1-loss) - 1).
    """
    excess_fuel = m.base_fuel_per_day * (1.0 / (1.0 - loss) - 1.0)
    co2_kg = excess_fuel * m.ef_co2
    nox_kg = excess_fuel * m.ef_nox
    energy_cost = excess_fuel * m.fuel_price
    exposure = (co2_kg / 1000.0) * CARBON_RATE_INR_PER_TCO2E + nox_kg * NOX_RATE_INR_PER_KG
    return {
        "loss": loss,
        "excess_fuel": excess_fuel,
        "co2_kg": co2_kg,
        "nox_kg": nox_kg,
        "energy_cost": energy_cost,
        "exposure": exposure,
        "total_cost": energy_cost + exposure,
    }


def daily_excess(m: Machine, severity: int, day: int) -> dict:
    """Excess fuel/emissions/cost on a given day, versus a healthy machine."""
    return excess_from_loss(m, loss_at(severity, day))


def simulate(m: Machine, severity: Optional[int] = None, horizon: int = HORIZON_DAYS) -> dict:
    """Cumulative liability curve plus break-even for 'wait for the planned window'.

    Fixing now costs repair + unplanned downtime. Waiting saves the difference
    between unplanned and planned downtime, but accrues excess cost and repair
    escalation. Break-even is the first day waiting costs more than it saves.
    """
    sev = int(severity if severity is not None else m.health.get("severity", 1))
    savings = max(0.0, m.downtime_cost_now - m.downtime_cost_planned)

    curve = []
    cum = cum_co2 = cum_nox = cum_fuel = 0.0
    break_even = None
    for d in range(0, horizon + 1):
        ex = daily_excess(m, sev, d)
        if d > 0:
            cum += ex["total_cost"]
            cum_co2 += ex["co2_kg"]
            cum_nox += ex["nox_kg"]
            cum_fuel += ex["excess_fuel"]
        repair_premium = m.repair_cost * m.repair_escalation_per_day * d
        waiting_cost = cum + repair_premium
        if break_even is None and d >= 1 and waiting_cost >= savings:
            break_even = d
        curve.append({
            "day": d,
            "daily_cost": round(ex["total_cost"], 2),
            "cum_liability": round(cum, 2),
            "waiting_cost": round(waiting_cost, 2),
            "cum_co2_kg": round(cum_co2, 2),
            "cum_nox_kg": round(cum_nox, 2),
        })

    w = min(m.planned_window_day, horizon)
    wait_at_window = curve[w]["waiting_cost"]
    recommendation = "DEFER_TO_WINDOW" if wait_at_window < savings else "FIX_NOW"

    today = daily_excess(m, sev, 0)
    return {
        "machine_id": m.id,
        "severity": sev,
        "today": {k: round(v, 4) for k, v in today.items()},
        "daily_liability_today": round(today["total_cost"], 2),
        "downtime_savings_if_wait": round(savings, 2),
        "planned_window_day": m.planned_window_day,
        "waiting_cost_at_window": round(wait_at_window, 2),
        "break_even_day": break_even,
        "recommendation": recommendation,
        "curve": curve,
        "assumptions": ASSUMPTIONS,
        "formula": (
            "excess_fuel = base_fuel x (1/(1-loss) - 1); "
            "cost = fuel x price + CO2(t) x carbon_rate + NOx(kg) x nox_rate; "
            "break-even = first day (cumulative excess cost + repair escalation) "
            ">= downtime saved by waiting"
        ),
    }


def deferral_cost(m: Machine, defer_days: int, severity: Optional[int] = None) -> dict:
    """Verified tool exposed to Gemini via function calling."""
    sev = int(severity if severity is not None else m.health.get("severity", 1))
    d = max(0, min(HORIZON_DAYS, int(defer_days)))
    sim = simulate(m, sev)
    pt = sim["curve"][d]
    return {
        "machine": m.name,
        "defer_days": d,
        "cumulative_excess_cost_inr": pt["cum_liability"],
        "total_waiting_cost_inr_incl_repair_escalation": pt["waiting_cost"],
        "cumulative_excess_co2_kg": pt["cum_co2_kg"],
        "downtime_saved_by_waiting_inr": sim["downtime_savings_if_wait"],
        "break_even_day": sim["break_even_day"],
        "daily_liability_today_inr": sim["daily_liability_today"],
        "recommendation": sim["recommendation"],
        "planned_window_day": m.planned_window_day,
    }


# ---------------------------------------------------------------- footprint passport
def footprint(m: Machine, severity: Optional[int] = None, day: int = 0) -> dict:
    """Direct combustion CO2 per unit of output: healthy versus current condition.

    This is the machine-condition-adjusted figure a customer could use in place of
    an industry-average factor. Illustrative until real fuel and output data are wired in.
    """
    sev = int(severity if severity is not None else m.health.get("severity", 1))
    out = m.output_per_day or 1.0
    healthy_kg = m.base_fuel_per_day * m.ef_co2
    ex = daily_excess(m, sev, day)
    current_kg = healthy_kg + ex["co2_kg"]
    return {
        "unit": m.output_label,
        "healthy_kg_per_unit": round(healthy_kg / out, 2),
        "current_kg_per_unit": round(current_kg / out, 2),
        "extra_kg_per_unit": round(ex["co2_kg"] / out, 2),
        "extra_pct": round(100.0 * ex["co2_kg"] / healthy_kg, 2) if healthy_kg else 0.0,
        "output_per_day": m.output_per_day,
    }


# ---------------------------------------------------------------- improvement
def improvement(m: Machine, before_sev: int, after_sev: int, horizon: int = 30) -> dict:
    """What a repair is worth: the model's trajectory before versus after, over `horizon` days."""
    b = simulate(m, before_sev, horizon=horizon)
    a = simulate(m, after_sev, horizon=horizon)
    cb, ca = b["curve"][horizon], a["curve"][horizon]
    return {
        "before_severity": before_sev,
        "after_severity": after_sev,
        "avoided_cost_per_day": round(b["daily_liability_today"] - a["daily_liability_today"], 2),
        "avoided_co2_kg_per_day": round(b["today"]["co2_kg"] - a["today"]["co2_kg"], 2),
        "horizon_days": horizon,
        "avoided_cost_horizon": round(cb["cum_liability"] - ca["cum_liability"], 2),
        "avoided_co2_kg_horizon": round(cb["cum_co2_kg"] - ca["cum_co2_kg"], 2),
        "footprint_before": footprint(m, before_sev),
        "footprint_after": footprint(m, after_sev),
    }


def fleet_impact(machines: dict, horizon: int = 30) -> dict:
    """If the 'fix now' machines are repaired, how much CO2 and excess cost is avoided."""
    fix_ids, fix_co2, fix_cost = [], 0.0, 0.0
    all_co2, all_cost, daily_total = 0.0, 0.0, 0.0
    for m in machines.values():
        sev = int(m.health.get("severity", 1))
        sim = simulate(m, sev, horizon=horizon)
        c = sim["curve"][horizon]
        all_co2 += c["cum_co2_kg"]
        all_cost += c["cum_liability"]
        daily_total += sim["daily_liability_today"]
        if sim["recommendation"] == "FIX_NOW":
            fix_ids.append(m.id)
            fix_co2 += c["cum_co2_kg"]
            fix_cost += c["cum_liability"]
    return {
        "horizon_days": horizon,
        "fix_now_ids": fix_ids,
        "fix_now_co2_t": round(fix_co2 / 1000.0, 2),
        "fix_now_cost": round(fix_cost, 0),
        "all_co2_t": round(all_co2 / 1000.0, 2),
        "all_cost": round(all_cost, 0),
        "fleet_daily_liability": round(daily_total, 0),
        "note": "Excess cost avoided is before repair and downtime costs. "
                "Assumes a repair removes the defect. Illustrative demo values.",
    }


# ================================================================ prototype extension (design E7)
# Pure functions: no I/O, no wall clock, no randomness. `as_of` is always an argument.
from datetime import date as _date, timedelta as _timedelta
from typing import Callable, Dict, List, Sequence

HEALTH_WINDOW_DAYS = 365
HEALTH_RECENT_DAYS = 90
HEALTH_LOW_HISTORY_N = 3
MIN_RESIDUAL_DAYS = 180
LIFESPAN_EXPONENT = 1.5
DOMAIN_K = {"air": 1.0, "water": 2.5, "chemical": 3.0, "soil": 2.0}  # illustrative degradation factors
INTENSITY_LOSS_CAP = 0.60
FACTOR_SOURCE = "illustrative assumption"
HEALTH_ASSUMPTIONS = {
    "window_days": HEALTH_WINDOW_DAYS, "recent_days": HEALTH_RECENT_DAYS,
    "low_history_n": HEALTH_LOW_HISTORY_N, "min_residual_days": MIN_RESIDUAL_DAYS,
    "lifespan_exponent": LIFESPAN_EXPONENT, "domain_degradation_k": DOMAIN_K,
    "loss_by_severity": LOSS_BY_SEVERITY, "label": "illustrative assumption",
}


@dataclass(frozen=True)
class SnagFact:
    snag_type_id: Optional[str]
    category: str
    severity: int
    reported_on: _date
    resolved_on: Optional[_date]
    unresolved_at_as_of: bool


def band_of(score: float) -> str:
    return "Good" if score >= 80 else "Fair" if score >= 60 else "Poor" if score >= 40 else "Critical"


def _age_days(install_year: Optional[int], as_of: _date) -> Optional[int]:
    if install_year is None or install_year > as_of.year:
        return None
    return (as_of - _date(install_year, 1, 1)).days


def health_score(facts: Sequence[SnagFact], install_year: Optional[int], design_life_years: int,
                 as_of: _date) -> dict:
    """E7.2. Every term is non-decreasing in the snag multiset and the unresolved set."""
    facts = [f for f in facts if f.reported_on <= as_of]
    ages = lambda f: (as_of - f.reported_on).days
    in_window = [f for f in facts if 0 <= ages(f) <= HEALTH_WINDOW_DAYS]
    recent = [f for f in facts if 0 <= ages(f) <= HEALTH_RECENT_DAYS]
    unresolved = [f for f in facts if f.unresolved_at_as_of]
    groups: Dict[str, int] = {}
    for f in in_window:
        k = f.snag_type_id or f.category
        groups[k] = groups.get(k, 0) + 1
    age_days = _age_days(install_year, as_of)
    p_freq = min(15.0, 1.5 * len(in_window))
    p_sev = min(15.0, 1.0 * sum(f.severity - 1 for f in in_window))
    p_recur = min(15.0, 3.0 * sum(max(0, c - 1) for c in groups.values()))
    p_trend = min(10.0, 1.0 * sum(max(0, f.severity - 2) for f in recent))
    p_unres = min(25.0, 2.0 * sum(f.severity for f in unresolved))
    p_age = 0.0 if age_days is None else 15.0 * min(1.0, age_days / (design_life_years * 365))
    repairs = [f.resolved_on for f in facts if f.severity >= 4 and f.resolved_on is not None
               and f.resolved_on <= as_of]
    if repairs:
        d_repair = (as_of - max(repairs)).days
        if age_days is not None:
            d_repair = min(d_repair, age_days)
        p_repair = 8.0 * min(1.0, d_repair / 730)
    elif age_days is not None:
        p_repair = 8.0 * min(1.0, age_days / 730)
    else:
        p_repair = 8.0
    terms = [
        ("frequency", "Snag frequency (last 365 days)", p_freq, f"{len(in_window)} snags x 1.5, cap 15"),
        ("severity", "Severity burden (last 365 days)", p_sev, "sum of (severity - 1), cap 15"),
        ("recurrence", "Recurring snag types", p_recur, f"3 per repeat of the same type, cap 15"),
        ("trend", "Recent high-severity load (90 days)", p_trend, f"{len(recent)} recent snags, sum of (severity - 2), cap 10"),
        ("unresolved", "Unresolved snags", p_unres, f"{len(unresolved)} open, 2 x severity each, cap 25"),
        ("age", "Age versus design life", p_age,
         "age unknown" if age_days is None else f"{age_days / 365:.1f} of {design_life_years} design years, cap 15"),
        ("repair", "Time since last major repair", p_repair,
         "no major repair on record" if not repairs else f"last major repair {max(repairs).isoformat()}, cap 8"),
    ]
    score = max(0.0, 100.0 - sum(t[2] for t in terms))

    def _mean(xs):
        return sum(xs) / len(xs) if xs else None
    m_now = _mean([f.severity for f in recent])
    m_prev = _mean([f.severity for f in facts if HEALTH_RECENT_DAYS < ages(f) <= 2 * HEALTH_RECENT_DAYS])
    trend = "stable"
    if m_now is not None and m_prev is not None:
        trend = "rising" if m_now - m_prev > 0.25 else "falling" if m_prev - m_now > 0.25 else "stable"
    if age_days is None:
        confidence, reason = "age-unknown", "Install year unknown: the age term is 0 and the result is less certain."
    elif len(facts) < HEALTH_LOW_HISTORY_N:
        confidence, reason = "low-history", f"Fewer than {HEALTH_LOW_HISTORY_N} snags on record."
    else:
        confidence, reason = "normal", None
    return {
        "score": score, "band": band_of(score), "confidence": confidence, "confidence_reason": reason,
        "factors": [{"id": i, "label": l, "penalty": round(p, 2), "detail": d} for i, l, p, d in terms],
        "trend_label": trend, "age_days": age_days, "age_penalty": p_age, "total_snags": len(facts),
    }


def remaining_lifespan(health: float, age_days: Optional[int], design_life_years: int, total_snags: int) -> dict:
    """E7.3. Non-decreasing in health for a fixed age; health 0 gives 0 days."""
    design_days = design_life_years * 365
    age_eff = age_days if age_days is not None else 0.5 * design_days
    base_days = max(design_days - age_eff, MIN_RESIDUAL_DAYS)
    remaining = min(max(base_days * (max(0.0, health) / 100.0) ** LIFESPAN_EXPONENT, 0.0), design_days)
    u = min(0.60, 0.25 + (0.20 if total_snags < 3 else 0.0) + (0.15 if age_days is None else 0.0))
    age_txt = "age unknown (half the design life assumed)" if age_days is None else \
        f"age {age_days / 365:.1f} of {design_life_years} design years"
    return {
        "remaining_days": remaining, "low_days": remaining * (1 - u),
        "high_days": min(design_days, remaining * (1 + u)), "uncertainty": u,
        "basis": f"Health {health:.1f} ({band_of(health)}); {age_txt}; exponent {LIFESPAN_EXPONENT}",
        "flags": [x for x, on in (("low-history", total_snags < 3), ("age-unknown", age_days is None)) if on],
        "label": "Estimate, not a prediction of failure",
    }


def sev_equiv(health: float) -> float:
    return 1.0 + 4.0 * (100.0 - max(0.0, min(100.0, health))) / 100.0


def degradation_loss(health: float) -> float:
    """Linear interpolation of LOSS_BY_SEVERITY at sev_equiv(health); strictly increasing as health falls."""
    s = sev_equiv(health)
    lo = min(4, int(s))
    return LOSS_BY_SEVERITY[lo] + (s - lo) * (LOSS_BY_SEVERITY[lo + 1] - LOSS_BY_SEVERITY[lo])


def pollution_intensity(health: float, domains: Sequence[str], domain_indicators: Dict[str, List[str]],
                        units: Dict[str, str], default_baselines: Dict[str, Optional[float]],
                        scale: float = 1.0, domain_scale: Optional[Dict[str, float]] = None,
                        machine: Optional[Machine] = None, remaining_days: float = 0.0) -> dict:
    """E7.4. Per-day intensity of each indicator of the machine's domains; out-of-domain omitted."""
    loss = degradation_loss(health)
    ds = domain_scale or {}
    per_day, cumulative = {}, {}
    for d in domains:
        for ind in domain_indicators[d]:
            base = default_baselines.get(ind)
            if d == "air" and machine is not None and machine.base_fuel_per_day:
                ef = machine.ef_co2 if ind == "co2e_kg" else machine.ef_nox
                b = machine.base_fuel_per_day * ef
                ex = excess_from_loss(machine, loss)
                value = b + (ex["co2_kg"] if ind == "co2e_kg" else ex["nox_kg"])
            elif base is None:
                continue
            else:
                b = base * scale * (1.0 if d == "air" else ds.get(d, 1.0))
                value = b / (1.0 - min(INTENSITY_LOSS_CAP, DOMAIN_K[d] * loss))
            per_day[ind] = {"value": value, "unit": units[ind], "domain": d, "baseline_reference": b,
                            "factor_source": FACTOR_SOURCE}
            cumulative[ind] = value * remaining_days
    return {"domains": list(domains), "sev_equiv": sev_equiv(health), "loss": loss,
            "per_day": per_day, "over_remaining_lifespan": cumulative}


def health_timeline(facts_at: Callable[[_date], Sequence[SnagFact]], install_year: Optional[int],
                    design_life_years: int, today: _date, intensity: Callable[[float], dict],
                    points: int = 12, step_days: int = 30) -> List[dict]:
    """E7.5: health and per-day intensities at today - 30k, oldest first."""
    out = []
    for k in range(points - 1, -1, -1):
        d = today - _timedelta(days=step_days * k)
        h = health_score(facts_at(d), install_year, design_life_years, d)["score"]
        out.append({"as_of": d.isoformat(), "health": round(h, 1),
                    "per_day": {i: round(v["value"], 2) for i, v in intensity(h)["per_day"].items()}})
    return out


# ---------------------------------------------------------------- E7.6 alternative economics
# Every constant is an ILLUSTRATIVE assumption, returned to the UI as `assumptions`.
from math import isfinite as _isfinite

OUTPUT_MARGIN = 0.15                 # a margin on extra output, not its gross value
EXPOSURE_RATE_INR = {"cod_kg": 2.0, "bod_kg": 3.0, "effluent_m3": 5.0, "chem_release_kg": 25.0,
                     "solid_waste_kg": 1.0, "soil_contam_index": 10.0}   # notional, per unit, not cash
TAX_RATE = 0.25                      # illustrative, for the accelerated-deduction benefit
MAX_INCENTIVE_SHARE = 0.75           # an incentive never removes more than 75% of capex
PAYBACK_EPS = 1e-9
ECONOMICS_ASSUMPTIONS = {
    "output_margin": OUTPUT_MARGIN, "carbon_exposure_inr_per_tco2e": CARBON_RATE_INR_PER_TCO2E,
    "nox_exposure_inr_per_kg": NOX_RATE_INR_PER_KG, "exposure_rate_inr_per_unit": EXPOSURE_RATE_INR,
    "tax_rate": TAX_RATE, "max_incentive_share": MAX_INCENTIVE_SHARE,
    "label": "illustrative assumption",
    "note": "Exposure is a notional proxy at illustrative rates, not cash. Paybacks never use Gemini totals.",
}
NO_PAYBACK, WITHIN_LIFE, BEYOND_LIFE = "no-payback", "within-design-life", "beyond-design-life"


def payback_status(p: Optional[float], horizon_years: float) -> str:
    if p is None:
        return NO_PAYBACK
    return WITHIN_LIFE if p <= horizon_years + PAYBACK_EPS else BEYOND_LIFE


def _div(a: float, b: float) -> Optional[float]:
    return a / b if b > 0 else None


def candidate_economics(params: dict, econ: dict, current: Dict[str, dict]) -> dict:
    """E7.6. `params`: capex_pct, eff_pct, prod_pct, opex_pct (percent) and `reductions`
    {domain: percent, negative = increase}. `econ`: replacement_value_inr,
    annual_operating_cost_inr, annual_output_value_inr, design_life_years. `current`: per-day
    intensity {indicator: {"value", "domain"}} of the machine's own domains only, so an
    out-of-domain reduction is ignored and an unmentioned domain counts as 0.
    Pure; Gemini never supplies any value computed here."""
    keys = ("replacement_value_inr", "annual_operating_cost_inr", "annual_output_value_inr", "design_life_years")
    if not current or any(econ.get(k) is None for k in keys):
        return {"status": "undetermined",
                "reason": "The current intensity or the machine economics are not available for this machine."}
    rv, oc, ov = (float(econ[k]) for k in keys[:3])
    horizon = int(econ["design_life_years"])
    capex = params["capex_pct"] * rv / 100.0
    energy = params["eff_pct"] * oc / 100.0
    margin = OUTPUT_MARGIN * params["prod_pct"] * ov / 100.0
    opex = params["opex_pct"] * oc / 100.0
    cash = energy + margin - opex
    red = params.get("reductions") or {}
    avoided = {i: v["value"] * float(red.get(v["domain"], 0.0)) / 100.0 * 365.0 for i, v in sorted(current.items())}
    exposure = 0.0
    for i, a in avoided.items():
        if i == "co2e_kg":
            exposure += a / 1000.0 * CARBON_RATE_INR_PER_TCO2E
        elif i == "nox_kg":
            exposure += a * NOX_RATE_INR_PER_KG
        else:
            exposure += a * EXPOSURE_RATE_INR.get(i, 0.0)
    with_exp = cash + exposure
    p_cash, p_exp = _div(capex, cash), _div(capex, with_exp)
    per_domain: Dict[str, dict] = {}
    for i, v in sorted(current.items()):
        d = v["domain"]
        pct = float(red.get(d, 0.0))
        if pct == 0:
            continue
        per_domain.setdefault(d, {"pct": pct})[f"{i}_per_year"] = avoided[i]
    return {
        "status": "ok", "capex_inr": capex, "efficiency_gain_pct": params["eff_pct"],
        "production_uplift_pct": params["prod_pct"], "extra_opex_pct": params["opex_pct"],
        "energy_saving_inr_per_year": energy, "production_margin_inr_per_year": margin,
        "extra_opex_inr_per_year": opex, "cash_benefit_inr_per_year": cash,
        "exposure_avoided_inr_per_year": exposure, "benefit_with_exposure_inr_per_year": with_exp,
        "payback_cash_years": p_cash, "payback_with_exposure_years": p_exp,
        "payback_horizon_years": horizon,
        "payback_cash_status": payback_status(p_cash, horizon),
        "payback_with_exposure_status": payback_status(p_exp, horizon),
        "payback_note": None if p_cash is not None else "no payback in cash terms",
        "exposure_note": "Includes a notional exposure proxy at illustrative rates; not cash.",
        "reduction_per_domain": per_domain, "avoided_annual": avoided,
    }


def gate_deltas(avoided_annual: Dict[str, float], horizon_days: int = 30) -> Dict[str, float]:
    """E7.8: per-medium mass deltas over `horizon_days` (negative = reduction). Volume and index
    indicators are not masses and are excluded."""
    f = horizon_days / 365.0
    a = lambda i: avoided_annual.get(i, 0.0)
    out = {"air": -(a("co2e_kg") + a("nox_kg")) * f, "water": -(a("cod_kg") + a("bod_kg")) * f,
           "chemical": -a("chem_release_kg") * f, "soil": -a("solid_waste_kg") * f}
    return {k: v for k, v in out.items() if any(i in avoided_annual for i in _MASS_INDICATORS[k])}


_MASS_INDICATORS = {"air": ("co2e_kg", "nox_kg"), "water": ("cod_kg", "bod_kg"),
                    "chemical": ("chem_release_kg",), "soil": ("solid_waste_kg",)}


# ---------------------------------------------------------------- E7.7 incentive adjustment
def incentive_eligible(s: dict, country: str, industry: str, size: str, kind: Optional[str] = None) -> bool:
    """Listing ignores applies_to (kind=None); the calculation also requires the candidate kind."""
    inds = s.get("industries") or []
    return (s.get("country") == country and (industry in inds or "*" in inds)
            and size in (s.get("enterprise_sizes") or [])
            and (kind is None or kind in (s.get("applies_to") or [])))


def incentive_reduction(s: dict, capex: float) -> Optional[float]:
    """INR off capex for a quantifiable scheme; None for a qualitative one (params null, which
    includes every real-named curated scheme) and for any Gemini-discovered record."""
    p = s.get("params")
    if not p or s.get("origin") == "gemini-grounded":
        return None
    if s.get("benefit_type") == "CAPITAL_SUBSIDY" and p.get("pct") is not None:
        r = float(p["pct"]) / 100.0 * capex
        return min(r, float(p["cap_inr"])) if p.get("cap_inr") is not None else r
    if s.get("benefit_type") == "ACCELERATED_DEPRECIATION" and p.get("additional_deduction_pct") is not None:
        return float(p["additional_deduction_pct"]) / 100.0 * capex * TAX_RATE
    return None


def apply_incentive(capex: float, cash_benefit: float, benefit_with_exposure: float, horizon_years: float,
                    schemes: Sequence[dict]) -> dict:
    """Single largest quantifiable reduction (no stacking; ties by scheme_id ascending). The
    adjusted capex is never above capex and each adjusted payback never above its unadjusted one."""
    best_id, best = None, 0.0
    for s in sorted(schemes, key=lambda x: x.get("scheme_id") or ""):
        r = incentive_reduction(s, capex)
        if r is None or not _isfinite(r) or r <= 0:
            continue
        if best_id is None or r > best:
            best_id, best = s["scheme_id"], r
    applied = next((s for s in schemes if s.get("scheme_id") == best_id), None)
    adjusted = capex - min(best, MAX_INCENTIVE_SHARE * capex)
    p_cash, p_exp = _div(adjusted, cash_benefit), _div(adjusted, benefit_with_exposure)
    return {
        "schemes_applied": [best_id] if best_id else [],
        "uses_illustrative_values": bool(applied) and (applied.get("origin") == "demo-synthetic"
                                                       or not applied.get("verified")),
        "incentive_reduction_inr": capex - adjusted, "adjusted_capex_inr": adjusted,
        "adjusted_payback_cash_years": p_cash, "adjusted_payback_with_exposure_years": p_exp,
        "adjusted_payback_cash_status": payback_status(p_cash, horizon_years),
        "adjusted_payback_with_exposure_status": payback_status(p_exp, horizon_years),
        "qualitative_schemes": sorted(s["scheme_id"] for s in schemes if incentive_reduction(s, capex) is None),
    }
