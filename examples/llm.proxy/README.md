# llm.proxy

Demonstrates `llm(proxy)`: a hop that routes purely on `LlmBeginEx.dialect`/
`model` -- both already resolved and populated by the `llm(server)` that
produced the stream -- never on request content. All three frontends' `llm(server)`
bindings exit into one shared `north_llm_proxy`, which picks the backend from
the model alone. The config knows one OpenAI dialect name, `openai`, which stands for
OpenAI's two APIs: Chat Completions (`POST /v1/chat/completions`) and the Responses
API (`POST /v1/responses`), told apart by path on the one openai-facing frontend:

- **Cross-dialect translation, chosen by model**: the model names the provider
  that serves it, whichever dialect the client speaks. An OpenAI SDK client
  asking for a Claude model is routed to an `llm(client)` that re-encodes the
  request for Anthropic, then re-encodes the response back as OpenAI on the
  way out; an Anthropic SDK client asking for a GPT model is translated the
  other way. A client already speaking the backend's dialect is not
  translated at all.
- **Two OpenAI APIs, one dialect**: an OpenAI SDK client calling
  `client.responses.create` reaches the same frontend as one calling
  `client.chat.completions.create`. `south_llm_client_openai` follows the inbound
  request: a Responses request reaches OpenAI same-dialect on `POST /v1/responses`,
  while a request in any other dialect (Chat Completions, Anthropic, Gemini) is
  translated to `POST /v1/chat/completions`. A route condition `dialect: openai`
  matches both APIs. Translating between Chat Completions and Responses, and
  Anthropic or Gemini to Responses, is not offered yet; a Responses request is
  translated across dialects to Anthropic or Gemini, by model.
- **Intra-dialect, model-based routing**: a specific model name is routed to
  a *second* deployment of the *same* dialect instead -- no translation on
  that leg at all. This is the routing this binding kind exists for --
  failover or multi-backend selection constrained to same-dialect backends,
  chosen from `LlmBeginEx.model` alone.

```text
                       ┌─────────────────────────── Zilla ────────────────────────────┐
openai client ────────►│ tcp(7161) → http(server) → llm(server, openai)             ──┐│
anthropic client ─────►│ tcp(7162) → http(server) → llm(server, anthropic)          ──┤│
gemini client ────────►│ tcp(7163) → http(server) → llm(server, gemini)             ──┼┼──► llm(proxy)
                       └──────────────────────────────────────────────────────────────┘
```

`north_llm_proxy` then picks one of six `llm(client)` legs, purely from the stream's `dialect`/`model`. Each
leg exits to the system `sys:http_client`, which does the HTTP framing, and the TLS and TCP connection for the
`server` the leg names. A condition's `dialect` is the dialect the request was received in; `openai` stands
for both OpenAI APIs, Chat Completions and Responses, which the openai-facing frontend tells apart by path.
`south_llm_client_openai` follows the inbound request: a Responses request is same-dialect on `/v1/responses`,
a request in any other dialect is translated to `/v1/chat/completions`:

| Inbound dialect | Model | Routed to | Translated? |
| --- | --- | --- | --- |
| `openai` | `gpt-4o` | `south_llm_client_openai_secondary` → `api.openai.com` (`/v1/chat/completions`, or `/v1/responses` for a Responses request) | no -- same dialect |
| `anthropic` | `claude-3-5-haiku-20241022` | `south_llm_client_anthropic_secondary` → `api.anthropic.com` | no -- same dialect |
| `openai` | `claude-*`, e.g. `claude-haiku-5-5` | `south_llm_client_anthropic` → `api.anthropic.com` | **yes -- openai (Chat Completions or Responses) to anthropic** |
| `anthropic` | `claude-*`, e.g. `claude-haiku-5-5` | `south_llm_client_anthropic` → `api.anthropic.com` | no -- same dialect |
| `openai` | `gpt-*`, e.g. `gpt-5`, `gpt-4o-mini` | `south_llm_client_openai` → `api.openai.com` (`/v1/chat/completions`, or `/v1/responses` for a Responses request) | no -- same dialect |
| `anthropic`, `gemini` | `gpt-*`, e.g. `gpt-4o-mini` | `south_llm_client_openai` → `api.openai.com` (`/v1/chat/completions`) | **yes -- anthropic or gemini to openai** |
| any | `gemini-*`, e.g. `gemini-3.8-flash` | `south_llm_client_gemini` → `generativelanguage.googleapis.com` | no for `gemini`; **yes -- openai (Chat Completions or Responses) or anthropic to gemini** |
| `gemini` | `claude-*`, e.g. `claude-haiku-5-5` | `south_llm_client_anthropic` → `api.anthropic.com` | **yes -- gemini to anthropic** |
| `openai` | *(any other model)* | `south_llm_client_openai` → `api.openai.com` | no -- same dialect |
| `anthropic` | *(any other model)* | `south_llm_client_anthropic` → `api.anthropic.com` | no -- same dialect |
| `gemini` | *(any other model)* | `south_llm_client_gemini` → `generativelanguage.googleapis.com` | no -- same dialect |

