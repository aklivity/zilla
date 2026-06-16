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
  schema compiled from the conformance `FileDescriptorSet` (via `Protobuf.schema(...)`).
- JSON/text formats return `skipped` — the conformance runner exercises only the binary category here;
  the protobuf ↔ JSON mapping lives in `common-protobuf`'s own `json` package (`ProtobufJson`). Malformed
  input returns `parse_error`.

`ConformanceTesteeTest` unit-tests this handler with no Docker.

## Image and runtime injection

`Dockerfile` builds a pinned image on a single JDK base (Ubuntu-based, so one registry pull): the
native `conformance_test_runner` and `protoc` are installed on top, the same JDK runs the testee,
and a `descriptors.bin` of the conformance test messages is baked in. It changes only with
`PROTOBUF_VERSION`, so publish it to a registry and let CI pull it rather than rebuild the runner.

`ProtobufConformanceIT` copies the testee classpath — `target/classes`, `target/test-classes`, and
the `agrona` jar — into the running container and invokes:

```
conformance_test_runner --enforce_recommended --failure_list /conformance/failure_list.txt \
    -- java -cp '/app/classes:/app/test-classes:/app/libs/*' \
       io.aklivity.zilla.runtime.common.protobuf.internal.ConformanceTestee /conformance/descriptors.bin
```

Because the testee is injected at runtime, iterating on `common-protobuf` never rebuilds the image.

The suite is **opt-in**: it builds the runner image from source (network + a multi-minute compile),
so it does not run on every `mvn verify`. Enable it explicitly in a dedicated job:

```
./mvnw verify -pl runtime/common-protobuf -Dzilla.conformance=true
```

Without that property — or where Docker is unavailable — the IT is skipped.

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
