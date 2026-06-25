# Zilla runtime — Agent Guide

Guidance scoped to anything under `runtime/` — the engine and all bindings.
Cross-cutting repo guidance (build, code style, contribution flow) lives in
the root [AGENTS.md](../AGENTS.md). Engine-specific conventions live in
[engine/AGENTS.md](engine/AGENTS.md). Spec/IT conventions live in
[../specs/AGENTS.md](../specs/AGENTS.md).

Each `binding-*` module follows the same internal layout:

```
src/main/java/.../internal/
  config/         # Binding-specific config POJOs, generated from JSON schema
  stream/         # Stream handler — the hot path
  types/          # Flyweight type definitions (.idl → generated Java)
src/test/java/    # Unit tests
```

---

## Java module system

Zilla is delivered as a fully modular Java runtime. Every `runtime/` module
declares a `module-info.java`. This is non-negotiable — it is what allows Zilla
to be packaged as a minimal, self-contained runtime image with no extraneous
classpath entries.

**When adding or modifying a binding, you must:**

- Declare `module-info.java` in `src/main/java/` for every new module
- Export only the SPI packages needed by the engine; keep all `internal.*`
  packages unexported
- Use `provides ... with ...` to register SPI implementations
  (e.g., `HttpBindingFactorySpi`, `JwtGuardFactorySpi`, `FileSystemVaultFactorySpi`,
  `MetricGroupFactorySpi`, `CatalogFactorySpi`, `ModelFactorySpi`, `ResolverFactorySpi`,
  `StoreFactorySpi`, `CommandFactorySpi`)
- Use `uses ...` in `engine/module-info.java` only for new SPI types added to
  the engine contract

**Dependency hygiene — this is critical:**

Introducing a non-modular (automatic module) dependency anywhere in the
`runtime/` tree breaks the module graph for the entire runtime image. Before
adding any dependency:

1. Verify it ships a `module-info.class` in its JAR (`jar --describe-module -f dep.jar`)
2. If it is automatic-module only, do not add it — find an alternative or
   isolate it behind a new SPI so the non-modular code never enters the
   runtime module graph
3. Never add `--add-opens` or `--add-exports` JVM flags as a workaround for
   a non-modular dependency; fix the dependency choice instead
4. Test dependencies (scope `test`) are exempt — they do not affect the
   delivered runtime image

The permitted automatic-module exceptions, if any, are listed in the root
`pom.xml` under `<jvm.opens>`. Do not extend this list without a maintainer
discussion on the tracking issue.

---

## Core architecture

### Stream model

All inter-binding communication uses four typed frames flowing over shared memory
(Agrona `RingBuffer`). There are no intermediate queues or network hops between
bindings in a pipeline.

| Frame    | Direction          | Purpose                                  |
|----------|--------------------|------------------------------------------|
| `BEGIN`  | initiator → target | Open a stream; carries protocol metadata |
| `DATA`   | both directions    | Payload bytes                            |
| `END`    | both directions    | Orderly close                            |
| `WINDOW` | target → initiator | Flow control credit                      |
| `ABORT`  | either             | Unilateral close on error                |
| `RESET`  | either             | Reject a stream                          |
| `FLUSH`  | either             | Advance stream position without payload  |
| `CHALLENGE` | target → initiator | Request re-authentication on a stream |

Each frame may carry a protocol-specific **extension** typed flyweight. For
example, `HttpBeginEx` carries HTTP headers on the `BEGIN` frame; `KafkaDataEx`
carries Kafka metadata on `DATA` frames.

**Extension type naming convention:**

Extension types follow the pattern `{BindingType}{FrameType}Ex`, where
`BindingType` is the capitalised binding type name and `FrameType` is the
capitalised frame type name. The generated flyweight classes append `FW`:

| Example binding type | Extension type | Generated flyweight class |
| --- | --- | --- |
| `http` | `HttpBeginEx` | `HttpBeginExFW` |
| `http` | `HttpDataEx` | `HttpDataExFW` |
| `kafka` | `KafkaBeginEx` | `KafkaBeginExFW` |
| `kafka` | `KafkaDataEx` | `KafkaDataExFW` |
| `kafka` | `KafkaFlushEx` | `KafkaFlushExFW` |
| `mqtt` | `MqttBeginEx` | `MqttBeginExFW` |
| `mqtt` | `MqttDataEx` | `MqttDataExFW` |

