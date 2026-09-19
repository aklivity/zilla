# llm.proxy

Demonstrates `llm(proxy)`: a hop that routes purely on `LlmBeginEx.dialect`/
`model` -- both already resolved and populated by the `llm(server)` that
produced the stream -- never on request content. Both frontends' `llm(server)`
bindings exit into one shared `north_llm_proxy`, which picks the backend:

- **Cross-dialect translation** (the default route for each dialect): an
  `llm(server)` speaking one dialect is routed to an `llm(client)` that
  re-encodes the request for an upstream speaking the *other* dialect, then
  re-encodes the response back on the way out.
- **Intra-dialect, model-based routing**: a specific model name is routed to
  a *second* deployment of the *same* dialect instead -- no translation on
  that leg at all. This is the routing this binding kind exists for --
  failover or multi-backend selection constrained to same-dialect backends,
  chosen from `LlmBeginEx.model` alone.

```text
                     ┌──────────────────────────── Zilla ────────────────────────────┐
openai client ──────►│ tcp(7161) → http(server) → llm(server, dialect: openai)    ──┐│
                     │                                                              ││
anthropic client ───►│ tcp(7162) → http(server) → llm(server, dialect: anthropic) ─┼┼──► llm(proxy)
                     └──────────────────────────────────────────────────────────────┘│
```

`north_llm_proxy` then picks one of four `llm(client)` legs, each behind its
own `http(client)` → `tcp(client)`, purely from the stream's `dialect`/`model`:

| Inbound dialect | Model | Routed to | Same dialect? |
| --- | --- | --- | --- |
| `openai` | `gpt-4o` | `south_llm_client_openai_secondary` → OpenAI's Chat Completions API (`mock-openai-secondary` in CI) | yes -- no translation |
| `anthropic` | `claude-3-5-haiku-20241022` | `south_llm_client_anthropic_secondary` → Anthropic's Messages API (`mock-anthropic-secondary` in CI) | yes -- no translation |
| `openai` | *(anything else, e.g. `gpt-4`)* | `south_llm_client_anthropic` → Anthropic's Messages API (`mock-anthropic` in CI) | no -- translated |
| `anthropic` | *(anything else, e.g. `claude-3-opus-20240229`)* | `south_llm_client_openai` → OpenAI's Chat Completions API (`mock-openai` in CI) | no -- translated |

