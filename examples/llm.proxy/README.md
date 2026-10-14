# llm.proxy

Demonstrates `llm(proxy)`: a hop that routes purely on `LlmBeginEx.dialect`/
`model` -- both already resolved and populated by the `llm(server)` that
produced the stream -- never on request content. Both frontends' `llm(server)`
bindings exit into one shared `north_llm_proxy`, which picks the backend from
the model alone:

- **Cross-dialect translation, chosen by model**: the model names the provider
  that serves it, whichever dialect the client speaks. An OpenAI SDK client
  asking for a Claude model is routed to an `llm(client)` that re-encodes the
  request for Anthropic, then re-encodes the response back as OpenAI on the
  way out; an Anthropic SDK client asking for a GPT model is translated the
  other way. A client already speaking the backend's dialect is not
  translated at all.
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

| Inbound dialect | Model | Routed to | Translated? |
| --- | --- | --- | --- |
| `openai` | `gpt-4o` | `south_llm_client_openai_secondary` → `mock-openai-secondary:4103` | no -- same dialect |
| `anthropic` | `claude-3-5-haiku-20241022` | `south_llm_client_anthropic_secondary` → `mock-anthropic-secondary:4104` | no -- same dialect |
| `openai` | `claude-*`, e.g. `claude-3-opus-20240229` | `south_llm_client_anthropic` → `mock-anthropic:4102` | **yes -- openai to anthropic** |
| `anthropic` | `gpt-*`, e.g. `gpt-4` | `south_llm_client_openai` → `mock-openai:4101` | **yes -- anthropic to openai** |
| `anthropic` | `claude-*`, e.g. `claude-3-opus-20240229` | `south_llm_client_anthropic` → `mock-anthropic:4102` | no -- same dialect |
| `openai` | `gpt-*`, e.g. `gpt-4` | `south_llm_client_openai` → `mock-openai:4101` | no -- same dialect |
| `openai` | *(any other model)* | `south_llm_client_openai` → `mock-openai:4101` | no -- same dialect |
| `anthropic` | *(any other model)* | `south_llm_client_anthropic` → `mock-anthropic:4102` | no -- same dialect |

A model neither prefix names is served by the provider of the client's own
dialect, with no translation.

