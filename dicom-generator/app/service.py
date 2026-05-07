from __future__ import annotations

import re
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List

from fastapi import HTTPException
import numpy as np
from pydicom import dcmread
from pydicom.dataset import Dataset, FileMetaDataset
from pydicom.datadict import dictionary_VR, tag_for_keyword
from pydicom.tag import Tag
from pydicom.uid import ExplicitVRLittleEndian, generate_uid
from pynetdicom import AE

from .models import (
    DicomIdentityOverrides,
    GenerateJobRequest,
    GenerateJobResult,
    GenerateJobStatus,
    GeneratedInstance,
    PacsTarget,
    PixelTransformOptions,
)


HEX_TAG_PATTERN = re.compile(r"^[0-9A-Fa-f]{8}$")


@dataclass
class JobRecord:
    job_id: str
    request: GenerateJobRequest
    status: str = "pending"
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    result: GenerateJobResult | None = None
    error: str | None = None

    def to_status(self) -> GenerateJobStatus:
        return GenerateJobStatus(
            job_id=self.job_id,
            status=self.status,
            request=self.request,
            created_at=self.created_at,
            updated_at=self.updated_at,
            result=self.result,
            error=self.error,
        )


class GeneratorService:
    def __init__(self) -> None:
        self._jobs: Dict[str, JobRecord] = {}
        self._lock = threading.Lock()
        self._executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="dicom-generator")

    def submit_job(self, request: GenerateJobRequest) -> GenerateJobStatus:
        job_id = str(uuid.uuid4())
        record = JobRecord(job_id=job_id, request=request)
        with self._lock:
            self._jobs[job_id] = record
        self._executor.submit(self._run_job, job_id)
        return record.to_status()

    def get_job(self, job_id: str) -> GenerateJobStatus:
        with self._lock:
            record = self._jobs.get(job_id)
        if record is None:
            raise HTTPException(status_code=404, detail=f"Job not found: {job_id}")
        return record.to_status()

    def export(self, request: GenerateJobRequest, job_id: str | None = None) -> GenerateJobResult:
        template_path = Path(request.template_path).expanduser().resolve()
        if not template_path.exists():
            raise HTTPException(status_code=404, detail=f"Template path not found: {template_path}")
        if request.mode in {"push_to_pacs", "simulate_upstream_pacs"} and request.pacs_target is None:
            raise HTTPException(status_code=400, detail="pacs_target is required when mode=push_to_pacs")

        files = self._list_template_files(template_path, request.recursive)
        if not files:
            raise HTTPException(status_code=400, detail=f"No DICOM files found under: {template_path}")
        if len(files) > 1 and request.identity.sop_instance_uid:
            raise HTTPException(status_code=400, detail="identity.sop_instance_uid only supports single-file templates")

        output_directory = self._resolve_output_directory(request, job_id)
        output_directory.mkdir(parents=True, exist_ok=True)

        study_uid = request.identity.study_instance_uid or generate_uid()
        generated_instances = self._rewrite_files(
            files,
            template_path,
            output_directory,
            request.identity,
            request.pixel_transform,
            request.tag_overrides,
            study_uid,
            request.overwrite,
        )
        pushed_count = 0

        if request.mode in {"push_to_pacs", "simulate_upstream_pacs"}:
            pushed_count = self._push_instances(generated_instances, request.pacs_target)

        return GenerateJobResult(
            job_id=job_id or "direct",
            mode=request.mode,
            output_directory=str(output_directory),
            generated_count=len(generated_instances),
            pushed_count=pushed_count,
            instances=generated_instances,
        )

    def _run_job(self, job_id: str) -> None:
        with self._lock:
            record = self._jobs[job_id]
            record.status = "running"
            record.updated_at = datetime.now(timezone.utc)

        try:
            result = self.export(record.request, job_id)
            with self._lock:
                record.status = "completed"
                record.result = result
                record.updated_at = datetime.now(timezone.utc)
        except Exception as exc:  # noqa: BLE001
            with self._lock:
                record.status = "failed"
                record.error = str(exc)
                record.updated_at = datetime.now(timezone.utc)

    def _list_template_files(self, template_path: Path, recursive: bool) -> List[Path]:
        if template_path.is_file():
            return [template_path]
        pattern = "**/*.dcm" if recursive else "*.dcm"
        return sorted(path for path in template_path.glob(pattern) if path.is_file())

    def _resolve_output_directory(self, request: GenerateJobRequest, job_id: str | None) -> Path:
        if request.output_directory:
            return Path(request.output_directory).expanduser().resolve()
        base = Path(__file__).resolve().parents[1] / "output"
        if request.mode == "simulate_upstream_pacs":
            base = base / "simulate-upstream-pacs"
        if job_id:
            return (base / job_id).resolve()
        return (base / "direct").resolve()

    def _rewrite_files(
        self,
        files: Iterable[Path],
        template_root: Path,
        output_directory: Path,
        identity: DicomIdentityOverrides,
        pixel_transform: PixelTransformOptions,
        tag_overrides: Dict[str, str],
        study_uid: str,
        overwrite: bool,
    ) -> List[GeneratedInstance]:
        if not isinstance(files, list):
            files = list(files)
        series_uid_map: Dict[str, str] = {}
        generated: List[GeneratedInstance] = []
        total_files = len(files)

        for index, source_path in enumerate(files, start=1):
            dataset = dcmread(str(source_path), force=True)
            original_series_uid = str(getattr(dataset, "SeriesInstanceUID", ""))
            series_uid = identity.series_instance_uid or series_uid_map.setdefault(original_series_uid, generate_uid())
            sop_uid = identity.sop_instance_uid if total_files == 1 and identity.sop_instance_uid else generate_uid()
            instance_number = identity.instance_number_start + index - 1 if identity.instance_number_start is not None else index

            self._rewrite_dataset(
                dataset,
                identity,
                study_uid,
                series_uid,
                sop_uid,
                instance_number,
                pixel_transform,
                tag_overrides,
            )

            relative_parent = source_path.parent.relative_to(template_root) if template_root.is_dir() else Path()
            target_dir = output_directory / relative_parent
            target_dir.mkdir(parents=True, exist_ok=True)
            target_path = target_dir / f"{sop_uid}.dcm"
            if target_path.exists() and not overwrite:
                raise HTTPException(status_code=409, detail=f"Output already exists: {target_path}")

            dataset.save_as(str(target_path), write_like_original=False)
            generated.append(
                GeneratedInstance(
                    source_path=str(source_path),
                    output_path=str(target_path),
                    study_instance_uid=str(dataset.StudyInstanceUID),
                    series_instance_uid=str(dataset.SeriesInstanceUID),
                    sop_instance_uid=str(dataset.SOPInstanceUID),
                    modality=str(getattr(dataset, "Modality", "")) or None,
                )
            )
        return generated

    def _rewrite_dataset(
        self,
        dataset: Dataset,
        identity: DicomIdentityOverrides,
        study_uid: str,
        series_uid: str,
        sop_uid: str,
        instance_number: int,
        pixel_transform: PixelTransformOptions,
        tag_overrides: Dict[str, str],
    ) -> None:
        dataset.PatientID = identity.patient_id or getattr(dataset, "PatientID", "PYGEN")
        if identity.patient_name:
            dataset.PatientName = identity.patient_name
        if identity.accession_number:
            dataset.AccessionNumber = identity.accession_number
        if identity.study_date:
            dataset.StudyDate = identity.study_date
        if identity.study_time:
            dataset.StudyTime = identity.study_time
        if identity.series_number is not None:
            dataset.SeriesNumber = identity.series_number
        dataset.InstanceNumber = instance_number
        dataset.StudyInstanceUID = study_uid
        dataset.SeriesInstanceUID = series_uid
        dataset.SOPInstanceUID = sop_uid

        if not getattr(dataset, "SOPClassUID", None):
            raise HTTPException(status_code=400, detail="Template is missing SOPClassUID")

        if not getattr(dataset, "file_meta", None):
            dataset.file_meta = FileMetaDataset()
        dataset.file_meta.MediaStorageSOPClassUID = dataset.SOPClassUID
        dataset.file_meta.MediaStorageSOPInstanceUID = dataset.SOPInstanceUID
        dataset.file_meta.TransferSyntaxUID = getattr(dataset.file_meta, "TransferSyntaxUID", None) or ExplicitVRLittleEndian

        self._apply_pixel_transform(dataset, pixel_transform)

        for key, value in tag_overrides.items():
            self._apply_tag_override(dataset, key, value)

    def _apply_pixel_transform(self, dataset: Dataset, options: PixelTransformOptions) -> None:
        if not any([options.flip_horizontal, options.flip_vertical, options.rotate_180, options.reverse_frames]):
            return
        if "PixelData" not in dataset:
            raise HTTPException(status_code=400, detail="Pixel transform requested but template has no PixelData")

        try:
            pixels = dataset.pixel_array
        except (AttributeError, NotImplementedError, ValueError) as exc:
            raise HTTPException(status_code=400, detail=f"Unable to decode PixelData for transform: {exc}") from exc

        transformed = pixels
        if options.reverse_frames and transformed.ndim >= 3:
            transformed = np.flip(transformed, axis=0)
        if options.flip_vertical:
            transformed = np.flip(transformed, axis=-2)
        if options.flip_horizontal:
            transformed = np.flip(transformed, axis=-1)
        if options.rotate_180:
            transformed = np.rot90(transformed, 2, axes=(-2, -1))

        transformed = np.ascontiguousarray(transformed)
        dataset.PixelData = transformed.tobytes()

        if transformed.ndim >= 3:
            dataset.NumberOfFrames = int(transformed.shape[0])

    def _apply_tag_override(self, dataset: Dataset, key: str, value: str) -> None:
        tag = self._parse_tag(key)
        vr = dictionary_VR(tag) or "LO"
        if tag in dataset:
            dataset[tag].value = value
        else:
            dataset.add_new(tag, vr, value)

    def _parse_tag(self, key: str) -> Tag:
        normalized = key.replace(",", "").strip()
        if HEX_TAG_PATTERN.fullmatch(normalized):
            return Tag(int(normalized, 16))
        keyword_tag = tag_for_keyword(key)
        if keyword_tag is None:
            raise HTTPException(status_code=400, detail=f"Unsupported DICOM tag override key: {key}")
        return Tag(keyword_tag)

    def _push_instances(self, generated_instances: List[GeneratedInstance], target: PacsTarget | None) -> int:
        if target is None:
            raise HTTPException(status_code=400, detail="pacs_target is required for push mode")

        ae = AE(ae_title=target.calling_aet)
        contexts = {}
        datasets = []
        for instance in generated_instances:
            dataset = dcmread(instance.output_path, force=True)
            transfer_syntax = getattr(getattr(dataset, "file_meta", None), "TransferSyntaxUID", ExplicitVRLittleEndian)
            context_key = (str(dataset.SOPClassUID), str(transfer_syntax))
            if context_key not in contexts:
                ae.add_requested_context(context_key[0], context_key[1])
                contexts[context_key] = True
            datasets.append(dataset)

        association = ae.associate(target.host, target.port, ae_title=target.called_aet)
        if not association.is_established:
            raise HTTPException(status_code=502, detail=f"Failed to associate with PACS {target.host}:{target.port}")

        pushed = 0
        try:
            for dataset in datasets:
                status = association.send_c_store(dataset)
                if status is None or getattr(status, "Status", 0xC000) != 0x0000:
                    raise HTTPException(status_code=502, detail=f"C-STORE failed for SOPInstanceUID {dataset.SOPInstanceUID}")
                pushed += 1
        finally:
            association.release()
        return pushed


service = GeneratorService()
