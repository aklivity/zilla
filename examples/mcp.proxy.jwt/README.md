# mcp.proxy.jwt

Fronts two upstream MCP (Model Context Protocol) servers with a single Streamable HTTP endpoint on port `7114`,
authenticates callers with JWT bearer tokens, and uses the token's scopes to decide which toolkits each caller
sees and may call. It also relays url-mode elicitation between an upstream server and the client, and forwards
the caller's own credential to that upstream server.

```text
                      ┌────────────────────────────── Zilla ──────────────────────────────┐
                      │ tcp(7114) → http → mcp(server, jwt) → mcp(proxy, guarded routes)  │
client ── Bearer ────►│                                          ┬                 ┬     │
                      │                                          ▼                 ▼     │
                      │                                  mcp(client)         mcp(client) │
                      │                                  everything          urlelicit   │
                      │                                  (ungated)           (guarded,   │
                      │                                                      forwards    │
                      │                                                      caller JWT) │
                      └──────────────────────────────────────┼─────────────────┼─────────┘
                                                             ▼                 ▼
                                                     everything:3001     urlelicit:3003
                                                     (reference server)  (url-mode elicitation)
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP and authenticates the session with the `authn_jwt` guard |
| `mcp` | `proxy` | Aggregates the two toolkits behind one endpoint and gates routes with `guarded:` |
| `mcp` | `client` | Talks to an upstream MCP server (`everything`, `urlelicit`); `urlelicit` also forwards the caller's own JWT |

## Authorization model

The token's `scope` claim is a space-separated list of roles, matched against the roles each `guarded:` route
requires.

| Route | Requires |
| --- | --- |
| `everything` toolkit | nothing, a session without a token can use it |
| `urlelicit` toolkit | `urlelicit:authorize` |

A caller that is not authorized for a toolkit never sees its tools in `tools/list`: there is no "present but
denied" state. A valid token whose scopes do not include `urlelicit:authorize` sees exactly what an anonymous
caller sees, because authentication alone grants nothing beyond ungated routes. Calling a hidden tool by name
returns no result.

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla, the `everything` reference MCP server on `:3001`, and a minimal `urlelicit` MCP server
on `:3003`.

## Verify

Run the automated smoke test used by the build workflow:

```bash
./.github/test.sh
```

It starts a one-off `verify` container inside the stack that mints the tokens it needs with
[clients/mint.mjs](clients/mint.mjs) and runs `etc/test/verify.sh`.

To drive the gateway by hand, use the on-demand `client` service. Tokens are signed with the RSA key in
[private.pem](private.pem), whose public half is in `guards.authn_jwt.options.keys` of
[etc/zilla.yaml](etc/zilla.yaml).

### Mint a JWT

```bash
export JWT_TOKEN=$(docker compose run --rm -T client mint "urlelicit:authorize")
```

### Observe filtered tools/list results

The `client` service prints one tool name per line. Without a token only the `everything__*` tools are listed:

```bash
docker compose run --rm -T client list
```

```text
everything__echo
everything__get-annotated-message
...
```

With the `urlelicit:authorize` scope the guarded toolkit appears as well:

```bash
docker compose run --rm -T -e JWT_TOKEN client list
```

```text
everything__echo
...
urlelicit__authorize
```

Mint a token with another scope, for example `"profile"`, and the `urlelicit__authorize` tool is gone again.

### Call an ungated tool

```bash
docker compose run --rm -T -e CALL_TOOL=everything__echo -e 'CALL_ARGS={"message":"hello"}' client list
```

```text
Echo: hello
```

### Trigger a url-mode elicitation round-trip

The `urlelicit` toolkit's `authorize` tool sends an `elicitation/create` request with `mode: "url"`, asking the
client to open a link for an out-of-band step, and later sends `notifications/elicitation/complete`. Zilla relays
both back to the client. The tool is only reachable with the `urlelicit:authorize` scope:

```bash
docker compose run --rm -T -e JWT_TOKEN client elicit
```

```text
[client] elicitation/create mode=url message="Open the link to authorize access to demo." url=http://localhost:3003/authorize?elicitation=...
[client] tool result: authorization accept for demo
[client] notifications/elicitation/complete elicitationId=...
OK url-mode elicitation relayed end-to-end
```

URL-mode elicitation only flows when the client advertised `elicitation.url` at `initialize`.

### Forward the caller's own credential to an upstream MCP server

`south_mcp_client_urlelicit` sets its own `options.authorization` with the same `authn_jwt` guard as the
`mcp(server)` binding. Because its `credentials` pattern is the default `Bearer {credentials}`, the guard resolves
the original token validated for the session, and Zilla attaches it as the `Authorization` header on requests to
`urlelicit`. The mock records each method and `Authorization` header it receives:

```bash
curl -s http://localhost:3003/requests
```

```text
initialize Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ...
tools/list Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ...
tools/call Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6ImV4YW1wbGUifQ...
```

The value is the exact JWT the caller presented at the gateway, not a separate service credential.

## Teardown

```bash
docker compose down -v
```

## References

- [Zilla docs -- `mcp` bindings](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [Zilla docs -- `jwt` guard](https://docs.aklivity.io/zilla/latest/reference/config/guards/jwt.html)
- [MCP -- Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
- [MCP -- elicitation](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)
- [SEP-1036 -- URL mode elicitation](https://modelcontextprotocol.io/seps/1036-url-mode-elicitation-for-secure-out-of-band-intera)
