# hop-json-object-builder-plugin

Apache Hop transform plugin that adds generic, practical JSON building blocks: the
**JSON Object Builder** and the **JSON Array Builder**. Both work with real JSON values
(Hop `ValueMetaJson`) and JSON text, use Jackson and support JSON Pointer (RFC 6901)
navigation for inserting fragments into existing documents.

## Why this plugin exists

Hop already ships strong JSON transforms. `JSON Input` reads JSON into rows, `JSON Output`
writes rows to JSON files and `Enhanced JSON Output` groups rows into arrays and nested
objects. What is missing for object-oriented payloads such as STAC items, GeoJSON, OpenAPI
requests or JSON configuration is:

- **dynamic object keys**: `"assets": { "geoparquet": { ... }, "interlis": { ... } }` where
  the key names come from a field of each row
- **inserting into an existing document** at a JSON Pointer (`/properties`, `/assets`)
  while creating missing object levels
- a mapping-driven, type-safe way to distinguish `"42"` (string) from `42` (number) and
  from `{...}` (JSON fragment) without JavaScript

The plugin stays generic on purpose: it is not a STAC writer. Any JSON document that is
assembled from typed rows can be built with it.

## Transforms

Both transforms appear in the **JSON** category.

| Transform | Plugin ID | Purpose |
|---|---|---|
| JSON Object Builder | `JSON_OBJECT_BUILDER` | Creates a new JSON object from typed key/value mappings or inserts the mappings into an existing JSON document at a JSON Pointer. |
| JSON Array Builder | `JSON_ARRAY_BUILDER` | Aggregates rows into a JSON array, optionally grouped by key fields and optionally inserted into an existing JSON document at a JSON Pointer. |

### JSON Object Builder

| Option | Meaning |
|---|---|
| Output field / output type | Field name and Hop type of the result: `JSON` (native `JsonNode`) or `STRING` (serialized JSON, compact or pretty printed). |
| Source | `Create new JSON object` or `Insert into existing JSON object`. |
| Base JSON field / JSON Pointer | In insert mode: the input field containing the base document (JSON or text) and the RFC 6901 pointer of the target object. Missing object levels are created. An empty pointer targets the root. |
| Field mappings | One row per key/value pair: key (literal or field), value (field or literal), value type, and "skip when null". |
| Group by fields | Optional. When set, all rows of a group are merged into **one** JSON object per group; the output row contains the group by fields and the JSON field. Dynamic keys across multiple rows require this mode. |

Value types: `AUTO` uses the input field type (String, Integer, Number, Big number,
Boolean, JSON, Date/Timestamp as string). `STRING`, `INTEGER`, `NUMBER`, `BIGNUMBER`,
`BOOLEAN`, `JSON` and `NULL` force a conversion. `JSON` parses text such as
`["data"]`, `{"href": "..."}` or `null` into real JSON structures instead of escaped
strings.

If the output field name equals an existing input field name, the field is replaced in
place (including its type). This allows FME-like chains: `item_json` is built, extended
with `/properties`, extended with `/assets` — the document grows in one field.

### JSON Array Builder

| Option | Meaning |
|---|---|
| Output field / output type | As above. |
| Element | `Field value` (with value type) or `Whole input row as JSON object`. |
| Target | `Insert into existing JSON object` with base JSON field and JSON Pointer; otherwise the array itself is emitted. |
| Group by fields | Optional. One array per group; the output row contains the group by fields and the array field. Without grouping all rows become a single array, emitted once at the end of the stream (an empty stream produces `[]`). |
| Skip null elements | Null and empty elements (or field values in whole-row mode) are omitted. |

### Null and empty handling

- `skip when null` / `Skip null elements` skip both Java `null` and empty string values.
- Without the flag, `null` becomes JSON `null`; empty strings stay empty strings.
- In insert mode a null or blank base JSON field is an error: the transform never
  invents a document silently.

### Grouping rules

Grouped modes follow the `Enhanced JSON Output` model: the input must be **sorted by the
group by fields**, otherwise rows of the same group are emitted as separate documents.
The transforms log a hint on startup; the shipped example pipelines use `Sort rows`
before grouped transforms. Grouped output rows contain only the group by fields and the
JSON field.

## Modules

- `./hop-json-object-builder-core` — Jackson-based JSON Pointer editing, literal/value
  conversion and serialization; no Hop dependency.
