# mcp.proxy.search

Keeps `tools/list` short with `options.cache.tools.eager` and makes everything else
discoverable on demand through `options.cache.tools.search`. Only a handful of
frequently used tools are listed eagerly; every other ("cold") tool is left out of the
listing, found with `zilla__search_tools`, described with `zilla__describe_tool`,
invoked with `zilla__execute_tool`, and still callable directly by name.

The cache is hydrated from the backend in the background and re-hydrated every
`cache.ttl`, so `tools/list` is answered without touching the backend.

```text
client ──► tcp(7114) → http → mcp(server) → mcp(proxy, cache + eager + search) → mcp(client) → http → tcp ──► everything:3001
                                                    │
                                                    └── store: memory
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP and records `mcp.*` metrics |
| `mcp` | `proxy` | Caches the backend listing, applies the eager policy, synthesizes the search tools |
| `mcp` | `client` | Talks to the `everything` reference MCP server; records `mcp.tools.list` so cache refreshes are observable |

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla and the `everything` reference MCP server
(`@modelcontextprotocol/server-everything`) on `:3001`. The first start downloads the
package with `npx`, which can take a few minutes; later starts reuse `.cache/npm-everything`.

## Verify

Run the automated smoke test that the build workflow uses:

```bash
./.github/test.sh
```

Or drive the gateway interactively with the MCP Inspector:

```bash
npx @modelcontextprotocol/inspector http://localhost:7114/mcp
```

### List the eager tools

`cache.tools.eager` with `policy: explicit` lists only the tools named in `match`:

```yaml
cache:
  store: cache
  ttl: PT30S
  tools:
    eager:
      policy: explicit
      match:
        - everything__echo
        - everything__get-env
    search:
      toolkit: zilla
```

Because `search.toolkit` is set, the cold tools are replaced in `tools/list` by three
synthesized tools:

```bash
docker compose run --rm tools-list-client
```

```text
everything__echo
everything__get-env
zilla__search_tools
zilla__describe_tool
zilla__execute_tool
```

The `everything` server registers a dozen more tools, such as `everything__get-sum` and
`everything__get-tiny-image`. They are cold.

### Search, describe and execute a cold tool

`zilla__search_tools` matches by keyword. Matches carry only a name and description, so
scanning many candidates stays cheap:

```bash
docker compose run --rm \
    -e CALL_TOOL=zilla__search_tools -e CALL_ARGS='{"query":"sum"}' \
    tools-list-client
```

```text
everything__get-sum
```

`zilla__describe_tool` resolves the full definition, input schema included:

```bash
docker compose run --rm \
    -e CALL_TOOL=zilla__describe_tool -e CALL_ARGS='{"name":"everything__get-sum"}' \
    tools-list-client
```

`zilla__execute_tool` invokes it by name and returns the tool's own result:

```bash
docker compose run --rm \
    -e CALL_TOOL=zilla__execute_tool \
    -e CALL_ARGS='{"name":"everything__get-sum","arguments":{"a":2,"b":3}}' \
    tools-list-client
```

```text
The sum of 2 and 3 is 5.
```

### Call a cold tool directly

Cold only changes what `tools/list` reports, not what `tools/call` accepts:

```bash
docker compose run --rm \
    -e CALL_TOOL=everything__get-sum -e CALL_ARGS='{"a":2,"b":3}' \
    tools-list-client
```

```text
The sum of 2 and 3 is 5.
```

### Observe the cache

`tools/list` is answered from the cache. The `north_mcp_server` binding counts every
client request and the `south_mcp_client_everything` binding counts the requests that
reach the backend. Start from a fresh stack, list the tools three times, then scrape:

```bash
for i in 1 2 3; do docker compose run --rm tools-list-client > /dev/null; done
curl -s http://localhost:7190/metrics | grep '^mcp_tools_list_total'
```

```text
mcp_tools_list_total{namespace="example",binding="north_mcp_server"} 0
mcp_tools_list_total{namespace="example",binding="south_mcp_client_everything"} 1
mcp_tools_list_total{namespace="example",binding="north_mcp_server",method="tools.list",outcome="ok"} 3
```

The three client listings are counted on the north binding, while the backend saw a
single `tools/list`: the one that hydrated the cache at startup. The first series is the
binding's unattributed total, which stays at `0` because every request carries the
`method`, `tool` and `outcome` attributes.

### Observe a cache refresh

`cache.ttl` is an ISO-8601 duration, `PT5M` by default. It is a refresh interval, not an
expiry: every `ttl` the cache hydrates again from the backend and keeps serving the
previous listing until the new one lands. This example sets `PT30S`. Wait a little over
30 seconds, without listing any tools, and scrape again:

```bash
sleep 35; curl -s http://localhost:7190/metrics | grep 'binding="south_mcp_client_everything"'
```

```text
mcp_tools_list_total{namespace="example",binding="south_mcp_client_everything"} 2
```

The backend counter has advanced by one with no client involved, and advances again every
30 seconds. The refresh leaves no line in `docker compose logs zilla`: Zilla only logs a
failed hydration, so the south `mcp.tools.list` counter is the signal to watch.

The listing does not change as long as the backend does not, so a refresh is invisible to
clients; `tools/list` returns the same five tools before and after.

### Observe MCP metrics

The `north_mcp_server` binding records each call as a counter attributed by `method`,
`tool` and `outcome`:

```bash
curl -s http://localhost:7190/metrics | grep '^mcp_tools_call_total'
```

```text
mcp_tools_call_total{namespace="example",binding="north_mcp_server",method="tools.call",outcome="ok",tool="zilla__search_tools"} 1
mcp_tools_call_total{namespace="example",binding="north_mcp_server",method="tools.call",outcome="ok",tool="everything__get-sum"} 1
```

## Teardown

```bash
docker compose down -v
```

## References

- [Zilla docs -- `mcp` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [MCP -- Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
