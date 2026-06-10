# Binary round-trip conformance corpus

`ProtobufBinaryConformanceTest` replays a vendored corpus of binary round-trip cases against
`ProtobufCanonicalizer`. This directory holds the tooling to (re)generate that corpus offline from
the upstream protobuf conformance suite. Generation needs a C++ toolchain and is run by a
maintainer on a protobuf version bump — never in CI. CI only replays the vendored corpus.

## Why binary round-trip (not JSON)

`common-protobuf` owns the Protobuf wire side only. The protobuf ↔ JSON mapping lives in
`model-protobuf`, which composes `common-protobuf` with `common-json`. So conformance here targets
the **binary** category: parse a serialized message and re-serialize it; two valid encodings of the
same logical message must produce identical canonical bytes. JSON-mapping conformance belongs to
`model-protobuf`.

## Corpus layout (what the harness reads)

Resources under `src/test/resources/.../conformance/`:

```
manifest.tsv          # <case-name>\t<fully.qualified.MessageName>  (one per line, # comments ok)
failure_list.txt      # case names that are known gaps; listed cases are skipped (xfail)
cases/<name>.in       # input wire bytes
cases/<name>.expected # expected canonical wire bytes
descriptors.bin       # google.protobuf.FileDescriptorSet for the corpus message types (full corpus)
```

For the full corpus the harness compiles `descriptors.bin` with
`StreamingProtobuf.schema(buffer, offset, length)` (descriptor.proto decoded by `common-protobuf`
itself — no `protobuf-java`) and resolves each case's message by name. The seed corpus committed
today uses a small schema built in-code; switching to `descriptors.bin` is the only harness change
needed once the full corpus is captured.

## Capture procedure (offline)

1. Build the protobuf conformance runner and the test message descriptors for the pinned version:

   ```bash
   PROTOBUF_VERSION=<pinned>            # keep in sync with the corpus; bump deliberately
   protoc --descriptor_set_out=descriptors.bin --include_imports \
       src/google/protobuf/test_messages_proto3.proto \
       src/google/protobuf/test_messages_proto2.proto
   ```

2. Capture every generated case as an `(input, expected-canonical)` pair. The runner generates
   cases programmatically rather than from static files, so the cases are captured by running
   `capture.sh`, which drives the runner against a capture testee that records each
   `ConformanceRequest` of `requested_output_format = PROTOBUF` together with the runner's expected
   serialized output. See `capture.sh` for the exact invocation against your protobuf checkout.

3. Drop the results into `cases/`, append rows to `manifest.tsv`, and copy `descriptors.bin` into
   the resources directory.

## Failure list

Cases that `ProtobufCanonicalizer` does not yet satisfy go in `failure_list.txt` and are skipped.
The canonicalizer currently drops unknown (non-descriptor) fields, so unknown-field-retention cases
belong there until preserved. The list is the explicit ledger of partial support: the suite is
complete (every captured case is replayed), and gaps are enumerated rather than hidden. When a
listed case starts passing, remove it — keeping the ledger honest as coverage grows.
