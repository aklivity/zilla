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
| `openai` | `gpt-4o` | `south_llm_client_openai_secondary` → `mock-openai-secondary:4103` | yes -- no translation |
| `anthropic` | `claude-3-5-haiku-20241022` | `south_llm_client_anthropic_secondary` → `mock-anthropic-secondary:4104` | yes -- no translation |
| `openai` | *(anything else, e.g. `gpt-4`)* | `south_llm_client_anthropic` → `mock-anthropic:4102` | no -- translated |
| `anthropic` | *(anything else, e.g. `claude-3-opus-20240229`)* | `south_llm_client_openai` → `mock-openai:4101` | no -- translated |

The `llm` binding sits on top of `http`, the same way `mcp` does in
`examples/mcp.proxy` -- `http(server)`/`http(client)` do the wire-level
HTTP/1.1 framing, and `llm(server)`/`llm(client)`/`llm(proxy)` operate on the
decoded request/response (or, for the proxy, on the stream's own metadata).

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `llm` | `server` (`north_llm_server_openai`) | Terminates OpenAI-dialect `/v1/chat/completions` requests on port 7161 |
| `llm` | `server` (`north_llm_server_anthropic`) | Terminates Anthropic-dialect `/v1/messages` requests on port 7162 |
| `llm` | `proxy` (`north_llm_proxy`) | Routes on `dialect`/`model` to one of the four `llm(client)` legs below |
| `llm` | `client` (`south_llm_client_anthropic`) | Default exit for openai-dialect traffic: re-encodes for `mock-anthropic`'s Messages API and translates its response back |
| `llm` | `client` (`south_llm_client_openai`) | Default exit for anthropic-dialect traffic: re-encodes for `mock-openai`'s Chat Completions API and translates its response back |
| `llm` | `client` (`south_llm_client_openai_secondary`) | `model: gpt-4o` exit: same dialect (openai) as the request, no translation, straight to `mock-openai-secondary` |
| `llm` | `client` (`south_llm_client_anthropic_secondary`) | `model: claude-3-5-haiku-20241022` exit: same dialect (anthropic) as the request, no translation, straight to `mock-anthropic-secondary` |

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
      server: mock-anthropic:4102
      authorization:
        api_key:
          credentials: "{credentials}"
    exit: south_http_client_anthropic
```

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

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

## Try it

Send an OpenAI-shaped request to the openai-facing frontend; the reply comes
back openai-shaped even though the real upstream (`mock-anthropic`) only
speaks Anthropic:

```bash
curl -s http://localhost:7161/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer your-openai-key' \
    -d '{"model":"gpt-4","messages":[{"role":"user","content":"Hello"}]}'
```

Ask for `gpt-4o` instead, and `north_llm_proxy` routes it to the second
openai-dialect deployment (`mock-openai-secondary`) rather than translating
it to Anthropic:

```bash
curl -s http://localhost:7161/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer your-openai-key' \
    -d '{"model":"gpt-4o","messages":[{"role":"user","content":"Hello"}]}'
```

Send an Anthropic-shaped request to the anthropic-facing frontend; the reply
comes back anthropic-shaped even though the real upstream (`mock-openai`)
only speaks OpenAI:

```bash
curl -s http://localhost:7162/v1/messages \
    -H 'Content-Type: application/json' \
    -H 'anthropic-version: 2023-06-01' \
    -H 'x-api-key: your-anthropic-key' \
    -d '{"model":"claude-3-opus-20240229","max_tokens":1024,"messages":[{"role":"user","content":"Hello"}]}'
```

Ask for `claude-3-5-haiku-20241022` instead, and `north_llm_proxy` routes it
to the second anthropic-dialect deployment (`mock-anthropic-secondary`)
rather than translating it to OpenAI:

```bash
curl -s http://localhost:7162/v1/messages \
    -H 'Content-Type: application/json' \
    -H 'anthropic-version: 2023-06-01' \
    -H 'x-api-key: your-anthropic-key' \
    -d '{"model":"claude-3-5-haiku-20241022","max_tokens":1024,"messages":[{"role":"user","content":"Hello"}]}'
```

Add a `tools` definition to either cross-dialect request (see
`etc/test/verify.py` for the full shape) to see a tool-call response
translated across dialects too.

## Using the official SDKs

The `curl` calls above prove the wire shapes match; pointing the real
`openai`/`anthropic` Python SDKs at the same frontends proves those dialects
are complete enough for an off-the-shelf client to succeed unmodified --
`base_url` is the only override, exactly as a caller would repoint an
existing integration at a self-hosted gateway:

```bash
pip install openai anthropic
```

```python
from openai import OpenAI

client = OpenAI(base_url="http://localhost:7161/v1", api_key="your-openai-key")
resp = client.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "Hello"}])
print(resp.choices[0].message.content)

