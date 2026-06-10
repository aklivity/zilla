# common-protobuf

A format-native, provider-free Protobuf wire library for the hot path: descriptor-based decode and
canonical re-encode over Agrona `DirectBuffer`s. It owns the Protobuf wire side only and has **no
JSON dependency**.

The protobuf ↔ JSON mapping is **not** here — it is composed in `model-protobuf`, which depends on
both `common-protobuf` (this wire layer) and [`common-json`](../common-json) (the JSON side). This
mirrors how the Avro ↔ JSON bridge lives in `model-avro`, keeping each `common-*` library
single-format and free of cross-format dependencies.

## Descriptor model

`ProtobufSchema` is a compiled, immutable model — build one per `schemaId` and cache it. It is
**provider-free**: there is no dependency on `protobuf-java`. Construct the model with the public
builders (`ProtobufSchema.Builder`, `ProtobufMessage.Builder`, `ProtobufField.Builder`,
`ProtobufEnum.Builder`); a consumer such as `model-protobuf` populates field metadata from its own
`.proto` compilation.

```java
ProtobufSchema schema = StreamingProtobuf.schema()
    .message(ProtobufMessage.builder("Person")
        .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
        .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
        .build())
    .build();
```

A map `field` is modeled exactly as Protobuf represents it on the wire: a repeated reference to a
synthetic map-entry message (`mapEntry(true)`) with `key` as field 1 and `value` as field 2.

A compiled `google.protobuf.FileDescriptorSet` (e.g. from `protoc --descriptor_set_out`) can be
turned into a `ProtobufSchema` directly — `descriptor.proto` is itself Protobuf, so it is decoded
with this library's own wire reader and needs no `protobuf-java`:

```java
ProtobufSchema schema = StreamingProtobuf.schema(descriptorSet, offset, length);
```

## Protobuf syntax support

The wire format is identical for proto2 and proto3, so the codec reads and writes both. The
descriptor model is syntax-agnostic — it carries no `syntax` and simply processes the field set it
is given (the `FileDescriptorSet` compiler reads `proto3_optional`, `oneof`, map entries, the
`packed` option, and the proto2 `required` label).

proto2 wire features supported: **groups** (decoded, canonicalized, and skipped) and the `required`
label (represented on the model). Not yet supported: extensions, `required`-field enforcement, and
proto2 explicit field defaults.

## Binary round-trip canonicalization

`StreamingProtobuf.canonicalizer(schema)` re-serializes a message to a canonical wire encoding:

- known fields ascending by number, scalars minimally re-encoded, repeated scalars packed,
- nested messages length-delimited, proto2 groups delimited, map entries in encounter order,
- **unknown (non-descriptor) fields retained** — passed through verbatim (tag and value, groups
  included) and merged into the ascending field-number ordering.

Two valid encodings of the same logical message canonicalize to identical bytes — the comparison a
binary round-trip conformance check needs. The operation is format-neutral and touches no JSON.

### Bounded-buffer contract

Protobuf fields are length-delimited and may arrive in any order, and a repeated field's elements
may be interleaved — which complicates strictly forward-streaming under a no-full-document-buffer
goal. `common-protobuf` resolves this with a **bounded-buffer contract**: it operates on a single,
fully-buffered message (the engine delivers the reassembled payload). Processing is bounded by the
message size, and for nested messages by nesting depth (each length-delimited nested message is
staged in per-depth scratch since its length is known only once its body is built). No unbounded
document is buffered. Truncated or overlong varints, lengths that run past the message, and
unterminated or mismatched groups are rejected with a `ProtobufException`.

## Conformance

`ProtobufBinaryConformanceTest` replays a vendored corpus of `(input, expected-canonical)` cases
through the canonicalizer, honoring a `failure_list.txt` of known gaps. The corpus is seeded with
hand-crafted cases (reorder, pack, proto2 group, unknown-field retention, empty); the full corpus
is captured offline from the protobuf conformance runner — see `src/test/conformance/README.md`.

## Run performance benchmarks

Build the benchmark jar from this directory:

```bash
../../mvnw clean install -Pbench
```
