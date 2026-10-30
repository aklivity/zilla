# mcp.kafka.proxy

Exposes Apache Kafka and a Schema Registry to MCP (Model Context Protocol) clients
through a single Streamable HTTP endpoint on port `7114`, fronted by JWT
authentication. Two bundled toolkits are served from one gateway:

- `kafka` (`mcp-kafka`) produces to and consumes from the `orders` topic and manages
  topics, configs, ACLs, brokers, the cluster and consumer groups on a real broker.
- `kafka_sr` (`mcp-schema-registry`) registers, reads and checks schemas on a real Karapace.

Neither toolkit needs an operator-authored tool list: each binding bundles its tools,
and the caller's JWT scopes decide which of them are visible and callable.

```text
                          ┌──────────────────────────────────── Zilla ────────────────────────────────────┐
client ── Bearer JWT ────►│ tcp(7114) → http → mcp(server, jwt) → mcp(proxy, guarded routes)               │
                          │                                         │                          │           │
                          │                                         ▼                          ▼           │
                          │                              mcp-kafka(client)        mcp-schema-registry(client)│
                          │                              kafka toolkit            kafka_sr toolkit          │
                          └───────────────────────────────────────┼──────────────────────────┼─────────────┘
                                                                  ▼                          ▼
                                                    kafka.examples.dev:29092      karapace-registry:8081
                                                    (single-node KRaft broker)    (stores schemas in Kafka)
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP, authenticates the session with the `authn_jwt` guard |
| `mcp` | `proxy` | Routes each toolkit, gating it with `guarded:` |
| `mcp-kafka` | `kind: client` | Generates its own Kafka client pipeline from `options.servers`; routes restrict `produce_message` and `consume_messages` to the `orders` topic |
| `mcp-schema-registry` | `kind: client` | Fixed tool set for the Schema Registry REST API; `options.server` is the only required setting |

## Authorization model

The `scope` claim of the JWT is a space-separated list of roles, matched against the roles
each `guarded:` route requires. A toolkit-level scope gates the toolkit, and a tool-level
scope layers on top for tools that mutate state. A caller that lacks a scope does not see
the tool in `tools/list`: unauthorized tools are absent, not marked denied.

| Scope | Grants |
| --- | --- |
| `kafka:tools` | the `kafka` toolkit's read-only tools: `consume_messages` (topic `orders` only), `describe_topic_configs`, `describe_broker_configs`, `list_acls`, `list_topics`, `describe_topic`, `cluster_overview`, `list_brokers`, `describe_cluster`, `list_consumer_groups`, `describe_consumer_group`, `describe_consumer_group_lag` |
| `kafka:write` | additionally `produce_message` (topic `orders` only) |
| `kafka:admin` | additionally `create_topics`, `delete_topics`, `alter_topic_configs`, `alter_broker_configs`, `reset_offsets` |
| `kafka:acls` | additionally `create_acls`, `delete_acls` |
| `kafka_sr:tools` | the `kafka_sr` toolkit's read-only tools: `list_subjects`, `describe_subject`, `get_schema`, `check_compatibility`, `get_compatibility`, `set_compatibility`, `delete_subject`, `delete_schema_version` |
| `kafka_sr:write` | additionally `register_schema` |

Routes match in declaration order, so each tool-level route in `etc/zilla.yaml` comes
before the catch-all that carries only the toolkit-level guard. Tools that share a scope
share one route: a `when` list matches by OR.

`create_acls` and `delete_acls` have their own scope instead of riding on `kafka:admin`:
an operator trusted to create topics or change configs is not thereby trusted to grant or
revoke another principal's access.

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla plus a real single-node KRaft Kafka broker on `:9092`
(`kafka.examples.dev`), with the `orders` topic created by the one-shot `kafka-init`
service, and a real Karapace Schema Registry on `:8081` that stores its schemas in a
`_schemas` topic on that same broker. The broker enables Kafka's `StandardAuthorizer`
with `allow.everyone.if.no.acl.found`, which the ACL tools need and which leaves every
topic open until an ACL is created for it.

## Verify

Run the automated smoke test that the build workflow uses:

```bash
./.github/test.sh
```

It starts a one-off `verify` container inside the stack that mints JWTs from
[private.pem](private.pem) and runs `etc/test/verify.sh`, where all the assertions live.

To drive the gateway by hand, mint a token with the `jwt-cli` service. The key in
[private.pem](private.pem) matches the public key in `guards.authn_jwt.options.keys`; see
[http.proxy.jwt](../http.proxy.jwt/README.md) for how it was generated.

```bash
export JWT_TOKEN=$(docker compose run --rm jwt-cli encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --payload "scope=kafka:tools kafka:write kafka:admin kafka:acls kafka_sr:tools kafka_sr:write" \
    --secret @/private.pem | tr -d '\r\n')
```

Omit scopes from `--payload` to see tools disappear from `tools/list`. The examples below
use the bundled `tools-list-client`, a headless MCP SDK client: with no `CALL_TOOL` it
lists tools, otherwise it calls one and prints its result. Or point the MCP Inspector at
`http://localhost:7114/mcp` with the token as a bearer header.

### Observe filtered tools/list results

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" tools-list-client
```

```text
kafka__produce_message
kafka__consume_messages
kafka__create_topics
...
kafka_sr__list_subjects
kafka_sr__register_schema
...
```

A token with only `scope=kafka:tools kafka_sr:tools` lists the read-only tools and leaves
out `kafka__produce_message`, `kafka__create_topics`, `kafka__create_acls` and
`kafka_sr__register_schema`. Every route is guarded, so a session with no token has no toolkit
to serve it and its `initialize` is never answered.

### Produce and consume through a real broker

`produce_message` and `consume_messages` each carry `topics: [orders]` in their route, which
turns it into an allow-list (exact names or `*` globs): a call naming any other topic has no
matching route and is never served, so nothing reaches the broker.

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__produce_message \
    -e CALL_ARGS='{"topic":"orders","key":"order-1","value":"hello from mcp-kafka"}' \
    tools-list-client
# Produced record to orders topic
```

