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
                                     ┌────────────────────────────────────────────────────────────────────────────────── Zilla ──────────────────────────────────────────────────────────────────────────────────┐
                                     │ tcp(7114) → http → mcp(server, jwt) → mcp(proxy, guarded routes)                                                                                                          │
client ── Authorization: Bearer ───►│                                                                                                                                                                          │
                                     │         ┬                     ┬                     ┬                   ┬                        ┬                              ┬                            ┬            │
                                     │         ▼                     ▼                     ▼                   ▼                        ▼                              ▼                            ▼            │
                                     │ mcp(client)         mcp(client)             mcp-http(proxy)    mcp-openapi(client)  mcp-schema-registry(client)  mcp-kafka-connect(client)        mcp-kafka(client)       │
                                     │ everything          urlelicit               github toolkit     petstore toolkit     kafka_sr toolkit             kafka_connect toolkit             kafka toolkit           │
                                     │ http →              http →                  http(client) →     http(client) →       http(client) →               http(client) →                   kafka_cache_client →    │
                                     │ tcp                 tcp                     tcp                tcp                  tcp                          tcp                              kafka_client → tcp      │
                                     └─────────┼─────────────────────┼─────────────────────┼───────────────────┼────────────────────────┼──────────────────────────────┼────────────────────────────┼────────────┘
                                               ▼                     ▼                     ▼                   ▼                        ▼                              ▼                            ▼           
                                       everything:3001     urlelicit:3003          ghapi:4001         petstore:4002        karapace-registry:8081       kafka-connect.examples.dev:8083  kafka.examples.dev:9092
                                       (reference server)  (url-mode elicitation)  (mock GitHub API)  (mock REST API,      (real Karapace               (real Kafka Connect              (real, single-node
                                                                                                      OpenAPI-described)   schema registry)             worker)                          KRaft Kafka broker)
