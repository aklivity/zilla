# Protobuf conformance

`common-protobuf` is conformance-checked by the native protobuf `conformance_test_runner` itself,
driven from `ProtobufConformanceIT` via Testcontainers. The runner is the judge — there is no
hand-authored corpus and no expected-output oracle to maintain.

## How it works

The runner generates its full case set at run time and drives a *testee* over stdin/stdout with
length-prefixed `ConformanceRequest` / `ConformanceResponse` protobufs. Our testee
(`ConformanceTestee`, in test sources) is that program:

- It reads/writes the conformance frames with `common-protobuf`'s own reader and writer (the
  conformance messages are themselves Protobuf — more dogfooding), no `protobuf-java`.
- For the binary category (`protobuf_payload` in, `PROTOBUF` out) it canonicalizes against the
  schema compiled from the conformance `FileDescriptorSet` (via `StreamingProtobuf.schema(...)`).
- JSON/text formats return `skipped` — that mapping is owned by `model-protobuf`. Malformed input
  returns `parse_error`.

`ConformanceTesteeTest` unit-tests this handler with no Docker.

## Image and runtime injection

`Dockerfile` builds a pinned image: a JDK base with the native `conformance_test_runner`, `protoc`,
and a baked `descriptors.bin` of the conformance test messages. It changes only with
`PROTOBUF_VERSION`, so publish it to a registry and let CI pull it rather than rebuild the runner.

`ProtobufConformanceIT` copies the testee classpath — `target/classes`, `target/test-classes`, and
the `agrona` jar — into the running container and invokes:

```
conformance_test_runner --enforce_recommended --failure_list /conformance/failure_list.txt \
    -- java -cp '/app/classes:/app/test-classes:/app/libs/*' \
       io.aklivity.zilla.runtime.common.protobuf.internal.ConformanceTestee /conformance/descriptors.bin
```

Because the testee is injected at runtime, iterating on `common-protobuf` never rebuilds the image.
The IT is skipped where Docker is unavailable.

## Failure list

`runner-failure-list.txt` is the explicit ledger of partial support: the runner exercises every
generated case, listed cases are allowed to fail, and JSON/text cases are reported skipped. Binary
gaps (e.g. extensions, proto2 `required` enforcement) are added here by their exact runner-reported
names after the first real run reveals them; remove a case when it starts passing.

## Tuning on first run

The protobuf CMake target/option names and the precise runner flags drift between releases. Pin
`PROTOBUF_VERSION`, then on the first real run adjust the Dockerfile build invocation and populate
`runner-failure-list.txt` from the runner's reported failures.

## Smoke test

`ProtobufBinaryConformanceTest` (no Docker) replays a small vendored corpus through the
canonicalizer so contributors without Docker still get a fast binary round-trip signal; the
Testcontainers IT above is the authoritative, comprehensive gate.