Not every frame type requires an extension — only declare an `*Ex` type in
the `.idl` when the binding needs to carry protocol-specific metadata on that
frame. Extensions are declared in
`src/main/resources/META-INF/zilla/<type>.idl` and referenced from
`zilla-types.idl` in the engine module.

### Binding kinds

Every binding has a `kind` that determines its role in the pipeline:

| Kind            | Responsibility                                              |
|-----------------|-------------------------------------------------------------|
| `server`        | Decode an inbound protocol; produce application streams     |
| `client`        | Encode outbound protocol; consume application streams       |
| `proxy`         | Pass-through with optional header/metadata manipulation     |
| `remote_server` | Adapt a Kafka topic stream into an application stream       |
| `cache_client`  | Read from the Zilla local Kafka cache                       |
| `cache_server`  | Serve the Zilla local Kafka cache                           |

### EngineWorker threading model

Zilla uses a fixed pool of `EngineWorker` threads — one per configured CPU core.
Each worker owns:

- A dedicated Agrona `ManyToOneRingBuffer` for inbound stream frames
- Its own set of handler instances for every pluggable type: bindings, guards,
  vaults, catalogs, stores, models, metrics, resolvers, and commands
- A memory-mapped event log file for diagnostics

Because each handler instance is owned exclusively by one worker thread and
all frame dispatch happens on that thread, **handler classes are single-threaded
by design**. There is no sharing of handler instances across workers.

**Critical rules for the hot path:**

- No heap allocation on the data path — use flyweight accessors on existing
  buffers only
- No blocking I/O — workers are single-threaded event loops
- No `synchronized`, no `Lock`, no `volatile` writes on the critical path
- Flyweight objects are reused across frames; never hold a reference to a
  flyweight beyond the current call stack
- Buffer ownership follows the call: a buffer passed into a handler is only
  valid for the duration of that call

### Handler class structure and flyweight reuse

The single-threaded ownership model means flyweight instances can be held as
plain non-static fields on the handler factory class and safely reused
across every call — no synchronisation, no thread-locals, no per-call allocation.

The canonical pattern is a top-level handler class (e.g., `HttpServerFactory`)
that holds all flyweight fields, with stream state captured in non-static inner
classes that close over the outer instance to access those flyweights:

```java
final class HttpServerFactory implements HttpBinding.StreamFactory
{
    // Flyweights declared once on the factory — reused across all streams
    // on this worker. Safe because only one thread ever calls this instance.
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final HttpBeginExFW         httpBeginExRO = new HttpBeginExFW();
    private final DataFW.Builder        dataRW        = new DataFW.Builder();
    private final DataFW                dataRO        = new DataFW();

    @Override
    public MessageConsumer newStream(...)
    {
        return new HttpStream()::onMessage;  // inner class captures factory fields
    }

    private final class HttpStream
    {
        // Per-stream state lives here.
        // Flyweight access goes through the outer factory fields — no allocation.

        private void onBegin(long traceId, long authorization, long affinity, OctetsFW payload)
        {
            // wrap the shared RO flyweight — valid only for this call
            final HttpBeginExFW beginEx = httpBeginExRO.wrap(payload.buffer(),
                payload.offset(), payload.limit());
            // ...
        }
    }
}
```

**Rules that follow from this pattern:**

- Declare all flyweight fields (`*RO`, `*RW`) as non-static fields on the
  factory class, not on the inner stream class and not as static fields
- Never allocate a new flyweight inside a stream method — wrap the existing
  field instance instead
- A flyweight wrapped in a call is only valid for the duration of that call;
  do not store the wrapped reference on the stream for later use
- Inner stream classes must not be static — they must capture the outer
  factory instance to reach its flyweight fields
