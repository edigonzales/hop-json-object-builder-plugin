#!/usr/bin/env python3
"""Build the documentation site precisely from this checkout or a Git revision."""

from __future__ import annotations

import argparse
import hashlib
import http.server
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
BASE = "https://jars.interlis.guru/snapshots/guru/interlis/thoth-biblios/0.0.1-SNAPSHOT"
SOURCE = "https://github.com/edigonzales/hop-json-object-builder-plugin.git"


def run(*args, cwd=None, capture=False):
    return subprocess.run(
        args,
        cwd=cwd or ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
    ).stdout


def snapshot(destination: Path, revision: str | None = None) -> str:
    """Create an isolated documentation source without changing the checkout."""
    paths = ["docs", "examples"]
    if revision:
        commit = run("git", "rev-parse", "--verify", revision + "^{commit}", capture=True).strip()
        names = run("git", "ls-tree", "-r", "--name-only", commit, "--", *paths, capture=True).splitlines()
        for name in names:
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(subprocess.check_output(["git", "show", f"{commit}:{name}"], cwd=ROOT))
        origin = commit
    else:
        for name in paths:
            source = ROOT / name
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(
                source,
                target,
                ignore=shutil.ignore_patterns(
                    "build", ".downloads", ".cache", ".biblios-cache", "biblios.local.yml", "downloads", "__pycache__"
                ),
            )
        origin = run("git", "rev-parse", "HEAD", capture=True).strip() + " + working tree"

    run("git", "init", "-q", "-b", "main", str(destination))
    run("git", "add", ".", cwd=destination)
    run(
        "git",
        "-c",
        "user.name=Biblios local snapshot",
        "-c",
        "user.email=biblios@localhost",
        "-c",
        "commit.gpgsign=false",
        "commit",
        "-qm",
        origin,
        cwd=destination,
    )
    print("Documentation source:", origin, flush=True)
    return origin


def resolve_jar(explicit: str | None = None):
    if explicit:
        jar = Path(explicit).expanduser().resolve(strict=True)
        version = "explicit: " + jar.name
    else:
        with urllib.request.urlopen(BASE + "/maven-metadata.xml", timeout=60) as response:
            metadata = ET.fromstring(response.read())
        versions = [
            node.findtext("value")
            for node in metadata.findall("versioning/snapshotVersions/snapshotVersion")
            if node.findtext("classifier") == "all"
            and node.findtext("extension") == "jar"
            and node.findtext("value")
        ]
        if not versions:
            raise RuntimeError("No all.jar Biblios snapshot in Maven metadata")
        version = versions[-1]
        jar = DOCS / ".downloads" / f"thoth-biblios-{version}-all.jar"
        jar.parent.mkdir(parents=True, exist_ok=True)
        if not jar.exists():
            partial = jar.with_suffix(".partial")
            try:
                with urllib.request.urlopen(BASE + "/" + jar.name, timeout=60) as response, partial.open("wb") as output:
                    shutil.copyfileobj(response, output)
                partial.replace(jar)
            finally:
                partial.unlink(missing_ok=True)

    hasher = hashlib.sha256()
    with jar.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(block)
    digest = hasher.hexdigest()
    print(f"Biblios version: {version}\nBiblios SHA-256: {digest}", flush=True)
    return jar, version, digest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", help="Exact Git revision; CI passes GITHUB_SHA. Default: working files.")
    parser.add_argument("--jar", default=os.environ.get("BIBLIOS_JAR"), help="Optional already downloaded fat JAR")
    parser.add_argument("--serve", action="store_true", help="Serve the completed build locally")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()

    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java"
    jar, version, digest = resolve_jar(args.jar)
    output = ROOT / "target/docs-site"

    with tempfile.TemporaryDirectory(prefix="biblios-source-") as temp:
        source = Path(temp) / "source"
        source.mkdir()
        origin = snapshot(source, args.revision)
        config = (source / "docs/biblios.yml").read_text(encoding="utf-8")
        expected = "url: " + SOURCE
        if config.count(expected) != 1:
            raise RuntimeError("Expected exactly one public Git source in biblios.yml")
        config = config.replace(expected, "url: " + json.dumps(source.as_uri()))
        generated = source / "docs/biblios.local.yml"
        generated.write_text(config, encoding="utf-8")
        run(java, "-jar", str(jar), "build", "--config", str(generated), "--output", str(output), "--clean", cwd=source)
        with (output / "site-assets/styles.css").open("a", encoding="utf-8") as styles:
            styles.write("\n" + (source / "docs/site.css").read_text(encoding="utf-8"))
        shutil.copytree(source / "examples", output / "examples", dirs_exist_ok=True)

    (output / ".nojekyll").touch()
    (output / "build-info.json").write_text(
        json.dumps(dict(source=origin, biblios=version, sha256=digest), indent=2) + "\n",
        encoding="utf-8",
    )
    run("python3", str(ROOT / "scripts/check-docs-site.py"), str(output))

    if args.serve:
        handler = lambda *a, **kw: http.server.SimpleHTTPRequestHandler(*a, directory=str(output), **kw)
        with http.server.ThreadingHTTPServer(("127.0.0.1", args.port), handler) as server:
            print(f"Preview: http://127.0.0.1:{args.port}/ (Ctrl-C stops)", flush=True)
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass


if __name__ == "__main__":
    main()
