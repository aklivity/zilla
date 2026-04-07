# Zilla — Claude Code Guide

Zilla is a multi-protocol event-driven gateway that bridges HTTP, WebSocket, SSE,
gRPC, MQTT, and MCP to Apache Kafka. It is configured entirely via `zilla.yaml`,
with no code changes required for protocol mapping. All runtime behavior is
defined by a pipeline of named **bindings**.

---

## Repository layout

```
runtime/                     # Core engine and all bindings
  engine/                    # EngineWorker, config loader, stream model
  binding-tcp/               # TCP server/client binding
  binding-tls/               # TLS binding
  binding-http/              # HTTP/1.1 + HTTP/2 binding
  binding-kafka/             # Kafka cache + client binding
  binding-mqtt/              # MQTT binding
  binding-grpc/              # gRPC binding
  binding-sse/               # Server-Sent Events binding
  binding-http-kafka/        # HTTP↔Kafka proxy binding
  binding-mqtt-kafka/        # MQTT↔Kafka proxy binding
  binding-grpc-kafka/        # gRPC↔Kafka proxy binding
  ...
specs/                       # Integration test specifications (IT)
incubator/                   # Bindings under active development
maven-plugin/                # Code generator for flyweight types
```

Each `binding-*` module follows the same internal layout:

```
src/main/java/.../internal/
  config/         # Binding-specific config POJOs, generated from JSON schema
  stream/         # Stream handler — the hot path
  types/          # Flyweight type definitions (.idl → generated Java)
src/test/java/    # Unit tests
```

---

## Build

Zilla uses Maven with Java 21+.

```bash
# Full build with tests
./mvnw install

# Skip integration tests (faster)
./mvnw install -DskipITs

# Skip all tests
./mvnw install -DskipTests

# Build a specific module
./mvnw install -pl runtime/binding-http -am

# Run a single test class
./mvnw test -pl runtime/binding-http -Dtest=HttpServerIT   # class names are type-prefixed: Http*
```

The Maven plugin in `maven-plugin/` generates flyweight Java classes from `.idl`
files. Always run a full build after modifying any `.idl` file.

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
|---|---|---|
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

### Kafka local cache

Zilla fetches each Kafka topic partition once and stores it as memory-mapped
segment files local to the node. The cache is served to any number of downstream
clients without additional round-trips to Kafka.

- Segment files are mmap'd via `IoUtil` (Agrona)
- Segments must be explicitly `munmap`'d on rotation to avoid TLB exhaustion
  on long-running instances — call `IoUtil.unmap()` on the `MappedByteBuffer`
  when a segment is evicted
- Cache retention is controlled by AUTHORITATIVE timestamps (from Kafka broker)
  and optionally ADVISORY timestamps (from message headers)

---

## Configuration and schema

`zilla.yaml` is validated against the Zilla JSON schema. Each binding type
publishes its own JSON schema fragment, merged at build time.

Key schema conventions:

- Binding `type` values use the module artifact ID minus the `binding-` prefix
  (e.g., `binding-http` → `type: http`)
- `options` is binding-specific; defined by the binding's config schema
- `routes[].when` conditions are ordered — first match wins
- `routes[].exit` names a downstream binding in the same namespace
- Namespace is declared at the top level; all binding names are scoped to it
- Guards (`guard:` section) provide auth; referenced by binding `options.authorization`
- Vaults (`vault:` section) provide keystores/truststores; referenced by TLS bindings
- Catalogs (`catalog:` section) provide schema registries; referenced by model bindings

---

## Adding a new binding

Follow this order — tests before implementation:

1. Open a GitHub Issue to discuss the design before writing any code
2. Create `specs/binding-<n>.spec/` and write `.rpt` scripts for the happy path
   and key error scenarios, derived from the relevant protocol specification
3. Create `runtime/binding-<n>/` following the existing module layout
4. Declare `module-info.java` — exports SPI packages only, keeps `internal.*`
   unexported, registers the factory SPI with `provides`