- The same pattern applies to all pluggable handler types — guard
  (e.g., `JwtGuardFactory`), vault (e.g., `FileSystemVaultFactory`), catalog
  (e.g., `ApicurioCatalogFactory`), model (e.g., `AvroModelFactory`), store
  (e.g., `MemoryStoreFactory`), metric group (e.g., `EngineMetricGroupFactory`),
  resolver, and command factories.
  Every factory class that is owned per-worker follows the same non-static
  flyweight field + inner class pattern.

### Server and Client binding patterns

Server and client bindings translate between a network protocol and
application-level Zilla streams with extension metadata. Both follow a dual
inner-class pattern:

- **`XxxServerFactory`** (for server bindings) or **`XxxClientFactory`** (for
  client bindings) — the top-level factory, holding shared flyweight fields
- **`XxxServer`** (inner class) — handles the network side: methods named
  `onNetMessage()`, `onNetBegin()`, `doNetBegin()`, `doNetData()`, `doNetEnd()`,
  etc. for inbound protocol frames
- **`XxxStream`** or **`XxxApplication`** (peer inner class) — handles the
  application side: methods named `onAppMessage()`, `onAppBegin()`,
  `doAppBegin()`, `doAppData()`, `doAppEnd()`, etc. for outbound application
  frames
- State tracking: each direction (initial → reply) maintains its own `int state`
  field, managed by an **`XxxState`** utility class with static bitmask methods
  (e.g., `accept(state)`, `consuming(state)`, `closed(state)`)
- Safety guard: `doNetEnd()`, `doAppEnd()` and similar close methods must guard
  against already-closed state with a check like `if (!closed(state))` before
  proceeding

This pattern applies to all server and client bindings uniformly. The inner
classes close over the factory instance to access shared flyweights and are
non-static.

**Method ordering in factory classes:** place the inner classes (e.g.,
`XxxServer`, `XxxStream`) before the factory-level `do*` methods (e.g.,
`doBegin`, `doData`, `doEnd`, `doAbort`, `doReset`, `doWindow`). Readers
navigating the class from the top encounter the stream logic first; the
low-level frame-writing helpers at the bottom are only consulted when needed
and do not need to be scrolled past to reach the interesting code.

### Per-stream field naming

Each inner stream class (in server, client, and proxy bindings) maintains a
standard set of fields tracking stream identity, flow control, and state. These
fields follow a strict `initialXxx` / `replyXxx` naming convention
corresponding to the two directions of a bidirectional stream (initial =
request direction, reply = response direction).

**Stream identity fields:**

| Field           | Type   | Purpose                                            |
|-----------------|--------|----------------------------------------------------|
| `initialId`     | `long` | Stream identifier for the initial direction        |
| `replyId`       | `long` | Stream identifier for the reply direction          |
| `originId`      | `long` | Origin binding identifier                          |
| `routedId`      | `long` | Routed binding identifier                          |
| `authorization` | `long` | Authorization context carried on the stream        |
| `affinity`      | `long` | Affinity hint for stream routing                   |

**Flow-control fields (declared per direction):**

| Suffix | Full name   | Type   | Purpose                                          |
|--------|-------------|--------|--------------------------------------------------|
| `Seq`  | sequence    | `long` | Running byte-position counter                    |
| `Ack`  | acknowledge | `long` | Acknowledged byte-position from peer             |
| `Max`  | maximum     | `int`  | Maximum window size (bytes in flight)            |
| `Bud`  | budget id   | `long` | Shared budget identifier for credit allocation   |
| `Pad`  | padding     | `int`  | Reserved bytes per frame (protocol overhead)     |

Every stream class declares both directions:

```java
private long initialSeq;
private long initialAck;
private int  initialMax;
private long initialBud;
private int  initialPad;

private long replySeq;
private long replyAck;
private int  replyMax;
private long replyBud;
private int  replyPad;
```

**State field:**

- `state` (`int`) — a bitfield tracking open/closing/closed for both
  directions, managed by an `XxxState` utility class with static bitmask
  methods (e.g., `openingInitial(state)`, `closedReply(state)`)

### Buffer slot usage

When a binding needs to buffer data mid-decode or mid-encode (e.g., to
reassemble a fragmented frame), it acquires a slot from a `BufferPool`:

