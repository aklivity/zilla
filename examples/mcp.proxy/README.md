# mcp.proxy

Aggregates multiple upstream MCP (Model Context Protocol) tool sources behind a
single Streamable HTTP endpoint on port `7114`, fronted by JWT authentication
and per-toolkit / per-tool authorization, with a shared in-memory cache for
`tools` / `prompts` / `resources` listings. The cache also keeps `tools/list`
short as toolkits accumulate: a fixed set of frequently used tools stays
eagerly listed, while every other tool is discoverable on demand through a
synthesized `zilla__search_tools` keyword-search tool instead of crowding out
every `tools/list` response.

```text
                                     ┌────────────────────────────────── Zilla ───────────────────────────────────┐
                                     │  tcp(7114) → http → mcp(server, jwt) → mcp(proxy, guarded routes)           │
client ── Authorization: Bearer ───►│                                     │                                       │
                                     │            ┌────────────┬──────────┴───────────┬───────────────┐          │
                                     │            ▼            ▼                      ▼               ▼          │
                                     │    mcp(client)     mcp(client)          mcp_http(proxy)   mcp_openapi(client)
                                     │    everything       urlelicit           github toolkit     petstore toolkit
                                     │       │                │                      │                   │        │
                                     │       ▼                ▼                      ▼                   ▼        │
                                     │     http →           http →              http(client) →      http(client) →
                                     │     tcp               tcp                 tcp                   tcp        │
                                     └───────┼────────────────┼──────────────────────┼───────────────────┼───────┘
                                             ▼                ▼                      ▼                   ▼
                                      everything:3001   urlelicit:3003          ghapi:4001         petstore:4002
                                      (reference server) (url-mode elicitation) (mock GitHub API)  (mock REST API,
                                                                                                     OpenAPI-described)
```

This one configuration exercises all five `mcp*` binding kinds:

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP, authenticates the session with the `authn_jwt` guard |
| `mcp` | `proxy` | Aggregates toolkits behind one endpoint, gates each toolkit's routes with `guarded:` |
| `mcp` | `client` | Talks to an upstream server that is itself MCP (`everything`, `urlelicit`); `urlelicit` also forwards the caller's own JWT upstream |
| `mcp_http` | `proxy` | Synthesizes MCP tools from hand-authored config, backed by a plain REST API (`github` toolkit) |
| `mcp_openapi` | `client` | Synthesizes MCP tools from an OpenAPI document, backed by a plain REST API (`petstore` toolkit) |

## Authorization model

Every session must present a JWT bearer token validated by the `authn_jwt`
guard (`options.authorization` on the `mcp(server)` binding). The token's
`scope` claim is a space-separated list of roles, matched against the roles
each `guarded:` route requires. Layering happens at three different points in
the pipeline, each demonstrating a different mechanism:

| Layer | Mechanism | Requires |
| --- | --- | --- |
| `mcp(proxy)` route for `urlelicit` toolkit | `routes[].guarded` on the toolkit route | `urlelicit:authorize` |
| `mcp(proxy)` route for `github` toolkit | `routes[].guarded` on the toolkit route | `github:tools` |
| `mcp_http(proxy)` route for `create_pr` | a second, tool-specific `routes[].guarded`, layered under the `mcp_http` binding's base guarded route | `github:tools` **and** `github:pr:write` |
| `mcp(proxy)` route for `petstore` toolkit | `routes[].guarded` on the toolkit route | `petstore:tools` |
| `mcp_openapi(client)` operation `create_pet` | the OpenAPI document's own `security` requirement, mapped to `authn_jwt` via `options.specs.petstore.security` | `petstore:tools` **and** `pets:write` |

`list_pets`, `list_featured_pets`, and `get_pet` declare no OpenAPI `security`
of their own, so they need only the toolkit-level `petstore:tools` scope --
the same "toolkit access is not tool access" layering `mcp_http` demonstrates
with `github:pr:write`, expressed through OpenAPI's own security model
instead of an explicit `guarded:` route.

The `everything` toolkit has no `guarded:` route at all, so it is reachable by
any session that can complete `initialize` -- including one with no token.

