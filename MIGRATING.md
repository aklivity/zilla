# Migrating from Zilla 1.x to 2.x

This guide covers backwards-incompatible changes between Zilla 1.x and the
upcoming 2.0.0 release. It is scoped to the core `zilla` repository (engine
and in-tree bindings/guards/vaults); components shipped from other
repositories (e.g. `zilla-plus`) are covered separately.

Read this before upgrading a running `zilla.yaml` deployment across the 2.0.0
boundary. Each section describes what changed, why, and how to update your
configuration.

## Config file format

### `zilla.json` fallback removed

Zilla's original config format (predating `zilla.yaml`) is gone. Earlier
1.x releases would silently fall back to a sibling `zilla.json` file with a
deprecation warning if `zilla.yaml` was missing; that fallback and its
warning have been removed entirely. `zilla.yaml` has been the only supported
format for a long time — this only affects a config directory that still
relies on the legacy fallback instead of an actual `zilla.yaml`.

**Action:** ensure your deployment has a `zilla.yaml` present; delete any
now-unused `zilla.json`.

## Bindings

### `binding-kafka`: `client`-kind bindings no longer route by topic condition

A `kafka` binding with `kind: client` maps a stream to a single broker
connection; it should not vary by topic — per-topic dispatch belongs to
`cache_client`/`cache_server`, which already index cached records by topic.
The schema now forbids `routes[].when` for `kind: client`, so a topic
condition on a client-kind route is rejected at config-validation time
instead of being silently ignored.

```yaml
# 1.x — conditional routing on a client-kind binding (now rejected)
bindings:
  kafka_client0:
    type: kafka
    kind: client
    routes:
      - exit: net0
        when:
          - topic: events-*

# 2.x — single, unconditional exit
bindings:
  kafka_client0:
    type: kafka
    kind: client
    exit: net0
```

If you relied on `when: [{ topic: ... }]` to select between multiple exits
on a `client`-kind binding, split it into multiple bindings (one per exit)
routed to from upstream, or move the topic-scoped behavior to the
`cache_client`/`cache_server` binding kinds, which still support it.

A related, narrower cleanup: the undocumented `routes[].when[].groupId`
condition (a leftover from `mqtt-kafka`'s old Kafka-consumer-group session
ownership, replaced by Store-based ownership ahead of 2.0) has also been
removed. No shipped spec, example, or fixture ever configured it, so this is
unlikely to affect any real `zilla.yaml`.

### `binding-kafka`: `options.sasl` removed — use `options.authorization`

`options.sasl` configures SASL credentials directly and statically on the
binding. `options.authorization` replaces it with a guard-name-keyed map, so
credentials are resolved from a referenced guard instead of being hardcoded
on the binding — for a static, non-request-driven credential pair like a
SASL username/password, an `inline` guard (`type: inline`) is the simplest
guard to reference: it holds the values as its own `options.identity` /
`options.credentials`, and the binding pulls them back out via
`{identity}`/`{credentials}` templates. The example below reuses the same
two environment variables in both forms to show that migrating is just
relocating them, not re-provisioning anything:

```yaml
# 1.x
bindings:
  app0:
    type: kafka
    kind: client
    options:
      servers:
        - localhost:9092
      sasl:
        mechanism: plain
        username: ${{env.SASL_USERNAME}}
        password: ${{env.SASL_PASSWORD}}
    routes:
      - exit: net0

# 2.x
guards:
  guard0:
    type: inline
    options:
      identity: ${{env.SASL_USERNAME}}
      credentials: ${{env.SASL_PASSWORD}}
bindings:
  app0:
    type: kafka
    kind: client
    options:
      servers:
        - localhost:9092
      authorization:
        guard0:
          credentials:
            mechanism: plain
            username: "{identity}"
            password: "{credentials}"
    routes:
      - exit: net0
```

**Action:** move every `options.sasl` block's `username`/`password` values
into an `inline` guard's `options.identity`/`options.credentials`, and
reference that guard from `options.authorization` as shown above
(`oauthbearer` uses `{mechanism: oauthbearer, token: ...}` instead of
`username`/`password`).

### `binding-grpc`: `options.services` removed — use a catalog instead