```

This one configuration exercises all eight `mcp*` binding kinds:

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `mcp` | `server` | Terminates Streamable HTTP, authenticates the session with the `authn_jwt` guard |
| `mcp` | `proxy` | Aggregates toolkits behind one endpoint, gates each toolkit's routes with `guarded:` |
| `mcp` | `client` | Talks to an upstream server that is itself MCP (`everything`, `urlelicit`); `urlelicit` also forwards the caller's own JWT upstream |
| `mcp-http` | `proxy` | Synthesizes MCP tools from hand-authored config, backed by a plain REST API (`github` toolkit) |
| `mcp-openapi` | `client` | Synthesizes MCP tools from an OpenAPI document, backed by a plain REST API (`petstore` toolkit) |
| `mcp-schema-registry` | `client` | Exposes a fixed, bundled tool set for the Karapace/Confluent Schema Registry REST API -- no operator-authored OpenAPI document, unlike `mcp-openapi` (`kafka_sr` toolkit) |
| `mcp-kafka-connect` | `client` | Exposes a fixed, bundled tool set for the Kafka Connect REST API (`list_connectors`/`create_connector`/`describe_connector`/`delete_connector`/`describe_connector_config`/`update_connector_config`/`validate_connector_config`/`describe_connector_status`/`restart_connector`/`pause_connector`/`resume_connector`/`stop_connector`/`list_connector_tasks`/`restart_connector_task`/`describe_connector_offsets`/`alter_connector_offsets`/`reset_connector_offsets`/`list_connector_plugins`) as intrinsic MCP tools against a real Kafka Connect worker (`kafka_connect` toolkit) |
| `mcp-kafka` | `client` | Exposes Kafka broker operations (`produce_message`/`consume`/`create_topics`/`delete_topics`/`describe_configs`/`alter_configs`/`list_acls`/`create_acls`/`delete_acls`/`list_topics`/`describe_topic`/`cluster_overview`/`list_brokers`/`describe_cluster`/`list_consumer_groups`/`describe_consumer_group`/`describe_consumer_group_lag`/`reset_offsets`) as intrinsic MCP tools, generating its own `kafka_cache_client → kafka_client → tcp_client` pipeline against a real broker (`kafka` toolkit) |

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
| `mcp-http(proxy)` route for `create_pr` | a second, tool-specific `routes[].guarded`, layered under the `mcp-http` binding's base guarded route | `github:tools` **and** `github:pr:write` |
| `mcp(proxy)` route for `petstore` toolkit | `routes[].guarded` on the toolkit route | `petstore:tools` |
| `mcp-openapi(client)` operation `create_pet` | the OpenAPI document's own `security` requirement, mapped to `authn_jwt` via `options.specs.petstore.security` | `petstore:tools` **and** `pets:write` |
| `mcp(proxy)` route for `kafka_sr` toolkit | `routes[].guarded` on the toolkit route | `kafka_sr:tools` |
| `mcp-schema-registry(client)` route for `register_schema` | a second, tool-specific `routes[].guarded` on the binding's own `when.tool` route, layered under the toolkit-level gate above | `kafka_sr:tools` **and** `kafka_sr:write` |
| `mcp(proxy)` route for `kafka_connect` toolkit | `routes[].guarded` on the toolkit route | `kafka_connect:tools` |
| `mcp-kafka-connect(client)` route for connector/task/offset-mutating tools | a second, tool-specific `routes[].guarded` on the binding's own `when.tool` route (every mutating tool coalesced into one route), layered under the toolkit-level gate above | `kafka_connect:tools` **and** `kafka_connect:admin` |
| `mcp(proxy)` route for `kafka` toolkit | `routes[].guarded` on the toolkit route | `kafka:tools` |
| `mcp-kafka(client)` route for `produce_message` | a second, tool-specific `routes[].guarded` on the binding's own `when.tool` route, layered under the toolkit-level gate above | `kafka:tools` **and** `kafka:write` |
| `mcp-kafka(client)` route for `create_topics` / `delete_topics` / `alter_configs` / `reset_offsets` | a second, tool-specific `routes[].guarded` on the binding's own `when.tool` route (all four tools coalesced into one route, since all four need the same scope), layered under the toolkit-level gate above | `kafka:tools` **and** `kafka:admin` |
| `mcp-kafka(client)` route for `create_acls` / `delete_acls` | a second, tool-specific `routes[].guarded` on the binding's own `when.tool` route (both tools coalesced into one route), layered under the toolkit-level gate above, but with its own scope rather than reusing `kafka:admin` | `kafka:tools` **and** `kafka:acls` |

`list_pets`, `list_featured_pets`, and `get_pet` declare no OpenAPI `security`
of their own, so they need only the toolkit-level `petstore:tools` scope --
the same "toolkit access is not tool access" layering `mcp-http` demonstrates
with `github:pr:write`, expressed through OpenAPI's own security model
instead of an explicit `guarded:` route.

`mcp-schema-registry` has no OpenAPI document of its own to attach a
`security` requirement to at all -- the bundled spec declares none -- so its
`register_schema` route demonstrates the same layering the other way around,
via an explicit `routes[].guarded` directly on the `mcp-schema-registry`
binding itself: `list_subjects`, `describe_subject`, `get_schema`, and every
other read-only tool need only the toolkit-level `kafka_sr:tools`
scope, while `register_schema` additionally requires `kafka_sr:write`.

`mcp-kafka-connect` demonstrates the same layering on a fixed, bundled tool
set for the Kafka Connect REST API rather than a schema registry: every tool
that creates, deletes, or otherwise mutates connector, task, or offset state
(`create_connector`, `delete_connector`, `update_connector_config`,
`restart_connector`, `pause_connector`, `resume_connector`, `stop_connector`,
`restart_connector_task`, `alter_connector_offsets`,
`reset_connector_offsets`) shares one route requiring `kafka_connect:admin` in
addition to the toolkit-level `kafka_connect:tools`, while every read-only
tool (`list_connectors`, `describe_connector`, `describe_connector_config`,
`validate_connector_config`, `describe_connector_status`,
`list_connector_tasks`, `describe_connector_offsets`,
`list_connector_plugins`) needs only the toolkit-level scope, sharing the
catch-all glob route.

`mcp-kafka` demonstrates the identical layering a third way, on its own
`when.tool` route, and splits it further into read/write/admin: `consume`,
`describe_configs`, `list_acls`, `list_topics`, `describe_topic`,
`cluster_overview`, `list_brokers`, `describe_cluster`,
`list_consumer_groups`, `describe_consumer_group`, and
`describe_consumer_group_lag` need only the
toolkit-level `kafka:tools` scope (all read-only), `produce_message` additionally
requires `kafka:write` since it mutates topic data, and the admin-risk
`create_topics`, `delete_topics`, `alter_configs`, and `reset_offsets` route
requires `kafka:admin` instead -- same mechanism as `register_schema`,
different toolkit, with a third tier for structural (not just data)
mutation. `create_topics`, `delete_topics`, `alter_configs`, and
`reset_offsets` share one route rather than four near-identical ones: a
route's `when` list already matches by OR, so a second, third, and fourth
`- tool: ...` entry under the same `create_topics` route reuses the one
`kafka:admin` guard for all four.

`create_acls` and `delete_acls` add a **fourth** tier, `kafka:acls`, rather
than folding into `kafka:admin`: KIP-1318 -- the same Kafka proposal these two
tools implement -- classifies ACL mutation as "destructive-mutate" in its own
right, distinct from a topic/config/offset change, on the reasoning that a
wrongly-scoped `ALLOW` grant is itself a security incident, not just an
operational one. An operator trusted with `kafka:admin` to create topics or
change configs is not thereby also trusted to grant or revoke another
principal's access -- so the two scopes are issued independently, and a
caller needs `kafka:acls` specifically (on top of the toolkit-level
`kafka:tools`) to call either tool. `list_acls` itself is read-only and needs
no scope beyond `kafka:tools`, the same tier as `describe_configs`.

`describe_configs`, `list_acls`, `list_topics`, `describe_topic`,
`cluster_overview`, `list_brokers`, `describe_cluster`,
`list_consumer_groups`, `describe_consumer_group`, and
`describe_consumer_group_lag` coalesce into one more shared route alongside
`consume`'s, this time with no `guarded:` block at all since the
toolkit-level gate is already sufficient.

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

This starts Zilla plus seven locally-reachable upstream services: a Node
`everything` reference MCP server on `:3001`, a minimal `urlelicit` MCP server
on `:3003` demonstrating url-mode elicitation, two plain REST mocks --
`ghapi` on `:4001` (subset of the GitHub API) and `petstore` on `:4002`
(a small Petstore API, described by an inline OpenAPI document) -- a real,
single-node KRaft-mode Kafka broker on `:9092` (`kafka.examples.dev`), with an
`orders` topic created by the one-shot `kafka-init` service, a real
Karapace Schema Registry on `:8081` (`karapace-registry`), storing schemas in
its own `_schemas` topic on that same broker, and a real Kafka Connect
distributed worker on `:8083` (`kafka-connect.examples.dev`), storing its own
config/offset/status state in three internal compacted topics on that same
broker.

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
    --payload "scope=urlelicit:authorize github:tools github:pr:write petstore:tools pets:write kafka_sr:tools kafka_sr:write kafka_connect:tools kafka_connect:admin kafka:tools kafka:write kafka:admin kafka:acls" \
    --secret @/private.pem | tr -d '\r\n')
```

