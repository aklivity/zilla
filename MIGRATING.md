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
    type: <your-schema-registry-catalog>   # e.g. inline, schema-registry, apicurio, karapace
    options:
      subject: echo
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
`inline` catalog with an embedded schema is the simplest drop-in
replacement for a local file) and reference it via `catalog:` on the
binding instead of `options.services`.

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
# 2.x — set once at the engine level (JVM property or environment
# variable), not per-binding
zilla start -Pzilla.engine.service.hostname=mqtt-1.example.com
# or: ZILLA_ENGINE_SERVICE_HOSTNAME=mqtt-1.example.com zilla start
```

**Action:** remove `options.server` from every `mqtt-kafka` binding and set
the `service.hostname` engine configuration property instead (it is not a
`zilla.yaml` binding option).

## Apache Kafka compatibility

Zilla 2.x's Kafka client and cache targets **Apache Kafka 2.8.0 or higher**.
This is a statement of the supported floor, not a new runtime version check
— brokers older than 2.8.0 are not tested against and may not negotiate the
API versions Zilla's client and cache rely on.

## Still deprecated — candidates for a future release

The following are currently marked deprecated but **have not been removed**.
They are called out here so the decision to drop them for 2.0.0 (or a later
2.x release) can be made deliberately, not as something already acted on in
this document:

| Item | Deprecated in favor of | Notes |
| --- | --- | --- |
| `binding-kafka` `options.sasl` | `options.authorization` (guard-based) | Schema still marks it `deprecated: true` but continues to validate and function |

None of these are removed by this document — they require their own review
and, where user-facing, their own deprecation-window decision before being
dropped.
