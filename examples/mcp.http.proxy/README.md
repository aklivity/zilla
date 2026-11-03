# mcp.http.proxy

Turns a plain REST API into MCP (Model Context Protocol) tools and resources,
behind a single Streamable HTTP endpoint on port `7114`. The `mcp-http` binding
synthesizes the `github` toolkit from hand-authored configuration, backed here
by `ghapi`, a small mock of the GitHub pull request API. Callers authenticate
with a JWT, and the scopes in the token decide which tools they can see and call.

```text
                  ┌──────────────────────────────── Zilla ───────────────────────────────┐
client ─ Bearer ─►│ tcp(7114) → http → mcp(server, jwt) → mcp(proxy) → mcp-http(proxy)   │
                  │                                                    http(client) → tcp │
                  └───────────────────────────────────────────────────────────┬───────────┘
                                                                              ▼
                                                                      ghapi:4001
                                                                (mock GitHub REST API)
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP and authenticates the session with the `authn_jwt` guard |
| `mcp` | `proxy` | Routes the `github` toolkit, gated by `github:tools` |
| `mcp-http` | `proxy` | Declares the `create_pr` and `get_pr` tools and the `pull_by_number` resource template, and maps each to a REST request |

## What it demonstrates

- **Hand-authored tools.** `create_pr` and `get_pr` are declared in `options.tools`,
  each with a required input schema, an optional output schema and a result
  `summary` template. A tool call is validated against its input schema before
  Zilla builds the upstream request.
- **Argument mapping.** Route `with.headers` builds the request line from call
  arguments, `owner` and `repo` into the `:path` as `${args.owner}`. `with.body`
  forwards the remaining arguments as the JSON request body, validated against
  `create_pr_body`, the input schema minus the arguments the path already consumed.
- **A resource template.** `pr://{owner}/{repo}/{number}` has `{...}` in its
  `uri`, so it is listed under `resources/templates/list`. Captured values reach
  the request as `${params.x}`, not `${args.x}`.
- **Layered scopes.** The toolkit needs `github:tools`. The `create_pr` route adds
  `github:pr:write`, so a caller can have toolkit access without tool access.
  A tool or resource a caller is not authorized for is absent from the listing,
  never present and marked denied.
- **Credential forwarding.** `options.authorization.credentials.headers` sends the
  caller's own token and identity to `ghapi` as `authorization` and `x-user-identity`.

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla and `ghapi`, a Node mock on `:4001` seeded with one pull
request, `acme/widget#42`.

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
| `mcp(proxy)` route for the `github` toolkit | `routes[].guarded` on the toolkit route | `github:tools` |
| `mcp-http(proxy)` route for `create_pr` | a second, tool-specific `routes[].guarded` under the binding's base guarded route | `github:tools` **and** `github:pr:write` |

### Observe filtered tools/list results

```bash
docker compose run --rm -e JWT_SCOPE="github:tools" client
```

```text
github__get_pr
template:github+pr://{owner}/{repo}/{number}
```

`create_pr` is missing: toolkit access is not tool access. Add the finer scope:

```bash
docker compose run --rm -e JWT_SCOPE="github:tools github:pr:write" client
```

```text
github__create_pr
github__get_pr
template:github+pr://{owner}/{repo}/{number}
```

A tool a caller is not authorized for is absent, exactly as if it did not exist.

### Read a pull request with a tool

`get_pr` is read-only, so it needs nothing beyond `github:tools`. The `number`
argument is validated as an integer and fills the `:path`:

```bash
docker compose run --rm -e JWT_SCOPE="github:tools" \
    -e CALL_TOOL=github__get_pr \
    -e CALL_ARGS='{"owner":"acme","repo":"widget","number":42}' client
```

```text
Pull request #42 is open: Seed data for the pull_by_number resource demo
{"number":42,"html_url":"https://github.com/acme/widget/pull/42","state":"open","title":"Seed data for the pull_by_number resource demo"}
```

### Create a pull request

`create_pr` forwards `title`, `head` and `base` to `ghapi` as the request body,
while `owner` and `repo` only appear in the path. The summary template renders
ghapi's own response:

```bash
docker compose run --rm -e JWT_SCOPE="github:tools github:pr:write" \
    -e CALL_TOOL=github__create_pr \
    -e CALL_ARGS='{"owner":"acme","repo":"widget","title":"Add feature","head":"feature","base":"main"}' client
```

```text
Created pull request #101: Add feature
{"number":101,"html_url":"https://github.com/acme/widget/pull/101","state":"open","title":"Add feature","opened_by":"alice"}
```

`opened_by` is the JWT subject, which ghapi received in the `x-user-identity`
header Zilla forwarded from `options.authorization`.

### Schema-validated tool calls

`mcp-http` requires every tool to declare an input schema. A call that violates
it is rejected before any upstream request is built, so `ghapi` never sees it:

```bash
# title is required
docker compose run --rm -e JWT_SCOPE="github:tools github:pr:write" \
    -e CALL_TOOL=github__create_pr \
    -e CALL_ARGS='{"owner":"acme","repo":"widget","head":"feature","base":"main"}' client
```

```text
ERROR MCP error -32602: Invalid params
```

```bash
# title must be a string
docker compose run --rm -e JWT_SCOPE="github:tools github:pr:write" \
    -e CALL_TOOL=github__create_pr \
    -e CALL_ARGS='{"owner":"acme","repo":"widget","title":42,"head":"feature","base":"main"}' client
```

```text
ERROR MCP error -32602: Invalid params
```

Once validated, an argument only reaches the request where a route puts it.
Omitting `with.body` does not mean send everything, it means the validated
arguments are discarded. A route that consumes some arguments in the path and
wants the rest forwarded needs an explicit `with.body` scoped to what is left.

### Read a resource template

`github+pr://acme/widget/42` is the `pull_by_number` template with a concrete
URI. Resources are prefixed with the toolkit name, as in the listing above:

```bash
docker compose run --rm -e JWT_SCOPE="github:tools" \
    -e READ_RESOURCE="github+pr://acme/widget/42" client
```

```text
{"number":42,"html_url":"https://github.com/acme/widget/pull/42","state":"open","title":"Seed data for the pull_by_number resource demo"}
```

### Observe MCP metrics

The `mcp(server)` binding records each request as a counter and a duration
histogram, attributed by `method`, `tool` and `outcome`:

```bash
curl -s http://localhost:7190/metrics | grep '^mcp_tools_call_total'
```

```text
mcp_tools_call_total{namespace="example",binding="north_mcp_server",method="tools.call",outcome="ok",tool="github__create_pr"} 1
mcp_tools_call_total{namespace="example",binding="north_mcp_server",method="tools.call",outcome="error",tool="github__create_pr"} 2
```

## Teardown

```bash
docker compose down -v
```

## References

- [Zilla docs, `mcp` bindings](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [Zilla docs, `mcp-http` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp-http/README.html)
- [Zilla docs, `jwt` guard](https://docs.aklivity.io/zilla/latest/reference/config/guards/jwt.html)
- [MCP, Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
