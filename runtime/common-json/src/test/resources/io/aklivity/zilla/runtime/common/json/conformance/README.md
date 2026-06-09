# JSON Schema conformance test data

The `draft4/`, `draft6/`, `draft7/`, `draft2019-09/`, and `draft2020-12/`
directories vendor a curated subset of the official [JSON Schema Test
Suite][suite], used by `JsonSchemaConformanceTest` to verify `JsonSchema`
against the specification across all five drafts. The `metaschema/` directory
holds the draft-04/06/07 meta-schemas, served to the harness via a
`JsonRefResolver` so that `definitions.json` can validate schemas against their
meta-schema.

## Scope

The vendored keyword files cover the validator's assertion and applicator
surface. `format` is included and expected to behave as an annotation (never an
assertion), matching the suite's main `format.json` and the prior leadpony
justify default.

Excluded by design:

- `optional/` cases, including `optional/format/*` (format-assertion vocabulary)
  — non-normative behavior.
- `refRemote.json` and the multi-document `ref.json` remote cases — the
  `$ref`/`$id`/`$anchor` engine is covered by `JsonSchemaRefResolutionTest`,
  `JsonSchemaRefTest`, and the meta-schema validation in `definitions.json`.
- `$dynamicRef`/`$dynamicAnchor` and `$recursiveRef`/`$recursiveAnchor`
  (dynamic-scope refs), and `unevaluatedProperties`/`unevaluatedItems`
  (annotation collection) — pending; the 2019/2020 `not.json` is therefore not
  yet vendored, as it exercises annotations inside `not`.

Within a vendored file, a test group whose schema uses an ECMA-262 regex
construct unsupported by `java.util.regex` is reported as skipped rather than
failed (a regex-dialect limitation, not a validator-logic gap).

## Provenance

Files were copied verbatim from the `main` branch of `json-schema-org/
JSON-Schema-Test-Suite`; the meta-schemas from `json-schema.org`. The upstream
suite is distributed under the MIT License; see `LICENSE` in this directory.
Update by re-copying the same files from upstream.

[suite]: https://github.com/json-schema-org/JSON-Schema-Test-Suite