The `grpc` binding no longer reads `.proto` service definitions from local
files declared under `options.services`. `options` is empty for both
`kind: server` and `kind: client`; service/method definitions are now
sourced from a `catalog` reference on the binding, the same mechanism used
by model bindings (JSON Schema, Avro, Protobuf) elsewhere in Zilla.

```yaml
# 1.x
bindings:
  net0:
    type: grpc
    kind: server
    options:
      services:
        - protobuf/echo.proto
    routes:
      - exit: app0
        when:
          - method: example.EchoService/*

# 2.x
catalogs:
  catalog0:
    type: inline
    options:
      subjects:
        echo:
          schema: |
            syntax = "proto3";
            package example;
            service EchoService {
              rpc EchoUnary(EchoMessage) returns (EchoMessage);
            }
            message EchoMessage {
              string message = 1;
            }
bindings:
  net0:
    type: grpc
    kind: server
    catalog:
      catalog0:
        - subject: echo
    routes:
      - exit: app0
        when:
          - method: example.EchoService/*
```

**Action:** register each `.proto` service definition in a catalog (an
`inline` catalog with an embedded schema, as shown above, is the simplest
drop-in replacement for a local file — a `schema-registry`, `apicurio`, or
`karapace` catalog also works if you already run one) and reference it via
`catalog:` on the binding instead of `options.services`.

### `binding-mqtt-kafka`: `options.server` removed — use the engine's `service.hostname` property

