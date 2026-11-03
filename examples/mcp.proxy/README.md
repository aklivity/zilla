# mcp.proxy

Fronts an upstream MCP (Model Context Protocol) server with Zilla on a single
Streamable HTTP endpoint on port `7114`. Zilla aggregates the upstream server as a
named toolkit, relays `tools/list`, `tools/call`, `resources/list`,
`resources/read` and `resources/subscribe` through it, and exports per-request
metrics to Prometheus and stdout.

The upstream is the [`@modelcontextprotocol/server-everything`](https://www.npmjs.com/package/@modelcontextprotocol/server-everything)
reference server, exposed as the `everything` toolkit: its tools surface as
`everything__<tool>` and its resources as `everything+<uri>`.

```text
                    ┌──────────────────────────── Zilla ───────────────────────────┐
client ── MCP ────► │ tcp(7114) → http → mcp(server) → mcp(proxy) → mcp(client)     │ ──► everything:3001
                    │                                                               │     (reference MCP server)
                    │ telemetry: mcp.* metrics ──► prometheus(7190), stdout         │
                    └───────────────────────────────────────────────────────────────┘
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP and records the `mcp.*` metrics |
| `mcp` | `proxy` | Routes each request to a toolkit by its `everything__` / `everything+` prefix |
| `mcp` | `client` | Talks to the upstream MCP server over HTTP |

This is the plain proxy. Add JWT authorization with
[`mcp.proxy.jwt`](../mcp.proxy.jwt) and cached tool search with
[`mcp.proxy.search`](../mcp.proxy.search).

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla and the `everything` MCP server. The first start downloads the
server with `npx`, which can take a few minutes; it is cached under
`.cache/npm-everything` for later runs.

## Verify

Run the automated check that the build workflow uses:

```bash
./.github/test.sh
```

It starts a one-off `verify` container inside the stack that runs
`etc/test/verify.sh`, where all the assertions live.

Or drive the gateway interactively with the MCP Inspector:

```bash
npx @modelcontextprotocol/inspector http://localhost:7114/mcp
```

### List tools and resources

```bash
docker compose run --rm tools-list-client
```

Every tool carries the `everything__` prefix, every resource the `everything+`
prefix:

```text
everything__echo
everything__get-sum
everything__get-env
...
resource:everything+demo://resource/static/document/architecture.md
...
```

### Call a tool

```bash
docker compose run --rm \
    -e CALL_TOOL=everything__echo \
    -e CALL_ARGS='{"message":"hello from zilla"}' \
    tools-list-client
```

```text
Echo: hello from zilla
```

Or with plain `curl`, which shows the Streamable HTTP exchange. `initialize`
returns an `Mcp-Session-Id` response header that later requests must send back:

```bash
curl -si http://localhost:7114/mcp \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}'
```

```text
HTTP/1.1 200 OK
mcp-session-id: ...
...
data: {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","capabilities":{"tools":{},"prompts":{},"resources":{"subscribe":true},...
```

```bash
curl -N http://localhost:7114/mcp \
    -H "Mcp-Session-Id: <session id from above>" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"everything__get-sum","arguments":{"a":2,"b":3}}}'
```

```text
data: {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"The sum of 2 and 3 is 5."}]}}
```

### Read a resource

```bash
docker compose run --rm \
    -e READ_RESOURCE=everything+demo://resource/static/document/architecture.md \
    tools-list-client
```

The contents are relayed from the upstream server.

### Trigger a resource-subscription round-trip

Subscribing registers interest in a resource URI, and the `everything` server's
`toggle-subscriber-updates` tool starts a per-session, five-second interval of
simulated updates for whatever the session has subscribed to. Subscribing alone
pushes nothing.

```bash
docker compose run --rm resource-subscribe-client
```

The client lists resources, subscribes to an `everything+...` URI, toggles the
updates on, waits for the relayed `notifications/resources/updated`, then
unsubscribes and toggles the updates off:

```text
[client] connected, protocolVersion=2025-11-25
[client] subscribed to everything+demo://resource/static/document/architecture.md
[client] toggle-subscriber-updates: Started simulated resource updated notifications ...
[client] notifications/resources/updated uri=everything+demo://resource/static/document/architecture.md
[client] unsubscribed from everything+demo://resource/static/document/architecture.md
OK resource subscription relayed end-to-end for everything+demo://resource/static/document/architecture.md
```

`resources.subscribe: true` in the `initialize` result is not configured; the
proxy learns it from the upstream server's own `initialize` handshake, and the
URI comes back re-prefixed with `everything+` like any other aggregated resource.

### Observe MCP metrics

The `mcp(server)` binding is configured with `telemetry.metrics: [mcp.*]` and
records each request as a counter plus a duration histogram, attributed by
`method`, `tool` and `outcome`. After the tool calls above, scrape the
Prometheus endpoint:

```bash
curl -s http://localhost:7190/metrics | grep '^mcp_tools_call_total'
```

```text
mcp_tools_call_total{namespace="example",binding="north_mcp_server",method="tools.call",outcome="ok",tool="everything__echo"} 1
mcp_tools_call_total{namespace="example",binding="north_mcp_server",method="tools.call",outcome="ok",tool="everything__get-sum"} 1
```

A second series per call carries the tool name without the toolkit prefix
(`tool="echo"`).

The same metrics are written to stdout by the `stdout` exporter:

```bash
docker compose logs zilla | grep mcp_tools_call
```

## Teardown

```bash
docker compose down -v
```

## References

- [Zilla docs -- `mcp` bindings](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [MCP -- Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
- [MCP -- resources](https://modelcontextprotocol.io/specification/2025-11-25/server/resources)
