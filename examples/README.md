# Examples

Two runnable pipelines demonstrate the JSON Object Builder and the JSON Array Builder.
Both use `Data Grid` inputs, so they need no external files, and both write their result
to `${OUTPUT_DIR}` (parameter, default `${java.io.tmpdir}`).

## STAC item (`stac-item/stac-item.hpl`)

One row per asset. The item fields repeat on every row:

```
Read STAC metadata
  -> Sort by item
  -> Asset object      (JSON Object Builder, create: href, type, title, roles = ["data"])
  -> Item base         (JSON Object Builder, create: stac_version, type, id, collection, bbox, geometry)
  -> Properties        (JSON Object Builder, insert /properties: datetime, title)
  -> Assets            (JSON Object Builder, insert /assets, key from asset_key,
                        group by item_id -> one object per item)
  -> Write STAC item   (Text File Output -> ${OUTPUT_DIR}/stac-item.json)
```

It shows dynamic object keys (`"assets": { "geoparquet": {...}, "interlis": {...} }`),
JSON Pointer insertion, real JSON fragments (`bbox`, `geometry`, `roles`) and in-place
updates of the `item_json` field. The expected document is
[`stac-item/expected/stac-item.json`](stac-item/expected/stac-item.json).

## Links array (`links-array/links-array.hpl`)

One row per link:

```
Read links
  -> Sort by item and rel
  -> Link object       (JSON Object Builder, create: rel, href, type <- media_type,
                        skip when null)
  -> Links array       (JSON Array Builder, element link_json, group by item_id)
  -> Write links       (Text File Output -> ${OUTPUT_DIR}/links.json)
```

The `root` link has no media type; the `skip when null` mapping omits the `type` key for
it. The expected document is
[`links-array/expected/links.json`](links-array/expected/links.json).

## Run

Open the pipelines in the Hop GUI and press Run, or use `hop-run`:

```bash
"$HOP_HOME/hop-run.sh" -r local -f examples/stac-item/stac-item.hpl -p OUTPUT_DIR=/tmp
"$HOP_HOME/hop-run.sh" -r local -f examples/links-array/links-array.hpl -p OUTPUT_DIR=/tmp
```

The installed-plugin E2E test executes exactly these pipelines:

```bash
python3 scripts/run-e2e.py --hop-home "$HOP_HOME" --plugin-zip path/to/hop-json-object-builder-plugin-<version>.zip
```
