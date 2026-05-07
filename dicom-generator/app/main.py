from __future__ import annotations

from fastapi import FastAPI

from .models import GenerateJobRequest, GenerateJobResult, GenerateJobStatus
from .service import service

app = FastAPI(title="dicom-generator", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/generate/jobs", response_model=GenerateJobStatus)
def create_job(request: GenerateJobRequest) -> GenerateJobStatus:
    return service.submit_job(request)


@app.get("/generate/jobs/{job_id}", response_model=GenerateJobStatus)
def get_job(job_id: str) -> GenerateJobStatus:
    return service.get_job(job_id)


@app.post("/generate/export", response_model=GenerateJobResult)
def export(request: GenerateJobRequest) -> GenerateJobResult:
    normalized = request.model_copy(update={"mode": "export"})
    return service.export(normalized)


@app.post("/generate/push-to-pacs", response_model=GenerateJobResult)
def push_to_pacs(request: GenerateJobRequest) -> GenerateJobResult:
    normalized = request.model_copy(update={"mode": "push_to_pacs"})
    return service.export(normalized)


@app.post("/generate/simulate-upstream-pacs", response_model=GenerateJobResult)
def simulate_upstream_pacs(request: GenerateJobRequest) -> GenerateJobResult:
    normalized = request.model_copy(update={"mode": "simulate_upstream_pacs"})
    return service.export(normalized)
