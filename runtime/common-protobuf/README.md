# common-protobuf

A format-native, provider-free Protobuf wire library for the hot path: a descriptor-bound streaming
parser and generator over Agrona `DirectBuffer`s, composable into validating, transforming pipelines.
It owns the Protobuf wire side only and has **no JSON dependency**.

The protobuf ↔ JSON mapping is **not** here — it is owned by the `model-protobuf` converter, which
composes this wire layer with a JSON layer. Keeping that mapping out lets `common-protobuf` stay
single-format and dependency-light.

## Descriptor model

`ProtobufSchema` is a compiled, immutable model — build one per `schemaId` and cache it. It is
**provider-free**: there is no dependency on `protobuf-java`. Construct the model with the public
builders (`ProtobufSchema.Builder`, `ProtobufMessage.Builder`, `ProtobufField.Builder`,
`ProtobufEnum.Builder`); a consumer such as `model-protobuf` populates field metadata from its own
`.proto` compilation.

```java
ProtobufSchema schema = Protobuf.schema()
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
ProtobufSchema schema = Protobuf.schema(descriptorSet, offset, length);
```

## Protobuf syntax support

The wire format is identical for proto2 and proto3, so the codec reads and writes both. The
descriptor model is syntax-agnostic — it carries no `syntax` and simply processes the field set it
is given (the `FileDescriptorSet` compiler reads `proto3_optional`, `oneof`, map entries, the
`packed` option, and the proto2 `required` label).

proto2 wire features supported: **groups** — decoded, skipped, surfaced as distinct
`START_GROUP`/`END_GROUP` events, and written via `startGroup`/`endGroup` — and the `required` label
(represented on the model). Because a group is delimited by start/end-group tags rather than a length
prefix, it is the framing of choice when a body length is not known up front. Not yet supported:
extensions, `required`-field enforcement, and proto2 explicit field defaults.

## Parsing — pull cursor

`Protobuf.parser(schema, message)` is a pull cursor: `wrap` a fully-buffered message, then loop
`hasNext()` / `nextEvent()`, reading each value through the parser's own accessors. It emits
`START_MESSAGE`/`END_MESSAGE`, `FIELD` (positions `field()`) then `VALUE` for a scalar, and a nested
`START_MESSAGE`…`END_MESSAGE` for a length-delimited message (or `START_GROUP`…`END_GROUP` for a
proto2 group) for a composite — rejecting malformed wire and wire-type/declared-type mismatches as it
reads. `START_MESSAGE` carries the whole message slice via `buffer()`/`offset()`/`length()`, so a copy
knows the length up front.

```java
ProtobufParser parser = Protobuf.parser(schema, "Person").wrap(buffer, offset, length);
while (parser.hasNext())
{
    switch (parser.nextEvent())
    {
    case FIELD: int number = parser.field().number(); break;
    case VALUE: long value = parser.longValue(); break;
    default: break;
    }
}
```

`nextEvent()` defaults to `STRUCTURED`; pass `Mode.SEGMENTED` to `nextEvent(mode)` at a composite `FIELD`
to receive that value as a raw `SEGMENT` (its bytes via `buffer()`/`offset()`/`length()`) instead of
recursing into it.

A schema-free cursor (`Protobuf.parser()`, no schema) tokenizes the wire into generic `FIELD`/`VALUE`
pairs carrying `fieldNumber()` and `wireType()` (see Schema-free mode below).

## Streaming pipeline

`Protobuf.stream(parser)` layers a composable push pipeline over the same cursor: it pumps the parser
and feeds each event through an ordered chain of `ProtobufTransform` stages to a terminal `ProtobufSink`.

```java
ProtobufGenerator generator = Protobuf.generator().wrap(out, 0);
ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(readSchema, "Person"))
    .transform(readSchema.validator("Person"))
    .into(ProtobufSink.of(generator, writeSchema, "PersonV2"));
pipeline.reset();
if (pipeline.feed(in, off, len) == ProtobufPipeline.Status.COMPLETED)  // COMPLETED / SUSPENDED / REJECTED
{
    int length = generator.length();   // PersonV2 wire bytes in out
}
```