- **Field naming:** `decodeSlot` / `encodeSlot` (int, initialised to
  `NO_SLOT`), with companion `decodeSlotOffset`, `decodeSlotReserved`,
  `encodeSlotOffset`, `encodeSlotLimit` tracking the used region
- **Acquire:** call `pool.acquire(streamId)` and immediately test the result;
  if it is still `NO_SLOT` the pool is exhausted — call `cleanup()` to
  abort/reset the stream rather than proceeding with a null buffer:

```java
if (decodeSlot == NO_SLOT)
{
    decodeSlot = decodePool.acquire(initialId);
}

if (decodeSlot == NO_SLOT)
{
    cleanupNetwork(traceId);
}
else
{
    final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
    buffer.putBytes(decodeSlotOffset, payload.buffer(), offset, limit - offset);
    decodeSlotOffset += limit - offset;
}
```

- **Release:** always guard with `!= NO_SLOT`, release the slot, and reset
  all tracking fields to zero / `NO_SLOT` in a dedicated
  `cleanupDecodeSlot()` / `cleanupEncodeSlot()` helper:

```java
private void cleanupDecodeSlot()
{
    if (decodeSlot != NO_SLOT)
    {
        decodePool.release(decodeSlot);
        decodeSlot = NO_SLOT;
        decodeSlotOffset = 0;
        decodeSlotReserved = 0;
    }
}
```

- Call the cleanup helper from the stream's `cleanup()` method so slots are
  always returned on both orderly and abortive close paths

### Network decode strategy

Server and client bindings that parse a byte-stream protocol implement their
decode logic as a **strategy pattern** using a `@FunctionalInterface` inner
interface, a `decoder` field on the inner server/client class, and a set of
private decode methods — one per protocol parse state.

**The decoder interface** is declared as a private inner interface of the
factory (e.g., `HttpServerDecoder` inside `HttpServerFactory`). Its single
method takes the server/client instance as its first parameter, followed by
the standard tracing and buffer navigation parameters, and returns the new
buffer offset (progress):

```java
@FunctionalInterface
private interface HttpServerDecoder
{
    int decode(
        HttpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit);
}
```

Passing the server/client instance into the interface method (rather than
closing over it) is what makes method references work cleanly: each decode
method is a plain private instance method on the factory that matches the
interface signature exactly and is assigned as a method reference.

**The `decoder` field** is held on the inner server/client class and
initialised in its constructor to the first expected decode state:

```java
private final class HttpServer
{
    private HttpServerDecoder decoder;

    HttpServer(...)
    {
        this.decoder = decodeEmptyLines;   // initial parse state
    }
}
```

**Decode methods** are named `decode<StateName>` and declared as private
instance methods on the factory. Each method handles exactly one parse state,
advances the buffer offset as far as it can, and transitions to the next
state by assigning `server.decoder`:

```java
private int decodeHeaders(
    HttpServer server,
    long traceId,
    long authorization,
    long budgetId,
    int reserved,
    DirectBuffer buffer,
    int offset,
    int limit)
{
    // parse request line and headers from buffer[offset..limit]
    // ...
    server.decoder = decodeContent;   // transition to next state
    return progress;
}
```

Error states assign a terminal decoder (e.g., `decodeIgnore`) that silently
discards all remaining bytes.

**The `decodeNetwork` method** on the inner server/client class drives the
loop. It tracks the previous decoder reference and keeps invoking the current
decoder until either the buffer is exhausted or the decoder does not change
(i.e., the current state could not make progress with the available bytes):

```java
private void decodeNetwork(
    long traceId,
    long authorization,
    long budgetId,
    int reserved,
    DirectBuffer buffer,
    int offset,
    int limit)
{
    HttpServerDecoder previous = null;
    int progress = offset;
    while (progress <= limit && previous != decoder)
    {
        previous = decoder;
        progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, progress, limit);
    }

    if (progress < limit)
    {
        // buffer remaining bytes in decodeSlot (see Buffer slot usage)
    }
    else
    {
        cleanupDecodeSlot();
    }
}
```