Omit scopes from `--payload` to see them disappear from `tools/list`.

### Observe filtered tools/list results

The bundled `tools-list-client` connects, lists tools, and prints one tool
name per line -- pass a token (or none) as `JWT_TOKEN`:

```bash
# No token: only the ungated "everything" toolkit is visible
docker compose run --rm tools-list-client

# Toolkit-level scopes only, no operation-level scopes: petstore__list_pets,
# kafka_sr__list_subjects, and kafka_connect__list_connector_plugins appear
# (none of them require an extra scope beyond toolkit access) but
# petstore__create_pet, github__create_pr, kafka_sr__register_schema, and
# kafka_connect__create_connector do not -- toolkit access alone is not tool
# access
export JWT_TOKEN=$(docker compose run --rm jwt-cli encode \
    --alg "RS256" --kid "example" \
    --iss "https://auth.example.com" --aud "https://api.example.com" \
    --exp=+1d --no-iat \
    --payload "scope=github:tools petstore:tools kafka_sr:tools kafka_connect:tools" \
    --secret @/private.pem | tr -d '\r\n')
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" tools-list-client

# Every scope: every toolkit's tools are listed
export JWT_TOKEN=$(docker compose run --rm jwt-cli encode \
    --alg "RS256" --kid "example" \
    --iss "https://auth.example.com" --aud "https://api.example.com" \
    --exp=+1d --no-iat \
    --payload "scope=urlelicit:authorize github:tools github:pr:write petstore:tools pets:write kafka_sr:tools kafka_sr:write kafka_connect:tools kafka_connect:admin kafka:tools kafka:write kafka:admin kafka:acls" \
    --secret @/private.pem | tr -d '\r\n')
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" tools-list-client
```

### Search, describe, and execute tools without listing them all

`options.cache.tools.eager` on `north_mcp_proxy` keeps only a fixed set of
frequently used tools -- `everything__echo`, `urlelicit__authorize`,
`github__create_pr`, `petstore__list_pets`, `petstore__search_pets`,
`petstore__create_pet`, `kafka_sr__list_subjects`,
`kafka_sr__register_schema`, `kafka_connect__list_connector_plugins`,
`kafka_connect__list_connectors`, `kafka_connect__describe_connector`,
`kafka_connect__create_connector`, `kafka_connect__delete_connector`,
`kafka__produce_message`, `kafka__consume`,
`kafka__create_topics`, `kafka__delete_topics`, `kafka__describe_configs`,
`kafka__alter_configs`, `kafka__list_acls`, `kafka__create_acls`,
`kafka__delete_acls`, `kafka__list_topics`, `kafka__describe_topic`,
`kafka__cluster_overview`, `kafka__list_brokers`, `kafka__describe_cluster`,
`kafka__list_consumer_groups`, `kafka__describe_consumer_group`,
`kafka__describe_consumer_group_lag`, and
`kafka__reset_offsets` -- eagerly listed in
`tools/list`. Every other tool is
"cold": because `options.cache.tools.search` also configures a `toolkit`
(`zilla` here), cold tools are omitted from `tools/list` entirely rather than
crowding it out, and three fixed-purpose tools are advertised instead --
`zilla__search_tools`, `zilla__describe_tool`, and `zilla__execute_tool` --
covering discovery, schema resolution, and invocation respectively without
ever requiring every cold tool's full definition up front. This is most
visible on the `everything` reference server, which registers over a dozen
demo tools -- `everything__get-sum`, `everything__get-env`,
`everything__get-tiny-image`, and more -- of which only `echo` is eager.

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
kafka_sr__list_subjects
kafka_sr__register_schema
kafka_connect__list_connector_plugins
kafka_connect__list_connectors
kafka_connect__describe_connector
kafka_connect__create_connector
kafka_connect__delete_connector
kafka__produce_message
kafka__consume
kafka__create_topics
kafka__delete_topics
kafka__describe_configs
kafka__alter_configs
kafka__list_acls
kafka__create_acls
kafka__delete_acls
kafka__list_topics
kafka__describe_topic
kafka__cluster_overview
kafka__list_brokers
kafka__describe_cluster
kafka__list_consumer_groups
kafka__describe_consumer_group
kafka__describe_consumer_group_lag
kafka__reset_offsets
zilla__search_tools
zilla__describe_tool
zilla__execute_tool
resource:petstore+/pets/featured
template:petstore+/pets/{petId}
template:github+pr://{owner}/{repo}/{number}
```

`everything__get-sum` -- one of the cold tools just omitted -- is still
discoverable by keyword. `zilla__search_tools` only ever searches within the
caller's own authorized scope, the same as `tools/list` itself, and its
matches are schema-free by design -- name and description only, so scanning
many candidates never costs more than a digest:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=zilla__search_tools -e CALL_ARGS='{"query":"sum"}' \
    tools-list-client
```