The `options.server` option (`serverRef` internally, announced deprecated in
1.x, tracking issues #1797/#1802) has been removed entirely: the config
class, adapter, schema property, and the effective-hostname machinery it
fed. It was superseded by the engine-level `service.hostname` configuration
property.

```yaml
# 1.x
bindings:
  mqtt_kafka0:
    type: mqtt-kafka
    kind: proxy
    options:
      server: mqtt-1.example.com:1883
```

```bash
# 2.x — set once at the engine level, not per-binding
zilla start -Pzilla.engine.service.hostname=mqtt-1.example.com
```

**Action:** remove `options.server` from every `mqtt-kafka` binding and set
the `service.hostname` engine configuration property instead (it is not a
`zilla.yaml` binding option).

### `binding-mqtt`: `kind: server` now requires `options.store`

Session state for a `kind: server` mqtt binding is now backed by a
referenced `store` binding instead of living only in engine memory, so
`options.store` is required (and `options` itself must be present).

```yaml
# 1.x
bindings:
  mqtt_server0:
    type: mqtt
    kind: server
    options:
      versions: [v5]
    exit: app0

# 2.x
bindings:
  mqtt_server0:
    type: mqtt
    kind: server
    options:
      versions: [v5]
      store: store0
    exit: app0
stores:
  store0:
    type: memory   # or another store implementation
```

**Action:** add `options.store` (referencing a configured `store` binding,
e.g. `store-memory`) to every `mqtt` binding with `kind: server`.

### `binding-tcp`: `routes[].exit` is now actually enforced for `kind: server`

A 1.x schema authoring bug wrote `routes: { required: [exit] }` directly on
the array-typed `routes` schema, where `required` has no effect — a
`kind: server` tcp binding could have a route with no `exit` and still pass
validation. 2.x fixes the schema to place `required: [exit]` on each route
item instead, and requires the array to be non-empty. A config that relied
on the old, unenforced schema would already misbehave at runtime (a route
with nowhere to send the stream), so this only surfaces a pre-existing
problem earlier, at validation time.

**Action:** if a 2.x tcp `kind: server` binding fails validation on
`routes`, add an `exit` to every route item.

### `binding-openapi` / `binding-asyncapi` / `binding-openapi-asyncapi`: `options.specs.<name>.servers` is now a list of strings, and required

`options.specs.<name>.servers` used to be a list of server objects
(`{ url, host, pathname }` for asyncapi/openapi-asyncapi; `{ url }` for
openapi), each `url` a full URL. It is now a list of plain URL strings, and
`servers` itself is required under every `specs` entry (it was optional in
1.x). The URL scheme reflects the protocol the spec describes — `http://`
for an openapi/asyncapi HTTP server, `kafka://` for an asyncapi Kafka
server, and so on.

```yaml
# 1.x
bindings:
  openapi0:
    type: openapi
    kind: client
    options:
      specs:
        petstore:
          servers:
            - url: http://localhost:9090/prod
          catalog:
            catalog0:
              subject: petstore
              version: latest

# 2.x
bindings:
  openapi0:
    type: openapi
    kind: client
    options:
      specs:
        petstore:
          servers:
            - http://localhost:9090/prod
          catalog:
            catalog0:
              subject: petstore
              version: latest
```

**Action:** change every `options.specs.<name>.servers` entry from a list
of `{url: ...}` objects to a list of plain URL strings (unwrap `url:` and
keep the URL value as-is), and make sure `servers` is present under every
`specs` entry — it can no longer be omitted.

### `binding-asyncapi` / `binding-openapi-asyncapi`: route conditions renamed, `kind: client` no longer routes

Unlike `binding-openapi` (which never supported `routes` in 1.x — routing
there is new in 2.x, not a rename of anything), `binding-asyncapi` and
`binding-openapi-asyncapi` did support `routes[].when[].api-id` /
`operation-id` (and the equivalent `routes[].with.api-id` / `operation-id`)
in 1.x. In 2.x these are renamed to `spec` / `operation` (plus a new `tag`
and per-route `servers` condition), and the old property names are now
rejected outright rather than silently ignored. `routes[].with` is also no
longer accepted on a `kind: server` route. Additionally, `kind: client`
bindings no longer accept `routes` at all — 1.x allowed a client-kind
`routes` list (with `exit` forbidden per item); 2.x rejects `routes`
entirely for `kind: client`.

```yaml
# 1.x
bindings:
  asyncapi0:
    type: asyncapi
    kind: server
    routes:
      - when:
          - api-id: petstore
            operation-id: getPets
        exit: app0

# 2.x
bindings:
  asyncapi0:
    type: asyncapi
    kind: server
    routes:
      - when:
          - spec: petstore
            operation: getPets
        exit: app0
```

**Action:** rename `api-id`/`operation-id` to `spec`/`operation` in every
`routes[].when[]` (and drop `routes[].with.api-id`/`operation-id`, which no
longer resolves) for `asyncapi` and `openapi-asyncapi` bindings; if a
`kind: client` binding has a `routes` list, remove it — client bindings
must use a plain `exit` instead.

### `binding-mcp`: options and routes restructured

The 1.x `mcp` binding's options and routing model has changed:

- `options.prompts` (a local, inline list of prompt name/description
  entries) has been removed entirely — no config-level replacement.
- `options.authorization` changes from `{ name: <guardName> }` to a
  guard-name-keyed map: `{ <guardName>: { credentials: ... } }`.
- `routes[].when[].capability` (an enum array of `tools`/`prompts`/
  `resources`) is replaced by separate `tool` / `prompt` / `resource`
  condition keys (each a string or array of strings).
- `kind: server` bindings no longer accept `routes` at all.
- `kind: client` bindings now require a new `options.server` (a URI).

```yaml
# 1.x
bindings:
  mcp_server0:
    type: mcp
    kind: server
    options:
      authorization:
        name: guard0
    routes:
      - when:
          - capability: [tools]
        exit: app0

# 2.x — kind: server can no longer route; move dispatch to the exit binding
bindings:
  mcp_server0:
    type: mcp
    kind: server
    options:
      authorization:
        guard0:
          credentials: "Bearer {credentials}"
    exit: app0
```

**Action:** treat this as a from-scratch reconfiguration per `kind` rather
than a field rename — drop `options.prompts`, re-key `options.authorization`
by guard name, replace `capability` route conditions with
`tool`/`prompt`/`resource`, remove `routes` from `kind: server` bindings,
and add `options.server` to `kind: client` bindings.

## Guards

### `guard-jwt`: `kind` is no longer accepted

The guard-level `kind` property (already meaningless for guards) is now
explicitly disallowed for `type: jwt`. This only affects a config that
literally set `kind:` on a jwt guard entry — unusual, and not something
any shipped spec or example did.

## Apache Kafka compatibility

Zilla 2.x's Kafka client and cache targets **Apache Kafka 2.8.0 or higher**.
This is a statement of the supported floor, not a new runtime version check
— brokers older than 2.8.0 are not tested against and may not negotiate the
API versions Zilla's client and cache rely on.

