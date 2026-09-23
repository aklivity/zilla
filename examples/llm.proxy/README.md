# llm.proxy

Demonstrates cross-dialect LLM translation by chaining an `llm(server)` to an
`llm(client)`: a client speaking one LLM API dialect is served by a frontend
that internally re-encodes the request for an upstream that only speaks the
other dialect, then re-encodes the response back on the way out. There is no
dedicated `llm proxy` binding kind yet -- chaining `llm(server)` to
`llm(client)` demonstrates the same capability with the two binding kinds
already implemented.

Two independent routes exercise both directions at once:

```text
                       ┌────────────────────────────────────────────── Zilla ───────────────────────────────────────────────┐
openai client ────────►│ tcp(7161) → http(server) → llm(server, dialect: openai)    → llm(client, dialect: anthropic) → http(client) → tcp │────► mock-anthropic:4102
                       │                                                                                                     │
anthropic client ─────►│ tcp(7162) → http(server) → llm(server, dialect: anthropic) → llm(client, dialect: openai)    → http(client) → tcp │────► mock-openai:4101
                       └─────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

The `llm` binding sits on top of `http`, the same way `mcp` does in
`examples/mcp.proxy` -- `http(server)`/`http(client)` do the wire-level
HTTP/1.1 framing, and `llm(server)`/`llm(client)` operate on the decoded
request/response, translating between dialects.

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `llm` | `server` (`north_llm_server_openai`) | Terminates OpenAI-dialect `/v1/chat/completions` requests on port 7161 |
| `llm` | `client` (`south_llm_client_anthropic`) | Re-encodes the request for `mock-anthropic`'s Messages API and translates its response back |
| `llm` | `server` (`north_llm_server_anthropic`) | Terminates Anthropic-dialect `/v1/messages` requests on port 7162 |
| `llm` | `client` (`south_llm_client_openai`) | Re-encodes the request for `mock-openai`'s Chat Completions API and translates its response back |

Each direction also demonstrates a tool-call round trip: a request carrying
an OpenAI `tools` definition gets an OpenAI `tool_calls` response translated
from the upstream's native Anthropic `tool_use` content block, and vice
versa.

## Credential pass-through

Both directions wire `options.authorization` on the `llm(server)` and the
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
    exit: south_llm_client_anthropic
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
separate secret of its own configured in `zilla.yaml`. The reverse route
mirrors this: an inbound `x-api-key` is captured with the `"{credentials}"`
template and forwarded upstream as `Authorization: Bearer {credentials}`.

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

Add a `tools` definition to either request (see `etc/test/verify.sh` for the
full shape) to see a tool-call response translated across dialects too.

## Verify

```bash
./.github/test.sh
```

Runs the assertions in `etc/test/verify.sh` inside the compose stack: both
translation directions, both tool-call round trips, and both credential
pass-through directions (confirmed by grepping each mock backend's own log
for the caller's forwarded token).

## Teardown

```bash
docker compose down
```

## References

- [llm binding reference](https://docs.aklivity.io/zilla/latest/reference/config/bindings/)
