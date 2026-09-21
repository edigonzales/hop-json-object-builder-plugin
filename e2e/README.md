# Installed-plugin E2E scenarios

The installed-plugin scenarios are the two runnable pipelines under `examples/`:

- `examples/stac-item/stac-item.hpl`
- `examples/links-array/links-array.hpl`

`scripts/run-e2e.py` installs the canonical plugin ZIP into a clean Apache Hop 2.19.0
client, runs both pipelines, and compares their JSON output with the checked-in expected
documents. The same examples are loaded by the Maven test suite to keep the pipeline XML
and transform metadata synchronized.
