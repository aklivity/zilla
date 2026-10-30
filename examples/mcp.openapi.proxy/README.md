# mcp.openapi.proxy

Turns a REST API described by an OpenAPI document into an authenticated MCP
(Model Context Protocol) server on a single Streamable HTTP endpoint, port
`7114`. The `mcp-openapi` binding reads the OpenAPI document of a Petstore API
and synthesizes MCP tools from its operations and MCP resources from its `GET`
operations, with no tool code to write.

```text
                         ┌──────────────────────────────────── Zilla ───────────────────────────────────┐
client ── Bearer JWT ───►│ tcp(7114) → http → mcp(server, jwt) → mcp(proxy, guarded) → mcp-openapi(client) │──► petstore:4002
                         └──────────────────────────────────────────────────────────────────────────────┘    (mock REST API)
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP, authenticates the session with the `authn_jwt` guard |
| `mcp` | `proxy` | Routes the `petstore` toolkit, gated by the `petstore:tools` scope |
| `mcp-openapi` | `client` | Synthesizes the `petstore` toolkit from an inline OpenAPI document and calls the REST API |

What it demonstrates:

- **Tools and resources from an OpenAPI document**: `list_pets`, `search_pets`
  and `create_pet` are tools, `featured_pets` is a static resource and `pet_by_id`
  is a templated resource.
- **Server override**: the document declares the public
  `https://api.petstore.example.com`, `options.specs.petstore.server` sends the
  requests to the local mock instead.
- **OpenAPI security mapped to a guard**: the document's `bearerAuth` scheme is
  mapped to `authn_jwt`, so `create_pet`, which declares `pets:write`, needs that
  scope on top of the toolkit-level `petstore:tools`.
- **Argument rename**: `search_pets` takes `category`, which `with.params` maps back
  to the OpenAPI `tag` query parameter.
- **Schema overrides**: `create_pet` replaces its derived input and output schemas
  with ones from the `petstore_catalog` inline catalog.

## Authorization model

Every session must present a JWT bearer token validated by the `authn_jwt` guard
on the `mcp(server)` binding. The token's `scope` claim is a space-separated list
of roles, matched against the roles each `guarded:` route requires.

| Layer | Mechanism | Requires |
| --- | --- | --- |
| `mcp(proxy)` route for the `petstore` toolkit | `routes[].guarded` | `petstore:tools` |
| `mcp-openapi(client)` operation `create_pet` | the OpenAPI `security` requirement, mapped by `options.specs.petstore.security` | `petstore:tools` **and** `pets:write` |

A caller that is not authorized for a tool or resource never sees it in the
`tools/list` or `resources/list` result: there is no "listed but denied" state.
A token the guard cannot verify is rejected with `401`.

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

This starts Zilla and the `petstore` mock REST API on `:4002`.

## Verify

Run the automated smoke test that the build workflow uses:

```bash
./.github/test.sh
```

It starts a one-off `verify` container inside the stack, which mints the caller
tokens and runs the assertions in [etc/test/verify.sh](etc/test/verify.sh).

To explore by hand, the `client` service connects through Zilla as a caller
holding the scopes in `JWT_SCOPE`. It signs a token with the RSA key in
[private.pem](private.pem), whose public half is configured in
`guards.authn_jwt.options.keys` (see [http.proxy.jwt](../http.proxy.jwt/README.md)
for how to derive it).

### Observe filtered tools/list results

```bash
# Toolkit access only: no create_pet
docker compose run --rm -e JWT_SCOPE="petstore:tools" client

# Toolkit access plus pets:write: create_pet is listed as well
docker compose run --rm -e JWT_SCOPE="petstore:tools pets:write" client
```

The second command prints:

```text
petstore__list_pets
petstore__search_pets
resource:petstore+/pets/featured
template:petstore+/pets/{petId}
```

### Call a tool

```bash
docker compose run --rm -e JWT_SCOPE="petstore:tools" \
    -e CALL_TOOL=petstore__list_pets client
```

The result is the pets from the mock, which answers on `petstore:4002` even
though the OpenAPI document declares `https://api.petstore.example.com`.

### Rename an argument

The OpenAPI `search_pets` operation filters on a query parameter named `tag`. The
custom input schema (`options.tools.search_pets.input`) exposes it to callers as
`category`, and `routes[].with.params` maps it back:

```yaml
- when:
    - tool: search_pets
  with:
    spec: petstore
    operation: search_pets
    params:
      tag: "${args.category}"
```

```bash
docker compose logs -f petstore &
docker compose run --rm -e JWT_SCOPE="petstore:tools" \
    -e CALL_TOOL=petstore__search_pets -e CALL_ARGS='{"category":"cat"}' client
```

The mock logs `search_pets query: {"tag":"cat"}`: the caller said `category`,
the request said `tag`, and only Whiskers comes back.

### Call a tool that needs an extra scope

`create_pet` requires `pets:write`. A caller holding only `petstore:tools` does not
see it listed, and a call to it is not routed and is never answered (stop the client with Ctrl-C):

```bash
docker compose run --rm -e JWT_SCOPE="petstore:tools" \
    -e CALL_TOOL=petstore__create_pet -e CALL_ARGS='{"name":"Nibbles","tag":"hamster"}' client
```

With `pets:write` the call succeeds, and the `summary` from
`options.tools.create_pet` is the text of the result:

```bash
docker compose run --rm -e JWT_SCOPE="petstore:tools pets:write" \
    -e CALL_TOOL=petstore__create_pet -e CALL_ARGS='{"name":"Nibbles","tag":"hamster"}' client
```

```text
Added pet: Nibbles
{"id":3,"name":"Nibbles","tag":"hamster"}
```

`create_pet`'s input schema requires `name`, so a call without it is rejected
before any request reaches the mock.

### Read resources

`mcp-openapi` maps `GET` operations to resources when the route says `resource:`
instead of `tool:`. Whether the result is a fixed entry in `resources/list` or a
`resources/templates` entry depends on the OpenAPI path itself:

- `GET /pets/featured`, with no path parameter, is the static resource
  `petstore+/pets/featured`.
- `GET /pets/{petId}`, with one path parameter, is the resource template
  `petstore+/pets/{petId}`, read with a concrete id.

Both need only `petstore:tools`:

```bash
docker compose run --rm -e JWT_SCOPE="petstore:tools" \
    -e READ_RESOURCE=petstore+/pets/featured client

docker compose run --rm -e JWT_SCOPE="petstore:tools" \
    -e READ_RESOURCE=petstore+/pets/2 client
```

## Teardown

```bash
docker compose down
```

## References

- [Zilla docs -- `mcp` bindings](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp/README.html)
- [Zilla docs -- `mcp-openapi` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp-openapi/README.html)
- [Zilla docs -- `jwt` guard](https://docs.aklivity.io/zilla/latest/reference/config/guards/jwt.html)
- [MCP -- Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