- **`Protobuf.stream(parser)`** begins the pipeline; **`ProtobufStream`** appends stages (`transform`) and
  terminates (`into`), yielding the runnable **`ProtobufPipeline`** (`reset` / `feed` / `Status`).
- **`ProtobufSource`** is the per-event read-only value view handed to a stage — the parser's typed
  accessors without the cursor advance, so a stage cannot disturb the pump.
- **`ProtobufTransform`** is an intermediate stage (`feed(control, source, event, sink)`);
  **`ProtobufSink`** is the terminal (`feed(control, source, event)`).
- **`schema.validator(messageName)`** returns a `ProtobufTransform` that forwards every event and
  adds descriptor-level semantic validation (proto2 `required`-field presence), reporting at the
  message boundary so callers abort on `REJECTED` (emit-then-abort). For a one-shot check,
  `schema.validate(messageName, buffer, offset, length)` returns a boolean.
- **`ProtobufSink.of(generator, schema, messageName)`** writes the event stream back out as wire
  through a buffer-backed `Protobuf.generator()`, encoded against the target message. Because
  protobuf needs field numbers and types to write, binding the sink to a target schema gives
  **schema transformation**: read with one schema, re-emit with another, mapping fields by name
  (fields absent in the target are dropped, including their subtrees) — a straight re-encode with the
  read schema, a rename/renumber with an evolved one (as above).
- **Segment delivery**: a stage calls `ProtobufController.segmentable()` on a composite `FIELD` to
  receive that value as a raw `SEGMENT` of wire bytes
  (`ProtobufSource.buffer()`/`offset()`/`length()`, with `bytesDeferred()` for a value spanning windows)
  instead of expanding it into structured events —
  preserving the nested bytes verbatim.

### Bounded output and streaming

`Protobuf.generator().wrap(out, 0, limit)` bounds the output (`limit` must fit the buffer capacity).
`limit` is a **hard bound** — the usable buffer is exactly `[offset, limit)` and nothing beyond it
exists; any write that would cross `limit` raises a `ProtobufException`. The output is **one logical
byte stream split into flow-control chunks that the consumer concatenates before parsing**, so the
per-record fragments of a too-large message merge on decode (concatenating repeated serializations of a
message type and parsing once *is* protobuf message-merge, recursively).

`startMessage(field, length)` reserves a length slot sized to an **upper bound** — the wire sink passes
the input length `source.length()` — and the actual length is computed later. When the **next field will
not fit** under `limit`, the sink calls `generator.flush()` and returns `SUSPENDED` with that field
unwritten: `flush()` fills each open message's slot with the body present (plus any bytes still deferred
by an in-flight value, see below), so the drained chunk's records are complete. The caller drains,
re-wraps, and resumes; the pump replays the field, and the generator **reopens** the records lazily as
fresh records on the next write. A message that fits a chunk back-patches its slot with its exact length
on `endMessage` (canonical); a message spanning chunks reassembles from its per-chunk records on decode.

A length-delimited value larger than a whole chunk **cannot** be deferred to a fresh buffer, so it is
**fragmented mid-byte** via `writeSegment`: its length prefix (the known total) is written once, then its
body streams as raw continuation bytes across as many chunks as needed, never buffered in full. The
enclosing records carry the value's deferred remainder in their lengths until it is fully written, then
close and reopen for the fields that follow. So a value of any size streams, provided its **total length
is known when it starts** (the parser supplies it via `source.length()`). The one unsupported case is a
transform that *grows* a message past that upper bound while the message also spans a chunk — its length
slot has already drained and cannot widen, so it is rejected; pass a deliberately large `startMessage`
length to reserve a wider slot.

