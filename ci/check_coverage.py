"""Fail CI when gated Iteration 2 code falls below its published line goals.

The Python report comes from coverage.py JSON and the Android report from
JaCoCo XML. Compose UI is reported separately because its device-test goal is
not yet met by the host-JVM suite.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
from xml.etree import ElementTree


def normalize(path: str) -> str:
    return path.replace("\\", "/").removeprefix("./")


def python_groups(report: Path) -> list[tuple[str, int, int, int | None]]:
    files = json.loads(report.read_text(encoding="utf-8"))["files"]
    lines = {
        normalize(path): (data["summary"]["covered_lines"], data["summary"]["num_statements"])
        for path, data in files.items()
    }

    def totals(paths: list[str]) -> tuple[int, int]:
        if not paths:
            raise ValueError("Coverage report is missing an expected Python source group")
        return sum(lines[path][0] for path in paths), sum(lines[path][1] for path in paths)

    endpoint_paths = ["server/views.py", "server/analysis/v2_adapter.py"]
    missing = [path for path in endpoint_paths if path not in lines]
    if missing:
        raise ValueError(f"Coverage report is missing: {', '.join(missing)}")
    root = Path(__file__).resolve().parents[1]
    v2_paths = sorted(
        path.relative_to(root).as_posix()
        for path in (root / "server/analysis/v2").glob("*.py")
        if path.name != "__init__.py"
    )
    missing = [path for path in v2_paths if path not in lines]
    if missing:
        raise ValueError(f"Coverage report is missing v2 source: {', '.join(missing)}")
    endpoint = totals(endpoint_paths)
    pipeline = totals(v2_paths)
    return [
        ("Django endpoint + v2 adapter", *endpoint, 85),
        ("v2 analysis package", *pipeline, 65),
    ]


def android_groups(report: Path) -> list[tuple[str, int, int, int | None]]:
    root = ElementTree.parse(report).getroot()
    counters = {}
    for package in root.findall("package"):
        counter = package.find("counter[@type='LINE']")
        if counter is not None:
            counters[package.attrib["name"]] = (
                int(counter.attrib["covered"]),
                int(counter.attrib["covered"]) + int(counter.attrib["missed"]),
            )

    prefix = "com/swpp/team5/frameless/"

    def package_lines(name: str) -> tuple[int, int]:
        path = prefix + name
        if path not in counters:
            raise ValueError(f"JaCoCo report is missing package: {path}")
        return counters[path]

    tracked_ui = ["ui", "ui/screens", "ui/components", "ui/theme"]
    ui_counts = [package_lines(name) for name in tracked_ui]
    return [
        ("Android data", *package_lines("data"), 80),
        ("Android presentation state", *package_lines("ui/state"), 85),
        ("Android ViewModel", *package_lines("ui/viewmodel"), 60),
        ("Compose views/theme (device tests pending)",
         sum(count[0] for count in ui_counts), sum(count[1] for count in ui_counts), None),
    ]


def render(groups: list[tuple[str, int, int, int | None]]) -> bool:
    table = ["| Layer | Covered lines | Goal | Result |", "| --- | ---: | ---: | --- |"]
    failed = False
    for name, covered, total, goal in groups:
        if total <= 0:
            raise ValueError(f"{name} has no measured production lines")
        percent = 100 * covered / total
        passes = goal is None or 100 * covered >= goal * total
        failed |= not passes
        target = f"≥{goal}%" if goal is not None else "tracking only"
        result = "PASS" if goal is not None and passes else "FAIL" if not passes else "OPEN GAP"
        print(f"{name}: {covered}/{total} lines ({percent:.1f}%), goal {target}: {result}")
        table.append(f"| {name} | {covered}/{total} ({percent:.1f}%) | {target} | {result} |")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write("## Iteration 2 line coverage\n\n" + "\n".join(table) + "\n\n")
    return not failed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("python", "android"))
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        groups = python_groups(args.report) if args.kind == "python" else android_groups(args.report)
        return 0 if render(groups) else 1
    except (KeyError, OSError, ValueError, ElementTree.ParseError) as exc:
        print(f"Could not check {args.kind} coverage: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