The `previous != decoder` guard is the key: if a decode method transitions to
a new state, the loop continues immediately with the new decoder — allowing
multiple logical states to be consumed in one `decodeNetwork` call (e.g.,
request line → headers → body). If the decoder does not change (the state
needs more bytes), the loop exits and any unconsumed bytes are saved to the
decode slot. `onNetworkData` prepends any previously buffered bytes from the
decode slot before calling `decodeNetwork`.

**Rules:**

- Declare the decoder interface as a `private` inner interface of the factory
  class, not in a separate file
- Name the interface `<Protocol><Role>Decoder` (e.g., `HttpServerDecoder`,
  `Http2ServerDecoder`, `MqttClientDecoder`)
- Initialise `decoder` in the server/client constructor to the first expected
  state
- Each `decode*` method must return the new buffer offset; returning the
  incoming `offset` unchanged signals "not enough data yet" and stops the loop
- Assign `server.decoder` (or `client.decoder`) directly inside decode methods
  to transition state — never call the next decode method recursively
- Terminal/error decoders must always advance progress (consume all remaining
  bytes) so the loop terminates

### Proxy binding patterns

Proxy bindings connect two protocol sides and are implemented in two variants:

**Same-protocol proxy** (e.g., `TcpProxyFactory`, `WsProxyFactory`):

- Factory class: **`XxxProxyFactory`**
- Two inner classes: **`XxxServer`** and **`XxxClient`**, each with methods
  named for their role (e.g., `onNetMessage()`, `doNetBegin()` on the server
  side; `onNetMessage()`, `doNetBegin()` on the client side)
- The same protocol abstraction flows through both sides

**Cross-protocol proxy** (e.g., `HttpKafkaProxyFactory`, `GrpcKafkaProxyFactory`):

- Factory class: **`XxxYyyProxyFactory`** (where `Xxx` and `Yyy` are the
  protocol names)
- Two inner classes named for the protocols: **`XxxProxy`** and **`YyyProxy`**,
  with methods like `onXxxMessage()`, `doXxxBegin()`, etc. and
  `onYyyMessage()`, `doYyyBegin()`, etc. respectively
- Sometimes multiple **capability variants** with separate inner class
  implementations (e.g., `HttpFetchProxy` and `KafkaFetchProxy` for different
  proxy capabilities — fetch, subscribe, etc.), each applying the above naming
  conventions for its protocol

All proxy implementations follow the same state tracking and safety guard
patterns as server/client bindings, adapted to their two-sided model.

---

## Implementing a binding — module-level checklist

The repo-wide steps for adding a new binding (issue, scaffolding, license
headers) live in the root [AGENTS.md](../AGENTS.md). Once the module skeleton
exists, the runtime-side work is:

1. Declare `module-info.java` — exports SPI packages only, keeps `internal.*`
   unexported, registers the factory SPI with `provides`
2. Define flyweight types in `src/main/resources/META-INF/zilla/<n>.idl`
3. Add the module to `runtime/pom.xml` and the root `pom.xml`
4. Verify all new dependencies are fully modular (see Java module system above)
5. Implement the type-prefixed factory SPI (e.g., `HttpBindingFactorySpi`,
   `MqttBindingFactorySpi`) and register it in
   `META-INF/services/io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi`.
   The factory SPI receives a general `Configuration`; construct the
   component-specific subclass from it (e.g., `new HttpKafkaConfiguration(config)`)
   and pass that subclass — not the raw `Configuration` — into the stream handler
   and any other collaborators that need config access
6. Implement the type-prefixed stream handler (e.g., `HttpServerFactory`,
   `MqttServerFactory`) extending `BindingHandler`, driven by the failing spec
   scripts
7. Add `XxxConfiguration extends Configuration` in `src/main/java/.../internal/`
   — even as a placeholder with an empty `ConfigurationDef` — so that runtime
   configuration properties can be added later without structural changes. See
   `HttpKafkaConfiguration` for a minimal example. Include two constructors:
   a no-args constructor that calls `super(XXX_CONFIG, new Configuration())`
   for use in tests and tooling, and a `Configuration`-parameter constructor
   that calls `super(XXX_CONFIG, config)` for production use. Prefer the
   no-args constructor in unit tests and any context where no external
   configuration is needed. Add a corresponding `XxxConfigurationTest` that
   calls `shouldVerifyConstants()` (verifying property name strings match the
   `PropertyDef` names) to satisfy class coverage requirements