```java
ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, limit);
ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(readSchema, "Person"))
    .into(ProtobufSink.of(generator, writeSchema, "PersonV2"));
pipeline.reset();

ProtobufPipeline.Status status = pipeline.feed(in, off, len);
while (status == ProtobufPipeline.Status.SUSPENDED)   // output full
{
    emitDataFrame(out, 0, generator.length());        // drain — flow-controlled
    generator.wrap(out, 0, limit);                    // reset output (fresh or recycled buffer)
    status = pipeline.feed(in, off, len);             // resume the in-flight message
}
// COMPLETED: emit the final generator.length() bytes
```

### Writing wire directly

`Protobuf.generator()` is a buffer-backed wire writer: each `writeXxx(field, value)` emits one
field's tag and value, typed by the Protobuf type since the wire is not self-describing about
signedness/zig-zag/fixed width. Nested messages take an **upper-bound length**:
`startMessage(field, length)` reserves a length slot sized to `length` (pass the expected size, or a
deliberately long value to widen the slot for a body that may grow), the body streams straight to the
output, and `endMessage()` fills the slot with the actual length — minimal when the width matched,
padded within the reserved width otherwise, never shifted (it throws if the body outgrows the reserved
width). A proto2 group is the length-free alternative: `startGroup(field)` writes only the start-group
tag, the body streams, and `endGroup()` writes the end-group tag — no length to know up front.
`writeMessage` length-prefixes a pre-encoded nested message, and `writeRaw` splices bytes verbatim.
`writeSegment(field, value, offset, length, deferred)` writes part of a length-delimited value whose
total is `length + deferred`: the first call emits the tag and total-length prefix then `length` bytes,
later calls append further body bytes — so a value too large for the buffer streams across chunks.

```java
ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
generator.writeInt32(1, id).writeString(2, name)
    .startMessage(3, addressEstimate).writeString(1, city).endMessage();
int length = generator.length();
```

`startMessage`/`endMessage` are the write-side mirror of the parser's `START_MESSAGE`/`END_MESSAGE`
events — `START_MESSAGE` carries the source length via `ProtobufSource.length()`, which a stage passes
straight through as the optimistic estimate — so a stage copies or transforms a message by driving the
generator directly from `ProtobufParser` events, in a single pass with no scratch.

### Schema-free mode

The wire is self-describing enough to tokenize without a schema, so `Protobuf.parser()` (no schema)
drives a schema-free pipeline: a `FIELD` event per wire field carrying `ProtobufSource.fieldNumber()`
and `wireType()`, then a `VALUE` carrying the raw value slice. Length-delimited values are opaque
bytes (no message-vs-string interpretation) and there is no recursion — typed values and field names
require a schema. It is suited to generic structural work: `ProtobufSink.of(generator)` writes the
generic stream back out verbatim (a lossless structural copy), and a `ProtobufTransform` between them
can keep/drop/redact fields by number with no schema:

```java
ProtobufGenerator generator = Protobuf.generator().wrap(out, 0);
ProtobufTransform redact = (control, source, event, sink) ->
    source.fieldNumber() == SSN ? ProtobufPipeline.Status.ADVANCED : sink.feed(control, source, event);
Protobuf.stream(Protobuf.parser()).transform(redact).into(ProtobufSink.of(generator));
```

### Bounded-buffer contract

Protobuf fields are length-delimited and may arrive in any order, and a repeated field's elements
may be interleaved — which complicates strictly forward-streaming under a no-full-document-buffer
goal. This library resolves it with a **bounded-buffer contract**: it operates on a single,
fully-buffered message (the engine delivers the reassembled payload). Processing is bounded by the
message size, and for nested messages by nesting depth — the parser decodes in place over a per-depth
frame stack, and the generator streams its output bounded by `limit`, fragmenting any value too large
rather than buffering it. No unbounded document is buffered. Truncated or overlong varints, lengths that
run past the message, and unterminated or mismatched groups are rejected with a `ProtobufException`.

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
