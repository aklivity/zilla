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

## Streaming validator pipeline

Mirroring `common-json`'s `JsonPipeline`, a composable streaming pipeline decodes a message against
the descriptor into a typed event stream and validates as it reads:

```java
ProtobufPipeline pipeline = StreamingProtobuf.parser(schema, "Person").stream()
    .transform(schema.validator("Person"))
    .into(ProtobufSink.discard());
pipeline.reset();
ProtobufPipeline.Status status = pipeline.feed(buffer, offset, length);  // PENDING / COMPLETE / REJECTED
```

- **`ProtobufStream`** (`transform`/`into`) → **`ProtobufPipeline`** (`reset`/`feed`/`Status`), peer to
  `JsonStream`/`JsonPipeline`.
- The descriptor-bound driver emits **`ProtobufEvent`**s — `START_MESSAGE`/`END_MESSAGE`, `FIELD`
  (positions `ProtobufSource.field()`) then `VALUE` for a scalar or a nested message — rejecting
  malformed wire and wire-type/declared-type mismatches as it reads.
- **`ProtobufSource`** is the per-event read-only value view (typed scalar accessors + raw slice).
- **`ProtobufTransform`** is an intermediate stage (`feed(control, source, event, sink)`);
  **`ProtobufSink`** is the terminal (`feed(control, source, event)`), with `ProtobufSink.discard()`
  for a pure validation pipeline whose verdict is the returned `Status`.
- **`schema.validator(messageName)`** returns a `ProtobufTransform` that forwards every event and
  adds descriptor-level semantic validation (proto2 `required`-field presence), reporting at the
  message boundary so callers abort on `REJECTED` (emit-then-abort).
- **`ProtobufSink.of(generator, schema, messageName)`** (peer of `JsonSink.of(generator, …)`) writes
  the event stream back out as wire through a buffer-backed `StreamingProtobuf.generator()`, encoded
  against the target message. Because protobuf needs field numbers and types to write, binding the
  sink to a target schema gives **schema transformation**: read with one schema, re-emit with another,
  mapping fields by name (fields absent in the target are dropped, including their subtrees). With the
  read schema it is a straight re-encode; with an evolved schema it renames/renumbers fields:

  ```java
  ProtobufGenerator generator = StreamingProtobuf.generator().wrap(out, 0);
  ProtobufPipeline pipeline = StreamingProtobuf.parser(readSchema, "Person").stream()
      .transform(readSchema.validator("Person"))
      .into(ProtobufSink.of(generator, writeSchema, "PersonV2"));
  pipeline.reset();
  if (pipeline.feed(in, off, len) == ProtobufPipeline.Status.COMPLETE)
  {
      int length = generator.length();   // PersonV2 wire bytes in out
  }
  ```
- **Segment delivery** mirrors `common-json` #1870: a stage calls `ProtobufController.segmentable()`
  on a composite `FIELD` to receive that value as `START_SEGMENT`/`END_SEGMENT` raw wire bytes
  (`ProtobufSource.buffer()`/`offset()`/`length()`) instead of expanding it into structured events.

### Schema-free mode

The wire is self-describing enough to tokenize without a schema, so `StreamingProtobuf.parser()`
(no schema) drives a schema-free pipeline: a `FIELD` event per wire field carrying
`ProtobufSource.fieldNumber()` and `wireType()`, then a `VALUE` carrying the raw value slice.
Length-delimited values are opaque bytes (no message-vs-string interpretation) and there is no
recursion — typed values, names, and the JSON mapping all require a schema. It is suited to generic
structural work: `ProtobufSink.of(generator)` writes the generic stream back out verbatim (a
lossless structural copy), and a `ProtobufTransform` between them can keep/drop/redact fields by
number with no schema:

```java
ProtobufGenerator generator = StreamingProtobuf.generator().wrap(out, 0);
ProtobufTransform redact = (control, source, event, sink) ->
    source.fieldNumber() == SSN ? ProtobufPipeline.Status.PENDING : sink.feed(control, source, event);
StreamingProtobuf.parser().stream().transform(redact).into(ProtobufSink.of(generator));
```

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