```text
everything__get-sum
```

Matches come back in the standard `structuredContent` field (alongside a
serialized-JSON `text` block for clients that predate `structuredContent`) --
there is no Zilla-specific content type involved, so this call works through
the same MCP SDK client used everywhere else in this example.

Once a match is worth acting on, `zilla__describe_tool` resolves its full
definition -- the same shape `tools/list` would show, schema included --
still enforcing the same per-tool scope a caller would need to see it listed
at all:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=zilla__describe_tool -e CALL_ARGS='{"name":"everything__get-sum"}' \
    tools-list-client
```

The response is the tool's full cached JSON object -- `name`, `description`,
and `inputSchema` (the real parameter names `@modelcontextprotocol/server-everything`
declares for `get-sum`) -- exactly what `tools/list` would show for this tool
were it not cold.

And `zilla__execute_tool` invokes it by name, through the identical
route-resolution and authorization path a direct `tools/call` for
`everything__get-sum` would take -- its result is the target tool's own
result, passed through unchanged:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=zilla__execute_tool \
    -e CALL_ARGS='{"name":"everything__get-sum","arguments":{"a":2,"b":3}}' \
    tools-list-client
# The sum of 2 and 3 is 5.
```

Cold does not mean inaccessible -- nothing about `options.cache.tools.eager`
touches `tools/call` routing, only what `tools/list` reports. Calling
`everything__get-sum` directly by name succeeds identically to an eager tool,
the same result `zilla__execute_tool` produced above:

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
`mcp-http` binding):

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

`mcp-http` requires every tool to declare an input schema
(`options.tools.create_pr.schemas.input`, backed by the `github_catalog`
inline catalog) -- a call is validated against it before Zilla builds the
upstream request at all. `mcp-openapi` makes the same `input`/`output`
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

`mcp-openapi` maps OpenAPI `GET` operations to MCP resources instead of
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

### Redirect the outbound host, and rename an argument (mcp-openapi)

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

### Register, look up, and configure schemas through a real Karapace instance

`south_mcp_schema_registry_client` is an `mcp-schema-registry` `kind: client`
binding -- like `petstore`, it proxies to a separate REST service, but unlike
`mcp-openapi` there is no OpenAPI document to author at all: the tool set
(`list_subjects`, `describe_subject`, `get_schema`, `register_schema`,
`delete_subject`, `delete_schema_version`, `check_compatibility`,
`get_compatibility`, `set_compatibility`) is bundled with the
binding itself, and `options.server` is the only required configuration. This
example points it at `karapace-registry`, a real Karapace Schema Registry
(not a mock) that stores schemas in its own `_schemas` topic on the same
Kafka broker the `kafka` toolkit above talks to.

Register a schema for a new subject -- the tool's summary interpolates the
id Karapace actually assigned, via `${result.id}` in its configured
`tool.summary` template:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__register_schema \
    -e CALL_ARGS='{"subject":"orders-value","schemaType":"AVRO","schema":"{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}"}' \
    tools-list-client
# Registered schema with id 1
```

`list_subjects` and `describe_subject` confirm it is now real, registered
state in Karapace, not just an echoed request:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__list_subjects tools-list-client
# ["orders-value"]

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__describe_subject \
    -e CALL_ARGS='{"subject":"orders-value"}' \
    tools-list-client
# [1]
```

`get_schema` reads it back by version -- its summary interpolates two
result fields at once (`${result.id}`, `${result.version}`):

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__get_schema \
    -e CALL_ARGS='{"subject":"orders-value","version":"latest"}' \
    tools-list-client
