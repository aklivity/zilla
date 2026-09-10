# inspect.schema

Prints the fully-merged `zilla.yaml` JSON Schema (the base engine schema plus
every binding/catalog/guard/vault/model/store/metric-group patch discovered on
this image's classpath) to stdout, without starting an engine.

## Requirements

- docker compose

## Setup

`zilla inspect schema` is `@Incubating`, so `ZILLA_INCUBATOR_ENABLED` must be
`true` for the command to exist at all (independent of whether any individual
schema property is itself still marked `x-incubating` in the output).

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml)
file, use:

```bash
docker compose up -d --wait
```

### Verify behavior

`zilla inspect schema` needs no running engine and no `zilla.yaml` of its own
-- the stack above only exists to give a healthy container to run the command
inside, alongside its own unrelated `start`ed engine, to prove the two don't
interfere:

```bash
docker compose exec zilla zilla inspect schema
```

output: a single JSON Schema document, byte-for-byte identical to the checked-in
golden file at [.github/schema.expected.json](.github/schema.expected.json) --
see that file for the exact, current expected output. [.github/test.sh](.github/test.sh)
diffs the live command's output against it, so this example fails the moment
the merged `zilla.yaml` JSON Schema changes anywhere in the repo, not just on
a handful of spot checks.

After an intentional schema change, regenerate the golden file with:

```bash
docker compose exec zilla zilla inspect schema > .github/schema.expected.json
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
