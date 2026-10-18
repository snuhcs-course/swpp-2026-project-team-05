"""Regression checks for the CI gate itself; uses only standard-library fixtures."""

import json
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase

from check_coverage import android_groups, python_groups, render


class CoverageGateTests(TestCase):
    def test_backend_report_must_include_every_v2_module(self):
        with TemporaryDirectory() as directory:
            report = Path(directory) / "coverage.json"
            report.write_text(json.dumps({"files": {
                "server/views.py": {"summary": {"covered_lines": 1, "num_statements": 1}},
                "server/analysis/v2_adapter.py": {"summary": {"covered_lines": 1, "num_statements": 1}},
            }}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "missing v2 source"):
                python_groups(report)

    def test_coverage_gate_fails_at_just_below_target(self):
        self.assertFalse(render([("backend", 84, 100, 85)]))
        self.assertTrue(render([("backend", 85, 100, 85)]))

    def test_android_report_must_include_required_packages(self):
        with TemporaryDirectory() as directory:
            report = Path(directory) / "jacoco.xml"
            report.write_text('<report name="fixture"/>', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "missing package"):
                android_groups(report)
