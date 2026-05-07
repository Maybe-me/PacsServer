from __future__ import annotations

from datetime import datetime
from typing import Dict, List, Literal, Optional

from pydantic import BaseModel, Field


class DicomIdentityOverrides(BaseModel):
    patient_id: Optional[str] = None
    patient_name: Optional[str] = None
    accession_number: Optional[str] = None
    study_instance_uid: Optional[str] = None
    series_instance_uid: Optional[str] = None
    sop_instance_uid: Optional[str] = None
    study_date: Optional[str] = None
    study_time: Optional[str] = None
    series_number: Optional[int] = None
    instance_number_start: Optional[int] = 1


class PixelTransformOptions(BaseModel):
    flip_horizontal: bool = False
    flip_vertical: bool = False
    rotate_180: bool = False
    reverse_frames: bool = False


class PacsTarget(BaseModel):
    host: str
    port: int = Field(ge=1, le=65535)
    called_aet: str
    calling_aet: str = "PYGEN"


class GenerateJobRequest(BaseModel):
    template_path: str
    output_directory: Optional[str] = None
    mode: Literal["export", "push_to_pacs", "simulate_upstream_pacs"] = "export"
    recursive: bool = True
    overwrite: bool = False
    identity: DicomIdentityOverrides = Field(default_factory=DicomIdentityOverrides)
    pixel_transform: PixelTransformOptions = Field(default_factory=PixelTransformOptions)
    tag_overrides: Dict[str, str] = Field(default_factory=dict)
    pacs_target: Optional[PacsTarget] = None


class GeneratedInstance(BaseModel):
    source_path: str
    output_path: str
    study_instance_uid: str
    series_instance_uid: str
    sop_instance_uid: str
    modality: Optional[str] = None


class GenerateJobResult(BaseModel):
    job_id: str
    mode: str
    output_directory: Optional[str] = None
    generated_count: int
    pushed_count: int = 0
    instances: List[GeneratedInstance] = Field(default_factory=list)


class GenerateJobStatus(BaseModel):
    job_id: str
    status: Literal["pending", "running", "completed", "failed"]
    request: GenerateJobRequest
    created_at: datetime
    updated_at: datetime
    result: Optional[GenerateJobResult] = None
    error: Optional[str] = None
