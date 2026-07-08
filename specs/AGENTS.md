# Zilla specs — Agent Guide

Guidance scoped to `specs/` — integration test specifications and JSON
schema patches for every pluggable component (binding, guard, vault, catalog,
store, model, etc.). Cross-cutting repo guidance lives in the root
[AGENTS.md](../AGENTS.md). Runtime-side conventions live in
[../runtime/AGENTS.md](../runtime/AGENTS.md).

---

## Configuration and schema

`zilla.yaml` is validated against an aggregated JSON schema that is assembled
dynamically at runtime from JSON schema patch contributions made by each
installed component.

### How the schema is assembled

The engine owns the base `zilla.yaml` schema — it defines the top-level
structure (`name`, `bindings`, `guards`, `vaults`, `catalogs`, `stores`,
`telemetry`, etc.). Each pluggable component — binding, guard, vault, catalog,
store, model, metric group, resolver, command — contributes a JSON schema patch
that extends the engine schema to describe its own `type`-specific `options`,
`routes[].when`, `routes[].with`, and any other fields it introduces.

The engine discovers and applies these patches at startup by loading them from
the classpath, one per installed component. The result is a single aggregated
schema that reflects exactly the components present in the running Zilla
instance — no component contributes schema for types that are not installed.

### Nested extension points within a component's own schema

Some components define further-pluggable sub-concepts inside their own
`options` — for example, a binding might maintain an internal registry of
named sub-behaviors (index types, transform types, etc.) that other,
independently-versioned modules should be able to extend without ever editing
the owning component's schema patch. This is a different problem from the
top-level `type` discriminator (`$defs/binding`, `$defs/guard`, etc.) above,
since a sub-concept's implementations may ship from other modules, or from
another repository entirely.

The convention is a dedicated top-level `$defs` scaffold per component kind:
`$defs/binding-ext`, `$defs/guard-ext`, `$defs/vault-ext`, `$defs/catalog-ext`,
`$defs/store-ext`, `$defs/exporter-ext`, `$defs/model-ext`. These are
pre-seeded as empty objects in the base engine schema, exactly like
`$defs/guard`/`$defs/vault`/`$defs/models` are pre-seeded for the top-level
discriminators. Pre-seeding is what makes them safe for independent,
unordered contributions: JSON Patch's `add` operation replaces an existing
key's value wholesale, so a parent that multiple unrelated modules might add
sub-keys under must already exist before any of them run — otherwise whichever
patch applies second silently wipes out the first's contribution.

A component that owns a sub-concept adds its own key under the relevant
`-ext` scaffold, once, in its own schema patch:

```json
{ "op": "add", "path": "/$defs/binding-ext/mcp", "value": {
    "indexes": { "properties": { "type": { "enum": [] } }, "allOf": [] }
} }
```

This `add` is safe even though `binding-ext` itself is shared, because
`binding-mcp` is the only thing that will ever name the `mcp` key underneath
it. Each pluggable implementation of that sub-concept — in-tree or from a
separate module or repository — then contributes its own patch appending into
the now-existing scaffold, using the same append-only idiom as the top-level
discriminators:

```json
{ "op": "add", "path": "/$defs/binding-ext/mcp/indexes/properties/type/enum/-", "value": "keyword" },
{ "op": "add", "path": "/$defs/binding-ext/mcp/indexes/allOf/-", "value": {
    "if": { "properties": { "type": { "const": "keyword" } } },
    "then": { "additionalProperties": false }
} }
```

Path shape: `/$defs/<kind>-ext/<type>/<extension-point-name>`, where `<kind>`
is the owning component's kind (`binding`, `guard`, `vault`, `catalog`,
`store`, `exporter`, `model`) and `<type>` is that component's own `type`
value (e.g. `mcp`). This keeps every component's sub-extension points
consistently namespaced and discoverable by grepping for `-ext`, regardless
of which module or repository ends up contributing to them.

### Schema file locations

Each component's JSON schema patch is checked in once, in the spec project:

```
specs/binding-<type>.spec/
  src/main/resources/
    META-INF/zilla/schema/
      binding-<type>.schema.json     # options, routes[].when, routes[].with
```

The Maven build copies this into the runtime module's output so it is
available on the classpath at runtime. You never manually copy or maintain a
second copy — the spec project is the single source of truth.

The same pattern applies to all pluggable types:

| Component | Spec project (schema lives here) |
| --- | --- |
| Binding | `specs/binding-<type>.spec/` |
| Guard | `specs/guard-<type>.spec/` |
| Vault | `specs/vault-<type>.spec/` |
| Catalog | `specs/catalog-<type>.spec/` |
| Store | `specs/store-<type>.spec/` |
| Model | `specs/model-<type>.spec/` |

### Schema conventions

- Binding `type` values use the module artifact ID minus the `binding-` prefix
  (e.g., `binding-http` → `type: http`; `binding-mcp-kafka` → `type: mcp_kafka`)
- `options` is component-specific; fully described in that component's schema patch
- `routes[].when` conditions are ordered — first match wins
- `routes[].exit` names a downstream binding in the same namespace
- Namespace is declared at the top level; all binding names are scoped to it
- Guards (`guard:` section) provide auth; referenced by binding `options.authorization`
- Vaults (`vault:` section) provide keystores/truststores; referenced by TLS bindings
- Catalogs (`catalog:` section) provide schema registries; referenced by model bindings
- Stores (`stores:` section) provide mutable runtime state; referenced by guards and bindings

