# AGENTS.md

## CI and tests

Before changing pipelines or test setup, read the
[shared CI contract](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/ci-contract.md).
Also read the
[plugin repository contract](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/plugin-repository-contract.md).
Use the interfaces at this repo's workflow and helper revisions (`main`); the documentation
link follows main and does not upgrade those revisions.

Run commands from the repository root.

Prerequisites:

- JDK 21 or newer and Maven
- Apache Hop 2.19.0 client ZIP for installed-plugin tests (CI downloads it; locally use an
  existing Hop home)

Exact commands:

- Canonical build: `mvn -U -B -ntp clean verify`
- Compatibility build (other OS/Java cells): `mvn -U -B -ntp clean test`
- Package check (canonical only): `python3 scripts/verify-package.py`
- Repository contract check (canonical only):
  `python3 .ci/hop-plugin-ci/scripts/check-plugin-repository.py --profile multi-module-suite`
- Documentation site build and check: `python3 scripts/build-docs-site.py`
- Installed E2E (after extracting the canonical ZIP into a clean Hop 2.19.0 client):
  `python3 scripts/run-e2e.py --hop-home "$HOP_HOME/hop" --plugin-zip <canonical ZIP>`
- Local plugin sync into a Hop home: `./scripts/dev-sync-hop-plugin.sh "$HOP_HOME"`

The canonical ZIP is
`assemblies/assemblies-hop-json-object-builder/target/hop-json-object-builder-plugin-<version>.zip`
and installs to `plugins/transforms/hop-json-object-builder`.

Workflow layout: `.github/workflows/ci.yml` runs `lint` (actionlint), `verify`
(`verify.yml` delegating to `hop-plugin-ci/plugin-verify.yml`), `installed-e2e` (clean Hop
2.19.0 client plus the canonical ZIP) and `publish` (snapshot via
`hop-plugin-ci/plugin-publish.yml` on `main`).
`.github/workflows/biblios-docs.yml` builds the documentation site for pull requests and
deploys it from `main`; pull requests publish no Maven or Pages artifacts.

Snapshot publication uses `ch.so.agi:hop-json-object-builder-plugin` at
`https://jars.interlis.guru/snapshots/` and the repository secrets
`INTERLIS_MAVEN_USERNAME` and `INTERLIS_MAVEN_TOKEN`.

The example pipelines in `examples/` are part of the test contract:
`JsonBuilderExamplesTest` loads them with Hop 2.19 and `scripts/run-e2e.py` executes them
against the installed plugin and compares the output with the checked-in expected JSON
documents. Keep example XML, transform metadata and expected documents in sync.