The `llm` binding sits on top of `http`, the same way `mcp` does in
`examples/mcp.proxy` -- `http(server)`/`http(client)` do the wire-level
HTTP/1.1 framing, and `llm(server)`/`llm(client)`/`llm(proxy)` operate on the
decoded request/response (or, for the proxy, on the stream's own metadata).

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `llm` | `server` (`north_llm_server_openai`) | Terminates OpenAI-dialect `/v1/chat/completions` requests on port 7161 |
| `llm` | `server` (`north_llm_server_anthropic`) | Terminates Anthropic-dialect `/v1/messages` requests on port 7162 |
| `llm` | `proxy` (`north_llm_proxy`) | Routes on `dialect`/`model` to one of the four `llm(client)` legs below |
| `llm` | `client` (`south_llm_client_anthropic`) | Default exit for openai-dialect traffic: re-encodes for Anthropic's Messages API and translates its response back |
| `llm` | `client` (`south_llm_client_openai`) | Default exit for anthropic-dialect traffic: re-encodes for OpenAI's Chat Completions API and translates its response back |
| `llm` | `client` (`south_llm_client_openai_secondary`) | `model: gpt-4o` exit: same dialect (openai) as the request, no translation, straight to the second OpenAI deployment |
| `llm` | `client` (`south_llm_client_anthropic_secondary`) | `model: claude-3-5-haiku-20241022` exit: same dialect (anthropic) as the request, no translation, straight to the second Anthropic deployment |

Each cross-dialect direction also demonstrates a tool-call round trip: a
request carrying an OpenAI `tools` definition gets an OpenAI `tool_calls`
response translated from the upstream's native Anthropic `tool_use` content
block, and vice versa.

## Routing

`north_llm_proxy` never parses a request body -- it only reads the two fields
`llm(server)` already resolved and attached to the stream's own begin
extension:

```yaml
bindings:
  north_llm_proxy:
    type: llm
    kind: proxy
    routes:
      - when:
          - dialect: openai
            model: [ gpt-4o ]
        exit: south_llm_client_openai_secondary
      - when:
          - dialect: anthropic
            model: [ claude-3-5-haiku-20241022 ]
        exit: south_llm_client_anthropic_secondary
      - when:
          - dialect: openai
        exit: south_llm_client_anthropic
      - when:
          - dialect: anthropic
        exit: south_llm_client_openai
```

Routes are evaluated top to bottom; the first matching one wins. The two
`dialect`+`model` routes only match one specific model each, so every other
model on that dialect falls through to the dialect-only catch-all beneath
them -- which is why the existing translation demo (any other model, e.g.
`gpt-4`/`claude-3-opus-20240229`) keeps working unchanged.

## Credential pass-through

Every direction wires `options.authorization` on the `llm(server)` and the
paired `llm(client)` to the same guard (`api_key`, `type: inline`):

```yaml
guards:
  api_key:
    type: inline
bindings:
  north_llm_server_openai:
    type: llm
    kind: server
    options:
      dialect: openai
      authorization:
        api_key:
          credentials: "Bearer {credentials}"
    exit: north_llm_proxy
  south_llm_client_anthropic:
    type: llm
    kind: client
    options:
      dialect: anthropic
      server: api.anthropic.com:443
      authorization:
        api_key:
          credentials: "{credentials}"
    exit: south_http_client_anthropic
```

(`south_http_client_anthropic` then exits through a `tls(client)` to reach
`api.anthropic.com:443` -- see `etc/zilla.yaml` for the full chain; elided
here since this section is about the credential template, not the network
path.)

The `api_key` guard doesn't validate anything -- it accepts whatever
token a caller presents and hands it back unchanged when asked. On the
server side, the `"Bearer {credentials}"` template strips the OpenAI
`Authorization` header's `Bearer ` prefix to recover the raw token; on the
client side, the `"{credentials}"` template (no prefix) re-injects that same
raw token into Anthropic's unprefixed `x-api-key` header. So a caller's own
API key is forwarded upstream exactly as presented -- Zilla never needs a
separate secret of its own configured in `zilla.yaml`, for the default routes
or the secondary, model-routed ones. The reverse route mirrors this: an
inbound `x-api-key` is captured with the `"{credentials}"` template and
forwarded upstream as `Authorization: Bearer {credentials}`.

`etc/zilla.yaml` targets the **real** OpenAI and Anthropic APIs directly over
TLS by default -- see [Testing against mocks instead](#testing-against-mocks-instead)
for the CI/local setup that swaps in mock backends so the example is
runnable without real API keys.

## Requirements

- docker compose
- a real OpenAI API key and/or a real Anthropic API key, to actually see a
  live response -- the containers start and the routing works without one,
  but the upstream API will reject an invalid key

## Setup

```bash
docker compose up -d
```

## Try it

Send an OpenAI-shaped request to the openai-facing frontend; the reply comes
back openai-shaped even though the real upstream (Anthropic's Messages API)
only speaks Anthropic:

```bash
curl -s http://localhost:7161/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer your-real-openai-key' \
    -d '{"model":"gpt-4","messages":[{"role":"user","content":"Hello"}]}'
```

Ask for `gpt-4o` instead, and `north_llm_proxy` routes it to the second
openai-dialect deployment rather than translating it to Anthropic (both
deployments target the same real OpenAI API in this example -- see
`south_llm_client_openai_secondary` in `etc/zilla.yaml` -- but a real
multi-backend setup would point this at a distinct second deployment):

```bash
curl -s http://localhost:7161/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer your-real-openai-key' \
    -d '{"model":"gpt-4o","messages":[{"role":"user","content":"Hello"}]}'
```

Send an Anthropic-shaped request to the anthropic-facing frontend; the reply
comes back anthropic-shaped even though the real upstream (OpenAI's Chat
Completions API) only speaks OpenAI:

```bash
curl -s http://localhost:7162/v1/messages \
    -H 'Content-Type: application/json' \
    -H 'anthropic-version: 2023-06-01' \
    -H 'x-api-key: your-real-anthropic-key' \
    -d '{"model":"claude-3-opus-20240229","max_tokens":1024,"messages":[{"role":"user","content":"Hello"}]}'
```

Ask for `claude-3-5-haiku-20241022` instead, and `north_llm_proxy` routes it
to the second anthropic-dialect deployment rather than translating it to
OpenAI:

```bash
curl -s http://localhost:7162/v1/messages \
    -H 'Content-Type: application/json' \
    -H 'anthropic-version: 2023-06-01' \
    -H 'x-api-key: your-real-anthropic-key' \
    -d '{"model":"claude-3-5-haiku-20241022","max_tokens":1024,"messages":[{"role":"user","content":"Hello"}]}'
```

Add a `tools` definition to either cross-dialect request (see
`etc/test/verify.sh` for the full shape) to see a tool-call response
translated across dialects too.

## Testing against mocks instead

```bash
./.github/test.sh
```

This is what CI runs, and works with no real API keys or network access to
either provider. `docker compose --env-file .github/.env.test run --rm
verify` merges in `.github/compose.mock.yaml` (via `.env.test`'s
`COMPOSE_FILE`), which stands up four mock backends and points zilla at
`.github/zilla.yaml` -- the same topology as `etc/zilla.yaml`, but every
`south_llm_client_*` targets its own mock instead of the real API, so the two
same-dialect deployments answer distinguishably and the model-based routing
assertions can tell them apart. It then runs the assertions in
`etc/test/verify.sh` inside the compose stack: both translation directions,
both tool-call round trips, both credential pass-through directions
(confirmed by grepping each mock backend's own log for the caller's forwarded
token), and both model-based routes to the secondary, same-dialect
deployments.

To bring the mock-backed stack up interactively instead of just running the
verify script:

```bash
docker compose --env-file .github/.env.test up -d
```

Nothing about the routing or the credential pass-through is specific to
either the real APIs or the mocks -- `options.authorization` templates the
*caller's own* credential rather than configuring one of Zilla's own, so the
same request shapes above work against either stack; only the upstream truly
answering the request differs.

## Teardown

```bash
docker compose down
```

If you brought up the mock-backed stack instead, tear it down with the same
`--env-file`:

```bash
docker compose --env-file .github/.env.test down
```

## References

- [llm binding reference](https://docs.aklivity.io/zilla/latest/reference/config/bindings/)