A model no prefix names is served by the provider of the client's own
dialect, with no translation.

The `llm` binding sits on top of `http`, the same way `mcp` does in
`examples/mcp.proxy` -- `http(server)`/`http(client)` do the wire-level
HTTP/1.1 framing, and `llm(server)`/`llm(client)`/`llm(proxy)` operate on the
decoded request/response (or, for the proxy, on the stream's own metadata).

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `llm` | `server` (`north_llm_server_openai`) | Terminates OpenAI-dialect requests on port 7161: `dialect: openai` stands for both of OpenAI's APIs, `/v1/chat/completions` and `/v1/responses`, told apart by path |
| `llm` | `server` (`north_llm_server_anthropic`) | Terminates Anthropic-dialect `/v1/messages` requests on port 7162 |
| `llm` | `server` (`north_llm_server_gemini`) | Terminates Gemini-dialect `/v1beta/models/{model}:generateContent` and `:streamGenerateContent` requests on port 7163; the model comes from the path and a caller's `x-goog-api-key` is the credential |
| `llm` | `proxy` (`north_llm_proxy`) | Routes on `dialect`/`model` to one of the six `llm(client)` legs below |
| `llm` | `client` (`south_llm_client_anthropic`) | Exit for `claude-*` models: for an openai-dialect request, re-encodes for Anthropic's Messages API and translates its response back; an anthropic-dialect request passes straight through |
| `llm` | `client` (`south_llm_client_openai`) | Exit for `gpt-*` models: follows the inbound request -- an openai-dialect request passes straight through, a Responses request on `POST /v1/responses` (the `/v1` base path plus `/responses`) and a Chat Completions request on `POST /v1/chat/completions`; an anthropic- or gemini-dialect request is re-encoded for Chat Completions and its response translated back |
| `llm` | `client` (`south_llm_client_gemini`) | Exit for `gemini-*` models: for an openai- or anthropic-dialect request, re-encodes for Gemini's `streamGenerateContent` API and translates its response back; a gemini-dialect request passes straight through, on the operation the caller used |
| `llm` | `client` (`south_llm_client_openai_secondary`) | `model: gpt-4o` exit: for an openai-dialect request same dialect, no translation, straight to OpenAI on a second leg of its own, a Responses request on its `/v1/responses` |
| `llm` | `client` (`south_llm_client_anthropic_secondary`) | `model: claude-3-5-haiku-20241022` exit: same dialect (anthropic) as the request, no translation, straight to Anthropic on a second leg of its own |

Each cross-dialect direction also demonstrates a tool-call round trip: an
OpenAI request for a Claude model carrying a `tools` definition gets an OpenAI
`tool_calls` response translated from the upstream's native Anthropic
`tool_use` content block, and vice versa. A Responses request gets a
`function_call` output item in the same way, and a same-dialect Responses
round trip sends the call back with its `function_call_output`.

Members only the Responses API has a place for (`metadata`, `temperature`,
`user`, `store`, ...) are carried through a same-dialect hop to OpenAI's
`/v1/responses` and echoed into the response it returns, and left behind on a
hop to Anthropic or Gemini, which have no place for them.

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
          - model: [ "gemini-*" ]
        exit: south_llm_client_gemini
      - when:
          - dialect: openai
        exit: south_llm_client_openai
      - when:
          - dialect: anthropic
        exit: south_llm_client_anthropic
      - when:
          - dialect: gemini
        exit: south_llm_client_gemini
```

Routes are evaluated top to bottom; the first matching one wins. The two
`dialect`+`model` routes only match one specific model each. `dialect: openai`
names the vendor, so the `gpt-4o` route matches a Chat Completions request and
a Responses request alike; the latter reaches the secondary deployment
same-dialect on its `/v1/responses`. The `model`-only
routes beneath them match any dialect and send each model to the provider that
serves it, so the same route is a straight pass-through for a client speaking
that provider's dialect and a cross-dialect translation for a client speaking
the other one -- the dialect translation is a consequence of the model chosen,
not something the route configures. The `gpt-*` route names
`south_llm_client_openai`, which follows the inbound request: a Responses request
(`gpt-5`, say) reaches OpenAI same-dialect on `/v1/responses`, and a request in any
other dialect is translated to Chat Completions. The three `dialect`-only routes last
catch any other model and keep it on the provider of the client's own dialect.

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

`gpt-5` carries the same stages as `gpt-4o`.
Guardrails are keyed by model and apply to canonical sections, so they guard a
request whichever dialect it arrived in and whichever backend serves it: a
Responses request for `gpt-4o` or `gpt-5` is checked and then passed straight
through to OpenAI's `/v1/responses`.

The [`classify`](../../runtime/model-classify) model rejects a value that any of its classifiers labels with
one of the labels listed under `reject`. The classifiers are declared at the top of
[`zilla.yaml`](etc/zilla.yaml):

- [`semantic`](../../runtime/classifier-semantic) (`moderator0`) labels a prompt that means the same as one
  of a set of exemplar phrases, whatever words it uses. Here `jailbreak` covers prompts that tell the model to
  ignore its instructions. The embeddings come from a local
  [text-embeddings-inference](https://github.com/huggingface/text-embeddings-inference) service (`tei`)
  hosting `sentence-transformers/all-MiniLM-L6-v2`, so no vendor account is needed.
- [`patterns`](../../runtime/classifier-patterns) (`secrets0`) labels a value matching a regular expression,
  such as an AWS access key, a GitHub token or a private key header.
- [`presidio`](../../runtime/classifier-presidio) (`presidio0`) labels personal data, such as an email
  address or a person's name, as reported by a [Presidio](https://microsoft.github.io/presidio/) analyzer
  running as the `presidio-analyzer` service.

- **Rejecting a section.** A section a stage rejects is never relayed. The proxy ends the other side of the
  stream and answers the sender with a dialect-neutral error (`invalid_request_error`, `param: user-text[0]`),
  which `llm(server)` renders in the caller's own dialect: an HTTP 400 whose body names the section, with
  `param` where the dialect has one and the section in the message where it does not. A `gpt-4o` prompt that
  carries an email address, an AWS access key, or an instruction to forget its instructions, is refused before
  it reaches OpenAI. The semantic stage rejects by meaning, so the last one is refused even
  when it shares no wording with the exemplars.
- **Chaining.** The stages that name a section type are applied in the order listed, each to what the previous
  one produced. A `gpt-4o` prompt of 2001 characters is rejected by the length stage ahead of the classifier.
- **Both directions.** A `response` stage applies to the sections the upstream produces. When a stage rejects
  one, the caller never receives it: the proxy aborts the response toward the caller and resets the upstream. A
  `gpt-4o` response that carries a private key is withheld by the `assistant-text` stage: the SDK reports a
  failed connection. The same holds for a Responses request for `gpt-4o` or `gpt-5`. A streamed response is one
  section delivered in fragments. On a `response` stage the `classify` model checks the text as it arrives, together
  with the trailing overlap of the text already checked, so a pattern that spans fragments is still matched. What
  passes is relayed as it passes, apart from the overlap, and a rejection aborts the response after the text already
  relayed. On a `request` stage the value is classified and relayed once it is complete.
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
      server: https://api.anthropic.com:443
      authorization:
        api_key:
          credentials: "{credentials}"
    exit: sys:http_client
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
forwarded upstream as `Authorization: Bearer {credentials}`. A Responses
request uses the same `Bearer` templates, since it enters the same openai-facing
frontend and leaves through the same `south_llm_client_openai`.

## Requirements

- docker compose
- an API key for each provider you want to call: OpenAI, Anthropic and/or Gemini. Any subset works; a key is
  only needed for the providers your requests are routed to

## Setup

```bash
docker compose up -d
```

`etc/zilla.yaml` connects to the real OpenAI, Anthropic and Gemini APIs. The first start also downloads the
embedding model for the semantic guardrail, so it can take a few minutes. On an arm64 host (such as Apple silicon) the embedding image
needs its own tag:

```bash
TEI_VERSION=cpu-arm64-1.9 docker compose up -d
```

## Try it

Point the official `openai`/`anthropic`/`google-genai` Python SDKs at the three frontends --
`base_url` is the only override, exactly as a caller would repoint an
existing integration at a self-hosted gateway:

```bash
pip install openai anthropic google-genai
```

**The key you present is the key the serving provider receives.** The credential is forwarded upstream exactly as
presented (see [Credential pass-through](#credential-pass-through)), and the *model* picks the provider. A
request for a `claude-*` model reaches Anthropic whichever SDK sent it, so it needs an Anthropic API key, a
`gpt-*` model needs an OpenAI key and a `gemini-*` model needs a Gemini key. The model names below are examples:
use any model your account can call.

Send an OpenAI-shaped request for a Claude model to the openai-facing frontend,
using your Anthropic API key. The model picks the backend, so the request is translated to Anthropic, and the
reply comes back openai-shaped even though Anthropic only speaks its own dialect:

```python
from openai import OpenAI

client = OpenAI(base_url="http://localhost:7161/v1", api_key="your-anthropic-key")
resp = client.chat.completions.create(
    model="claude-haiku-5-5",
    messages=[{"role": "user", "content": "Hello"}])
print(resp.choices[0].message.content)
```

Ask for a GPT model instead, with your OpenAI key, and the same frontend routes the request straight to OpenAI
with no translation, since that model is served by an openai-dialect backend. The model `gpt-4o` is routed to a
second `llm(client)` leg of the same dialect instead -- here it reaches the same OpenAI API, but it is the leg a
second deployment would sit behind:

```python
client = OpenAI(base_url="http://localhost:7161/v1", api_key="your-openai-key")
resp = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "Hello"}])
print(resp.choices[0].message.content)
```

Streaming works the same way, including across the translated leg:

```python
for chunk in client.chat.completions.create(
        model="gpt-4o-mini", messages=[{"role": "user", "content": "Hello"}], stream=True):
    if chunk.choices and chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="")
