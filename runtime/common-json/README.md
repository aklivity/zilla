# common-json

A provider-free, streaming JSON library for the hot path: a resumable pull parser and a buffer-backed
generator over Agrona `DirectBuffer`s, composable into validating, projecting, transforming pipelines.
It ships its own `jakarta.json` implementation and needs **no JSON provider** on the classpath.

JSON Schema validation and the schema model live alongside the wire codec; the streaming pipeline
keeps parse, transform, and serialize on a single pass with no intermediate DOM.

## Pull parser

`StreamingJson.createParser()` returns a resumable pull cursor fed one frame at a time via
`wrap(buffer, offset, length)`, then driven with the standard `hasNext()` / `next()`. It also
implements `jakarta.json.stream.JsonParser`, so `StreamingJson.createParser(in)` works anywhere a
one-shot pull parser is expected. `PATH_INCLUDES` / `PATH_EXCLUDES` config bounds which paths are
materialized (so deep values can be scanned and discarded without allocating), and `TOKEN_MAX_BYTES`
fails fast on a single value that cannot make progress under reset semantics.

```java
JsonParserEx parser = StreamingJson.createParser().wrap(buffer, offset, length);
while (parser.hasNext())
{
    switch (parser.next())
    {
    case KEY_NAME: CharSequence key = parser.getKey(); break;
    case VALUE_STRING: String value = parser.getString(); break;
    default: break;
    }
}
```

## Streaming pipeline

`stream()` layers a composable push pipeline over the same cursor: it pumps the parser and feeds each
event through an ordered chain of `JsonTransform` stages to a terminal `JsonSink`.

```java
JsonGeneratorEx generator = StreamingJson.createGenerator().wrap(out, 0, out.capacity());
JsonPipeline pipeline = StreamingJson.createParser().stream()
    .transform(StreamingJson.projector(List.of("/id", "/name")))
    .into(JsonSink.of(generator));
pipeline.reset();
if (pipeline.feed(in, off, len) == JsonPipeline.Status.COMPLETE)   // RESUMABLE / SUSPENDED / COMPLETE / REJECTED
{
    int length = generator.length();    // projected JSON bytes in out
}
```

- **`stream()`** begins the pipeline; **`JsonStream`** appends stages (`transform`) and terminates
  (`into`), yielding the runnable **`JsonPipeline`** (`reset` / `feed` / `Status`).
- **`JsonSource`** is the per-event read-only value view handed to a stage — the parser's accessors
  without the cursor advance, so a stage cannot disturb the pump.
- **`JsonTransform`** is an intermediate stage (`feed(control, source, event, sink)`); **`JsonSink`**
  is the terminal (`feed(control, source, event)`). Third parties may implement either to consume or
  rewrite the projected event stream (e.g. field masking, encryption).
- **`StreamingJson.projector(pointers)`** returns a `JsonTransform` that prunes a document to a set of
  retained RFC 6901 pointers (`-` matches any array index), forwarding only kept events.
  `projector(schema)` derives the pointers from a `JsonSchema`.
- **`JsonSchema.of(text).validator()`** returns a `JsonTransform` that forwards every event and adds
  schema validation, reporting at the value boundary so callers abort on `REJECTED`
  (emit-then-abort). For a one-shot validating pull parse, `schema.newParser(validate, parser)` wraps a
  parser and throws on the first violation.
- **`JsonSink.of(generator)`** renders the event stream as normalized, compact JSON.
  **`JsonSink.of(generator, Delivery.SEGMENTABLE)`** opts a kept value in to verbatim delivery: it
  arrives as `START_SEGMENT`/`CONTINUE_SEGMENT`/`END_SEGMENT` raw byte slices spliced unchanged,
  preserving the original bytes (and any insignificant whitespace) rather than re-rendering.

### Four-state status

`feed` returns one of four states, separating **input** bounding from **output** bounding:

- **`RESUMABLE`** — the parser exhausted this frame mid-value; feed the next frame to continue. The
  parser is not re-wrapped on a resumed feed, so a value spanning frames reassembles seamlessly.
- **`SUSPENDED`** — the bounded output filled; drain it, re-target the generator, and feed the same
  frame again to continue (see below).
- **`COMPLETE`** — the current top-level value finished and was accepted.
- **`REJECTED`** — the value was rejected (malformed JSON, or a schema violation from a `validator`
  stage); the output must be abandoned. Malformed input surfaces as `REJECTED` rather than escaping
  `feed` as an exception.

### Bounded output and streaming