8. Write unit tests covering the stream state machine (see below)

The Maven plugin generates flyweight classes during `generate-sources`. Run
`./mvnw generate-sources -pl runtime/binding-<n>` to regenerate after `.idl`
changes without a full build.

JSON schema for `options` and `routes[].when` lives in the matching spec
project — see [../specs/AGENTS.md](../specs/AGENTS.md).

---

## Unit tests

- Live in `src/test/java/` within each module
- Cover the stream handler state machine exhaustively: every state transition,
  every error path, every flow-control edge case
- Use `mockito` for collaborators; never spin up a real engine instance
- Target: 100% branch coverage on all `stream/` classes
- Run with: `./mvnw test -pl runtime/binding-<n>`

Spec-based integration tests (the source of truth for protocol behaviour) are
described in [../specs/AGENTS.md](../specs/AGENTS.md).

---

## Integration test dependencies — use the `test` implementations, never another component's

A module's tests must **never** depend on another component's production SPI
implementation (e.g. `catalog-inline`, `vault-filesystem`, `guard-jwt`,
`model-json`, `store-redis`) just to satisfy a `type:` reference in a test
`zilla.yaml`, or to exercise the edge where this component calls into another
component's API. Doing so couples test scope to an unrelated production module,
drags its transitive dependencies onto the test classpath, and obscures which
contract is actually under test.

Instead, use the engine's **test implementation** for every SPI concept. The
engine `test-jar` (already a `test` dependency of every runtime module) ships a
`type: test` implementation for each concept — `TestBinding`, `TestGuard`,
`TestVault`, `TestCatalog`, `TestStore`, `TestModel`, `TestExporter`,
`TestMetricGroup`, `TestRouter`, `TestResolver` — wired through
`META-INF/services` and activated as soon as `engine:<version>:test-jar` is on
the classpath. Reference `type: test` in the test `zilla.yaml` and configure it
inline. See [engine/AGENTS.md](engine/AGENTS.md) for the test implementations
and their schema patches.

When a `type: test` implementation lacks behavior an integration test needs,
**extend the test implementation** (additively, preserving existing behavior so
other modules' tests are unaffected) rather than reaching for the production
module. For example, `TestCatalog` supports runtime schema registration so a
binding that registers schemas through the `CatalogHandler` API can be tested
against `type: test` without depending on `catalog-inline`.

A production runtime SPI dependency belongs in the docker-image assembly, not in
a `runtime/<module>/pom.xml` `test` scope. The rare genuine exception (a test
that must assert another production module's behavior — usually a sign the test
belongs in that other module) must be justified in the PR and scoped narrowly.

---

## Benchmarks

JMH benchmarks live alongside unit tests under `src/test/java/.../bench/` (e.g.
`JsonPipelineBM`). They are compiled in the `test` phase but not run by Surefire.

**Always run the affected benchmark before checking in any change to it** — a
clean `test-compile` is not enough; a real JMH pass must succeed and produce
sensible numbers. Build the module, then run the JMH harness over the test
classpath:

```bash
# 1) compile (generates the JMH BenchmarkList) and resolve the test classpath
./mvnw test-compile -pl runtime/<module>
./mvnw dependency:build-classpath -pl runtime/<module> -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt -q

# 2) run JMH (agrona needs the jdk.internal.misc open that Surefire adds for us)
CP="runtime/<module>/target/classes:runtime/<module>/target/test-classes:$(cat /tmp/cp.txt)"
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -cp "$CP" \
    org.openjdk.jmh.Main "<BenchmarkClass>" -prof gc          # full run
# quick smoke pass while iterating:
#   ... org.openjdk.jmh.Main "<BenchmarkClass>" -f 1 -wi 3 -i 4 -w 1 -r 1 -prof gc
```

Include `-prof gc` so allocation-per-op (`gc.alloc.rate.norm`, in B/op) is
reported — it is the stable signal; throughput at smoke settings has wide error
bars. Capture representative before/after numbers in the PR when a change is
meant to affect performance.
