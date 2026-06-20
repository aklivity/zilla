# Vendored JSON conformance corpus

These `test_parsing/` files are vendored verbatim from the JSON Parsing Test Suite:

- Source: https://github.com/nst/JSONTestSuite (`test_parsing/`)
- Snapshot: `master` branch, fetched 2026-06-10
- License: MIT (see `LICENSE` in this directory)

File name prefixes follow the suite convention:

- `y_*` — must be accepted
- `n_*` — must be rejected
- `i_*` — implementation-defined (either result is RFC 8259 compliant)

Driven by `JsonTestSuiteConformanceTest`. Do not edit these files.