```

A response is streamed or not as the upstream's content type says, not as the
request's `stream` setting says, so a request that streams still gets its events
when the upstream answers with a single document, and a request that does not
stream still gets a complete response when the upstream answers with
server-sent events. Text and tool calls alike are delivered by the upstream in
fragments and reach the SDK in the other dialect's own streamed shape.

Send a Responses API request to the same openai-facing frontend (port 7161) --
the same `OpenAI` client class, `client.responses.create` instead of
`client.chat.completions.create`; its `dialect: openai` stands for both OpenAI
APIs, told apart by path. `gpt-5` and `gpt-4o` are both served same-dialect, so
the request passes straight through to OpenAI's `POST /v1/responses`, `gpt-4o`
on the secondary leg. A `claude-*` model is translated to Anthropic and a
`gemini-*` model to Gemini, and the reply comes back as a Response object. The
key is the one of the provider that serves the model, as above:

```python
client = OpenAI(base_url="http://localhost:7161/v1", api_key="your-openai-key")

resp = client.responses.create(model="gpt-5", input="Hello")
print(resp.output_text)

print(client.responses.create(model="gpt-4o", input="Hello").output_text)

for event in client.responses.create(model="gpt-5", input="Hello", stream=True):
    if event.type == "response.output_text.delta":
        print(event.delta, end="")

