from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from pydicom import dcmread
from pydicom.dataset import FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian, generate_uid

from app.models import DicomIdentityOverrides, GenerateJobRequest, PacsTarget
from app.service import GeneratorService


def build_test_dicom(path: Path, pixel_data: bytes = b"\x00\x01", number_of_frames: int | None = None) -> None:
    file_meta = FileMetaDataset()
    file_meta.MediaStorageSOPClassUID = "1.2.840.10008.5.1.4.1.1.2"
    file_meta.MediaStorageSOPInstanceUID = generate_uid()
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian

    dataset = FileDataset(str(path), {}, file_meta=file_meta, preamble=b"\0" * 128)
    dataset.PatientID = "TEMPLATE"
    dataset.PatientName = "Template^Patient"
    dataset.AccessionNumber = "ACC-TEMPLATE"
    dataset.StudyInstanceUID = generate_uid()
    dataset.SeriesInstanceUID = generate_uid()
    dataset.SOPInstanceUID = file_meta.MediaStorageSOPInstanceUID
    dataset.SOPClassUID = file_meta.MediaStorageSOPClassUID
    dataset.Modality = "CT"
    dataset.StudyDate = "20240501"
    dataset.StudyTime = "101010"
    dataset.Rows = 1
    dataset.Columns = 2
    dataset.SamplesPerPixel = 1
    dataset.PhotometricInterpretation = "MONOCHROME2"
    dataset.BitsAllocated = 8
    dataset.BitsStored = 8
    dataset.HighBit = 7
    dataset.PixelRepresentation = 0
    if number_of_frames is not None:
        dataset.NumberOfFrames = number_of_frames
    dataset.PixelData = pixel_data
    dataset.save_as(str(path), write_like_original=False)


class GeneratorServiceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="dicom-generator-test-"))
        self.template_file = self.temp_dir / "template.dcm"
        build_test_dicom(self.template_file)
        self.output_dir = self.temp_dir / "output"
        self.service = GeneratorService()

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_export_rewrites_identity_and_tags(self) -> None:
        request = GenerateJobRequest(
            template_path=str(self.template_file),
            output_directory=str(self.output_dir),
            identity=DicomIdentityOverrides(
                patient_id="PAT-001",
                patient_name="Generated^Patient",
                accession_number="ACC-001",
                study_date="20260504",
                study_time="235959",
                series_number=7,
                instance_number_start=11,
            ),
            tag_overrides={"StudyDescription": "Generated Study"},
        )

        result = self.service.export(request, job_id="unit-test")

        self.assertEqual(1, result.generated_count)
        output_path = Path(result.instances[0].output_path)
        self.assertTrue(output_path.exists())

        dataset = dcmread(str(output_path), force=True)
        self.assertEqual("PAT-001", dataset.PatientID)
        self.assertEqual("Generated^Patient", str(dataset.PatientName))
        self.assertEqual("ACC-001", dataset.AccessionNumber)
        self.assertEqual("20260504", dataset.StudyDate)
        self.assertEqual("235959", dataset.StudyTime)
        self.assertEqual(7, int(dataset.SeriesNumber))
        self.assertEqual(11, int(dataset.InstanceNumber))
        self.assertEqual("Generated Study", dataset.StudyDescription)

    def test_export_applies_pixel_transform(self) -> None:
        transformed_template = self.temp_dir / "pixel-template.dcm"
        build_test_dicom(transformed_template, pixel_data=b"\x00\x01")

        request = GenerateJobRequest(
            template_path=str(transformed_template),
            output_directory=str(self.output_dir),
            pixel_transform={"flip_horizontal": True},
        )

        result = self.service.export(request, job_id="pixel-transform")

        dataset = dcmread(result.instances[0].output_path, force=True)
        self.assertEqual(b"\x01\x00", dataset.PixelDat

    def test_push_to_pacs_sends_generated_instances(self) -> None:
        request = GenerateJobRequest(
            template_path=str(self.template_file),
            output_directory=str(self.output_dir),
            mode="push_to_pacs",
            pacs_target=PacsTarget(host="127.0.0.1", port=11112, called_aet="MY_PACS"),
        )

        association = MagicMock()
        association.is_established = True
        association.send_c_store.return_value = type("Status", (), {"Status": 0x0000})()
        ae_instance = MagicMock()
        ae_instance.associate.return_value = association

        with patch("app.service.AE", return_value=ae_instance):
            result = self.service.export(request, job_id="push-test")

        self.assertEqual(1, result.generated_count)
        self.assertEqual(1, result.pushed_count)
        ae_instance.add_requested_context.assert_called_once()
        association.send_c_store.assert_called_once()
        association.release.assert_called_once()

    def test_simulate_upstream_pacs_mode_pushes_and_uses_dedicated_output_root(self) -> None:
        request = GenerateJobRequest(
            template_path=str(self.template_file),
            mode="simulate_upstream_pacs",
            pacs_target=PacsTarget(host="127.0.0.1", port=11112, called_aet="UPSTREAM"),
        )

        association = MagicMock()
        association.is_established = True
        association.send_c_store.return_value = type("Status", (), {"Status": 0x0000})()
        ae_instance = MagicMock()
        ae_instance.associate.return_value = association

        with patch("app.service.AE", return_value=ae_instance):
            result = self.service.export(request, job_id="simulate-upstream")

        self.assertEqual("simulate_upstream_pacs", result.mode)
        self.assertEqual(1, result.pushed_count)
        self.assertIn("simulate-upstream-pacs", result.output_directory)