### Adding a new component's schema

When implementing a new binding, guard, vault, or other component:

1. Create `binding-<type>.schema.json` in the spec project under
   `src/main/resources/META-INF/zilla/schema/`
2. Copy it to the runtime project under the same path
3. The engine picks it up from the classpath at startup automatically —
   no engine registration step required
4. Validate the schema by running the spec IT with an intentionally invalid
   `zilla.yaml` and confirming a clear validation error is produced

---

## Spec-based integration tests — the source of truth

Integration tests live in `specs/binding-<n>.spec/` and use the `.rpt` script
format. Each script is a declarative, human-readable description of a protocol
exchange at the network byte level, derived directly from the relevant
protocol specification (RFC, OASIS standard, etc.).

**Why specs are the source of truth:**

The `.rpt` scripts capture what the protocol requires, not how Zilla implements
it. This means:

- Scripts survive complete refactors of the Java codebase — if the observable
  protocol behavior is correct, the spec passes regardless of internal changes
- Scripts can be written by anyone who understands the protocol spec, before
  the implementation exists
- Regressions are caught precisely: a failing spec identifies exactly which
  protocol scenario broke, not just that a test failed

### Script folder layout

Scripts are organised under `streams/network/` and `streams/application/`
within the spec project. Each scenario is a subdirectory containing a
`client.rpt` and a `server.rpt`. Never copy scripts from the spec project into
the runtime project — the spec module is declared as a test-scoped dependency
in the runtime module's `pom.xml`, so scripts are resolved automatically via
the classpath at test time. The `network/` and `application/` trees are
**shared between the server-kind and client-kind** ITs for the same binding
type — there is no duplication. The IT class declares a `K3poRule` script root
pointing at `streams/network/...` or `streams/application/...` and references
scripts as `${net}/scenario/client` and `${net}/scenario/server` (or `${app}/`
for application-layer scenarios).

Each scenario also has a corresponding test method in a `NetworkIT` or
`ApplicationIT` class (in the spec project's `src/test/` tree) that runs
`client.rpt` and `server.rpt` directly against each other — without Zilla —
to verify that the two scripts are complementary and self-consistent. Every
new scenario must have both a binding IT method (running against Zilla) and a
`NetworkIT`/`ApplicationIT` method (running the scripts peer-to-peer).

IT test method names are derived from the scenario directory name by
prepending `should` and converting `dot.separated.words` to `camelCase` — for
example, the scenario directory `update.topic.partition.offset` becomes the
method name `shouldUpdateTopicPartitionOffset()`. Follow this convention
exactly so the mapping between script directories and test methods is
immediately obvious.

### Script structure

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

Each scenario has a corresponding type-prefixed `*IT.java` class (e.g.,
`HttpRequestIT`, `KafkaFetchIT`) that runs the scripts against a live Zilla
engine instance configured with a minimal `zilla.yaml`.

### `XxxFunctions` — builder and matcher helpers for extension types

Single-protocol binding spec projects (e.g., `binding-http.spec`,
`binding-kafka.spec`) provide an `XxxFunctions` class (e.g., `HttpFunctions`,
`KafkaFunctions`) under `src/main/java/.../internal/` that exposes `@Function`
methods used in `.rpt` scripts to construct and match extension type bytes.
Cross-protocol proxy binding specs (e.g., `binding-http-kafka.spec`) do **not**
define their own `XxxFunctions` — they reuse the `XxxFunctions` from each
protocol they map between.

- **Builder functions** (e.g., `beginEx()`, `flushEx()`, `endEx()`) — used on
  the **write side** of a script to build the full binary extension. Every
  field that must be set is specified on the builder.
- **Matcher functions** (e.g., `matchBeginEx()`, `matchFlushEx()`) — used on
  the **read side** of a script. Matchers implement `BytesMatcher` and only
  need to assert the fields relevant to the test scenario; unspecified fields
  are ignored. This allows partial matching without coupling scripts to fields
  that are not under test.

Add a builder and a matcher for every extension type declared in the
binding's `.idl`. The matcher's `build()` method returns `null` (skip check)
when no constraints have been set, allowing unconditional reads when the
extension content is irrelevant.

**Typed method variants for non-string values:** JUEL (the expression language
used in `.rpt` scripts) cannot dispatch Java method overloads by argument type
— it always coerces numeric literals to `long`. Where a field accepts values
of different primitive types, provide explicitly typed method variants rather
than overloading a single method name. For example, use `headerInt(name, int)`,
`headerLong(name, long)`, `headerShort(name, short)` instead of overloading
`header(name, value)`. See `KafkaFunctions` for examples of this pattern.

**Alignment in `.rpt` scripts:** when a function call is chained across
multiple lines inside `${ }`, align each `.` directly under the `.` of the
opening function name:

```text
read zilla:begin.ext ${http:matchBeginEx()
                           .typeId(zilla:id("http"))
                           .header(":method", "PUT")
                           .build()}
```

The leading `.` of each method call lines up with the `.` before
`matchBeginEx` (or `beginEx`, etc.), not under the `$` or the function
argument list.

### k3po and JUnit 4 rule compatibility

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

### Required spec coverage for every binding

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

---

## Engine concept test schemas

Test schema patches for engine concept types (used by the `specs/engine.spec`
IT and unpacked into the engine module's test resources) are described in
[../runtime/engine/AGENTS.md](../runtime/engine/AGENTS.md). The spec project
is the single source of truth — do not duplicate these JSON files anywhere in
the engine module.