client = OpenAI(base_url="http://localhost:7161/v1", api_key="your-anthropic-key")
print(client.responses.create(model="claude-haiku-5-5", input="Hello").output_text)
```

Members that only the Responses API has, such as `metadata` or `temperature`, are
carried through to OpenAI's `/v1/responses` and left behind on a hop to
Anthropic or Gemini. Translating between Chat Completions and Responses, in
either direction, is not offered yet, and neither is translating an Anthropic or
Gemini request to Responses: a Chat Completions, Anthropic or Gemini client
asking for `gpt-5` reaches `/v1/chat/completions`.

Send an Anthropic-shaped request for a GPT model to the anthropic-facing
frontend, using your OpenAI key; the request is translated to OpenAI, and the reply comes back
anthropic-shaped:

```python
from anthropic import Anthropic

client = Anthropic(base_url="http://localhost:7162", api_key="your-openai-key")
resp = client.messages.create(
    model="gpt-4o-mini",
    max_tokens=1024,
    messages=[{"role": "user", "content": "Hello"}])
print(resp.content[0].text)
```

Ask for a Claude model instead, with your Anthropic key, and the same client is routed straight to Anthropic with
no translation:

```python
client = Anthropic(base_url="http://localhost:7162", api_key="your-anthropic-key")
resp = client.messages.create(
    model="claude-haiku-5-5",
    max_tokens=1024,
    messages=[{"role": "user", "content": "Hello"}])