The key observable behavior: a session that is not authorized for a toolkit or
tool never sees it in `tools/list`. There is no "tool present but access
denied" state -- unauthorized tools are absent, exactly as if they did not
exist.

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla plus four locally-reachable upstream services: a Node
`everything` reference MCP server on `:3001`, a minimal `urlelicit` MCP server
on `:3003` demonstrating url-mode elicitation, and two plain REST mocks --
`ghapi` on `:4001` (subset of the GitHub API) and `petstore` on `:4002`
(a small Petstore API, described by an inline OpenAPI document).

## Verify

Run the automated smoke test that the build workflow uses:

```bash
./.github/test.sh
```

Or drive the gateway interactively with the MCP Inspector, supplying a bearer
token as described below:

```bash
npx @modelcontextprotocol/inspector http://localhost:7114/mcp
```

### Mint a JWT with jwt-cli

Tokens are signed with the RSA key in [private.pem](private.pem) (generated
with `openssl genrsa -out private.pem 2048`; see [http.proxy.jwt](../http.proxy.jwt/README.md)
for the equivalent walkthrough of extracting the public modulus into
`guards.authn_jwt.options.keys`). Mint one with the bundled `jwt-cli` service:

```bash
export JWT_TOKEN=$(docker compose run --rm jwt-cli encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --payload "scope=urlelicit:authorize github:tools github:pr:write petstore:tools pets:write" \
    --secret @/private.pem | tr -d '\r\n')
```

Omit scopes from `--payload` to see them disappear from `tools/list`.

### Observe filtered tools/list results

The bundled `tools-list-client` connects, lists tools, and prints one tool
name per line -- pass a token (or none) as `JWT_TOKEN`:

```bash
# No token: only the ungated "everything" toolkit is visible
docker compose run --rm tools-list-client

# Toolkit-level scopes only, no operation-level scopes: petstore__list_pets
# appears (its OpenAPI operation has no security requirement) but
# petstore__create_pet and github__create_pr do not -- toolkit access alone
# is not tool access
export JWT_TOKEN=$(docker compose run --rm jwt-cli encode \
    --alg "RS256" --kid "example" \
    --iss "https://auth.example.com" --aud "https://api.example.com" \
    --exp=+1d --no-iat \
    --payload "scope=github:tools petstore:tools" \
    --secret @/private.pem | tr -d '\r\n')
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" tools-list-client

# Every scope: every toolkit's tools are listed
export JWT_TOKEN=$(docker compose run --rm jwt-cli encode \
    --alg "RS256" --kid "example" \
    --iss "https://auth.example.com" --aud "https://api.example.com" \
    --exp=+1d --no-iat \
    --payload "scope=urlelicit:authorize github:tools github:pr:write petstore:tools pets:write" \
    --secret @/private.pem | tr -d '\r\n')
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" tools-list-client
```

### Search tools by keyword instead of listing them all

`options.cache.tools.eager` on `north_mcp_proxy` keeps only a fixed set of
frequently used tools -- `everything__echo`, `urlelicit__authorize`,
`github__create_pr`, `petstore__list_pets`, `petstore__search_pets`, and
`petstore__create_pet` -- eagerly listed in `tools/list`. Every other tool is
"cold": because `options.cache.tools.search` also configures a
`zilla__search_tools` tool, cold tools are omitted from `tools/list`
entirely rather than crowding it out. This is most visible on the
`everything` reference server, which registers over a dozen demo tools --
`everything__get-sum`, `everything__get-env`, `everything__get-tiny-image`,
and more -- of which only `echo` is eager.

List tools with the full-scope `$JWT_TOKEN` from above and note how few
tools come back compared to what every toolkit actually exposes:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" tools-list-client
```

```text
everything__echo
urlelicit__authorize
github__create_pr
petstore__list_pets
petstore__search_pets
petstore__create_pet
zilla__search_tools
resource:petstore+/pets/featured
template:petstore+/pets/{petId}
template:github+pr://{owner}/{repo}/{number}
```

`everything__get-sum` -- one of the cold tools just omitted -- is still
discoverable by keyword. `zilla__search_tools` only ever searches within the
caller's own authorized scope, the same as `tools/list` itself.

Its result content uses `{"type":"tool_reference","tool_name":"..."}`, a
Zilla-specific content block with no entry in the MCP `CallToolResult`
content schema (`text`, `image`, `audio`, `resource_link`, `resource`) --
`tools-list-client`'s strict SDK client rejects it as invalid, so call it
over raw HTTP instead, reusing the session `initialize` establishes:

```bash
SESSION_ID=$(curl -sS -D - -o /dev/null http://localhost:7114/mcp \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}' \
    | grep -i '^mcp-session-id:' | tr -d '\r' | cut -d' ' -f2)