5. Define flyweight types in `src/main/resources/META-INF/zilla/<n>.idl`
6. Add the module to `runtime/pom.xml` and the root `pom.xml`
7. Verify all new dependencies are fully modular (see Java module system section)
8. Implement the type-prefixed factory SPI (e.g., `HttpBindingFactorySpi`, `MqttBindingFactorySpi`)
   and register it in `META-INF/services/io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi`
9. Implement the type-prefixed stream handler (e.g., `HttpServerFactory`, `MqttServerFactory`)
   extending `BindingHandler`, driven by the
   failing spec scripts
10. Write unit tests covering the stream state machine
11. Add JSON schema for `options` and `routes[].when` under
    `src/main/resources/META-INF/zilla/schema/`
12. Confirm `./mvnw install` passes including all ITs

The Maven plugin generates flyweight classes during `generate-sources` phase.
Run `./mvnw generate-sources -pl runtime/binding-<n>` to regenerate after
`.idl` changes without a full build.

---

## Testing strategy

### Test-first

Zilla follows a strict test-first discipline. For every new feature or bug fix:

1. Write the spec script(s) and/or unit tests first, before any implementation
2. Confirm the tests fail for the right reason against the current code
3. Implement until the tests pass
4. Do not open a PR with implementation code that has no corresponding tests

This is especially important for new bindings. The spec scripts define the
correct protocol behavior; the Java implementation exists to satisfy them.
Never write implementation code and retrofit tests to match it — that
defeats the purpose.

### Unit tests

- Live in `src/test/java/` within each module
- Cover the stream handler state machine exhaustively: every state transition,
  every error path, every flow-control edge case
- Use `mockito` for collaborators; never spin up a real engine instance
- Target: 100% branch coverage on all `stream/` classes
- Run with: `./mvnw test -pl runtime/binding-<n>`

### Spec-based integration tests — the source of truth

Integration tests live in `specs/binding-<n>.spec/` and use the `.rpt` script
format (`.rpt`). Each script is a declarative, human-readable
description of a protocol exchange at the network byte level, derived directly
from the relevant protocol specification (RFC, OASIS standard, etc.).

**Why specs are the source of truth:**

The `.rpt` scripts capture what the protocol requires, not how Zilla implements
it. This means:

- Scripts survive complete refactors of the Java codebase — if the observable
  protocol behavior is correct, the spec passes regardless of internal changes
- Scripts can be written by anyone who understands the protocol spec, before
  the implementation exists
- Regressions are caught precisely: a failing spec identifies exactly which
  protocol scenario broke, not just that a test failed

**Script structure:**

```
# Client sends an HTTP/1.1 request
connect "zilla://streams/net0"
  connected
  write "GET /items HTTP/1.1\r\n"
  write "Host: localhost:7114\r\n"
  write "\r\n"
  read "HTTP/1.1 200 OK\r\n"
  read /Date: .*\r\n/
  read "\r\n"
  close
  closed
```

Each scenario has a corresponding type-prefixed `*IT.java` class (e.g., `HttpRequestIT`,
`KafkaFetchIT`) that runs the scripts against a live Zilla engine instance
configured with a minimal `zilla.yaml`.

**k3po and JUnit 4 rule compatibility:**