print(resp.content[0].text)
```

Streaming:

```python
with client.messages.stream(
        model="claude-haiku-5-5", max_tokens=1024,
        messages=[{"role": "user", "content": "Hello"}]) as stream:
    for text in stream.text_stream:
        print(text, end="")
```

Both directions round-trip through the exact same cross-dialect translation, and the SDKs are simply unaware of
it.

The Gemini frontend works the same way. Point the `google-genai` SDK at port 7163 with the key of the provider
that serves the model you ask for -- a `gemini-*` model goes to Gemini with no translation, a `claude-*` or
`gpt-*` model is translated to that provider and the reply comes back Gemini-shaped, and the other two frontends
can ask for a `gemini-*` model too, with a Gemini key:

```python
from google import genai
from google.genai import types

client = genai.Client(
    api_key="your-gemini-key",
    http_options=types.HttpOptions(base_url="http://localhost:7163", api_version="v1beta"))

resp = client.models.generate_content(model="gemini-3.8-flash", contents="Hello")
print(resp.text)

for chunk in client.models.generate_content_stream(model="gemini-3.8-flash", contents="Hello"):
    print(chunk.text, end="")
```

Gemini retires models over time and answers a retired model with a `404` that names its replacement, which Zilla
relays unchanged. The routes and guardrails in `etc/zilla.yaml` are keyed on `gpt-4o` and
`claude-3-5-haiku-20241022`; replace those with models your accounts can call if you want to exercise the
same-dialect routes and the guardrail stages against the real APIs.

Gemini thinking models spend output tokens on thinking, and the output limit counts them: `max_tokens` on the
openai and anthropic frontends and `maxOutputTokens` on the gemini frontend all become the same Gemini limit, and the
`output_tokens` reported back include the thinking tokens. A small limit can be used up before any text is
produced, so the reply ends with a `max_tokens` stop reason, possibly with no content at all. Use a generous limit,
as the examples above do.

Add a `tools` definition to any cross-dialect request (see
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
upstream is Anthropic, which never reports a total, while the Responses API
always reports one, recorded at every binding of the exchange. `llm_interactions_total`
counts one interaction per binding an exchange passes through, so a Responses request that
reaches `north_llm_server_openai` increments it at the server, the proxy and
the client that served it.

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

`${llm.request.model}` and `${llm.response.model}` label a series with the model the
request named and the model that served it, which can differ, as when a request for
`gpt-4` is served as `gpt-4-0613`. This example's `llm` bindings label every series
that way, so `north_llm_proxy` reports:

```bash
curl -s http://localhost:7190/metrics | grep '^llm_tokens_input_count.*north_llm_proxy'
```

```text
llm_tokens_input_count{namespace="example",binding="north_llm_proxy",requested="gpt-4",served="gpt-4-0613"} 1
```

`${llm.response.model}` is known only once the response starts, so `llm.active.requests`
carries just the values known when the request starts, and a response model the upstream
does not report is left off the series.

Each distinct attribute value creates its own series, so avoid claims with
unbounded values.

## Real endpoints

Each `llm(client)` in `etc/zilla.yaml` names its provider in `options.server` and exits to `sys:http_client`, the
system `http(client)` that routes `https` to a TLS connection and `http` to plain TCP, taking the host, port and
server name from the `server` URL:

```yaml
south_llm_client_gemini:
  type: llm
  kind: client
  options:
    dialect: gemini
    server: https://generativelanguage.googleapis.com:443
    authorization:
      api_key:
        credentials: "{credentials}"
  exit: sys:http_client