The `llm` binding sits on top of `http`, the same way `mcp` does in
`examples/mcp.proxy` -- `http(server)`/`http(client)` do the wire-level
HTTP/1.1 framing, and `llm(server)`/`llm(client)`/`llm(proxy)` operate on the
decoded request/response (or, for the proxy, on the stream's own metadata).

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `llm` | `server` (`north_llm_server_openai`) | Terminates OpenAI-dialect `/v1/chat/completions` requests on port 7161 |
| `llm` | `server` (`north_llm_server_anthropic`) | Terminates Anthropic-dialect `/v1/messages` requests on port 7162 |
| `llm` | `proxy` (`north_llm_proxy`) | Routes on `dialect`/`model` to one of the four `llm(client)` legs below |
| `llm` | `client` (`south_llm_client_anthropic`) | Exit for `claude-*` models: for an openai-dialect request, re-encodes for `mock-anthropic`'s Messages API and translates its response back; an anthropic-dialect request passes straight through |
| `llm` | `client` (`south_llm_client_openai`) | Exit for `gpt-*` models: for an anthropic-dialect request, re-encodes for `mock-openai`'s Chat Completions API and translates its response back; an openai-dialect request passes straight through |
| `llm` | `client` (`south_llm_client_openai_secondary`) | `model: gpt-4o` exit: same dialect (openai) as the request, no translation, straight to `mock-openai-secondary` |
| `llm` | `client` (`south_llm_client_anthropic_secondary`) | `model: claude-3-5-haiku-20241022` exit: same dialect (anthropic) as the request, no translation, straight to `mock-anthropic-secondary` |

Each cross-dialect direction also demonstrates a tool-call round trip: an
OpenAI request for a Claude model carrying a `tools` definition gets an OpenAI
`tool_calls` response translated from the upstream's native Anthropic
`tool_use` content block, and vice versa.

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
          - model: [ "claude-*" ]
        exit: south_llm_client_anthropic
        with:
          fallback: [ claude-3-5-haiku-20241022 ]
      - when:
          - model: [ "gpt-*" ]
        exit: south_llm_client_openai
      - when:
          - dialect: openai
        exit: south_llm_client_openai
      - when:
          - dialect: anthropic
        exit: south_llm_client_anthropic
```

Routes are evaluated top to bottom; the first matching one wins. The two
`dialect`+`model` routes only match one specific model each. The `model`-only
routes beneath them match any dialect and send each model to the provider that
serves it, so the same route is a straight pass-through for a client speaking
that provider's dialect and a cross-dialect translation for a client speaking
the other one -- the dialect translation is a consequence of the model chosen,
not something the route configures. The two `dialect`-only routes last catch any
other model and keep it on the provider of the client's own dialect.

### Fallback

A route can list `fallback` models under `with`. The route is resolved when the
request begins, and `llm(proxy)` tries the exit with the requested model. An
`llm(client)` sends its first window only once the connection to its server is
established, so a server that cannot be reached resets the stream before any
window. When the exit resets the stream before it sends a window, `llm(proxy)`
takes the next model in the list, routes it through the same routes as any
other request, and retries on the exit that model resolves to, rewriting the
request to carry that model. A fallback model that matches no route the caller
is authorized for is skipped. The path locks as soon as the exit sends a
window, so a failure after the server has accepted the request is never
retried, and when every model has been refused or skipped the last reset reaches
the client. Fallback entries are exact model names, not patterns.

## Guardrails

`options.guardrails` on `north_llm_proxy` applies stages to the sections of a request and of its response,
keyed by the requested model name. A stage names the section types it applies to and a `value` model, any
model configured the way it would be elsewhere in `zilla.yaml`:

```yaml
north_llm_proxy:
  type: llm
  kind: proxy
  options:
    guardrails:
      gpt-4o:
        request:
          - section: user-text
            value:
              model: string
              maxLength: 2000
          - section: user-text
            value:
              model: classify
              reject:
                moderator0:
                  - jailbreak
                secrets0:
                  - secret.aws_key
                  - secret.github_token
                presidio0:
                  - email
                  - person
        response:
          - section: assistant-text
            value:
              model: classify
              reject:
                secrets0:
                  - secret.private_key
                presidio0:
                  - email
      claude-3-5-haiku-20241022:
        request:
          - section: user-text
            value:
              model: string
              maxLength: 200
```

The [`classify`](../../incubator/model-classify) model rejects a value that any of its classifiers labels with
one of the labels listed under `reject`. The classifiers are declared at the top of
[`zilla.yaml`](etc/zilla.yaml):

- [`semantic`](../../incubator/classifier-semantic) (`moderator0`) labels a prompt that means the same as one
  of a set of exemplar phrases, whatever words it uses. Here `jailbreak` covers prompts that tell the model to
  ignore its instructions. The embeddings come from a local
  [text-embeddings-inference](https://github.com/huggingface/text-embeddings-inference) service (`tei`)
  hosting `sentence-transformers/all-MiniLM-L6-v2`, so no vendor account is needed.
- [`patterns`](../../incubator/classifier-patterns) (`secrets0`) labels a value matching a regular expression,
  such as an AWS access key, a GitHub token or a private key header.
- [`presidio`](../../incubator/classifier-presidio) (`presidio0`) labels personal data, such as an email
  address or a person's name, as reported by a [Presidio](https://microsoft.github.io/presidio/) analyzer
  running as the `presidio-analyzer` service.

- **Rejecting a section.** A section a stage rejects is never relayed. The proxy ends the other side of the
  stream and answers the sender with a dialect-neutral error (`invalid_request_error`, `param: user-text[0]`),
  which `llm(server)` renders in the caller's own dialect: an HTTP 400 whose body names the section, with
  `param` where the dialect has one and the section in the message where it does not. A `gpt-4o` prompt that
  carries an email address, an AWS access key, or an instruction to forget its instructions, is refused before
  it reaches `mock-openai-secondary`. The semantic stage rejects by meaning, so the last one is refused even
  when it shares no wording with the exemplars.
- **Chaining.** The stages that name a section type are applied in the order listed, each to what the previous
  one produced. A `gpt-4o` prompt of 2001 characters is rejected by the length stage ahead of the classifier.
- **Both directions.** A `response` stage applies to the sections the upstream produces. When a stage rejects
  one, the caller never receives it: the proxy aborts the response toward the caller and resets the upstream. A
  `gpt-4o` request that asks for the private key is answered by `mock-openai-secondary` with one, and the
  `assistant-text` stage withholds it: the SDK reports a failed connection. A streamed response carries each
  delta as its own section, so a stage sees one delta at a time and cannot match text that spans deltas.
- **Fallback.** A request that falls back to another model passes through the stages of the requested model,
  followed by those of the model it falls back to. `claude-unreachable` has no stages of its own, so a prompt
  over 200 characters sent to it is rejected by the stage of `claude-3-5-haiku-20241022`, the model it falls
  back to.
- **Transforming stages.** A stage whose model rewrites the section, rather than only accepting or rejecting
  it, passes what it produces to the next stage and then to the other side, within the flow control credit that
  side grants. A transforming stage must be deterministic, which is checked when the binding is attached.
  Sections that no stage names, and requests for models without a guardrail, are relayed untouched.

The `tei` service downloads its model the first time it starts, so the first `docker compose up` can take a
while; the model is cached in a Docker volume that survives `docker compose down` and `up`. The semantic
classifier embeds its exemplars the first time it classifies a prompt, so the first request can be slow.

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
separate secret of its own configured in `zilla.yaml`, for the cross-dialect
routes or the secondary, same-dialect ones. The reverse route mirrors this: an
inbound `x-api-key` is captured with the `"{credentials}"` template and
forwarded upstream as `Authorization: Bearer {credentials}`.

## Requirements

- docker compose

## Setup

```bash
docker compose up -d
```

## Try it

Point the official `openai`/`anthropic` Python SDKs at the two frontends --
`base_url` is the only override, exactly as a caller would repoint an
existing integration at a self-hosted gateway. Since the SDKs enforce the
real wire contract (not just a matching substring), a successful call here
proves each dialect is complete enough for an off-the-shelf client to
succeed unmodified:

```bash
pip install openai anthropic
```

Send an OpenAI-shaped request for a Claude model to the openai-facing
frontend; the model picks the backend, so the request is translated to
Anthropic, and the reply comes back openai-shaped even though the real
upstream (`mock-anthropic`) only speaks Anthropic:

```python
from openai import OpenAI

client = OpenAI(base_url="http://localhost:7161/v1", api_key="your-openai-key")
resp = client.chat.completions.create(
    model="claude-3-opus-20240229",
    messages=[{"role": "user", "content": "Hello"}])
print(resp.choices[0].message.content)
```

Ask for `gpt-4` instead and the same client is routed straight to `mock-openai`
with no translation, since that model is served by an openai-dialect backend.
Ask for `gpt-4o`, and `north_llm_proxy` routes it to the second
openai-dialect deployment (`mock-openai-secondary`) instead:

```python
resp = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "Hello"}])
print(resp.choices[0].message.content)
```

Streaming works the same way, including across the translated leg:

```python
for chunk in client.chat.completions.create(
        model="claude-3-opus-20240229", messages=[{"role": "user", "content": "Hello"}], stream=True):
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="")
```

Send an Anthropic-shaped request for a GPT model to the anthropic-facing
frontend; the request is translated to OpenAI, and the reply comes back
anthropic-shaped even though the real upstream (`mock-openai`) only speaks
OpenAI:

```python
from anthropic import Anthropic