# Retrieved schema id 1, version 1
```

A freshly registered subject has no compatibility level configured yet --
`get_compatibility` on it fails until `set_compatibility` establishes one, a
real Karapace/Confluent behavior this example surfaces rather than papering
over with a default:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__set_compatibility \
    -e CALL_ARGS='{"subject":"orders-value","compatibility":"FULL"}' \
    tools-list-client
# Compatibility level set to FULL

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__get_compatibility \
    -e CALL_ARGS='{"subject":"orders-value"}' \
    tools-list-client
# Compatibility level is FULL

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_sr__check_compatibility \
    -e CALL_ARGS='{"subject":"orders-value","version":"1","schemaType":"AVRO","schema":"{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}"}' \
    tools-list-client
# Compatibility check result: true
```

### Manage connectors through a real Kafka Connect worker

`south_mcp_kafka_connect_client` is an `mcp-kafka-connect` `kind: client`
binding -- like `mcp-schema-registry`, its tool set
(`list_connectors`/`create_connector`/`describe_connector`/`delete_connector`/
`describe_connector_config`/`update_connector_config`/
`validate_connector_config`/`describe_connector_status`/`restart_connector`/
`pause_connector`/`resume_connector`/`stop_connector`/`list_connector_tasks`/
`restart_connector_task`/`describe_connector_offsets`/
`alter_connector_offsets`/`reset_connector_offsets`/`list_connector_plugins`)
is bundled with the binding itself, with `options.server` the only required
configuration. This example points it at `kafka-connect`, a real Kafka
Connect distributed worker (`apache/kafka:4.1.1`'s own
`connect-distributed.sh`, not a mock) that stores its own config/offset/status
state in three internal compacted topics on the same Kafka broker the `kafka`
toolkit above talks to.

`list_connector_plugins` lists the bundled FileStream source/sink connector
plugins -- proof `plugin.path` resolved the broker distribution's own `libs/`
directory rather than a separately downloaded plugin:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__list_connector_plugins tools-list-client
# org.apache.kafka.connect.file.FileStreamSinkConnector
# org.apache.kafka.connect.file.FileStreamSourceConnector
```

`create_connector` creates a real `FileStreamSourceConnector` reading a file
already seeded inside the worker container, gated by its own tool-specific
`kafka_connect:admin` scope layered under the toolkit-level
`kafka_connect:tools` scope -- the same layering mechanism as
`register_schema`/`kafka_sr:write`:

```bash
docker compose exec kafka-connect sh -c \
    "echo 'hello from mcp-kafka-connect' > /tmp/kc-source.txt"

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__create_connector \
    -e CALL_ARGS='{"name":"file-source-demo","config":{"connector.class":"org.apache.kafka.connect.file.FileStreamSourceConnector","tasks.max":"1","file":"/tmp/kc-source.txt","topic":"connect-demo"}}' \
    tools-list-client
# Created connector file-source-demo
```

`list_connectors` and `describe_connector_status` confirm it is now real,
running worker state, not just an echoed request:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__list_connectors tools-list-client
# ["file-source-demo"]

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__describe_connector_status \
    -e CALL_ARGS='{"connector":"file-source-demo"}' \
    tools-list-client
# Connector file-source-demo is RUNNING
```

`pause_connector` and `resume_connector` transition the connector and its one
task between `PAUSED` and `RUNNING` on the real worker, each confirmed by a
follow-up `describe_connector_status` call:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__pause_connector \
    -e CALL_ARGS='{"connector":"file-source-demo"}' \
    tools-list-client
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__describe_connector_status \
    -e CALL_ARGS='{"connector":"file-source-demo"}' \
    tools-list-client
# Connector file-source-demo is PAUSED

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__resume_connector \
    -e CALL_ARGS='{"connector":"file-source-demo"}' \
    tools-list-client
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__describe_connector_status \
    -e CALL_ARGS='{"connector":"file-source-demo"}' \
    tools-list-client
# Connector file-source-demo is RUNNING
```

`restart_connector` succeeds against the running connector, sharing
`pause_connector`/`resume_connector`'s `kafka_connect:admin`-gated route.
`delete_connector` then removes it, confirmed by `list_connectors` reporting
none remaining:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__restart_connector \
    -e CALL_ARGS='{"connector":"file-source-demo"}' \
    tools-list-client

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__delete_connector \
    -e CALL_ARGS='{"connector":"file-source-demo"}' \
    tools-list-client

docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka_connect__list_connectors tools-list-client
# []
```

### Produce, consume, create/delete topics, describe/alter configs, and cluster introspection through a real Kafka broker