- `./hop-transform-json-object-builder` — both transforms with metadata, dialogs, icons
  and tests.
- `./assemblies/assemblies-hop-json-object-builder` — installation ZIP for
  `plugins/transforms/hop-json-object-builder`.

## Build

```bash
mvn clean verify
```

Build prerequisites: Java 21 (Java 25 is covered by the compatibility matrix) and Maven.
Apache Hop `2.19.0` is resolved from Maven Central; Jackson comes from Hop at runtime and
is not bundled.

## Produced artifacts

- Core JAR: `hop-json-object-builder-core/target/hop-json-object-builder-core-<version>.jar`
- Transform JAR: `hop-transform-json-object-builder/target/hop-transform-json-object-builder-<version>.jar`
- Plugin ZIP: `assemblies/assemblies-hop-json-object-builder/target/hop-json-object-builder-plugin-<version>.zip`

The ZIP installs to `plugins/transforms/hop-json-object-builder` and contains both
transforms (one plugin JAR) plus the core JAR under `lib/`.

## Install in Hop

```bash
unzip -o assemblies/assemblies-hop-json-object-builder/target/hop-json-object-builder-plugin-<version>.zip -d "$HOP_HOME"
```

Fast local sync:

```bash
./scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

## Examples

The [`examples`](examples/README.md) directory contains two runnable pipelines that are
also the installed-Hop E2E tests:

- `examples/stac-item/stac-item.hpl` — one row per asset; builds a STAC item with
  nested `properties`, a real `bbox`/`geometry` and a dynamic `assets` object.
- `examples/links-array/links-array.hpl` — one row per link; builds a grouped JSON array
  and demonstrates `skip when null` for a link without media type.

```bash
"$HOP_HOME/hop-run.sh" -r local -f examples/stac-item/stac-item.hpl -p OUTPUT_DIR=/tmp
```

The installed-plugin E2E runs both pipelines and compares the produced JSON documents
with the checked-in expected documents:

```bash
python3 scripts/run-e2e.py --hop-home "$HOP_HOME" --plugin-zip path/to/hop-json-object-builder-plugin-<version>.zip
```

## CI and publication

GitHub Actions uses the shared `edigonzales/hop-plugin-ci` contract. The matrix runs Java
21 and 25 on Ubuntu, macOS and Windows. Ubuntu with Java 21 is the canonical run: it
executes `clean verify`, validates the installation ZIP with `scripts/verify-package.py`
and creates the only publishable bundle. A separate job installs the canonical ZIP into a
clean Apache Hop 2.19.0 client and runs `scripts/run-e2e.py`. A push to `main` publishes
the verified snapshot without rebuilding it.

The published Maven ZIP coordinate is:

```text
ch.so.agi:hop-json-object-builder-plugin:0.1.0-SNAPSHOT
```

Publication uses `INTERLIS_MAVEN_USERNAME` / `INTERLIS_MAVEN_TOKEN` and the shared
`hop-plugin-ci` workflow.

## Tests

The automated test suite covers:

- JSON Pointer editing: creating missing object levels, overwriting, dynamic keys,
  array navigation, `-` append, escaping, error cases
- literal conversion for all value types, including JSON literals
- transform metadata defaults, clone behavior, XML roundtrip and validation remarks
- runtime behavior: row-wise object creation, pointer insertion, grouped dynamic keys,
  in-place replacement, skip-when-null/empty, invalid base JSON, array aggregation with
  and without grouping, whole-row elements, array insertion at a pointer
- plugin contract (IDs, category, dialog classes, packaged icons)
- loading the shipped example pipelines with Hop 2.19 to keep them in sync
- the installed-plugin E2E in CI

Run tests only:

```bash
mvn test
```

## Limitations

- Grouped modes buffer the rows of a group and require sorted input (same as
  `Enhanced JSON Output`).
- Only objects are created automatically along JSON Pointers; existing array elements can
  be addressed by index, and `-` appends to an existing array.
- JSON Pointer insertion replaces an existing value at the target key.
- There is no JSON Schema validation in this plugin; validation of the produced document
  is a separate concern.
- Element/row conversion uses the Hop value type; explicit Date/Timestamp JSON types are
  not offered (they are written as formatted strings).

## License

[MIT](LICENSE).
