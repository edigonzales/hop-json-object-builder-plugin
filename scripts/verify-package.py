#!/usr/bin/env python3
"""Validate the canonical JSON Object Builder installation ZIP."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
POM_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}"
PLUGIN_ROOT = "plugins/transforms/hop-json-object-builder"
FORBIDDEN_JAR_NAMES = (
    "jackson",
    "hop-core",
    "hop-engine",
    "hop-ui",
    "swt",
    "jandex",
)


def project_version() -> str:
    root = ET.parse(ROOT / "pom.xml").getroot()
    version = root.findtext(f"{POM_NAMESPACE}version") or root.findtext("version")
    if not version:
        raise SystemExit("Could not resolve project.version from pom.xml")
    return version


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_safe_path(name: str) -> None:
    path = Path(name)
    if path.is_absolute() or ".." in path.parts:
        raise SystemExit(f"ZIP contains an unsafe path: {name}")


def validate_jar(name: str, content: bytes, required: set[str]) -> None:
    with zipfile.ZipFile(io.BytesIO(content)) as jar:
        if jar.testzip() is not None:
            raise SystemExit(f"JAR is corrupt: {name}")
        entries = set(jar.namelist())
        classes = [entry for entry in entries if entry.endswith(".class")]
        forbidden_prefixes = ("org/apache/hop/", "org/eclipse/swt/", "com/fasterxml/jackson/")
        if any(entry.startswith(forbidden_prefixes) for entry in classes):
            raise SystemExit(f"JAR embeds Hop, SWT or Jackson classes: {name}")
        missing = sorted(required - entries)
        if missing:
            raise SystemExit(f"JAR {name} is missing required entries: {missing}")


def validate(path: Path, version: str) -> dict[str, object]:
    expected_name = f"hop-json-object-builder-plugin-{version}.zip"
    if not path.is_file():
        raise SystemExit(f"Missing package ZIP: {path}")
    if path.name != expected_name:
        raise SystemExit(f"Unexpected package name {path.name!r}; expected {expected_name!r}")

    candidate_zips = sorted(path.parent.glob("hop-json-object-builder-plugin-*.zip"))
    if candidate_zips != [path]:
        raise SystemExit(f"Expected exactly one plugin ZIP in {path.parent}, found {candidate_zips}")

    plugin_jar_name = f"{PLUGIN_ROOT}/hop-transform-json-object-builder-{version}.jar"
    core_jar_name = f"{PLUGIN_ROOT}/lib/hop-json-object-builder-core.jar"
    object_icon = "ch/so/agi/hop/json/builder/transform/icons/json-object-builder.svg"
    array_icon = "ch/so/agi/hop/json/builder/transform/icons/json-array-builder.svg"

    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise SystemExit(f"Package ZIP is corrupt: {path}")
        entries = archive.namelist()
        for entry in entries:
            check_safe_path(entry)
        files = {entry for entry in entries if not entry.endswith("/")}

        plugin_jars = sorted(
            entry
            for entry in files
            if entry.startswith(f"{PLUGIN_ROOT}/")
            and entry.endswith(".jar")
            and "/lib/" not in entry
        )
        if plugin_jars != [plugin_jar_name]:
            raise SystemExit(
                f"Expected exactly one plugin JAR {plugin_jar_name!r}, found {plugin_jars}"
            )

        library_jars = sorted(
            entry
            for entry in files
            if entry.startswith(f"{PLUGIN_ROOT}/lib/") and entry.endswith(".jar")
        )
        if library_jars != [core_jar_name]:
            raise SystemExit(
                f"Expected exactly one core runtime JAR {core_jar_name!r}, found {library_jars}"
            )

        forbidden = sorted(
            entry
            for entry in files
            if entry.endswith(".jar")
            and any(name in Path(entry).name for name in FORBIDDEN_JAR_NAMES)
        )
        if forbidden:
            raise SystemExit(f"Package contains bundled libraries that must come from Hop: {forbidden}")

        plugin_content = archive.read(plugin_jar_name)
        core_content = archive.read(core_jar_name)
        validate_jar(
            plugin_jar_name,
            plugin_content,
            {
                "META-INF/jandex.idx",
                "ch/so/agi/hop/json/builder/transform/JsonObjectBuilderMeta.class",
                "ch/so/agi/hop/json/builder/transform/JsonArrayBuilderMeta.class",
                object_icon,
                array_icon,
            },
        )
        validate_jar(core_jar_name, core_content, set())

        for icon in (object_icon, array_icon):
            source_icon = ROOT / "hop-transform-json-object-builder/src/main/resources" / icon
            if not source_icon.is_file():
                raise SystemExit(f"Missing source icon: {source_icon}")
            with zipfile.ZipFile(io.BytesIO(plugin_content)) as plugin_jar:
                packaged_icon = plugin_jar.read(icon)
            if source_icon.read_bytes() != packaged_icon:
                raise SystemExit(f"Packaged {Path(icon).name} differs from the source resource")

    return {
        "schemaVersion": 1,
        "version": version,
        "zipFile": str(path),
        "sha256": sha256_file(path),
        "pluginRoot": PLUGIN_ROOT,
        "pluginJar": plugin_jar_name,
        "pluginJarSha256": sha256_bytes(plugin_content),
        "coreJar": core_jar_name,
        "coreJarSha256": sha256_bytes(core_content),
    }


def main() -> int:
    version = project_version()
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--zip",
        type=Path,
        default=ROOT
        / f"assemblies/assemblies-hop-json-object-builder/target/hop-json-object-builder-plugin-{version}.zip",
    )
    args = parser.parse_args()
    report = validate(args.zip, version)
    output = ROOT / "target/package-verification.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