The `.rpt` scripts are driven by [k3po](https://github.com/k3po/k3po), which
integrates via a JUnit `@Rule` (`K3poRule`). JUnit `@Rule` is a JUnit 4
construct. IT classes must therefore enable JUnit 4 rule migration support
when running under JUnit 5:

```java
@ExtendWith(EngineExtension.class)
@EnableRuleMigrationSupport          // required for K3poRule under JUnit 5
class HttpRequestIT
{
    @Rule
    public final K3poRule k3po = new K3poRule().addScriptRoot("specs", "io/aklivity/zilla/specs/binding/http");

    @Test
    @Specification({ "client.request/client", "client.request/server" })
    public void shouldReceiveClientRequest() throws Exception
    {
        k3po.finish();
    }
}
```

Do not attempt to replace `K3poRule` with a JUnit 5 extension — k3po's
script execution lifecycle is bound to the `@Rule` contract. The
`@EnableRuleMigrationSupport` annotation from
`org.junit.jupiter:junit-jupiter-migrationsupport` is the correct and only
approach.

**Required spec coverage for every binding:**

- Happy path for each `kind` and each `capability` the binding supports
- Flow control: sender blocked by zero WINDOW, WINDOW credit restores flow
- Orderly close: client-initiated END, server-initiated END
- Abortive close: ABORT mid-stream, RESET on rejected stream
- Protocol error: malformed input rejected with correct error response
- Config validation: invalid `zilla.yaml` produces a clear startup error

When implementing a new protocol feature, write the spec script first by
consulting the relevant protocol RFC or specification. Do not derive expected
behavior from existing implementation code.

### Running integration tests

```bash
# All ITs
./mvnw verify -pl specs/binding-http.spec

# Single IT class
./mvnw verify -pl specs/binding-http.spec -Dit.test=HttpRequestIT
```

### Test implementations for engine concepts

Every new engine concept (binding, guard, vault, catalog, store, metric group,
resolver, command) must have a corresponding **test implementation** used
exclusively in spec-based integration tests. The test implementation proves
that the engine correctly wires and invokes the concept without requiring a
real production implementation as a test dependency.

The pattern is:

- Create `runtime/<concept>-test/` (e.g., `runtime/store-test/`,
  `runtime/binding-test/`) containing a minimal implementation of the concept's
  SPI — just enough behavior to be driven by a `.rpt` script
- The test implementation is declared as a `test` or `provided` scope
  dependency in spec modules only; it never appears in production modules
- Spec scripts drive an interaction through one known concept (e.g., a
  `binding-test` server that writes to a store) to prove the engine correctly
  wires the adjacent concept (e.g., the store) — no production binding or
  store implementation is required in the test
- The test implementation's `module-info.java` follows the same rules as
  production modules: exports SPI packages only, registers via `provides`

Example for a new `store` concept: the spec test uses a `binding-test` server
that calls `storeHandler.putIfAbsent(...)` on a named `store-test` instance
wired via `zilla.yaml`, then a client-side script verifies the expected
response. This proves the engine resolves the store by name, injects it into
the binding factory, and the handler operates correctly — all from a `.rpt`
script, with no `store-memory` dependency in the test.

---

## Code style

- Java 21; no preview features
- No Lombok
- Package-private classes preferred over public where there is no SPI contract
- `final` on all fields; immutable config objects
- Flyweight field names use the `*RO` / `*RW` suffix convention consistently
- Error paths must call `cleanup()` and release any acquired resources before
  returning
- Log via the Zilla event system (`BindingEvent`), not `java.util.logging` or
  SLF4J, on the hot path

---

## Key dependencies

| Dependency | Purpose |
|---|---|
| `agrona` | Lock-free ring buffers, flyweight buffer access, `IoUtil` for mmap |
| `zilla:maven-plugin` | Generates flyweight Java from `.idl` type definitions |
| `junit5` | Unit and integration tests |
| `mockito` | Mocking in unit tests |

---

## Contribution workflow

1. Fork the repo and create a branch: `feature/<short-description>` or
   `fix/<issue-number>-<short-description>`
2. Make changes; ensure `./mvnw install` passes with no failures
3. Open a PR against the `develop` branch (not `main`)
4. PRs require at least one approving review from a maintainer
5. Commit messages follow Conventional Commits:
   `feat(binding-http): add trailers support`
   `fix(engine): release mmap'd segment on log rotation`
6. Do not include generated sources (`target/`) or IDE files in commits

For significant new bindings or behavior changes, open a GitHub Issue first
to discuss the design before writing code.

---

## Useful references

- Docs: https://docs.aklivity.io/zilla/latest/
- Binding reference: https://docs.aklivity.io/zilla/latest/reference/config/bindings/
- How Zilla Works (architecture deep-dive): https://www.aklivity.io/post/how-zilla-works
- Examples: https://github.com/aklivity/zilla-examples
