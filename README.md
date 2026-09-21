# hop-json-object-builder-plugin

Apache Hop transform plugin that adds generic JSON Object Builder and JSON Array Builder
transforms for typed JSON construction, JSON Pointer insertion and grouped row aggregation.

## Features

Both transforms appear in Hop's **JSON** category and work with native Hop JSON values as well
as JSON text.

| Transform | Plugin ID | Purpose |
|---|---|---|
| JSON Object Builder | `JSON_OBJECT_BUILDER` | Creates a new JSON object from typed mappings or inserts mappings into an existing JSON document at a JSON Pointer. |
| JSON Array Builder | `JSON_ARRAY_BUILDER` | Aggregates rows into a JSON array, optionally grouped by key fields and optionally inserted into an existing JSON document. |

The plugin is useful for object-oriented payloads such as STAC items, GeoJSON, OpenAPI requests
and JSON configuration. It supports dynamic object keys, missing object levels along JSON Pointer
paths, and type-safe conversion without JavaScript.

### JSON Object Builder

The transform can create a new object or insert mappings into an existing JSON document. A mapping
can use a literal or field-based key, a field or literal value, an explicit value type and the
`skip when null` option. Optional grouping merges rows into one object per group, which enables
dynamic keys across multiple rows.

Supported value types include `AUTO`, `STRING`, `INTEGER`, `NUMBER`, `BIGNUMBER`, `BOOLEAN`,
`JSON` and `NULL`. JSON literals are parsed into real JSON structures rather than escaped strings.

### JSON Array Builder

The transform emits either a field value or the whole input row as a JSON array element. It can
aggregate all rows into one array or emit one array per sorted group. Null and empty elements can
be skipped, and the resulting array can be inserted at a JSON Pointer.

### Semantics and limitations

- Native JSON documents and fragments are copied before use; inputs and previously emitted rows are
  not mutated.
- JSON text must contain exactly one complete JSON value; trailing whitespace is allowed.
- Insert mode rejects a null or blank base JSON field instead of inventing a document.
- Grouped modes follow the `Enhanced JSON Output` model and require input sorted by the group fields.
- Only objects are created automatically along JSON Pointer paths; existing array elements can be
  addressed by index and `-` appends to an existing array.
- There is no JSON Schema validation; that remains a separate concern.

## Requirements

- Apache Hop 2.19.0
- Java 21 or newer
- Maven for building from source

Jackson is provided by Apache Hop at runtime and is not bundled in the plugin ZIP.

## Install

Download the published ZIP from the Maven snapshot repository:

```text
https://jars.interlis.guru/snapshots/ch/so/agi/hop-json-object-builder-plugin/0.1.0-SNAPSHOT/
```

Extract it into the Apache Hop installation:

```bash
unzip -o hop-json-object-builder-plugin-<version>.zip -d "$HOP_HOME"
```

The ZIP installs to `plugins/transforms/hop-json-object-builder`. For local development, sync the
locally built plugin with:

```bash
./scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

## Documentation

- Rendered handbook: <https://edigonzales.github.io/hop-json-object-builder-plugin/>
- Canonical source: [`docs/master.adoc`](docs/master.adoc)
- Transform reference: [`docs/transforms/json-object-builder.adoc`](docs/transforms/json-object-builder.adoc)
- User examples: [`examples/README.md`](examples/README.md)
- Installed-plugin scenarios: [`e2e/README.md`](e2e/README.md)
- Biblios configuration: [`docs/biblios.yml`](docs/biblios.yml)

Build and preview the handbook locally:

```bash
python3 scripts/build-docs-site.py --serve
```

## Build and development

Build and test the complete project:

```bash
mvn -U -B -ntp clean verify
```

Run tests without packaging:

```bash
mvn -U -B -ntp clean test
```

Validate the installation ZIP and repository contract:

```bash
python3 scripts/verify-package.py
python3 .ci/hop-plugin-ci/scripts/check-plugin-repository.py --profile multi-module-suite
```

The installed-plugin E2E test requires a clean Apache Hop 2.19.0 client:

```bash
python3 scripts/run-e2e.py --hop-home "$HOP_HOME/hop" --plugin-zip <canonical ZIP>
```

The Maven test suite also loads the example pipelines to keep their XML and transform metadata
in sync. Dialog tests use the test-only `hop-ui-rcp` dependency and require a desktop display or
Xvfb on headless Linux.

## Modules and artifacts

- `hop-json-object-builder-core`: Jackson-based JSON Pointer editing, conversion and serialization
  without a Hop dependency.
- `hop-transform-json-object-builder`: both transforms, metadata, dialogs, icons and tests.
- `assemblies/assemblies-hop-json-object-builder`: installable plugin ZIP.

The build produces:

- `hop-json-object-builder-core/target/hop-json-object-builder-core-<version>.jar`
- `hop-transform-json-object-builder/target/hop-transform-json-object-builder-<version>.jar`
- `assemblies/assemblies-hop-json-object-builder/target/hop-json-object-builder-plugin-<version>.zip`

The ZIP contains one transform JAR and the core JAR under `lib/`.

## CI and publication

GitHub Actions tests Java 21 and 25 on Ubuntu, macOS and Windows. Ubuntu with Java 21 is the
canonical build: it runs `clean verify`, validates the ZIP with `scripts/verify-package.py`,
checks the repository contract and creates the only publishable bundle. A separate job installs
that exact canonical ZIP into a clean Apache Hop 2.19.0 client and runs both example pipelines.

Pushes to `main` publish the verified snapshot without rebuilding it:

```text
ch.so.agi:hop-json-object-builder-plugin:0.1.0-SNAPSHOT
```

Publication uses `https://jars.interlis.guru/snapshots/` and maps the protected repository secrets
`INTERLIS_MAVEN_USERNAME` and `INTERLIS_MAVEN_TOKEN` to the shared `hop-plugin-ci` workflow.
Pull requests publish neither Maven artifacts nor documentation pages.

## License

See [LICENSE](LICENSE).
