# JSON Schema conformance test data

The `draft4/`, `draft6/`, and `draft7/` directories vendor a curated subset of
the official [JSON Schema Test Suite][suite], used by
`JsonSchemaConformanceTest` to verify `JsonSchema` against the specification.

## Scope

Only the keyword files exercised by the validator are vendored. Excluded by
design:

- `format` — the validator treats `format` as an annotation, not an assertion.
- `ref`/`definitions`/remote refs — `$ref` resolution is covered by
  `JsonSchemaRefTest`; the vendored suite focuses on assertion keywords.
- `2019-09`/`2020-12` dialects and their keywords (`dependentRequired`,
  `dependentSchemas`, `$defs`-only refs, `unevaluated*`, `$dynamicRef`, …).
- `optional/` cases (non-normative behavior).

Within a vendored file, individual test groups that use a feature outside the
validator's surface (for example a structural `enum`/`const` value) compile to
an `UnsupportedOperationException` and are reported as skipped rather than
failed by the harness.

## Provenance

Files were copied verbatim from the `main` branch of `json-schema-org/
JSON-Schema-Test-Suite`. The upstream suite is distributed under the MIT
License; see `LICENSE` in this directory. Update by re-copying the same
keyword files from upstream.

[suite]: https://github.com/json-schema-org/JSON-Schema-Test-Suite
