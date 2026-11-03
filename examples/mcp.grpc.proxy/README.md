# mcp.grpc.proxy

Turns a gRPC service into MCP (Model Context Protocol) tools, behind a single
Streamable HTTP endpoint on port `7114`. The `mcp-grpc` binding reads the
service's protobuf definition from a catalog and generates one tool for each
unary method, backed here by `inventory`, a small mock gRPC service. Callers
authenticate with a JWT, and the scopes in the token decide who can reach the
toolkit.

```text
                  ┌───────────────────────────────── Zilla ──────────────────────────────────┐
client ─ Bearer ─►│ tcp(7114) → http → mcp(server, jwt) → mcp(proxy) → mcp-grpc(client)      │
                  │                                                    http(client, h2) → tcp │
                  └────────────────────────────────────────────────────────────┬─────────────┘
                                                                               ▼
                                                                       inventory:4004
                                                                    (mock gRPC service)
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP and authenticates the session with the `authn_jwt` guard |
| `mcp` | `proxy` | Routes the `inventory` toolkit, gated by `inventory:tools` |
| `mcp-grpc` | `client` | Generates the `get_item` and `list_items` tools from `etc/protos/inventory.proto` and calls the matching gRPC methods |

## What it demonstrates

- **Tools generated from a `.proto`.** The binding reads `etc/protos/inventory.proto`
  from a catalog. `inputSchema` and `outputSchema` come from the request and reply
  messages, so the tool list cannot drift from the service definition. `options.tools`
  only adds a title, description and annotations.
- **Method selection.** Each route pins one method with `with.method` and names it
  with `when.tool`. Without routes, every unary method would be exposed under its
  default name, the fully qualified method in snake case.
- **JSON to protobuf and back.** `tools/call` arguments become a protobuf request and
  the protobuf reply, a repeated message field included, comes back as JSON.
- **gRPC status as a tool error.** A gRPC `NOT_FOUND` is returned as a tool result
  with `isError` set, carrying the status name and message.
- **Credential forwarding.** `options.authorization.credentials.headers` sends the
  caller's own token and identity to `inventory` as gRPC metadata. The mock rejects a
  call that arrives without the credential.
- **The generated client's exit.** `mcp-grpc` creates its own gRPC client and sends it
  to an exit chosen by the `zilla.binding.mcp.grpc.client.exit` property, set in
  [compose.yaml](compose.yaml) to the `h2` `http` client in front of the `tcp` client.

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla and `inventory`, a Node mock on `:4004` with three items.

## Verify

Run the automated smoke test the build workflow uses:

```bash
./.github/test.sh
```

It starts a one-off `verify` container inside the stack, which runs
`etc/test/verify.sh`. The scenarios below are the same behaviors, by hand.

### Authorization model

Tokens are RS256 JWTs signed with [private.pem](private.pem), which pairs with the
public key in `guards.authn_jwt.options.keys` (see [http.proxy.jwt](../http.proxy.jwt/README.md)
for how the modulus is extracted). The token's `scope` claim is a space-separated
list, matched against the `guarded` roles each route requires. The bundled
`client` service mints a token for `JWT_SCOPE` and connects with it:

| Layer | Mechanism | Requires |
| --- | --- | --- |
| `mcp(proxy)` route for the `inventory` toolkit | `routes[].guarded` on the toolkit route | `inventory:tools` |

### List the generated tools

```bash
docker compose run --rm -e JWT_SCOPE="inventory:tools" client
```

```text
inventory__get_item
inventory__list_items
```

### Read an item

```bash
docker compose run --rm -e JWT_SCOPE="inventory:tools" \
    -e CALL_TOOL=inventory__get_item \
    -e CALL_ARGS='{"sku":"sku-1"}' client
```

```text
{"sku":"sku-1","name":"Widget","category":"tools","quantity":12}
```

### List items by category

`list_items` replies with a repeated `Item` field, which becomes a JSON array:

```bash
docker compose run --rm -e JWT_SCOPE="inventory:tools" \
    -e CALL_TOOL=inventory__list_items \
    -e CALL_ARGS='{"category":"toys"}' client
```

```text
{"items":[{"sku":"sku-3","name":"Gizmo","category":"toys","quantity":40}]}
```

### See a gRPC status as a tool error

```bash
docker compose run --rm -e JWT_SCOPE="inventory:tools" \
    -e CALL_TOOL=inventory__get_item \
    -e CALL_ARGS='{"sku":"nope"}' client
```

```text
ERROR NOT_FOUND: unknown sku nope
```

The call succeeded as far as MCP is concerned and the failure is the tool's
result, so the metrics below count it as `ok`.

### Without the toolkit scope

A token without `inventory:tools` gets no result from the toolkit. The call
stays unanswered until the client gives up, so bound it:

```bash
docker compose run --rm --entrypoint sh -e JWT_SCOPE="unrelated:tools" \
    -e CALL_TOOL=inventory__get_item -e CALL_ARGS='{"sku":"sku-1"}' client \
    -c 'npm install --no-audit --no-fund --silent && timeout 8 node client.mjs'
```

### Observe MCP metrics

The `mcp(server)` binding records each request as a counter and a duration
histogram, attributed by `method`, `tool` and `outcome`:

```bash
curl -s http://localhost:7190/metrics | grep '^mcp_tools_call_total'
```

```text
mcp_tools_call_total{namespace="example",binding="north_mcp_server",method="tools.call",outcome="ok",tool="inventory__get_item"} 2
mcp_tools_call_total{namespace="example",binding="north_mcp_server",method="tools.call",outcome="ok",tool="inventory__list_items"} 1
```

## Teardown

```bash
docker compose down -v
```

## References

- [Zilla docs, `mcp` bindings](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [Zilla docs, `mcp-grpc` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp-grpc/README.html)
- [Zilla docs, `jwt` guard](https://docs.aklivity.io/zilla/latest/reference/config/guards/jwt.html)
- [MCP, Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
