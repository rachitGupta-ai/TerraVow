"""Pydantic models of the AI-generated deep machine template (design E6.1, E6.1a L8).

The classes are declared once inside `_build(strict)`, which returns the whole family:
 - strict family (`GeneratedTemplate`): extra="forbid" and strict numbers; used by
   validate_template() and adapt_base().
 - lax family (`GeneratedTemplateSchema`): default config; used ONLY as Gemini's
   response_schema and as the compact schema in the json-prompt fallback, because a strict
   model emits additionalProperties: false, which Gemini's structured output rejects.
A test asserts both families have identical field names and annotations.
"""
from __future__ import annotations

from types import SimpleNamespace
from typing import List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field

Domain = Literal["air", "water", "chemical", "soil"]
Category = Literal["mechanical", "electrical", "thermal", "hydraulic_pneumatic",
                   "instrumentation_control", "structural", "process_chemical",
                   "emissions_control", "other"]


def _build(strict: bool) -> SimpleNamespace:
    cfg = ConfigDict(extra="forbid") if strict else ConfigDict()

    def num():  # strict numbers: "3" for a float is an error, not coerced
        return Field(strict=True) if strict else Field()

    class Base(BaseModel):
        model_config = cfg

    class SpecItem(Base):
        name: str
        value: str
        unit: Optional[str] = None

    class OperatingParam(Base):
        id: str
        label: str
        unit: str
        typical_range: str
        why_it_matters: str

    class CostBand(Base):
        low_inr: int = num()
        high_inr: int = num()

    class JobType(Base):
        id: str
        name: str
        trigger: str
        schedule: str
        checklist: List[str]
        parts_and_tools: List[str]
        safety_and_permit_notes: List[str]
        est_downtime_hours: float = num()
        est_cost_band: CostBand

    class SeverityDef(Base):
        level: int = num()
        definition: str

    class PollutionEffect(Base):
        domain: Domain
        effect: str

    class SnagType(Base):
        id: str
        name: str
        category: Category
        symptoms: List[str]
        root_causes: List[str]
        detection_method: str
        severity_levels: List[SeverityDef]
        immediate_action: str
        recommended_job_id: str
        time_to_fix_hours: float = num()
        parts: List[str]
        safety_hazards: List[str]
        escalation_criteria: str
        pollution_effects: List[PollutionEffect]

    class FormField(Base):
        id: str
        label: str
        type: Literal["text", "dropdown"]
        required: bool = Field(strict=True) if strict else Field()
        max_length: Optional[int] = Field(default=None, strict=True) if strict else None
        options: Optional[List[str]] = None
        help_text: str = ""
        domain: Optional[Domain] = None

    class MachineProfile(Base):
        summary: str
        typical_duty: str
        key_components: List[str]
        specs: List[SpecItem]

    class GeneratedTemplate(Base):
        machine_profile: MachineProfile
        operating_parameters: List[OperatingParam]
        job_types: List[JobType]
        snag_types: List[SnagType]
        pollutant_capture: List[FormField]
        form_fields: List[FormField]

    return SimpleNamespace(SpecItem=SpecItem, OperatingParam=OperatingParam, CostBand=CostBand,
                           JobType=JobType, SeverityDef=SeverityDef, PollutionEffect=PollutionEffect,
                           SnagType=SnagType, FormField=FormField, MachineProfile=MachineProfile,
                           GeneratedTemplate=GeneratedTemplate)


STRICT = _build(True)
LAX = _build(False)
GeneratedTemplate = STRICT.GeneratedTemplate
GeneratedTemplateSchema = LAX.GeneratedTemplate