curl -sS -N http://localhost:7114/mcp \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -H "Mcp-Session-Id: $SESSION_ID" \
    -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

curl -sS -N http://localhost:7114/mcp \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -H "Mcp-Session-Id: $SESSION_ID" \
    -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"zilla__search_tools","arguments":{"query":"sum"}}}'
```

```text
{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"tool_reference","tool_name":"everything__get-sum"}],"isError":false}}
```

Cold does not mean inaccessible -- nothing about `options.cache.tools.eager`
touches `tools/call` routing, only what `tools/list` reports. Calling
`everything__get-sum` directly by name succeeds identically to an eager tool:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=everything__get-sum -e CALL_ARGS='{"a":2,"b":3}' \
    tools-list-client
# The sum of 2 and 3 is 5.
```

### Call an authorized tool

With the full-scope `$JWT_TOKEN` from above, calling `github__create_pr`
reaches the `ghapi` mock and forwards the caller's own bearer credential and
identity upstream (`options.authorization.credentials.headers` on the
`mcp_http` binding):

```bash
curl -N http://localhost:7114/mcp \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}'

curl -N http://localhost:7114/mcp \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"github__create_pr","arguments":{"owner":"acme","repo":"widget","title":"Add feature","head":"feature","base":"main"}}}'
```

The response's `html_url` points at the fabricated `ghapi` pull request, and
`opened_by` echoes the identity extracted from the JWT.

### Schema-validated tool calls, and where the arguments go

`mcp_http` requires every tool to declare an input schema
(`options.tools.create_pr.schemas.input`, backed by the `github_catalog`
inline catalog) -- a call is validated against it before Zilla builds the
upstream request at all. `mcp_openapi` makes the same `input`/`output`
override optional: `list_pets` relies on the schema auto-derived from the
OpenAPI document, while `create_pet` explicitly overrides both
(`options.tools.create_pet.input`/`output`, backed by `petstore_catalog`) to
show the same mechanism used deliberately rather than inferred.

Once validated, an argument only reaches the upstream request where a route
says to put it. `create_pr`'s route consumes `owner`/`repo` in the `:path`
template (`/repos/${args.owner}/${args.repo}/pulls`) and forwards the rest
(`title`, `head`, `base`, optionally `body`) as the JSON request body via
`with.body`, whose schema (`create_pr_body`) is the input schema minus
`owner`/`repo` -- omitting `with.body` entirely does not mean "send
everything"; it means the validated arguments are discarded with nowhere to
go, so a route that consumes some arguments as path segments and wants the
remainder forwarded still needs an explicit `with.body` scoped to what's left.

### Browse petstore resources (static and templated)

`mcp_openapi` maps OpenAPI `GET` operations to MCP resources instead of
tools when the route's `when` says `resource:` instead of `tool:`. Whether
the result is a fixed entry in `resources/list` or a `resources/templates`
entry depends entirely on the OpenAPI path itself:

- `list_featured_pets` (`GET /pets/featured`, no path parameters) becomes a
  **static** resource at the fixed URI `petstore+/pets/featured`.
- `get_pet` (`GET /pets/{petId}`, one path parameter) becomes a **dynamic**
  resource template `petstore+/pets/{petId}`, read with a concrete `petId`
  substituted in.

Both need only the toolkit-level `petstore:tools` scope -- see them appear
with any `petstore:tools`-scoped token from above (`$JWT_TOKEN` currently
holds the full-scope one):

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" tools-list-client
# ...
# resource:petstore+/pets/featured
# template:petstore+/pets/{petId}
```

Read the templated resource for a specific pet with MCP Inspector's Resources
tab, or with any MCP client that supports `resources/read` against
`petstore+/pets/1`.

### Redirect the outbound host, and rename an argument (mcp_openapi)

The petstore OpenAPI document declares its public server as
`https://api.petstore.example.com` -- a realistic, external-looking address,
not the local mock. `options.specs.petstore.server: http://petstore:4002`
overrides where Zilla actually sends the request, independent of what the
document says; nothing else about routing changes.

`search_pets` renames its one argument from the OpenAPI parameter's own name
(`tag`, a query parameter) to `category`, via a custom input schema
(`options.tools.search_pets.input`) plus `routes[].with.params: {tag:
"${args.category}"}` reconciling the two. Call it and watch the mock observe
the original parameter name:

```bash
docker compose logs -f petstore &
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=petstore__search_pets -e CALL_ARGS='{"category":"cat"}' \
    tools-list-client
```

`petstore`'s log line reads `search_pets query: {"tag":"cat"}` -- the caller
said `category`, the request said `tag`.

### Trigger a form elicitation round-trip

Call the `everything` server's elicitation-demo tool through the gateway (no
token required -- `everything` has no `guarded:` route):

```bash
curl -N http://localhost:7114/mcp \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{"elicitation":{"url":{}}},"clientInfo":{"name":"curl","version":"0"}}}'

curl -N http://localhost:7114/mcp \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"everything__trigger-elicitation-request-async"}}'
```

`@modelcontextprotocol/server-everything` responds with an `elicitation/create`
JSON-RPC request bound for the client. Zilla forwards it back through
`mcp(client) → mcp(proxy) → mcp(server) → http(server)` without unwrapping.

### Trigger a url-mode elicitation round-trip

Mint a token with the `urlelicit:authorize` scope (see above), then use MCP
Inspector (which knows how to handle `elicitation/create`) to call the
`urlelicit` toolkit's `authorize` tool, supplying `Authorization: Bearer
$JWT_TOKEN` as a custom header:

```text
urlelicit__authorize { "resource": "demo" }
```

The `urlelicit` server replies with a `mode:"url"` `elicitation/create` request
carrying a link for the user to open in their browser. Zilla relays it back to
the client unchanged; once the out-of-band interaction completes, the server
sends `notifications/elicitation/complete`, which Zilla also relays. URL-mode
elicitation only flows when the client advertised `elicitation.url` at
`initialize` -- a form-only or older client never sees the url request.

### Forward the caller's own credential to an upstream MCP server

`south_mcp_client_urlelicit` sets its own `options.authorization`, using the
same `authn_jwt` guard as the `mcp(server)` binding. Because its `credentials`
pattern is the default `Bearer {credentials}`, the guard resolves the
*original* bearer token that was validated when this session was
authenticated -- not a separate service credential -- and Zilla attaches it
as the `Authorization` header on every request to `urlelicit`. Confirm it
arrives unchanged:

```bash
docker compose logs urlelicit | grep authorization:
```

Each line shows the exact JWT a given caller presented at the gateway. This
is the `mcp(client)` binding's own credential-forwarding mechanism -- a
narrower, single-header equivalent of `mcp_http`'s
`options.authorization.credentials.headers` map used for `github__create_pr`
above, without needing to name the header or interpolate `{identity}`
separately.

> A more elaborate scenario -- an `mcp(client)` binding that itself drives an
> elicitation round-trip to obtain a credential for an OAuth-protected
> upstream, rather than simply forwarding one it already has -- likely needs
> an `oauth` guard that doesn't exist in the open-source runtime yet, so it's
> a better fit for a future zilla-plus version of this example.

### Observe the cache

Repeat a `tools/list` request within five minutes and tail Zilla's logs:

```bash
docker compose logs -f zilla | grep mcp.proxy.cache
```

The first call shows a cache miss; subsequent ones within `ttl` are served
from memory. The cache is keyed per authorization, so different callers with
different scopes never see each other's filtered results.

### Observe MCP metrics

The `mcp(server)` binding is configured with `telemetry.metrics: [mcp.*]` and
records each request as a counter plus a duration histogram, attributed by
`method`, `tool`, and `outcome`. Scrape them from the Prometheus endpoint:

```bash
curl -s http://localhost:7190/metrics | grep '^mcp_'
```

After a `github__create_pr` tool call, for example, you will see:

```text
mcp_tools_call_total{method="tools.call",outcome="ok",tool="github__create_pr"} 1
```

## Teardown

```bash
docker compose down -v
```

## References

- [Zilla docs -- `mcp` bindings](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [Zilla docs -- `mcp-http` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp-http/README.html)
- [Zilla docs -- `jwt` guard](https://docs.aklivity.io/zilla/latest/reference/config/guards/jwt.html)
- [MCP -- Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
- [MCP -- elicitation](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)
- [SEP-1036 -- URL mode elicitation](https://modelcontextprotocol.io/seps/1036-url-mode-elicitation-for-secure-out-of-band-intera)