`StreamingJson.createGenerator().wrap(out, 0, limit)` bounds the output. `limit` is a **hard bound**
asserted at `wrap` to fit the buffer capacity: the usable region is exactly `[offset, limit)` and no
write crosses it. A driver watches `generator.remaining()` and, when it nears the limit at an event
boundary, the sink returns `SUSPENDED`.

Unlike a format with merge semantics, the chunks are **consecutive byte ranges of one continuous
serialization that the consumer concatenates** — there is no per-chunk framing to reopen. The
generator preserves its structural context (open object/array depth and pending separators) across the
re-wrap, so the value continues exactly where it paused; `reset()` clears that context to begin a fresh
value.

```java
JsonGeneratorEx generator = StreamingJson.createGenerator().wrap(out, 0, limit);
JsonPipeline pipeline = StreamingJson.createParser().stream()
    .into(JsonSink.of(generator));
pipeline.reset();

JsonPipeline.Status status = pipeline.feed(in, off, len);
while (status == JsonPipeline.Status.SUSPENDED)   // output full
{
    emitDataFrame(out, 0, generator.length());    // drain — flow-controlled
    generator.wrap(out, 0, limit);                // re-target output, structural context preserved
    status = pipeline.feed(in, off, len);         // resume the in-flight value
}
// COMPLETE: emit the final generator.length() bytes
```

`reset()` also clears the generator's structural context (via the pipeline's `reset()` cascade), so a
generator returned to a pool mid-value — an abandoned `RESUMABLE` frame or a `REJECTED` value left with
open structure — does not leak that structure into the next checkout.

### Fragmenting values larger than the bound

A value whose verbatim form exceeds `remaining()` is **fragmented mid-byte** rather than overrunning
the bound. `generator.writeSegment(source, index, length, deferred)` appends the bytes that fit and
records `deferred` — how many bytes of the value remain — and the sink returns `SUSPENDED`; on resume
it continues from where it paused until `deferred` reaches zero. A value of any size streams this way
through the segment path, never buffered in full.

Resumption is a **per-stage cascade**, not an event replay: `JsonSink.resume()` and
`JsonTransform.resume(downstream)` continue any in-flight fragment before the next event is pulled.
`JsonTransform.resume(downstream)` defaults to `downstream.resume()`, so a stage that only forwards
events ignores it entirely; a stage that itself emits a value across chunks (substituting or expanding
output) overrides `resume(downstream)` to continue its own emission, draining the downstream first.
This keeps the transform contract simple — no stage has to be suspend/resume aware unless it originates
`SUSPENDED`.

### Writing JSON directly

`StreamingJson.createGenerator()` is a buffer-backed, compact writer that inserts structural separators
and quoting automatically from an internal context stack, emitting in source order with no
insignificant whitespace. It implements `jakarta.json.stream.JsonGenerator` with covariant returns for
fluent chaining, plus the streaming-to-buffer extensions: `writeNumber(literal)` emits a numeric
lexeme verbatim, `writeRaw` / `writeRawContinue` splice a pre-encoded value (in one or more fragments),
and `writeSegment` writes a bounded, deferred-tracking fragment.

```java
JsonGeneratorEx generator = StreamingJson.createGenerator().wrap(out, 0, out.capacity());
generator.writeStartObject()
    .write("id", id)
    .write("name", name)
    .writeEnd();
int length = generator.length();
```

### Bounded-buffer contract

The pipeline operates on a single buffered frame and is bounded by the value size and, for structured
rendering, by nesting depth — no unbounded document is buffered. The hard `[offset, limit)` bound makes
the constraint explicit: a value larger than the bound must arrive through the **segment path**, which
fragments it across chunks; a one-shot structured scalar or an object key written in a single call must
fit the bound. Malformed wire (a syntax error in the frame) is rejected as `REJECTED`.

## Run performance benchmarks

Build the benchmark jar from this directory:

```sh
../../mvnw clean -DskipTests package
```

Run the JSON validation and projection benchmarks with GC allocation profiling:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  -jar target/common-json-develop-SNAPSHOT-shaded-tests.jar \
  '.*Json.*BM.*' -prof gc
```

For a quick smoke run while iterating, reduce the warmup and measurement time:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  -jar target/common-json-develop-SNAPSHOT-shaded-tests.jar \
  '.*Json.*BM.*' -prof gc -wi 1 -i 1 -r 200ms -w 200ms -f 0
```

The `--add-opens` option is required on recent JDKs when Agrona accesses
`jdk.internal.misc.Unsafe` from the shaded benchmark jar.
