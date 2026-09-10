# inspect.schema

Prints the fully-merged `zilla.yaml` JSON Schema (the base engine schema plus
every binding/catalog/guard/vault/model/store/metric-group patch discovered on
this image's classpath) to stdout, without starting an engine.

## Requirements

- docker compose
- [jq](https://jqlang.org/)

## Setup

`zilla inspect schema` is `@Incubating`, so `ZILLA_INCUBATOR_ENABLED` must be
`true` for the command to exist at all (independent of whether any individual
schema property is itself still marked `x-incubating` in the output).

To run the command via the Docker Compose stack defined in the
[compose.yaml](compose.yaml) file, use:

```bash
docker compose up
```

### Verify behavior

```bash
docker compose logs --no-log-prefix zilla | jq .
```

output: a single JSON Schema document, starting with

```json
{
  "$schema": "https://json-schema.org/draft/2019-09/schema",
  ...
}
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
