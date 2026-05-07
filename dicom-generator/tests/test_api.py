from __future__ import annotations

import shutil
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

from app.main import app
from tests.test_generator_service import build_test_dicom


class GeneratorApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="dicom-generator-api-"))
        self.template_file = self.temp_dir / "template.dcm"
        build_test_dicom(self.template_file)
        self.output_dir = self.temp_dir / "output"
        self.client = TestClient(app)

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_export_endpoint_returns_generated_file(self) -> None:
        response = self.client.post(
            "/generate/export",
            json={
                "template_path": str(self.template_file),
                "output_directory": str(self.output_dir),
                "identity": {"patient_id": "API-001"},
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(1, payload["generated_count"])
        self.assertTrue(Path(payload["instances"][0]["output_path"]).exists())

    def test_job_endpoint_transitions_to_completed(self) -> None:
        response = self.client.post(
            "/generate/jobs",
            json={
                "template_path": str(self.template_file),
                "output_directory": str(self.output_dir),
                "identity": {"patient_id": "ASYNC-001"},
            },
        )
        self.assertEqual(200, response.status_code)
        job_id = response.json()["job_id"]

        deadline = time.time() + 5
        while time.time() < deadline:
            status_response = self.client.get(f"/generate/jobs/{job_id}")
            self.assertEqual(200, status_response.status_code)
            status_payload = status_response.json()
            if status_payload["status"] == "completed":
                self.assertEqual(1, status_payload["result"]["generated_count"])
                return
            time.sleep(0.1)

        self.fail("Generator job did not complete in time")

    def test_simulate_upstream_endpoint_pushes_to_target(self) -> None:
        association = MagicMock()
        association.is_established = True
        association.send_c_store.return_value = type("Status", (), {"Status": 0x0000})()
        ae_instance = MagicMock()
        ae_instance.associate.return_value = association

        with patch("app.service.AE", return_value=ae_instance):
            response = self.client.post(
                "/generate/simulate-upstream-pacs",
                json={
                    "template_path": str(self.template_file),
                    "pacs_target": {
                        "host": "127.0.0.1",
                        "port": 11112,
                        "called_aet": "UPSTREAM",
                    },
                },
            )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("simulate_upstream_pacs", payload["mode"])
        self.assertEqual(1, payload["pushed_count"])