client = Anthropic(base_url="http://localhost:7162", api_key="your-anthropic-key")
resp = client.messages.create(
    model="gpt-4",
    max_tokens=1024,
    messages=[{"role": "user", "content": "Hello"}])
print(resp.content[0].text)
```

Ask for `claude-3-opus-20240229` instead and the same client is routed straight
to `mock-anthropic` with no translation. Ask for `claude-3-5-haiku-20241022`,
and `north_llm_proxy` routes it to the second anthropic-dialect deployment
(`mock-anthropic-secondary`) instead:

```python
resp = client.messages.create(
    model="claude-3-5-haiku-20241022",
    max_tokens=1024,
    messages=[{"role": "user", "content": "Hello"}])
print(resp.content[0].text)
```

Streaming:

```python
with client.messages.stream(
        model="gpt-4", max_tokens=1024,
        messages=[{"role": "user", "content": "Hello"}]) as stream:
    for text in stream.text_stream:
        print(text, end="")
```

Both directions round-trip through the exact same cross-dialect translation
-- the openai client's request for a Claude model lands on `mock-anthropic`,
the anthropic client's request for a GPT model on `mock-openai` -- the SDKs
are simply unaware of it.

Add a `tools` definition to either cross-dialect request (see
`etc/test/verify.py` for the full shape) to see a tool-call response
translated across dialects too.

## Metrics

Every `llm` binding records `llm.*` metrics -- per-exchange token usage
(`llm.tokens.input`, `llm.tokens.output`, `llm.tokens.total`,
`llm.tokens.cache.read`, `llm.tokens.cache.write`, `llm.tokens.reasoning`),
`llm.duration`, `llm.time.to.first.token` (request to first reply data),
`llm.interactions` and `llm.usage.incomplete` (exchanges that ended without a
complete usage report) and `llm.active.requests` -- exported for Prometheus on
port 7190:

```bash
curl -s http://localhost:7190/metrics | grep '^llm_tokens_input_count'
```

A token count the upstream dialect does not report is left unrecorded rather
than recorded as zero, so `llm_tokens_total_count` stays at `0` when the
upstream is Anthropic, which never reports a total.

### Metric attributes

A binding's `telemetry.attributes` label every `llm.*` series it records.
`${guarded['<guard>'].identity}` and `${guarded['<guard>'].attributes.<name>}`
resolve against the named guard using the caller's authorization, so a claim a
JWT guard exposes as an attribute can label usage per tenant:

```yaml
guards:
  jwt0:
    type: jwt
    options:
      issuer: https://auth.example.com
      audience: llm-proxy
      attributes:
        tenant: tenant
bindings:
  north_llm_server_openai:
    type: llm
    kind: server
    telemetry:
      attributes:
        tenant: ${guarded['jwt0'].attributes.tenant}
      metrics:
        - llm.*
```

Each distinct attribute value creates its own series, so avoid claims with
unbounded values.

## Verify

```bash
./.github/test.sh
```

Runs the assertions in `etc/test/verify.py` inside the compose stack, driven
through the real `openai`/`anthropic` Python SDKs rather than hand-built HTTP
calls: both translation directions (non-streaming and streaming), both
tool-call round trips, both credential pass-through directions (confirmed by
grepping each mock backend's own log for the caller's forwarded token), and
both model-based routes to the secondary, same-dialect deployments, and the
`llm.*` metrics recorded at every `llm` binding an exchange passes through.

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