`south_mcp_kafka_client` is an `mcp-kafka` `kind: client` binding -- unlike
every other toolkit in this example, it does not proxy to a separate MCP or
REST server. It generates its own `kafka_cache_client → kafka_client →
tcp_client` pipeline directly from `options.servers`, talking to the real,
single-node KRaft-mode Kafka broker this example starts
(`kafka.examples.dev:9092`, the `kafka` compose service; the `orders` topic
is created by the one-shot `kafka-init` service, though
`KAFKA_AUTO_CREATE_TOPICS_ENABLE` would create it anyway on first produce_message call).

Both routes below restrict their tool to exactly the `orders` topic --
`tool` and `topics` together in one `when` form an allow-list (exact names or
`*`-glob patterns), not just a dispatch by tool name; a `produce_message`/`consume`
call naming any other topic has no matching route and is rejected.

Produce a record with the full-scope `$JWT_TOKEN` from above:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__produce_message \
    -e CALL_ARGS='{"topic":"orders","value":"hello from mcp-kafka"}' \
    tools-list-client
# Produced record to orders topic
```

Then consume it back -- `limit` bounds how many records one call returns
(1-100, default 10); with nothing else produced to `orders` yet, the earliest
record is exactly the one just written:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__consume \
    -e CALL_ARGS='{"topic":"orders","limit":1}' \
    tools-list-client
# Consumed 1 messages from topic orders
# {"topic":"orders","messages":[{"key":null,"headers":[],"value":"hello from mcp-kafka"}],"count":1}
```

The second line is the tool result's raw `structuredContent` -- each message
carries its Kafka `key` (`null` here, since `produce_message` was not given one),
`headers`, and `value`. Both `produce_message` and `consume` stream incrementally
rather than buffering the whole request or result in memory: arguments are
parsed as they arrive, and consumed records are written to the reply as each
one comes off the broker, so neither tool is bounded by how large a single
value or a full result set happens to be.

Unlike `produce_message`/`consume`, `create_topics` and `delete_topics` have no
`topics` allow-list on their route -- each takes an array of topics as call
arguments, not a single routed topic, and both share one route gated by
`kafka:admin` (see "Authorization model" above) instead of a route each.
Create a new topic with the full-scope `$JWT_TOKEN` from above:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__create_topics \
    -e CALL_ARGS='{"topics":[{"name":"widgets","partitions":1,"replicas":1}]}' \
    tools-list-client
# Created topic(s): widgets
```

`create_topics` accepts multiple topics in a single call -- each item's
`name`, `partitions`, and `replicas` are required, with optional
`assignments` (explicit partition-to-broker placement) and `configs`
(per-topic config overrides).

Delete it again -- `delete_topics` takes a flat array of topic names (no
per-topic object, unlike `create_topics`) and needs the same `kafka:admin`
scope, since it shares `create_topics`' route:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__delete_topics \
    -e CALL_ARGS='{"topics":["widgets"]}' \
    tools-list-client
# Deleted topic(s): widgets
```

`describe_configs` is read-only, like `consume`, so it needs only the
toolkit-level `kafka:tools` scope. It takes a `resource_type` (`topic` or
`broker`) and a `resource_name`, not a routed topic, so -- like
`create_topics`/`delete_topics`/`alter_configs` -- it has no `topics`
allow-list on its route. With no `configs` given, it returns every config
Kafka reports for the resource, including ones the broker set by default:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__describe_configs \
    -e CALL_ARGS='{"resource_type":"topic","resource_name":"orders"}' \
    tools-list-client
# Described 15 config(s)
# {"configs":[{"name":"cleanup.policy","value":"delete","is_default":true,"is_sensitive":false}, ...]}
```

`alter_configs` sets configs on a resource and needs the same `kafka:admin`
scope as `create_topics`/`delete_topics`, since it shares their route.
Change the `orders` topic's `cleanup.policy`:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__alter_configs \
    -e CALL_ARGS='{"resource_type":"topic","resource_name":"orders","configs":{"cleanup.policy":"compact"}}' \
    tools-list-client
# Updated configs for topic orders
```

`list_acls` is read-only, like `describe_configs`, so it needs only the
toolkit-level `kafka:tools` scope and shares that route. Every filter field
is optional -- an absent field matches any value, the same semantics as
Kafka's own `AclBindingFilter`:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__list_acls \
    -e CALL_ARGS='{"resource_type":"topic","resource_name":"orders"}' \
    tools-list-client
```

`create_acls` and `delete_acls` need the dedicated `kafka:acls` scope
described in "Authorization model" above, on top of the toolkit-level
`kafka:tools` -- neither reuses `kafka:admin`. `create_acls` accepts multiple
bindings in a single call -- each item's `resource_type`, `resource_name`,
`principal`, `operation`, and `permission_type` are required; `pattern_type`
defaults to `literal` and `host` defaults to `*` (any host) when omitted.
Grant `User:bob` read access to the `orders` topic:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__create_acls \
    -e CALL_ARGS='{"acls":[{"resource_type":"topic","resource_name":"orders","principal":"User:bob","operation":"read","permission_type":"allow"}]}' \
    tools-list-client
```

