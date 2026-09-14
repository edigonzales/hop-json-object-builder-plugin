#!/usr/bin/env python3
"""Run the shipped example pipelines against an isolated Hop installation.

Installs the packaged plugin ZIP into the given Hop home, runs every example
pipeline with hop-run and compares the produced JSON documents with the
checked-in expected documents.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PLUGIN_ROOT = "plugins/transforms/hop-json-object-builder"

EXAMPLES = (
    {
        "name": "stac-item",
        "pipeline": "examples/stac-item/stac-item.hpl",
        "output": "stac-item.json",
        "expected": "examples/stac-item/expected/stac-item.json",
    },
    {
        "name": "links-array",
        "pipeline": "examples/links-array/links-array.hpl",
        "output": "links.json",
        "expected": "examples/links-array/expected/links.json",
    },
)


def extract_plugin(zip_path: Path, hop_home: Path) -> None:
    if not zip_path.is_file():
        raise SystemExit(f"Missing plugin ZIP: {zip_path}")
    with zipfile.ZipFile(zip_path) as archive:
        if archive.testzip() is not None:
            raise SystemExit(f"Corrupt plugin ZIP: {zip_path}")
        entries = archive.namelist()
        prefix = PLUGIN_ROOT.rstrip("/") + "/"
        if not any(entry.startswith(prefix) for entry in entries):
            raise SystemExit(f"{zip_path.name} lacks installation root {prefix}")
        for entry in entries:
            path = Path(entry)
            if path.is_absolute() or ".." in path.parts:
                raise SystemExit(f"Unsafe ZIP entry {entry!r} in {zip_path.name}")
        archive.extractall(hop_home)


def write_run_configuration(config: Path) -> None:
    metadata = config / "metadata/pipeline-run-configuration"
    metadata.mkdir(parents=True, exist_ok=True)
    (metadata / "local.json").write_text(
        '{\n'
        '  "name": "local",\n'
        '  "engineRunConfiguration": {"Local": {"rowset_size": "2", "safe_mode": true}},\n'
        '  "configurationVariables": []\n'
        '}\n',
        encoding="utf-8",
    )


def run_pipeline(hop_home: Path, pipeline: Path, output_dir: Path, env: dict[str, str]) -> None:
    command = [
        str(hop_home / "hop-run.sh"),
        "-r",
        "local",
        "-f",
        str(pipeline),
        "-p",
        f"OUTPUT_DIR={output_dir}",
    ]
    print("==>", " ".join(command), flush=True)
    subprocess.run(command, check=True, env=env, timeout=300)


def compare_json(actual_path: Path, expected_path: Path, example: str) -> None:
    if not actual_path.is_file():
        raise SystemExit(f"[{example}] Hop did not create the expected output: {actual_path}")
    actual = json.loads(actual_path.read_text(encoding="utf-8"))
    expected = json.loads(expected_path.read_text(encoding="utf-8"))
    if actual != expected:
        raise SystemExit(
            f"[{example}] Output differs from {expected_path.name}.\n"
            f"--- actual ---\n{json.dumps(actual, indent=2, ensure_ascii=False)}\n"
            f"--- expected ---\n{json.dumps(expected, indent=2, ensure_ascii=False)}"
        )
    print(f"[{example}] output matches {expected_path.relative_to(ROOT)}", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--hop-home", required=True, type=Path)
    parser.add_argument("--plugin-zip", required=True, type=Path)
    args = parser.parse_args()

    hop_home = args.hop_home.resolve()
    if not (hop_home / "hop-run.sh").is_file():
        raise SystemExit(f"Not an Apache Hop home: {hop_home}")
    if (hop_home / PLUGIN_ROOT).exists():
        raise SystemExit(f"Hop home already contains the JSON Object Builder plugin: {hop_home / PLUGIN_ROOT}")

    with tempfile.TemporaryDirectory(prefix="hop-json-object-builder-e2e-") as temporary:
        work = Path(temporary)
        config = work / "config"
        audit = work / "audit"
        output_dir = work / "output"
        output_dir.mkdir()
        audit.mkdir()
        write_run_configuration(config)

        extract_plugin(args.plugin_zip, hop_home)

        env = os.environ.copy()
        env["HOP_CONFIG_FOLDER"] = str(config)
        env["HOP_AUDIT_FOLDER"] = str(audit)
        if env.get("JAVA_HOME"):
            env["HOP_JAVA_HOME"] = env["JAVA_HOME"]

        for example in EXAMPLES:
            run_pipeline(hop_home, ROOT / example["pipeline"], output_dir, env)
            compare_json(
                output_dir / example["output"], ROOT / example["expected"], example["name"]
            )

    print("Installed Hop JSON Object Builder E2E OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
