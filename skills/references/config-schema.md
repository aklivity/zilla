# Zilla config schema (for validating a `zilla.yaml`)

Summarized from https://docs.aklivity.io/zilla/next/reference/config/overview.html
— consult that page (or the specific binding/guard/vault/catalog page linked
from it) for full property-level detail on any one type; this file is only
for a quick top-level sanity check.

## Top-level shape

A `zilla.yaml` has exactly these top-level keys:

```yaml
---
name: <namespace>        # required, string
bindings: { ... }        # named map
guards: { ... }           # named map, optional
vaults: { ... }           # named map, optional
catalogs: { ... }         # named map, optional
stores: { ... }           # named map, optional
telemetry: { ... }        # optional
```

Anything outside this set at the top level is not part of the schema.

## Bindings

Every binding needs a `type` and a `kind`, and (except for a true dead-end
like a terminal `echo` binding) `routes` with conditions and an `exit`
pointing at the next binding in the chain, or an unconditional `exit` as a
default.

**Typical pipeline order** (client-facing → server-facing):
`tcp server → tls server → <protocol> server → <bridge> proxy → kafka
cache_client → kafka cache_server → kafka client → tls client → tcp client`

When validating a generated config, check each binding's `type`/`kind`
combination against the tables below. A `kind` not listed for a given
`type` is invalid.

### Protocol bindings (encode/decode one protocol)
| type | valid kinds |
|---|---|
| `tcp` | `server`, `client` |
| `tls` | `server`, `client`, `proxy` |
| `http` | `server`, `client` |
| `grpc` | `server`, `client` |
| `mqtt` | `server`, `client` |
| `sse` | `server`, `client` |
| `ws` | `server`, `client` |
| `amqp` | `server` — Incubator, needs `ZILLA_INCUBATOR_ENABLED=true` |
| `pgsql` | `server`, `client` |

### Bridge bindings (translate between two protocols)
| type | valid kinds | bridges |
|---|---|---|
| `http-kafka` | `proxy` | HTTP CRUD verbs ↔ Kafka produce/fetch |
| `grpc-kafka` | `proxy` | gRPC request/response ↔ Kafka topic streams |
| `mqtt-kafka` | `proxy` | MQTT pub/sub ↔ Kafka topic streams |
| `sse-kafka` | `proxy` | SSE ↔ Kafka topic streams (server push) |
| `kafka-grpc` | `remote_server` | Kafka topic stream → dispatched as gRPC requests |
| `http-filesystem` | `proxy` | HTTP GET path ↔ local filesystem path (pairs with `filesystem` binding) |
| `pgsql-kafka` | `proxy` | PostgreSQL request/response ↔ Kafka topic streams |
| `openapi-asyncapi` | `proxy` | OpenAPI operations ↔ AsyncAPI operations |

### Spec-driven bindings (generated from an API spec file)
| type | valid kinds |
|---|---|
| `openapi` | `server`, `client` — composes `tcp`+`tls`+`http` internally |
| `asyncapi` | `server`, `client`, `proxy` — composes Kafka/MQTT/HTTP bindings internally |

### Kafka bindings
| type | valid kinds |
|---|---|
| `kafka` | `cache_client`, `cache_server`, `client` |
| `kafka-proxy` | `proxy` — Zilla Plus only |

### Infrastructure bindings
| type | valid kinds |
|---|---|
| `filesystem` | `server` |
| `proxy` (HAProxy PROXY v2) | `server`, `client` |
| `socks` | `server`, `client`, `remote_server`, `remote_client` |
| `smux` | `server`, `client` |
| `fan` | `server` |
| `echo` | `server` |
| `schema-registry` | `proxy` |
| `risingwave` | `proxy` — Incubator |
| `mcp` | `server`, `client`, `proxy` |

Note there are two different bindings named `proxy`: the HAProxy-protocol
infrastructure binding (`type: proxy`, kind `server`/`client`) and any
binding used with `kind: proxy` (e.g. `http-kafka` proxy). Don't confuse
them when reviewing a config.

## guards

Named map. Each guard has a `type`: `jwt`, `api-keys` (Plus), `azure-ad`
(Plus), `aws-lambda` (Plus), `identity` (Plus, pass-through/testing only).
Guards are referenced from binding routes via `guarded` to gate access by
role.

## vaults

Named map. Each vault has a `type`: `filesystem` (PEM/PKCS12/JKS from local
files), `aws-acm` (Plus), `aws-secrets` (Plus). Used by `tls` bindings to
supply keys/certs.

## catalogs

Named map. Each catalog has a `type`: `inline` (schemas defined in
`zilla.yaml` itself), `filesystem`, `schema-registry`,
`confluent-schema-registry` (Plus), `apicurio-registry`,
`karapace-schema-registry`, `aws-glue` (Plus). Used by bindings that
validate message payloads against a schema.

## stores

Named map. Each store has a `type`: `memory` (resets on restart), `redis`
(Plus), `hazelcast` (Plus). Used for state that needs to survive across
requests (e.g. API key revocation lists, MQTT session state).

## telemetry

```yaml
telemetry:
  attributes: { ... }     # optional, default metric attributes
  exporters: { ... }      # named map
  metrics: [ ... ]        # array of metric names, e.g. stream.*, http.*, grpc.*
```

Exporter `type`: `stdout`, `prometheus`, `otlp`, `aws-cloudwatch` (Plus),
`syslog` (Plus).

`[Plus]` = requires a Zilla Plus license — flag if a generated config for a
plain open-source Zilla deployment uses one of these, since it won't run
without a license. `[Incubator]` = experimental, only works with
`ZILLA_INCUBATOR_ENABLED=true` set on the Zilla container/process — flag if
that env var isn't set anywhere in the accompanying `compose.yaml`.

## How to validate a generated/edited config with this reference

1. Confirm the top-level keys are only from the set above, and `name` is
   present.
2. For every binding: confirm `type` is real, confirm `kind` is one of the
   valid kinds for that `type` (tables above), confirm it has `routes`
   and/or `exit`.
3. Walk the binding chain via `routes...exit` end to end and confirm it
   doesn't dead-end unexpectedly and roughly follows the typical pipeline
   order (protocol servers → bridge/proxy → Kafka bindings → protocol
   clients).
4. Confirm anything referenced by name from a binding (a `guard`, `vault`,
   or `catalog`) is actually declared in the corresponding top-level
   section.
5. Flag any `[Plus]` type if the target deployment is open-source Zilla,
   and any `[Incubator]` type if `ZILLA_INCUBATOR_ENABLED` isn't set.
6. If a binding's specific option fields are in question (not just
   type/kind), fetch that binding's own reference page — e.g.
   `https://docs.aklivity.io/zilla/next/reference/config/bindings/<type>/`
   — rather than guessing from memory.
