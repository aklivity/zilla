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

All five drafts validate the full assertion + applicator surface, including
`$ref`/`$id`/`$anchor`, `$dynamicRef`/`$dynamicAnchor`,
`$recursiveRef`/`$recursiveAnchor`, and `unevaluatedProperties`/
`unevaluatedItems`. Remote documents (the official `remotes/` corpus and the
draft meta-schemas) are vendored under `remotes/` and `metaschema/` and served
to the harness through a `JsonRefResolver`.

Excluded by design:

- `optional/` cases, including `optional/format/*` (format-assertion vocabulary)
  — non-normative behavior.
- `refRemote.json` and the standalone `ref.json` files — the
  `$ref`/`$id`/`$anchor` engine is exercised by `JsonSchemaRefResolutionTest`,
  `JsonSchemaRefTest`, the `definitions`/`defs`/`anchor` files, and the
  meta-schema validation throughout.

ECMA-262 `\p{...}` Unicode general-category names (e.g. `\p{Letter}`) are
translated to their `java.util.regex` equivalents at compile time, so the
`pattern`/`patternProperties` suites pass. A case referencing a remote document
that is not vendored here is reported as skipped (a fixture-availability
limitation — the resolution engine itself is proven by the vendored remotes and
the dynamic-ref suites).

## Provenance

Files were copied verbatim from the `main` branch of `json-schema-org/
JSON-Schema-Test-Suite`; the meta-schemas from `json-schema.org`. The upstream
suite is distributed under the MIT License; see `LICENSE` in this directory.
Update by re-copying the same files from upstream.

[suite]: https://github.com/json-schema-org/JSON-Schema-Test-Suite