```

OpenAI and Anthropic follow the same shape with `https://api.openai.com:443` and `https://api.anthropic.com:443`;
`south_llm_client_openai` takes `dialect: openai` and serves both of OpenAI's APIs from the same
`https://api.openai.com:443`: a Responses request is sent to its `/v1` base path plus `/responses`, any other
dialect's request to `/chat/completions`.
The Gemini client's default path is `/v1beta`; set `options.server` to
`https://generativelanguage.googleapis.com:443/v1` for the stable API instead. The system TLS client trusts the CA
bundle shipped in the Zilla image. To trust an additional CA, such as a TLS-intercepting proxy's, or to present a
client certificate, exit to your own `http(client)` → `tls(client)` → `tcp(client)` legs with a `vault` instead of
`sys:http_client`; see the
[TLS binding reference](https://docs.aklivity.io/zilla/latest/reference/config/bindings/tls/) for the options.

## Testing against mocks instead

CI verifies this example without API keys or network access to any provider. `.github/.env.test` sets
`COMPOSE_FILE` to merge `.github/compose.mock.yaml` into `compose.yaml`, which stands up one mock per upstream
(`.github/mock-openai`, `.github/mock-anthropic`, `.github/mock-gemini` and the two secondary same-dialect
deployments) and points `zilla` at `.github/zilla.yaml` -- the same topology, with each `llm(client)` targeting its
mock over plain HTTP instead of the provider over TLS, still through `sys:http_client`. Run it the same way CI does:

```bash
./.github/test.sh
```

or bring the stack up on its own against the mocks:

```bash
docker compose --env-file .github/.env.test up -d --wait
```

`test.sh` runs the assertions in `etc/test/verify.py` inside the compose stack, driven through the real
`openai`/`anthropic`/`google-genai` Python SDKs rather than hand-built HTTP calls: every translation direction
between the dialects (non-streaming and streaming), every tool-call round trip, every credential
pass-through direction (confirmed by grepping each mock backend's own log for the caller's forwarded token), both
model-based routes to the secondary, same-dialect deployments, and the `llm.*` metrics recorded at every `llm`
binding an exchange passes through. The mocks answer with server-sent events when the prompt carries the marker
`[stream]`, whatever the request's `stream` setting says, which is how `verify.py` exercises each
combination of a streamed or non-streamed request and upstream response.

The Responses API is covered through `client.responses.create` against port
7161: text and streamed events (obfuscation on, and off with
`stream_options.include_obfuscation`), `instructions`, a function-call round
trip, the same-dialect hops to `mock-openai` (`gpt-5` and other `gpt-*` models) and
`mock-openai-secondary` (`gpt-4o`) on `/v1/responses`, the translated hops to Anthropic
(`claude-*`) and Gemini (`gemini-*`), members echoed back by the mock on the
same-dialect hop and absent from what the Anthropic mock logs, each model's route
and credential pass-through (read from the mock's own log), the guardrails of
`gpt-4o` and `gpt-5`, the fallback of `claude-unreachable`, an unknown model, and
the `llm.tokens.*` and `llm.interactions` metrics.

## Teardown

```bash
docker compose down
```

## References

- [llm binding reference](https://docs.aklivity.io/zilla/latest/reference/config/bindings/)