Revoke it again -- `delete_acls` takes an array of *filter* objects (every
field optional, matching-and-deleting every ACL binding that satisfies all
the fields given) rather than the exact bindings `create_acls` took:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__delete_acls \
    -e CALL_ARGS='{"acls":[{"resource_type":"topic","resource_name":"orders","principal":"User:bob"}]}' \
    tools-list-client
```

**Prerequisite:** unlike every other tool in this example, the three ACL
tools require the broker itself to have an authorizer configured
(`authorizer.class.name`) -- Kafka rejects `DescribeAcls`/`CreateAcls`/
`DeleteAcls` outright with `SecurityDisabledException` ("No Authorizer is
configured on the broker") when none is set. This example's `kafka` service
enables Kafka's built-in `StandardAuthorizer` with
`allow.everyone.if.no.acl.found: true`, so every other tool call remains
implicitly permitted exactly as before -- the rule only stops applying to a
resource once an ACL actually exists for it. `create_acls` above deliberately
targets the `orders` topic to demonstrate that consequence directly: once
`User:bob`'s grant exists, a caller with no matching ALLOW ACL of their own
is no longer implicitly permitted against `orders`, so revoke it with
`delete_acls` when you are done experimenting, or subsequent `produce_message`/
`consume` calls in this same broker session may be denied.

`list_topics`, `describe_topic`, and `cluster_overview` are read-only
cluster/topic metadata tools built the same way `create_topics`/
`delete_topics` are -- against the real Kafka broker's Metadata API, not a
mock. Unlike `produce_message`/`consume`, none of the three take a `topics`
allow-list on their route: `list_topics` and `cluster_overview` request
metadata for every topic on the broker, and `describe_topic` names its one
topic as a call argument rather than a route match. All three need only the
toolkit-level `kafka:tools` scope -- the same tier as `consume` -- so they
share one route with no `guarded:` block of their own.

List every topic on the broker -- each entry reports its own partition count
and replication factor:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__list_topics \
    tools-list-client
# Found 1 topic(s)
# {"topics":[{"name":"orders","partition_count":1,"replication_factor":1}]}
```

Describe the `orders` topic by name -- the result reports each partition's
leader, replica set, and in-sync replica (ISR) set:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__describe_topic \
    -e CALL_ARGS='{"topic":"orders"}' \
    tools-list-client
# Described topic orders
# {"name":"orders","partitions":[{"partition_id":0,"leader":1,"replicas":[1],"isr":[1]}]}
```

Get a whole-cluster summary -- broker count, controller broker id, and
under-replicated/offline partition counts, useful as a single health check
across every topic at once:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__cluster_overview \
    tools-list-client
# Cluster overview: 1 topic(s), 1 broker(s)
# {"broker_count":1,"controller_id":1,"under_replicated_partitions":0,"offline_partitions":0,"topic_count":1}
```

### List brokers and describe the cluster

`list_brokers` and `describe_cluster` are read-only cluster introspection --
unlike `produce_message`/`consume` and `create_topics`/`delete_topics`, they take no
arguments and have no topic to route on, so they share `consume`'s unscoped
`kafka:tools`-only route rather than requiring `kafka:admin`. Both issue the
same Kafka `DescribeCluster` request through `KafkaApiDescribeClusterClient`,
differing only in how the response is shaped into each tool's result:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__list_brokers \
    tools-list-client
# Brokers: 1@kafka.examples.dev:29092
```

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__describe_cluster \
    tools-list-client
# Described cluster <cluster-id>, controller 1
```

The single node started by this example (`KAFKA_NODE_ID`/`KAFKA_BROKER_ID`
both `1`) is both the only broker `list_brokers` reports and the controller
`describe_cluster` reports -- `<cluster-id>` is a KRaft-generated identifier
that varies per broker startup.

### Manage consumer group offsets through a real Kafka broker

`list_consumer_groups`, `describe_consumer_group`,
`describe_consumer_group_lag`, and `reset_offsets` round out `mcp-kafka` with
consumer group management, against the same real broker as
`produce_message`/`consume`/`create_topics`/`delete_topics` above.
`list_consumer_groups`, `describe_consumer_group`, and
`describe_consumer_group_lag` are read-only, needing only the toolkit-level
`kafka:tools` scope; `reset_offsets` shares `create_topics`/`delete_topics`'
`kafka:admin`-gated route, since resetting a group's committed offsets is the
same kind of structural, admin-risk mutation.

Describe a group name that has never committed an offset on this broker --
real Kafka reports its state as `Dead`, not an error, since the group simply
does not exist yet:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__describe_consumer_group \
    -e CALL_ARGS='{"group_id":"orders-analytics"}' \
    tools-list-client
# Consumer group orders-analytics is Dead
```

`list_consumer_groups` takes no arguments and succeeds against the real
broker the same way:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__list_consumer_groups \
    tools-list-client
# No consumer groups found
```

