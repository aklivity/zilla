# mcp.kafka.connect.proxy

Exposes a [Kafka Connect](https://kafka.apache.org/documentation/#connect) worker's REST API as MCP
(Model Context Protocol) tools behind a single Streamable HTTP endpoint on port `7114`, fronted by JWT
authentication and per-tool authorization.

```text
                 ┌───────────────────────────────── Zilla ──────────────────────────────────┐
client ─ Bearer ►│ tcp(7114) → http → mcp(server, jwt) → mcp(proxy) → mcp-kafka-connect     │
                 └──────────────────────────────────────────────────────────────────┬───────┘
                                                                                    ▼
                                                                  kafka-connect:8083 ── kafka
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP, authenticates the session with the `authn_jwt` guard |
| `mcp` | `proxy` | Routes the `kafka_connect` toolkit, gated by the `kafka_connect:tools` scope |
| `mcp-kafka-connect` | `client` | Bundles a fixed tool set for the Connect REST API; only `options.server` is required |

`mcp-kafka-connect` needs no operator-authored tool definitions. It exposes these tools, each prefixed
with the toolkit name, e.g. `kafka_connect__list_connectors`:

- `list_connectors`, `create_connector`, `describe_connector`, `delete_connector`
- `describe_connector_config`, `update_connector_config`, `validate_connector_config`
- `describe_connector_status`, `restart_connector`, `pause_connector`, `resume_connector`, `stop_connector`
- `list_connector_tasks`, `restart_connector_task`
- `describe_connector_offsets`, `alter_connector_offsets`, `reset_connector_offsets`
- `list_connector_plugins`

## Authorization model

The token's `scope` claim is a space-separated list of roles, matched against the roles each `guarded:`
route requires. Two layers apply:

| Layer | Mechanism | Requires |
| --- | --- | --- |
| `mcp(proxy)` route for the `kafka_connect` toolkit | `routes[].guarded` on the toolkit route | `kafka_connect:tools` |
| `mcp-kafka-connect(client)` route for mutating tools | a second `routes[].guarded` on the binding's own `when.tool` route, layered under the toolkit gate | `kafka_connect:tools` **and** `kafka_connect:admin` |

Every tool that creates, deletes or otherwise changes connector, task or offset state shares the admin
route. Read-only tools share the catch-all `tool: "*"` route and need only the toolkit scope. Routes match
in declaration order, so the admin route comes first. A caller without `kafka_connect:admin` does not see the admin tools in `tools/list`.

## Requirements

- Docker Compose

## Setup

```bash
docker compose up -d
```

This starts Zilla, a single-node KRaft Kafka broker (`kafka.examples.dev`), and a Kafka Connect distributed
worker (`kafka-connect:8083`) storing its config, offset and status state in internal topics on that broker.

The worker's `plugin.path` points at a directory holding only `connect-file-*.jar`, copied out of the
Kafka distribution. That one jar bundles the `FileStreamSource` and `FileStreamSink` connectors this example
uses. Pointing `plugin.path` at the whole `/opt/kafka/libs` makes the worker scan about a hundred jars before
its REST port opens, which took roughly 140s here against about 10s for the single jar.

## Verify

Run the automated check that the build workflow uses:

```bash
./.github/test.sh
```

It starts a one-off `verify` container inside the stack that mints the JWTs and runs `etc/test/verify.sh`.

To drive the gateway by hand, mint a token with the `mcp-client` service, which signs with
[private.pem](private.pem) (the public key is in `guards.authn_jwt.options.keys`; see
[http.proxy.jwt](../http.proxy.jwt/README.md) for how the modulus is extracted):

```bash
export JWT_TOKEN=$(docker compose run --rm -T mcp-client mint.mjs "kafka_connect:tools kafka_connect:admin")
```

Or point the MCP Inspector at `http://localhost:7114/mcp` with that token as a bearer header:

```bash
npx @modelcontextprotocol/inspector http://localhost:7114/mcp
```

### Scope-filtered tool list

A caller with only `kafka_connect:tools` sees the read tools:

```bash
export JWT_TOOLS=$(docker compose run --rm -T mcp-client mint.mjs "kafka_connect:tools")
docker compose run --rm -e JWT_TOKEN="$JWT_TOOLS" mcp-client client.mjs
```

```text
kafka_connect__list_connectors
kafka_connect__describe_connector
kafka_connect__describe_connector_config
kafka_connect__validate_connector_config
kafka_connect__describe_connector_status
kafka_connect__list_connector_tasks
kafka_connect__describe_connector_offsets
kafka_connect__list_connector_plugins
```

With `kafka_connect:admin` as well, `create_connector`, `delete_connector`, `pause_connector` and the other
mutating tools appear:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" mcp-client client.mjs
```

### List connector plugins

The plugins on the worker's `plugin.path`:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__list_connector_plugins mcp-client client.mjs
```

```text
Plugins: [{"class":"org.apache.kafka.connect.file.FileStreamSinkConnector","type":"sink","version":"4.1.1"},{"class":"org.apache.kafka.connect.file.FileStreamSourceConnector","type":"source","version":"4.1.1"}, ...]
```

### Create a connector

`kafka-connect` seeds `/tmp/kc-source.txt` before starting its worker, so a `FileStreamSourceConnector` can
read it straight away. `create_connector` requires `kafka_connect:admin`:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__create_connector \
    -e CALL_ARGS='{"name":"file-source-demo","config":{"connector.class":"org.apache.kafka.connect.file.FileStreamSourceConnector","tasks.max":"1","file":"/tmp/kc-source.txt","topic":"connect-demo"}}' \
    mcp-client client.mjs
```

```text
Created connector file-source-demo
```

### Inspect the connector

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOOLS" \
    -e CALL_TOOL=kafka_connect__list_connectors mcp-client client.mjs
docker compose run --rm -e JWT_TOKEN="$JWT_TOOLS" \
    -e CALL_TOOL=kafka_connect__describe_connector_status \
    -e CALL_ARGS='{"connector":"file-source-demo"}' mcp-client client.mjs
```

```text
Connectors: ["file-source-demo"]
Connector file-source-demo is RUNNING
```

The connector starts asynchronously, so the status may briefly read `UNASSIGNED` before `RUNNING`; the pause and
resume states settle the same way.

### Pause, resume, restart and delete

Each lifecycle tool requires `kafka_connect:admin`. `pause_connector` and `resume_connector` move the
connector and its task between `PAUSED` and `RUNNING`, confirmed with `describe_connector_status`:

```bash
for tool in pause resume restart; do
  docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
      -e CALL_TOOL=kafka_connect__${tool}_connector \
      -e CALL_ARGS='{"connector":"file-source-demo"}' mcp-client client.mjs
  docker compose run --rm -e JWT_TOKEN="$JWT_TOOLS" \
      -e CALL_TOOL=kafka_connect__describe_connector_status \
      -e CALL_ARGS='{"connector":"file-source-demo"}' mcp-client client.mjs
done
```

```text
Paused connector
Connector file-source-demo is PAUSED
Resumed connector
Connector file-source-demo is RUNNING
Restarted connector
Connector file-source-demo is RUNNING
```

`delete_connector` removes it, confirmed by `list_connectors` reporting none remaining:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__delete_connector \
    -e CALL_ARGS='{"connector":"file-source-demo"}' mcp-client client.mjs
docker compose run --rm -e JWT_TOKEN="$JWT_TOOLS" \
    -e CALL_TOOL=kafka_connect__list_connectors mcp-client client.mjs
```

```text
Deleted connector
Connectors: []
```

## Teardown

```bash
docker compose down -v --remove-orphans
```

## References

- [Zilla docs -- `mcp` bindings](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [Zilla docs -- `mcp-kafka-connect` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp-kafka-connect/README.html)
- [Zilla docs -- `jwt` guard](https://docs.aklivity.io/zilla/latest/reference/config/guards/jwt.html)
- [Kafka Connect REST API](https://kafka.apache.org/documentation/#connect_rest)
- [MCP -- Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