`limit` bounds how many records one call returns (1-100, default 10):

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__consume_messages \
    -e CALL_ARGS='{"topic":"orders","limit":1}' \
    tools-list-client
# Consumed 1 messages from topic orders
# {"topic":"orders","messages":[{"key":"order-1","value":"hello from mcp-kafka","partition":0,"offset":0,"timestamp":1770000000000,"headers":[]}],"count":1}
```

The second line is the tool result's raw `structuredContent`. Both tools stream: arguments are
parsed as they arrive and records are written to the reply as each comes off the broker, so
neither is bounded by the size of one value or a full result set.

### Read topic configs and cluster metadata

These tools name their target in call arguments rather than a routed topic, so they have no
allow-list and need only `kafka:tools`:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__describe_topic_configs \
    -e CALL_ARGS='{"topic":"orders"}' \
    tools-list-client
# Described 15 config(s)
# {"configs":[{"name":"cleanup.policy","value":"delete","is_default":true,"is_sensitive":false}, ...]}
```

`list_topics`, `describe_topic` and `cluster_overview` take the same scope:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" -e CALL_TOOL=kafka__cluster_overview tools-list-client
# Cluster overview: 1 topic(s), 1 broker(s)
# {"broker_count":1,"controller_id":1,"under_replicated_partitions":0,"offline_partitions":0,"topic_count":1}
```

Mutating topics and configs (`create_topics`, `delete_topics`, `alter_topic_configs`,
`alter_broker_configs`) shares one route requiring `kafka:admin`:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__create_topics \
    -e CALL_ARGS='{"topics":[{"topic":"widgets","partitions":1,"replicas":1}]}' \
    tools-list-client
# Created topic(s): widgets
```

### Manage ACLs

`list_acls` needs only `kafka:tools`; `create_acls` and `delete_acls` need `kafka:acls`. Every
filter field of `list_acls` is optional, and an absent field matches any value. Grant
`User:bob` read access to a dedicated topic:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__create_acls \
    -e CALL_ARGS='{"acls":[{"resource_type":"topic","resource_name":"acl-test-topic","principal":"User:bob","operation":"read","permission_type":"allow"}]}' \
    tools-list-client
# Created 1 ACL(s)

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__list_acls \
    -e CALL_ARGS='{"principal":"User:bob"}' \
    tools-list-client

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__delete_acls \
    -e CALL_ARGS='{"acls":[{"resource_type":"topic","resource_name":"acl-test-topic","principal":"User:bob"}]}' \
    tools-list-client
# Deleted 1 ACL(s)
```

Use a dedicated topic rather than `orders`: once any ACL exists for a resource, the
authorizer stops implicitly allowing every other principal on it, which would block later
`produce_message` and `consume_messages` calls.

### List brokers and describe the cluster

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" -e CALL_TOOL=kafka__list_brokers tools-list-client
# Brokers: 1@kafka.examples.dev:29092

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" -e CALL_TOOL=kafka__describe_cluster tools-list-client
# Described cluster <cluster-id>, controller 1
```

The single node is both the only broker and the controller; `<cluster-id>` is generated by
KRaft at broker startup.

### Inspect consumer groups

A group that has never committed an offset is reported `Dead`, which is what Kafka returns for
a group that does not exist yet, not an error:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__describe_consumer_group \
    -e CALL_ARGS='{"group_id":"orders-analytics"}' \
    tools-list-client
# Consumer group orders-analytics is Dead
```

`list_consumer_groups` and `describe_consumer_group_lag` take the same scope. `reset_offsets`
shares the `kafka:admin` route and rejects a group that still has active members.

### Register and read schemas through a real Karapace

`mcp-schema-registry` needs no OpenAPI document: the tool set is bundled with the binding.
`register_schema` additionally requires `kafka_sr:write`, via its own route ahead of the
catch-all `tool: "*"` route. The tool's summary interpolates the id Karapace assigned.

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__register_schema \
    -e CALL_ARGS='{"subject":"orders-value","schemaType":"AVRO","schema":"{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}"}' \
    tools-list-client
# Registered schema with id 1
```

List the subjects and read the schema back by version, where the summary interpolates two
result fields at once:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" -e CALL_TOOL=kafka_sr__list_subjects tools-list-client
# ["orders-value"]

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__get_schema \
    -e CALL_ARGS='{"subject":"orders-value","version":"latest"}' \
    tools-list-client
# Retrieved schema id 1, version 1
```

Set a compatibility level, then check a schema against it. A freshly registered subject has
none of its own until `set_compatibility` establishes one:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__set_compatibility \
    -e CALL_ARGS='{"subject":"orders-value","compatibility":"FULL"}' \
    tools-list-client
# Compatibility level set to FULL

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__check_compatibility \
    -e CALL_ARGS='{"subject":"orders-value","version":"1","schemaType":"AVRO","schema":"{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}"}' \
    tools-list-client
# Compatibility check result: true
```

## Teardown

```bash
docker compose down -v
```

## References

- [Zilla docs -- `mcp` bindings](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [Zilla docs -- `mcp-kafka` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp-kafka/README.html)
- [Zilla docs -- `jwt` guard](https://docs.aklivity.io/zilla/latest/reference/config/guards/jwt.html)
- [MCP -- Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
