# mcp.proxy

Aggregates multiple upstream MCP (Model Context Protocol) servers behind a single
Streamable HTTP endpoint on port `7114`, with a shared in-memory cache for
`tools` / `prompts` / `resources` listings.

```text
              ┌──────────────────── Zilla ─────────────────────┐
              │   tcp(7114) → http → mcp(server) → mcp(proxy)  │
client ──────►│                            │                   │
              │                 ┌──────────┴──────────┐        │
              │                 ▼                     ▼        │
              │        mcp(client) everything   mcp(client) urlelicit
              │                 │                     │        │
              │                 ▼                     ▼        │
              │              http →                http →      │
              │              tcp                   tcp         │
              └─────────────────┼─────────────────────┼───────┘
                                ▼                     ▼
                         everything:3001        urlelicit:3003
                         (reference server)     (url-mode elicitation)
```

The proxy demonstrates, in one configuration:

- **Multi-toolkit aggregation** — `routes[].when.toolkit` fans one Streamable HTTP
  endpoint into multiple upstream MCP servers.
- **Shared cache** — `options.cache` backs `tools` / `prompts` / `resources`
  listings with an in-memory store and a five-minute TTL.
- **Protocol version negotiation** — Zilla offers MCP protocol `2025-11-25` and
  negotiates down per peer; the negotiated version is echoed on the `initialize`
  response and stamped on every upstream request.
- **Form elicitation pass-through** — Zilla forwards MCP `elicitation/create`
  messages in both directions, so any upstream that elicits structured user input
  drives the flow through the gateway with no extra configuration. The
  `everything` reference server's `trigger-elicitation-request-async` tool
  exercises this directly.
- **URL-mode elicitation pass-through (SEP-1036)** — when the client advertises
  the `elicitation.url` capability, Zilla advertises it to the upstream, so a
  url-mode-capable upstream can ask the user to complete a secure out-of-band
  interaction in their browser. Zilla relays the `mode:"url"` `elicitation/create`
  request and the `notifications/elicitation/complete` notification end-to-end.
  The `urlelicit` server's `authorize` tool exercises this directly.
- **MCP metrics** — the `mcp(server)` binding records per-method counters and
  duration histograms (`mcp.initialize`, `mcp.tools.list`, `mcp.tools.call`,
  `mcp.prompts.*`, `mcp.resources.*`), dimensioned by `method`, `tool`, and
  `outcome`, exported over Prometheus on port `7190`.

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla plus two locally-reachable upstream MCP servers: a Node
`everything` reference server on `:3001`, and a minimal `urlelicit` server on
`:3003` that demonstrates url-mode elicitation.

## Verify

Run the automated smoke test that the build workflow uses:

```bash
./.github/test.sh
```

Or drive the gateway interactively with the MCP Inspector:

```bash
npx @modelcontextprotocol/inspector http://localhost:7114/mcp
```

### Initialize the MCP session

A client that supports url-mode elicitation advertises the `elicitation.url`
capability and offers protocol `2025-11-25`; Zilla echoes the negotiated version:

```bash
curl -N http://localhost:7114/mcp \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{"elicitation":{"url":{}}},"clientInfo":{"name":"curl","version":"0"}}}'
```

The `result.protocolVersion` in the response is `2025-11-25`. Because the client
advertised `elicitation.url`, Zilla advertises the same capability on its
`initialize` request to each upstream.

### Trigger a form elicitation round-trip

Call the `everything` server's elicitation-demo tool through the gateway:

```bash
curl -N http://localhost:7114/mcp \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"everything__trigger-elicitation-request-async"}}'
```

`@modelcontextprotocol/server-everything` responds with an `elicitation/create`
JSON-RPC request bound for the client. Zilla forwards it back through
`mcp(client) → mcp(proxy) → mcp(server) → http(server)` without unwrapping.

### Trigger a url-mode elicitation round-trip

Use MCP Inspector (which knows how to handle `elicitation/create`) to call the
`urlelicit` server's `authorize` tool:

```text
urlelicit__authorize { "resource": "demo" }
```

The `urlelicit` server replies with a `mode:"url"` `elicitation/create` request
carrying a link for the user to open in their browser. Zilla relays it back to
the client unchanged; once the out-of-band interaction completes, the server
sends `notifications/elicitation/complete`, which Zilla also relays. URL-mode
elicitation only flows when the client advertised `elicitation.url` at
`initialize` — a form-only or older client never sees the url request.

### Observe the cache

Repeat a `tools/list` request within five minutes and tail Zilla's logs:

```bash
docker compose logs -f zilla | grep mcp.proxy.cache
```

The first call shows a cache miss; subsequent ones within `ttl` are served from
memory.

### Observe MCP metrics

The `mcp(server)` binding is configured with `telemetry.metrics: [mcp.*]` and
records each request as a counter plus a duration histogram, attributed by
`method`, `tool`, and `outcome`. Scrape them from the Prometheus endpoint:

```bash
curl -s http://localhost:7190/metrics | grep '^mcp_'
```

After an `everything__echo` tool call, for example, you will see:

```text
mcp_tools_call_total{method="tools.call",outcome="ok",tool="everything__echo"} 1
```

## Teardown

```bash
docker compose down -v
```

## References

- [Zilla docs — `binding-mcp`](https://docs.aklivity.io/zilla/latest/reference/config/bindings/binding-mcp.html)
- [MCP — Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
- [MCP — elicitation](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)
- [SEP-1036 — URL mode elicitation](https://modelcontextprotocol.io/seps/1036-url-mode-elicitation-for-secure-out-of-band-intera)