`describe_consumer_group_lag` sequences a real `OffsetFetch` then a real
`ListOffsets` against the broker (`lag = endOffset - committedOffset` per
partition), the same generic `apiRequest`/`apiResponse` envelope
`describe_consumer_group`'s `DescribeGroups` call uses rather than a
coordinator-targeted or fictitious dedicated API. Against the same
never-used group, `OffsetFetch` reports no topics, so the result carries an
empty `partitions` array rather than an error -- a group with no committed
offsets simply has no lag to report yet:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__describe_consumer_group_lag \
    -e CALL_ARGS='{"group_id":"orders-analytics"}' \
    tools-list-client
# Consumer group orders-analytics has total lag 0
```

`reset_offsets` is modeled as a real Kafka `OffsetCommit`
(`generationId=-1`, `memberId=""`) -- the same "admin commit"
`AdminClient.alterConsumerGroupOffsets()` makes against an inactive group in
real Kafka, rather than a fictitious dedicated API. It resolves the group's
coordinator broker (`FindCoordinator`), rejects if the group has active
members (`DescribeGroups`), then commits directly:

```bash
docker compose run --rm -e JWT_TOKEN="$JWT_TOKEN" \
    -e CALL_TOOL=kafka__reset_offsets \
    -e CALL_ARGS='{"group_id":"orders-analytics","topic":"orders","partition":0,"offset":0}' \
    tools-list-client
```

If `orders-analytics` had an active member consuming `orders` (state
`Stable`, `PreparingRebalance`, or `CompletingRebalance` instead of `Empty`
or `Dead`), `reset_offsets` would reject the call instead of committing,
with `isError: true` and a message naming the group's actual state --
resetting offsets out from under an active consumer is never attempted
silently.

**Known gap:** this example's automated test (`.github/test.sh`) exercises
`reset_offsets`' `FindCoordinator` and `DescribeGroups` hops (the same
`DescribeGroups` call `describe_consumer_group` makes) against the real
broker, but not its final `OffsetCommit` hop -- driving that hop against a
real broker surfaced a hang in how the `mcp-kafka` client's auto-generated
composite routes an `OffsetCommit` stream to a dynamically resolved
coordinator host/port (as opposed to the statically configured
`options.servers`), tracked as a follow-up rather than papered over.

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

### Trigger a resource-subscription round-trip

`resources/subscribe`, `resources/unsubscribe`, and the resulting
`notifications/resources/updated` (aklivity/zilla#2220) pass through all
three `mcp` binding kinds the same way `elicitation/create` does above.
`@modelcontextprotocol/server-everything` implements all three: subscribing
registers interest in a resource URI, and a dedicated
`toggle-subscriber-updates` tool starts (or stops) a per-session, five-second
interval of simulated update notifications for whatever that session has
subscribed to -- subscribing alone does not push anything until this tool is
called.

```bash
docker compose run --rm resource-subscribe-client
```

The client lists resources to find a live `everything+...` URI, subscribes to
it, calls `everything__toggle-subscriber-updates`, waits for the relayed
`notifications/resources/updated`, then unsubscribes and toggles the updates
back off:

```text
[client] connected, protocolVersion=2025-11-25
[client] subscribed to everything+demo://resource/static/document/architecture.md
[client] toggle-subscriber-updates: Started simulated resource updated notifications for session ... at a 5 second pace...
[client] notifications/resources/updated uri=everything+demo://resource/static/document/architecture.md
OK resource subscription relayed end-to-end for everything+demo://resource/static/document/architecture.md
```

The URI arrives back prefixed `everything+`, same as any other aggregated
resource -- `north_mcp_proxy` re-prefixes it on the way out, exactly like the
`petstore+`/`github+` prefixes above. `north_mcp_proxy` also aggregates each
south binding's *real* capabilities (learned from that binding's own
`initialize` handshake with its upstream) into what it advertises for
`resources.subscribe`, rather than only reflecting a `routes[]`-declared
static capability -- so the very first session against a freshly started
gateway can occasionally race that south handshake and see
`resources.subscribe: false` for a moment; the client above retries on that
specific miss rather than treating it as a hard failure. See "Observe the
cache" below for the same warm-up behavior applied to `tools/list`.

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
narrower, single-header equivalent of `mcp-http`'s
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
- [Zilla docs -- `mcp-kafka` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp-kafka/README.html)
- [Zilla docs -- `mcp-kafka-connect` binding](https://docs.aklivity.io/zilla/latest/reference/config/bindings/mcp-kafka-connect/README.html)
- [Zilla docs -- `jwt` guard](https://docs.aklivity.io/zilla/latest/reference/config/guards/jwt.html)
- [MCP -- Streamable HTTP transport](https://modelcontextprotocol.io/docs/concepts/transports)
- [MCP -- elicitation](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)
- [SEP-1036 -- URL mode elicitation](https://modelcontextprotocol.io/seps/1036-url-mode-elicitation-for-secure-out-of-band-intera)