for chunk in client.chat.completions.create(
        model="gpt-4", messages=[{"role": "user", "content": "Hello"}], stream=True):
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="")
```

```python
from anthropic import Anthropic

client = Anthropic(base_url="http://localhost:7162", api_key="your-anthropic-key")
resp = client.messages.create(
    model="claude-3-opus-20240229",
    max_tokens=1024,
    messages=[{"role": "user", "content": "Hello"}])
print(resp.content[0].text)

with client.messages.stream(
        model="claude-3-opus-20240229", max_tokens=1024,
        messages=[{"role": "user", "content": "Hello"}]) as stream:
    for text in stream.text_stream:
        print(text, end="")
```

Both round-trip through the exact same cross-dialect translation as the
`curl` examples above -- the openai client's request lands on
`mock-anthropic`, the anthropic client's on `mock-openai` -- the SDKs are
simply unaware of it.

## Verify

```bash
./.github/test.sh
```

Runs the assertions in `etc/test/verify.py` inside the compose stack, driven
through the real `openai`/`anthropic` Python SDKs rather than hand-built HTTP
calls: both translation directions (non-streaming and streaming), both
tool-call round trips, both credential pass-through directions (confirmed by
grepping each mock backend's own log for the caller's forwarded token), and
both model-based routes to the secondary, same-dialect deployments.

## Using the real OpenAI/Anthropic APIs instead of the mocks

Nothing about the routing or the credential pass-through above is specific to
the mocks -- swap any `llm(client)`'s upstream for the real thing and a
caller's own real API key still forwards through unchanged, because
`options.authorization` templates the caller's *own* credential rather than
configuring one of Zilla's own.

To point `south_llm_client_openai` (or `south_llm_client_openai_secondary`)
at the real OpenAI API instead of `mock-openai`:

1. Change its `options.server` from `mock-openai:4101` to
   `api.openai.com:443`.
2. Real endpoints need TLS; insert a `tls(client)` between the `llm(client)`
   and the `tcp(client)`:

   ```yaml
   south_llm_client_openai:
     type: llm
     kind: client
     options:
       dialect: openai
       server: api.openai.com:443
       authorization:
         api_key:
           credentials: "Bearer {credentials}"
     exit: south_http_client_openai
   south_http_client_openai:
     type: http
     kind: client
     exit: south_tls_client_openai
   south_tls_client_openai:
     type: tls
     kind: client
     vault: south_clients
     options:
       trustcacerts: true
       sni:
         - api.openai.com
     exit: south_tcp_client_openai
   south_tcp_client_openai:
     type: tcp
     kind: client
     options:
       host: api.openai.com
       port: 443
   vaults:
     south_clients:
       type: filesystem
       options: {}
   ```

3. Call the openai-facing frontend (port 7161) with your **real** OpenAI API
   key in the `Authorization` header, exactly as you would call OpenAI
   directly -- `options.authorization`'s `"Bearer {credentials}"` /
   `"{credentials}"` templates forward it upstream unchanged, so no key is
   ever written into `zilla.yaml`.

The same three steps apply to `south_llm_client_anthropic` (or
`south_llm_client_anthropic_secondary`): point `options.server` at
`api.anthropic.com:443`, insert an equivalent `tls(client)`/`vault` pair with
`sni: [api.anthropic.com]`, and call port 7162 with a real `x-api-key`.

See the [TLS binding reference](https://docs.aklivity.io/zilla/latest/reference/config/bindings/tls/)
for the full set of `tls(client)` options.

## Teardown

```bash
docker compose down
```

## References

- [llm binding reference](https://docs.aklivity.io/zilla/latest/reference/config/bindings/)
