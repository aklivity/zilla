# common-protobuf

A format-native, provider-free Protobuf wire library for the hot path: a descriptor-bound streaming
parser and generator over Agrona `DirectBuffer`s, composable into validating, transforming pipelines.
The core wire layer (package `io.aklivity.zilla.runtime.common.protobuf`) owns the Protobuf wire side
only and carries **no JSON dependency**.

The protobuf ↔ JSON mapping lives apart from that core in the `io.aklivity.zilla.runtime.common.protobuf.json`
package (`ProtobufJson`), which composes the wire layer with the `common-json` transcoder — see
[protobuf ↔ JSON](#protobuf--json) below. The wire core stays single-format and dependency-light; only
the `.json` package pulls in `common-json`.

## Descriptor model

`ProtobufSchema` is a compiled, immutable model — build one per `schemaId` and cache it. It is
**provider-free**: there is no dependency on a third-party protobuf library. Construct the model with the public
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
with this library's own wire reader and needs no third-party protobuf library:

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
reads. `START_MESSAGE` carries the whole message slice via `segment()`, so a copy
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
to receive that value as a raw `SEGMENT` (its bytes via `segment()`) instead of
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
if (pipeline.transform(in, off, len) == ProtobufPipeline.Status.COMPLETED)  // COMPLETED / SUSPENDED / REJECTED
{
    int length = generator.length();   // PersonV2 wire bytes in out
}
```

- **`Protobuf.stream(parser)`** begins the pipeline; **`ProtobufStream`** appends stages (`transform`) and
  terminates (`into`), yielding the runnable **`ProtobufPipeline`** (`reset` / `feed` / `Status`).
- **`ProtobufSource`** is the per-event read-only value view handed to a stage — the parser's typed
  accessors without the cursor advance, so a stage cannot disturb the pump.
- **`ProtobufTransform`** is an intermediate stage (`transform(control, source, event, sink)`);
  **`ProtobufSink`** is the terminal (`transform(control, source, event)`).
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
  (`ProtobufSource.segment()`, with `deferredBytes()` for a value spanning windows)
  instead of expanding it into structured events —
  preserving the nested bytes verbatim.

### Two back-pressure axes: input `STARVED` and output `SUSPENDED`

`feed` carries two independent kinds of back-pressure, each with its own non-terminal "call `feed`
again" status, so a message flows in bounded memory with a window of input in and a window of output
out:

- **Output — `SUSPENDED`** (the generator filled): drain the output, reset the generator, and call
  `feed` again with the **same** input window to resume the in-flight message from where it paused.
- **Input — `STARVED`** (the input window was consumed before the message completed): retain the
  unconsumed tail and call `feed` again with it prepended to the **next** window. End of input is
  signalled by the caller via the `last` flag on `feed(buffer, offset, limit, last)`, which feeds the
  half-open range `[offset, limit)`; pass `last == true` only on the final window. The three-argument
  `feed(buffer, offset, limit)` is the whole-buffer shorthand (`last == true`) and never returns
  `STARVED`.

`STARVED` is returned only when `last == false`; under `last == true` a clean message end yields
`COMPLETED` and an incomplete one (a primitive, length-prefix, or nested message that runs past the
bytes) yields `REJECTED`.

The pipeline holds **no input buffer of its own** — it never copies or retains input. Instead it reports
`remaining()`, the number of bytes at the tail of the most recently fed window not yet consumed (always at
a whole-unit boundary). On `STARVED`, those trailing `remaining()` bytes are the unconsumed tail; the caller
retains them — typically in the reassembly slot it already owns — and re-presents them, contiguous with the
next window, on the following `feed`. Because it is window-relative, the caller drops the consumed prefix
without tracking the slot's absolute base. Because the unknown-field skip and large leaf values both stream,
that retained tail is only ever a partial primitive or a partial UTF-8 code point — a handful of bytes.

```java
pipeline.reset();
for (boolean done = false; !done; )
{
    appendToSlot(nextWindowBytes());                 // grow the reassembly slot with new input
    Status status = pipeline.transform(slot, 0, slotLength, finalWindow);
    while (status == Status.SUSPENDED)               // output full
    {
        emitDataFrame(out, 0, generator.length());   // drain — flow-controlled
        generator.wrap(out, 0, limit);               // reset output (fresh or recycled buffer)
        status = pipeline.transform(slot, 0, slotLength, finalWindow);
    }
    switch (status)
    {
    case STARVED:
        compactSlot(slotLength - pipeline.remaining()); // drop the consumed prefix, keep the tail at the front
        break;
    case COMPLETED: emitDataFrame(out, 0, generator.length()); done = true; break;
    case REJECTED: abort(); done = true; break;
    default: break;
    }
}
```

Leaf values stream too: a `string`/`bytes` value larger than the input window arrives as repeated
`VALUE` events, each carrying the bytes still to come in `ProtobufSource.deferredBytes()` (a `string` is
split only on UTF-8 code-point boundaries, `bytes` at the raw window edge), and the wire sink forwards
each chunk straight through the generator's `writeSegment` — so a multi-megabyte value flows
in-window → out-window with nothing buffered but the current slice. Proto2 groups straddle windows as
well, decoded incrementally to their `EGROUP` tag.

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

ProtobufPipeline.Status status = pipeline.transform(in, off, len);
while (status == ProtobufPipeline.Status.SUSPENDED)   // output full
{
    emitDataFrame(out, 0, generator.length());        // drain — flow-controlled
    generator.wrap(out, 0, limit);                    // reset output (fresh or recycled buffer)
    status = pipeline.transform(in, off, len);             // resume the in-flight message
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
    source.fieldNumber() == SSN ? ProtobufPipeline.Status.ADVANCED : sink.transform(control, source, event);
Protobuf.stream(Protobuf.parser()).transform(redact).into(ProtobufSink.of(generator));
```

### Bounded-buffer contract

Protobuf fields are length-delimited and may arrive in any order, and a repeated field's elements
may be interleaved — which complicates strictly forward-streaming under a no-full-document-buffer
goal. This library resolves it with a **bounded-buffer contract**: a message is processed either whole (the
engine delivers the reassembled payload) or as successive input windows (the streaming contract, see
*Two back-pressure axes* above), without buffering the whole document either way. Processing is bounded
by the message structure — the parser decodes over a per-depth frame stack whose scope ends are tracked
by a swap-safe position counter rather than the refillable byte limit, so a frame survives a window
swap. Leaf `string`/`bytes` values and unknown-field skips both stream in window-sized pieces rather
than being reassembled whole, and the cursor itself **never copies or retains input** — it reports
`remaining()` and leaves any unconsumed tail (only ever a partial primitive or a partial UTF-8 code
point) for the caller to retain and re-present (see *Two back-pressure axes* above). The generator
streams its output bounded by `limit`, fragmenting any value too large rather than buffering it. No
unbounded document is buffered. Truncated or overlong varints, lengths that run past the message under
`last`, and unterminated or mismatched groups are rejected with a `ProtobufException`.

## protobuf ↔ JSON

`ProtobufJson` (package `io.aklivity.zilla.runtime.common.protobuf.json`) bridges the wire layer to the
`common-json` transcoder by **adapting the wire `ProtobufParser` and `ProtobufGenerator` to a `JsonParserEx`
and `JsonGeneratorEx`** — so both directions drop into the existing pipeline machinery unchanged, as a pure
`ProtobufParser` ↔ `ProtobufGenerator` pair or via `Protobuf.stream(...).into(ProtobufSink.of(...))`. It applies
the proto3 JSON mapping: a message is a JSON object keyed by each field's proto3 json name, a `repeated` field a
JSON array, a `map` a JSON object, 64-bit and unsigned-64-bit integers JSON strings, `bytes` a base64 string, an
`enum` its value name (its number when unknown), and `float`/`double` JSON numbers (`"NaN"`/`"Infinity"`/
`"-Infinity"` as strings).

**JSON → protobuf** — `ProtobufJson.parser(JsonParserEx, schema, messageName)` is a `ProtobufParser` that reads
JSON and maps each value onto its descriptor field, so a wire `ProtobufGenerator` (or a whole pipeline) encodes it:

```java
ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
ProtobufPipeline pipeline = Protobuf.stream(ProtobufJson.parser(JsonEx.createParser(), schema, "Person"))
    .into(ProtobufSink.of(generator, schema, "Person"));
pipeline.reset();
if (pipeline.transform(jsonIn, off, len) == ProtobufPipeline.Status.COMPLETED)
{
    int length = generator.length();   // Person wire bytes in out
}
```

It streams windowed JSON input the same way the wire parser does — `nextEvent` returns `null` on a partial window
and `resume` continues with the next — pulling one JSON token at a time with no document buffer (only the bounded
per-message frame stack). One JSON leaf value must fit a single input window; message structure may split across
windows at any token boundary.

**protobuf → JSON** — `ProtobufJson.generator(JsonGeneratorEx, schema, messageName)` is a `ProtobufGenerator` that
renders the wire write calls as JSON, resolving each field by number against the schema:

```java
ProtobufGenerator json = ProtobufJson.generator(JsonEx.createGenerator(), schema, "Person");
json.wrap(out, 0, out.capacity());
ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "Person"))
    .into(ProtobufSink.of(json, schema, "Person"));
pipeline.reset();
if (pipeline.transform(in, off, len) == ProtobufPipeline.Status.COMPLETED)
{
    json.flush();                 // finalize the root object (the protobuf root carries no end event)
    int length = json.length();   // Person rendered as JSON in out
}
```

The root object opens on the first write and is finalized by `flush()`; protobuf input may stream across windows,
while the JSON output buffer must hold the whole document.

Numbers are formatted into a reused `StringBuilder` and emitted via `writeNumber`/`write`, so the generator side
adds no per-message allocation (`ProtobufJsonPipelineBM.protobufToJson` measures ≈ 0 B/op). The
`ProtobufJsonParser` likewise reuses its frame stack and value buffers, and reads scalars and keys through the
parser's non-owning `getStringView()` / `getKey()` char views — parsing integers with
`Long.parseLong(CharSequence, …)`, resolving field names with the allocation-free `ProtobufMessage.field(
CharSequence)`, and UTF-8-encoding strings straight into the reused value buffer — so no per-value
`String`/`byte[]` is materialized. The small residual (`ProtobufJsonPipelineBM.jsonToProtobuf`, ≈ 100 B/op) is
`float`/`double` values, which round-trip through a `String` because the JDK has no `CharSequence` floating-point
parse.

Bounded-buffer contract: no unbounded document is buffered either way. Malformed JSON, a non-object root, an
unknown message, and an unknown enum value are rejected with a `ProtobufException` (the pipeline reports
`REJECTED`). Unknown JSON fields are ignored and `null` values omitted, per the proto3 JSON mapping.

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
