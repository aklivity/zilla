# agui-a2a (proxy) — AG-UI to A2A mapping design

Design spike for #2822. Accepts `agui` streams (#2821 / PR #2850 server, #2856 / PR #2857 client), produces `a2a`
streams (#2814 stream model, PRs #2825-#2835). No code, no `.rpt`, no spec change is made by this document; spec changes it proposes are listed in section 4.

Shape mirrored: `a2a-http` / `mcp-http` (proxy kind adapting one stream type to another) and llm's
documented-lossiness cross-dialect approach (#2496). Request transform mirrors the inverse of `AguiClientFactory`'s
run input encoder; response decode mirrors `AguiEventDecoder` (incremental JSON, decode slot, decoder states).

## 0. What the branch actually gives us (and where it departs from the issue)

| Fact in the branch | Consequence for the design |
| --- | --- |
| `RUN_STARTED` / `RUN_FINISHED` / `RUN_ERROR` are **not** data events on the `agui` stream: they are the `AguiBeginEx` (`threadId`, `runId`, `parentRunId`), the `AguiEndEx` (`outcome` NONE/SUCCESS/INTERRUPT/CANCELLED + `detail`) and abort (`AguiAbortEx.error`) / reset (`AguiResetEx.error`) | The issue's "`working` -> `RUN_STARTED`/`STEP_*`" does not apply. The proxy never writes lifecycle data frames; it opens the reply (begin) and finishes it with end / abort / reset. `STEP_*` is not driven by A2A states |
| The `agui` request body is `RunAgentInput` JSON, forwarded verbatim by agui(server); `AguiBeginEx` only carries the ids | The proxy must parse the body (`messages`, `resume`, `state`, `context`, `forwardedProps`, `tools`) itself |
| agui(server) has `routes: false`, one `exit` resolved by authorization only, and no path or header visible on the `agui` stream (the HTTP `:path` is read only for the method / content-type / accept checks) | There is no route key on the stream. Revision 2 adopts one: `AguiBeginEx.agent`, fed by `routes[].when.path` / `with.agent` on agui(server) (decision Q-route, section 4) |
| agui(server) turns an app reset into an HTTP status (`doEncodeResponseRejected`) but adds `WWW-Authenticate` only for its own guard failures | A downstream 401 relayed as an app reset would be a 401 without a challenge, and would blame the caller for the proxy's own upstream credential (see the reset table in 1.3) |
| `a2a` application streams are half-duplex with `contentLength` in `A2aMessageSend/StreamBeginEx`; request and response bodies are v1.0 JSON verbatim; `A2aDataEx` tags `task` / `message` / `statusUpdate` / `artifactUpdate` with ids and `state` only | The proxy must buffer and transform the request to know `contentLength`; it must parse each response event body for `parts`, `append`, `lastChunk`, `status.message` |
| `A2aMessageStreamBeginEx` has `agent` (slug) and `extensions`; `A2aTasksCancelBeginEx` has `agent` + `taskId`; `A2aAgentCardBeginEx` has `agent` | Everything the mapping needs is routable with the existing BeginEx; no new case |
| No application script exercises `FAILED`, `REJECTED`, `INPUT_REQUIRED`, `AUTH_REQUIRED` over `messageStream` / `messageSend` (the enum has them; only `cache.serve.card.retry` and `push.deliver.slots` mention a non-happy state) | **Report, not a design change:** the state mapping below is untested at the a2a spec layer. Revision 2 puts the a2a-layer scripts in the a2a PRs (S4 / A6, section 4) and the mapping scripts in I2 / I3 |
| No script shows a2a(client) cancelling the remote task when a `messageStream` is aborted | Proxy must issue `tasksCancel` itself; abort of the stream alone must be treated as "detach", not "cancel" |
| A2A v1.0 wire shapes and the AG-UI input content-part schema were written from memory | **Verified in revision 3** against a2aproject/A2A@d924e07 and ag-ui-protocol/ag-ui@7e7c110 (section 7). Part / state / event shapes held; the AG-UI part types and the A2A `contextId` rules corrected the mapping (1.1, 1.2, 1.3) |

## 1. Mapping table

Legend — lossiness: `none` exact; `lossy` information dropped or approximated; `n/a` v1 does not map.

### 1.1 Identity and continuation (AG-UI -> A2A)

| AG-UI | A2A | Lossiness | Notes |
| --- | --- | --- | --- |
| `threadId` | `Message.contextId` (new conversation) | none (see OPEN-1) | sent on every run unless `options.context: none`. The A2A spec lets a server accept or **reject** a client-provided `contextId` (it MUST NOT substitute its own) and says clients SHOULD NOT send one unless they understand how the server treats it, hence the option. The proxy is stateless |
| `runId` | new task per run; `Message.metadata.agui.runId` | lossy | A2A task ids are server-assigned, so `runId` cannot be the task id. No A2A id is surfaced to the AG-UI client (8.0); the task id travels only inside the sealed interrupt id (8.4) and in Zilla events |
| `parentRunId` | `Message.metadata.agui.parentRunId` | lossy | informational only; never used for continuation |
| `resume[].interruptId` | `Message.taskId` (and **no** `contextId`) | none | the interrupt id is an opaque sealed token (8.4) that encodes the A2A `taskId`; the proxy unseals it, so continuation of an `input-required` / `auth-required` task needs no proxy state. A2A requires the server to infer `contextId` from the task and to reject a mismatching pair, so the resume message carries `taskId` only. The agent slug is equally stateless on resume: the client re-posts `RunAgentInput` to the same URL, so the same `routes[].when.path` yields the same `AguiBeginEx.agent` |
| `AguiBeginEx.agent` (new, revision 2) | `A2aBeginEx.agent` (slug), on `agentCard`, `messageStream` / `messageSend` and `tasksCancel` | none | pass-through by default; `routes[].with.agent` on the proxy rewrites it. Null stays null (the tenant-less A2A endpoint) |
| `resume[].payload` | message `parts` (string -> `text`, other JSON -> `data`; absent payload on `resolved` -> one empty `data` part `{}`) | lossy for `status` other than `resolved` | `status: cancelled` on a resume -> `tasksCancel` of that `taskId`, run ends `CANCELLED`, no message sent. An entry naming an interrupt id other than the one being continued is unrecognised and ignored (AG-UI: proceed without it); `resume[].metadata` -> `Message.metadata.agui.resume` |
| `AguiBeginEx` (reply) | reply begin echoes request `threadId` / `runId` / `parentRunId` | none | the A2A `contextId` is **not** returned as `threadId` (the client owns it). A conforming server either keeps the `contextId` it was sent or rejects the request, so a different `contextId` in a response to a run that sent one is a protocol violation: abort `error{status 502, code "agent_protocol"}`. Under `options.context: none` the server-generated id is not surfaced |

### 1.2 Request (AG-UI `RunAgentInput` -> A2A `SendMessage` / `SendStreamingMessage` params)

| `RunAgentInput` | A2A | Lossiness | Notes |
| --- | --- | --- | --- |
| `messages[]` trailing `user` messages (after the last `assistant` / `tool` message) | one `Message{role: ROLE_USER, messageId, contextId?, taskId?, parts[], metadata}` | lossy | AG-UI says a producer MUST treat `messages` as the complete history it is shown (`run-input.mdx`); this proxy deliberately resends only the new turn because the agent owns history through `contextId`. An edited / regenerated history, or a thread the agent has no context for, is not expressible (OPEN-6). `messageId` = id of the last trailing user message (idempotent retries); a `resume`-only run uses `runId` |
| `messages[]` earlier turns, `assistant`, `tool`, `activity`, `reasoning` roles | dropped | lossy | `tool` role is the frontend-tool result path (n/a v1, see 1.5); `activity` messages are consumer rendering material that AG-UI says never travel back |
| `messages[]` `system` / `developer` roles | dropped | lossy | the agent owns its system prompt; forwarding would be prompt injection across a trust boundary |
| user content `string` / `{type: text}` | `{text}` (part `metadata` -> part `metadata`) | none | consecutive parts keep order |
| `image` / `audio` / `video` / `document` part, `source: {type: data, value (base64), mimeType}` | `{raw: base64, mediaType}` | none | the AG-UI `ContentPart` types are `text`, `image`, `audio`, `video`, `document`; there is no `binary` type and no filename (revision 1 assumed both) |
| same part types, `source: {type: url, value, mimeType?}` | `{url, mediaType?}` | none | |
| same part types, `source: {type: file, value, provider?}` (provider file handle) | dropped | lossy | opaque to anyone but the issuing provider; AG-UI says a peer that cannot use a part skips it and continues; A2A has no equivalent |
| `state` (non-empty) and `context[]` (non-empty) | one `{data: {state, context}, mediaType: application/json, metadata: {agui: {part: "input"}}}` appended after content parts | lossy (agent may ignore) | controlled by `options.forward` (default `[state, context]`) |
| `forwardedProps` | `Message.metadata.agui.forwardedProps` | none | opaque to the proxy; not used for routing |
| `tools[]` | dropped (v1) | lossy | see 1.5 |
| no trailing user message and no `resume` | reset 422 `invalid_run` before reply begin | n/a | mirrors `run.rejected` |
| `SendMessageConfiguration` | `{"historyLength": 0}` | n/a | AG-UI never needs history in the response |

### 1.3 Response (A2A events -> AG-UI)

Applies to `messageStream` events; `messageSend` uses the same decode on the single `task` / `message` body (1.4).

| A2A event | AG-UI | Lossiness | Notes |
| --- | --- | --- | --- |
| a2a reply begin (`connected`) | reply begin, then agui(server) emits `RUN_STARTED` | none | |
| `task` `SUBMITTED` / `statusUpdate` `SUBMITTED`, `WORKING` without message | nothing | lossy | A2A working is not an AG-UI step; no `STEP_*` in v1 |
| `statusUpdate` `WORKING` with `status.message` | nothing (v1); `ACTIVITY_SNAPSHOT` `a2a.status` (follow-up) | lossy | progress chatter must not pollute the transcript |
| `task` carrying `artifacts[]` (the first event, or the final snapshot a server MAY resend before closing) | text for artifacts whose `artifactId` has not been streamed yet; skipped for ids already streamed | none | reconcile by `artifactId` so a final snapshot does not duplicate text; shared path with `messageSend`, where every artifact is unseen |
| `artifactUpdate`, text part, first sight of `artifactId` | `TEXT_MESSAGE_START` (`messageId = artifactId`, role ASSISTANT) + `CONTENT` | none | `text` data frame, init flag + `AguiTextDataEx` |
| `artifactUpdate`, text part, `append: true` | `TEXT_MESSAGE_CONTENT` on the open message | none | payload-only frames, `chunks` framing |
| `artifactUpdate`, `append: false` on an already streamed `artifactId` | new text message `messageId = artifactId + "#n"` | lossy | AG-UI cannot retract streamed text; earlier text stays |
| `artifactUpdate` `lastChunk: true`, or terminal / interrupt state with a text message open | `TEXT_MESSAGE_END` (empty fin frame) | none | the proxy uses `run.text.chunks` framing because `lastChunk` can arrive after the last text |
| `artifactUpdate` / `task` / `message` non-text part (`data`, `file`) | `CUSTOM` `agent.data` (value = the data JSON) / `agent.file` (value = `{mimeType, name?, url or data}`) | lossy | AG-UI flavoured names and shapes, no A2A vocabulary (8.0); an open text message is closed first. Tier 2 maps tagged parts to native events instead (8.2) |
| `message` (agent reply with no task) | text message from its text parts (`messageId` = `Message.messageId`), non-text as `CUSTOM`; run ends `SUCCESS` | none | |
| `statusUpdate` `COMPLETED` | end `SUCCESS`, no `detail` | none | `result` is the run's return value and A2A completion has none; no A2A ids are placed on the client stream (8.0), they go to a Zilla event |
| `statusUpdate` `FAILED` | abort `AguiAbortEx.error{status 502, code "agent_failed", message = status.message text, default "Agent failed"}` | lossy | AG-UI `RUN_ERROR`; `message` is required there, the idl's is nullable, so the proxy always fills it |
| `statusUpdate` `REJECTED` | abort `error{status 422, code "agent_rejected", message, default "Agent declined"}` | lossy | |
| `statusUpdate` `CANCELED` | end `CANCELLED` | none | whoever cancelled it |
| `statusUpdate` `INPUT_REQUIRED` | end `INTERRUPT`, `detail = {"outcome":{"interrupts":[{"id":"<sealed>","reason":"input","message":"<status.message text>","expiresAt":"<seal expiry>"}]}}` | lossy | the prompt travels in the interrupt message, not as a transcript message; `reason` is a neutral open string. The run ends when the state is seen, whether or not the server closes the stream (the A2A spec says the stream closes at terminal states in one place and at terminal or interrupted in another); the proxy then ends its own a2a stream without cancelling the task. Tier 2 enriches `reason`, `responseSchema`, `toolCallId` (8.2) |
| `statusUpdate` `AUTH_REQUIRED` | end `INTERRUPT`, same shape, `reason: "authorization"` | lossy | A2A auth-required is out-of-band credentials for the agent's own downstream; the client can only resume |
| `UNSPECIFIED`, or a2a end with no terminal / interrupt state seen | abort `error{status 502, code "agent_incomplete"}` | n/a | outcome is derived from the last state, never from stream end alone |
| a2a abort (`A2aAbortEx`), end with `outcome: ERROR` | abort `error{status 502, code "agent_aborted", message = reason}` | lossy | |

Errors before the reply begin (a2a reset on the first stream) map to **reset** on the agui stream, so agui(server) answers with an HTTP status rather than an SSE `RUN_ERROR`:

| a2a reset | agui reset | Lossiness |
| --- | --- | --- |
| `A2aBearerResetEx` `INVALID_TOKEN` / `INVALID_REQUEST` | status 502 `agent_unauthorized` | lossy by intent: the caller was already authenticated by agui(server); a token the agent rejects is the proxy's upstream credential (a2a(client) `options.authorization`), which the caller cannot fix. Relaying 401 would also carry no `WWW-Authenticate` challenge |
| `A2aBearerResetEx` `INSUFFICIENT_SCOPE` (agent-level guard on the shared authorization) | status 403 `forbidden` | lossy: required `scope` not relayed (decision on candidate (d), section 4) |
| `A2aErrorResetEx` code 404 (`agent.not.found`) | status 404 `agent_not_found` | none |
| `A2aErrorResetEx` other 4xx (`invalid.params`, `extension.required`, `version.unsupported`) | status 422 `invalid_run`, message passed through | lossy |
| `A2aErrorResetEx` 5xx, or no answer | status 502 | lossy (matches `run.unavailable`) |

### 1.4 Streaming vs non-streaming

| Card `capabilities.streaming` | A2A operation | AG-UI output |
| --- | --- | --- |
| `true` | `messageStream` | events as they arrive (1.3) |
| `false` / absent | `messageSend` (`A2aMessageSendBeginEx`) | one `task` or `message` body decoded through the same path: artifacts in order -> text messages (each emitted whole), then outcome from `status.state`. Synthesized; no incremental text |

### 1.5 Frontend tools

| AG-UI | A2A | Decision |
| --- | --- | --- |
| `RunAgentInput.tools[]`, `tool` role messages, `TOOL_CALL_*` | no client-tool concept | AG-UI ends a run that stops on a frontend tool call as `SUCCESS` with `pendingToolCallIds` and takes the answer as `tool` messages in the next input, not through `resume` (`lifecycle.mdx`); F1 must follow that shape. v1: dropped (lossy, documented). Follow-up: an A2A extension (URI declared in the card `capabilities.extensions`, activated through `A2aMessageStreamBeginEx.extensions`) carrying tool declarations in `metadata` and tool calls as tagged `data` parts, with the `input-required` round trip providing the result path. No new idl case needed |

### 1.6 Cancellation

| Trigger | Proxy action |
| --- | --- |
| agui stream abort / reset after `taskId` is known (first `task` / `statusUpdate` / `artifactUpdate` DataEx) | with `options.abort: cancel` (default): open `tasksCancel` (`agent`, `taskId`) on the exit, ignore its result; always abort the `messageStream`. With `detach`: abort the `messageStream` only |
| abort before any DataEx (no `taskId` yet) | abort the `messageStream` only; the remote task may keep running (documented lossy) |
| `resume[].status = cancelled` | `tasksCancel` for the interrupt id, end `CANCELLED` |
| a2a `CANCELED` state not initiated here | end `CANCELLED` |

AG-UI and CopilotKit facts that bear on this (verified, section 7): the AG-UI HTTP + SSE binding is a POST of `RunAgentInput` to whatever URL the consumer is configured with, and defines no stop / cancel; a consumer that abandons the stream has a *truncated run* and "MUST NOT synthesize a `RUN_FINISHED`", while `cancelled` is a producer's report about a run it stopped (`lifecycle.mdx`). The JS `HttpAgent.abortRun()` is an `AbortController` abort of the fetch. CopilotKit's runtime exposes `POST /agent/:agentId/stop/:threadId` to its own browser client; the handler calls `runner.stop`, which ends in `agent.abortRun()` on the upstream agent, i.e. the same fetch abort. It is thread-wide unless an optional `{runId}` body is sent, and reports the stop to the browser as `RUN_ERROR` with code `STOPPED` (a CopilotKit convention, not AG-UI's `cancelled` outcome). None of that reaches Zilla: Zilla sees only the aborted request. A Stop button and a closed tab are indistinguishable there, so cancelling is a policy (`options.abort`, OPEN-5). `/connect` and `/stop/...` are CopilotKit runtime routes, not AG-UI; with `routes[].when.path` they are unmatched and answered 404. Cancelling by `taskId` is run-scoped, so it avoids a thread-wide stop cancelling the next run.

### 1.7 Card and capability negotiation

| Step | Behavior |
| --- | --- |
| Before opening the message stream | open `agentCard` (`agent` = slug) on the same exit, parse the card body |
| `capabilities.streaming` | selects `messageStream` vs `messageSend` (1.4) |
| `defaultInputModes` (and skill input modes when the card lists them) | non-text parts whose `mediaType` is not accepted (wildcards honoured) -> reset 415 `unsupported_media`; text is always accepted |
| `defaultOutputModes` | informational, not enforced (text is rendered; others become `CUSTOM`) |
| Card cost | none extra when the exit is a2a(proxy) (store-backed per-agent cache owned there); direct to a2a(client) the card is fetched per run, which is acceptable for v1 and makes a2a(proxy) the recommended exit |
| Card failure | a2a reset on `agentCard` mapped by the 1.3 reset table |

### 1.8 Authorization and identity propagation

| Concern | Design |
| --- | --- |
| Authentication at the edge | agui(server) `options.authorization` resolves the guard; the resulting `authorization` rides every stream (`zilla:authorization`) |
| Passing identity to A2A | the proxy copies `authorization` onto every a2a stream it opens (card, message, cancel) — propagation is the engine's authorization id, not a header copy |
| Credentials on the wire to the agent | a2a(client) `options.authorization` maps the guard to the outbound credential, as today |
| Route-level access | `routes[].guarded` on the proxy restricts the agent to roles; a2a(proxy) / a2a(client) can further restrict by `agent` / `skill` |
| End-user claims in the message | not forwarded (A2A has no end-user identity field in `Message`); lossy by design |
| Challenge details on 401 / 403 | lossy (1.3 reset table) |

### 1.9 v1 scope

| In v1 | Follow-up |
| --- | --- |
| run lifecycle (begin / end / abort / reset), text (streaming and non-streaming), errors, interrupt + resume via `taskId`, cancel, card-based streaming / input-mode checks, `CUSTOM` passthrough of non-text parts, authorization propagation | frontend tools (extension), `STATE_*`, `ACTIVITY_*` (incl. working status messages), `REASONING_*`, `TOOL_CALL_*`, `STEP_*`, threadId -> contextId mapping store, task resubscribe on reconnect |

## 2. Decisions on the nine questions

| # | Decision | Departure from the issue's assumption |
| --- | --- | --- |
| 1 Identity | `threadId` -> `contextId` verbatim (agreed, OPEN-1); new task per run, `runId` / `parentRunId` in `metadata.agui`; continuation only via `resume` with a sealed interrupt id that encodes the `taskId` | `parentRunId` is not a continuation key (the `resume[]` array with `interruptId` is the actual AG-UI continuation signal; the idl already carries it in `run.resume`). The interrupt id is opaque and sealed rather than a raw `taskId` (8.4) |
| 2 Request | trailing user messages after the last assistant / tool message, merged into one `Message`; `system` / `developer` / history dropped; `state` + `context` -> one tagged `data` part; `forwardedProps` -> `Message.metadata`; binary -> `raw`, url -> `url` | "last user message" widened to trailing user messages (several user messages without an assistant reply between them are one turn); `state` / `context` go in a `data` part (agents see them as input) while `forwardedProps` goes to `metadata` (opaque) |
| 3 Response | per 1.3. Lifecycle is begin / end / abort / reset, not data events. `canceled` -> end `CANCELLED` (the idl has it). `input-required` / `auth-required` -> end `INTERRUPT` with a sealed `interrupts[0].id` | `RUN_STARTED` / `STEP_*` are not derived from `working`; `data` / `file` parts -> `CUSTOM` `agent.data` / `agent.file` in v1 (not `STATE_SNAPSHOT`, which needs an A2A convention: tier 2, 8.2); no A2A ids or vocabulary on the client stream (8.0) |
| 4 Frontend tools | dropped in v1; follow-up via a declared A2A extension | as the issue's candidate, but through the existing `extensions` BeginEx field rather than an unmarked `data` part |
| 5 Streaming | card-driven: `messageStream` when the card says streaming, else `messageSend` + synthesized events | the issue says "always `SendStreamingMessage`"; since Q7 reads the card anyway, the proxy follows `capabilities.streaming` and streams whenever the card allows it |
| 6 Cancellation | `tasksCancel` when `taskId` is known and `options.abort: cancel` (default), else abort only. AG-UI has no stop endpoint and treats an abandoned stream as a truncated run, so cancel is this proxy's policy, not a protocol rule (1.6, OPEN-5) | no cancel before the first event, and a2a abort alone is not assumed to cancel |
| 7 Card | yes: read card, choose streaming, refuse unsupported input modes with 415 | recommended exit is a2a(proxy) for its shared cache |
| 8 Authorization | pass the engine `authorization` through; `routes[].guarded` for role gates (now available on agui(server) routes too); no end-user claims in the message; agent-side token rejection is 502, not 401 | `AguiResetEx` cannot carry `WWW-Authenticate` realm / scopes; judged sufficient (candidate (d)) |
| 9 v1 scope | 1.9. One addition over the recommended scope: `CUSTOM` passthrough of non-text parts and the interrupt / resume path | interrupt / resume is included because the stateless `taskId` design makes it cheap and the idl already has the outcome and `resume` scripts |
| Q-route (agent slug) | **route key on the stream** (user decision, revision 2): `AguiBeginEx.agent`; agui(server) `routes[].when.path` -> `with.agent`; agui-a2a `routes[].when.agent` -> optional `with.agent` -> `A2aBeginEx.agent`; agui(client) `routes[].when.agent` -> `with.path` | replaces revision 1's static `options.agent` (dropped). Routing by `forwardedProps` still rejected: client-controlled, forces body parse before route resolve |

### OPEN decisions needing the user's call

| ID | Question | Recommendation |
| --- | --- | --- |
| OPEN-1 | The A2A spec says clients SHOULD NOT send a client-generated `contextId` unless they understand how the server processes it, and a server that cannot accept one MUST reject the request (it may not substitute its own). Default for `options.context` | default `thread` (send `threadId`), opt out per agent with `none`; a rejection surfaces as 422 `invalid_run` through the reset table; verify against the example's reference agent; F3 (store) is the path for agents that generate their own ids |
| ~~OPEN-2~~ | dropped (user decision, revision 4) | no `result` and no `metadata` ids on RUN_FINISHED: A2A-side details do not leak to the AG-UI side (8.0) |
| OPEN-3 | `system` / `developer` messages | recommendation in 8.1: drop by default; forward only as an advisory tagged `data` part when the operator lists it in `options.forward`; lossless only through the tier-2 extension, where the agent chooses what authority to grant. Still the user's call |
| OPEN-4 | agui(client) `with.path`: replace the path of `options.server`, or append to it | replace (an HTTP `:path` of its own, scheme and authority kept from `options.server`), so one client can address `/agent/{agent}/run` style servers and a bare `/agent` server by the same rule; with no routes the `options.server` path is used unchanged |
| OPEN-5 | Cancel on abort: AG-UI cannot tell a Stop button (CopilotKit's stop ends in `agent.abortRun()`, which aborts the fetch) from a closed tab; both are a truncated run, and an A2A task may be meant to outlive its viewer | `options.abort: cancel` default (an abandoned run has no consumer and A2A tasks have no AG-UI resubscribe path), `detach` for agents whose tasks should finish regardless |
| OPEN-6 | `messages` is the complete history by AG-UI rules, but only the new turn is sent; an agent with no context for the thread (new `contextId`, or history edited / regenerated client-side) answers without it | v1 as designed; seeding history is not attempted; F3's store lets the proxy know whether the agent holds a thread's context |
| OPEN-7 | Interrupt ids: raw `taskId` or sealed | sealed, with `options.seal` required (fails fast at config load): the AG-UI side must not see A2A ids, and sealing binds the id to the caller's identity and an expiry independent of the agent's own task scoping (8.4) |
| OPEN-8 | Naming for the tier-2 extension: URI domain and the metadata key (`agui` short key vs URI key) | key `agui` in both tiers so tier 2 is a superset with no rework; URI under an Aklivity-owned namespace (the `a2a-protocol.org/extensions/` prefix is reserved for the A2A organisation); both are cheap to settle now and costly after agents ship |
| OPEN-9 | `options.context: scoped` (`<identity>/<threadId>` as the A2A contextId) in v1 | include it in v1, default stays `thread`: the A2A spec scopes task operations to the caller but does not scope `contextId` (8.4), so an agent that keys state by `contextId` alone lets a caller who knows another user's `threadId` join that context |


## 3. Config sketch and routing

```yaml
name: example
guards:
  jwt0:
    type: jwt
    options:
      issuer: https://auth.example.internal
      audience: agents
bindings:
  tcp_server0:
    type: tcp
    kind: server
    options:
      host: 0.0.0.0
      port: 8080
    exit: http_server0
  http_server0:
    type: http
    kind: server
    exit: agui_server0
  agui_server0:
    type: agui
    kind: server
    options:
      authorization:
        jwt0:
          credentials: Bearer {credentials}
    routes:
      - when:
          - path: /agent/{agent}/run          # HTTP-scoped
        with:
          agent: ${params.agent}              # AGUI-scoped -> AguiBeginEx.agent
        exit: agui_a2a0
  agui_a2a0:
    type: agui-a2a
    kind: proxy
    options:
      forward: [ state, context ]
      context: thread                         # send threadId as the A2A contextId (default)
      seal:                                   # seals interrupt ids to the caller (8.4)
        vault: secrets0
        key: agui-a2a
        ttl: PT15M
      abort: cancel                           # tasksCancel when the client aborts (default)
    routes:
      - when:
          - agent: weather                    # AGUI-scoped
        with:
          identity: ${guarded['jwt0'].identity}  # binds sealed ids (8.4); also the scope of context: scoped
        guarded:
          jwt0: [ agents:weather ]
        exit: a2a_proxy0                      # A2aBeginEx.agent = weather (pass-through)
      - when:
          - agent: travel
        with:
          agent: travel-v2                    # A2A-scoped rewrite of the slug
        guarded:
          jwt0: [ agents:travel ]
        exit: a2a_proxy0
  a2a_proxy0:
    type: a2a
    kind: proxy
    options:
      cache:
        store: memory0
    routes:
      - when:
          - agent: weather
        exit: a2a_client0
      - when:
          - agent: travel-v2
        exit: a2a_client0
  a2a_client0:
    type: a2a
    kind: client
    options:
      server: https://agents.example.internal/a2a
    exit: tcp_client0
```

Inverted direction, agui(client) addressing a remote AG-UI server:

```yaml
  agui_client0:
    type: agui
    kind: client
    options:
      server: https://agents.example.internal
    routes:
      - when:
          - agent: "{agent}"                  # AGUI-scoped, template
        with:
          path: /agent/${params.agent}/run    # HTTP-scoped, resolved against options.server
        exit: tcp_client0
```

### Route semantics per binding

| Binding | `when` | `with` | Also | No routes |
| --- | --- | --- | --- | --- |
| agui(server) | `path`: template with `{name}` params and `*` (HTTP-scoped; matched against the request `:path`, after the existing POST / content-type / accept checks) | `agent`: static slug or `${params.name}` (AGUI-scoped) | `guarded`, `exit` per route; top-level `exit` still allowed | single `exit`, resolved by authorization as today, `agent` null, no existing script changes. Path matches nothing -> 404; path matches but no authorized route -> 403 (both through `AguiRejectHandler`, next to the 405 / 415 / 406 rejects) |
| agui(client) | `agent`: template with `{name}` params (AGUI-scoped) | `path`: HTTP-scoped, `${params.name}`, replaces the path of `options.server` (OPEN-4) | `guarded`, `exit` per route | `options.server` path unchanged, `agent` ignored |
| agui-a2a(proxy) | `agent`: exact slug or template (AGUI-scoped); omitted = catch-all | optional `agent`: static A2A slug, default pass-through of `AguiBeginEx.agent` | `guarded`, `exit` per route; top-level `exit` allowed | single `exit`, slug passed through (null stays null, the tenant-less A2A endpoint) |

Mirrored implementations:
- agui(server) and agui-a2a: `http-kafka`, whose route is `when {method, path-template}` -> `with {…, key: ${params.id}}`: `HttpKafkaConditionMatcher` (path template to named regex groups, `parameter(name)`) and `HttpKafkaWithResolver` (`${params.name}` substitution), with `guarded` / `exit` at route level (see `proxy.put.item.yaml`).
- agui(client): `a2a-http`, whose route is `when {skill}` (protocol-scoped) -> `with {headers[":path"]: ".../${args.x}"}` (HTTP-scoped), reusing the http-kafka `${params.name}` substitution for uniformity with the server side.

Schema (I1 / A2 / A4 / I4 descriptions):
- `type: agui`, `kind: server`: `routes` becomes allowed (remove `"routes": false`); `routes[].when[]` `{path: string}`, `routes[].with` `{agent: string}`, `guarded`, `exit` required per route; `exit` or `routes`.
- `type: agui`, `kind: client`: same, `when[]` `{agent: string}`, `with` `{path: string}`; `required` becomes `options` plus `exit` or `routes`.
- `type: agui-a2a`, `kind: proxy`: `options.forward` (array of `state`, `context`, `forwardedProps`, `system`, `developer`, `tools`; default `state`, `context`), `options.context` (`thread` default, `scoped`, `none`), `options.abort` (`cancel` default, `detach`), `options.extension` (`auto` default, `none`), `options.seal` (`vault`, `key`, `ttl`; required); `routes[].when[]` `{agent: string}`, `with` `{agent: string, identity: string}`, `guarded`, `exit`; no `options.agent`; `vault`, `catalog`, `entry` false.

## 4. Spec changes

The bar is "does the spec support agui-a2a sufficiently well". PR attribution: `agui.idl`, `AguiFunctions` and the agui schema patch / fixtures / scripts live in the agui(server) stack (#2821 / PR #2850; `agui.idl` was introduced there, commit baf64421); the client-only schema, scripts and chain scenarios live in #2856 / PR #2857; `a2a.idl`, `A2aFunctions` and the a2a scripts live in the a2a spec PR at the base of #2825-#2835 (the exact number of that PR and of the a2a(server) / a2a(client) PRs is for the coordinator to confirm; the issues are #2814 stream model, #2816 server, #2817 client).

### 4.1 Adopted

| # | Change | Exact change | Affects | Lands in |
| --- | --- | --- | --- | --- |
| S1 | route key on the stream (candidate (b)) | `agui.idl`: `struct AguiBeginEx { threadId; runId; parentRunId = null; string16 agent = null; }`, appended **last**: the required ids keep their offsets and the new field has the same shape as the `agent = null` on every `A2aBeginEx`. Reply begin does not need it (agui(server) only reads the reply ids); producers leave it null | `AguiFunctions`: `AguiBeginExBuilder.agent(String)` after `parentRunId` (callers skipping `parentRunId` rely on the same default-filling as `a2a:beginEx().agentCard()` without `agent`; covered by a new test), `AguiBeginExMatcherBuilder.agent(String)` + `matchAgent` (null = unconstrained). `AguiFunctionsTest`: add `shouldGenerateBeginExWithAgent`, `...WithAgentWithoutParentRunId`, `shouldMatchBeginExWithAgent`. **Existing `.rpt` scripts need no edit**: builders default `agent` to null, matchers ignore an unset member, and `AguiFunctionsTest` asserts fields, not byte layouts | PR #2850 |
| S2 | agui(server) routes | schema patch drops `"routes": false` for `kind: server` and adds `routes[].when.path` / `with.agent` / `guarded` / `exit`. Fixture `config/server.routes.invalid.yaml` (asserts routes are rejected) flips to a valid `server.routes.yaml`; add invalid fixtures for unknown `when` / `with` members and a route without `exit`. New scripts: network `request.path.not.found` (404), `request.path.forbidden` (403), and paired network + application `run.routed` (`/agent/weather/run` -> application BeginEx `.agent("weather")`) | `SchemaTest`, `NetworkIT` / `ApplicationIT` of the server | PR #2850 |
| S3 | agui(client) routes | same for `kind: client`: `config/client.routes.invalid.yaml` flips to valid `client.routes.yaml`; `required` relaxes to `exit` or `routes`; new paired scenario `run.routed` (application BeginEx `.agent("weather")` -> network request path `/agent/weather/run`) and a `chain` scenario `run.routed` (server routes by path, client routes by agent) | `SchemaTest`, client ITs, chain ITs | PR #2857 |
| S4 | a2a non-happy task states (flagged in revision 1) | scripts only: `stream.message.failed`, `.rejected`, `.input.required`, `.auth.required`, `.canceled` and `send.message.task.failed` / `.input.required`, asserting the `A2aStatusUpdateDataEx` / `A2aTaskDataEx` `state` tag for each `TASK_STATE_*` string, including the terminal `statusUpdate` followed by end. They belong with the a2a spec (the state-string to `A2aTaskState` tag is an a2a(server) / a2a(client) contract that is independent of any proxy) and are driven through the a2a(server) / a2a(client) ITs; agui-a2a adds only its own mapping scripts on top | a2a spec PR + a2a(server) / a2a(client) PRs (#2816 / #2817) | a2a PRs |
| S5 | client abort is detach (flagged in revision 1) | script `stream.message.client.aborted` (client aborts the application stream; assert the HTTP request is torn down and **no** `tasksCancel` is issued), making "abort = detach, cancel is explicit" an a2a contract rather than an assumption. No idl change | a2a spec PR + a2a(client) PR | a2a PRs |

### 4.2 Candidates re-evaluated on whether they make agui-a2a work well

| Candidate | What the mapping loses or does awkwardly without it | Exact idl change | Scripts / functions affected | Lands in | Recommendation |
| --- | --- | --- | --- | --- | --- |
| (a) `append` / `lastChunk` (and parts) in `A2aArtifactUpdateDataEx` | Only replace detection: `append: false` on an already streamed `artifactId` (a rare, already-lossy path). Text framing does not need it: first sight of an `artifactId` -> init, later events -> continuation, the empty fin frame closes on `lastChunk` or terminal state. More importantly the members are not guaranteed to precede `parts` in the JSON, so populating DataEx would force **a2a(server) / a2a(client) to buffer the whole event** to read them: it moves the buffering from the consumer to the producer for no net gain, and defeats incremental handling of large artifacts (`stream.message.100k`) | `A2aArtifactUpdateDataEx { …; uint8 append = 1; uint8 lastChunk = 0; }` (tri-state would be needed to say "unknown") | `A2aFunctions` artifactUpdate builder / matcher; every script that writes `artifactUpdate` with `append` in the body would need the ex kept consistent | a2a spec PR, plus the a2a(server) / a2a(client) PRs that populate it | **No.** Revisit only if a consumer needs the flags without reading the body |
| (b) route key in `AguiBeginEx` | A single endpoint cannot front more than one agent; the slug would have to be baked into one binding per agent (revision 1) | see S1 | see S1 / S2 / S3 | PR #2850 (idl, server), PR #2857 (client) | **Yes (adopted, user decision)** |
| (c) `taskId` in `A2aMessageStreamBeginEx` / `A2aMessageSendBeginEx` | Nothing: continuation `taskId` is a member of the message JSON the proxy builds, and `tasksCancel` already carries `taskId` in its BeginEx. It would only let an a2a proxy route or pin affinity on a task without reading the body, and no route key uses it today | `string16 taskId = null` (and `contextId = null`) on both structs | `A2aFunctions` message-send / stream builders and matchers; a2a(server) would have to lift it from the JSON | a2a spec PR and a2a(server) / a2a(client) PRs | **No** |
| (d) `realm` / `scope` in `AguiResetEx` | A reset relayed as 401 / 403 carries no `WWW-Authenticate`. But agui(server) is the authenticator and already emits its own challenge for its own guard failures; the only downstream-origin cases are an agent rejecting the proxy's upstream credential (not the caller's to fix: 502) and agent-level `insufficient_scope` (403 is enough; the `scope` hint has no client that needs it) | `AguiResetEx { error; string16 realm = null; string16 scope = null; }`, agui(server) emitting the challenge | `AguiFunctions` reset builder / matcher, `run.unauthorized` | PR #2850 | **No**; mapping changed instead (1.3: INVALID_TOKEN -> 502). Adopt later if a downstream challenge must be relayed |

### 4.3 Other gaps scanned

| Gap | Assessment |
| --- | --- |
| a2a `FAILED` / `REJECTED` / `INPUT_REQUIRED` / `AUTH_REQUIRED` / `CANCELED` unscripted | S4 (a2a PRs); agui-a2a mapping scripts in I2 / I3 |
| cancel-on-abort unscripted | S5 (a2a PRs); agui-a2a cancel scripts in I8 |
| agui(server) behaviour for an unmatched path or an unauthorized matched route | S2 defines 404 / 403 |
| agui(server) app reset to HTTP status carries no challenge | accepted, see (d) |
| reply `AguiBeginEx` need not echo `agent` | recorded in S1; no script change |
| agui `detail` / outcomes (`INTERRUPT`, `CANCELLED`), `AguiAbortEx.error` (`status`, `code`, `message`) | sufficient for every row of 1.3 |
| `A2aAbortEx.reason`, `A2aEndEx.outcome`, `A2aErrorResetEx.code`, `A2aBearerResetEx.error` | sufficient for the reset / abort rows of 1.3 |
| input content-part schema and A2A v1.0 part shapes written from memory | verified in revision 3 (section 7) |

## 5. Drafted follow-up issues (stack order)

Parent: aklivity/zilla-platform#1312. Each is justified by its own binding or contract, with no downstream consumer named
(`agui`, `a2a` are shared components). Repo rules apply: test-first, Aklivity Community License files and `mvnw` /
`mvnw.cmd` per new project, `./mvnw license:format`, `./mvnw notice:generate --projects ...`, `module-info`,
`incubator/pom.xml` modules + dependencyManagement. Items A1-A7 amend existing issues / PRs (commits land in those PRs);
the rest are new.

| # | Title | Amends / Depends | Body (summary) | Acceptance criteria |
| --- | --- | --- | --- | --- |
| A1 | `binding-agui.spec: add agent to AguiBeginEx` | amends #2821 / PR #2850; none | An AG-UI endpoint serves many agents, addressed by URL; the `agui` stream carries only run ids, so a binding downstream of agui(server) cannot tell which agent a run addresses. Add `agent = null` (S1), the builder / matcher members and tests | idl regenerates; `AguiFunctionsTest` new cases pass; the whole existing `.rpt` suite passes unedited |
| A2 | `binding-agui.spec: routes on the agui server (when.path, with.agent)` | amends #2821 / PR #2850; A1 | S2: schema, fixtures, network + application scripts | `SchemaTest` incl. flipped fixture; `request.path.not.found`, `request.path.forbidden`, `run.routed` scenarios paired and green |
| A3 | `binding-agui: resolve agui server routes by path` | amends #2821 / PR #2850; A2 | Runtime: resolve after the method / content-type / accept checks with `route.resolve(authorization, path)`, path-template matcher and `${params.x}` with-resolver mirroring http-kafka, write `agent` in the app BeginEx, 404 / 403 via `AguiRejectHandler`. Test-first: A2 scenarios fail first | A2 scenarios pass in `AguiServerIT`; no-routes behaviour unchanged |
| A4 | `binding-agui.spec: routes on the agui client (when.agent, with.path)` | amends #2856 / PR #2857; A1 | S3: schema, fixtures, `run.routed` and chain scenarios | `SchemaTest`; `run.routed` and chain `run.routed` green |
| A5 | `binding-agui: resolve agui client routes by agent` | amends #2856 / PR #2857; A4 | Runtime: read `AguiBeginEx.agent`, template match, resolve `with.path` against `options.server` (OPEN-4), mirroring `a2a-http` route shape | A4 scenarios pass in `AguiClientIT`; no-routes behaviour unchanged |
| A6 | `binding-a2a.spec: task state scenarios for message send and stream` | amends #2814 family (a2a spec PR, #2816 / #2817 PRs); none | S4 | each state scenario paired and green in the a2a(server) and a2a(client) ITs |
| A7 | `binding-a2a.spec: a client abort detaches without cancelling` | amends #2814 family (a2a spec PR, #2817 PR); none | S5 | `stream.message.client.aborted` green; a2a(client) issues no `tasksCancel` |
| A8 | `binding-a2a.spec: reply begin reports the activated extensions` | amends #2814 family (a2a spec PR, #2816 / #2817 PRs); none | S6 (8.5): the reply begin carries the `A2A-Extensions` activation the server echoed, through the existing `extensions` member; scripts only plus the a2a(client) / a2a(server) behaviour. Optional, recommended now | `send.message.extensions` and `stream.message.extensions` gain a matched reply begin; green in the a2a(server) and a2a(client) ITs |
| I1 | `binding-agui-a2a.spec: scaffold spec module and config schema` | A1 | Module `incubator/binding-agui-a2a.spec` (mirror `binding-a2a-http.spec`: no idl, uses `agui` and `a2a`); schema patch for `type: agui-a2a`, `kind: proxy`: `options.forward` (subset of state, context, forwardedProps, system, developer, tools), `options.context` (thread, scoped, none), `options.abort` (cancel, detach), `options.extension` (auto, none), `options.seal` (`vault`, `key`, `ttl`; required), `routes[].when.agent`, `routes[].with.agent`, `routes[].with.identity`, `guarded`, `exit` (section 3, 8.6); no `options.agent` | valid / invalid fixtures (`when` unknown member, `with` unknown member, route without `exit`, `forward` unknown value, `seal` missing, `kind` invalid, unknown option); `SchemaTest` green; `NOTICE` generated; module in `incubator/pom.xml` |
| I2 | `binding-agui-a2a.spec: run lifecycle, text, error and routing scenarios` | I1, A6 | Paired `.rpt` scenarios under `streams/application/{agui,a2a}`: slug pass-through and `with.agent` rewrite onto `A2aBeginEx.agent`, null slug, text chunks, append / replace, multi-artifact, `message` reply, non-streaming `messageSend`, `COMPLETED` / `FAILED` / `REJECTED` / `CANCELED`, a2a abort / `A2aErrorResetEx` / bearer reset (502 / 403 per 1.3) -> agui reset / abort | every row of 1.3 and its reset table covered; ITs per side (mirroring `A2aServerIT` / `HttpClientIT`) green |
| I3 | `binding-agui-a2a.spec: request mapping, interrupt / resume, cancel and card scenarios` | I2, A7 | 1.2 (trailing user messages, binary / url parts, state + context data part, forwardedProps metadata, empty -> 422), `resume` -> `taskId`, `INPUT_REQUIRED` / `AUTH_REQUIRED` -> `INTERRUPT`, cancel with and without `taskId`, card streaming switch, 415, card failure | rows of 1.1, 1.2, 1.6, 1.7 each covered; request bodies checked verbatim |
| I4 | `binding-agui-a2a: scaffold runtime module and config adapter` | I1 | Module `incubator/binding-agui-a2a` + `.conf` (failing ITs first), factory SPI, config adapter for all `options` of I1 and the routes, `module-info`, the `BindingEvent` catalog (8.0: run routed / finished with `agent`, `threadId`, `runId`, `taskId`, `contextId`, outcome) | config tests; ITs fail for the right reason before I5; `incubator/pom.xml` modules + dependencyManagement |
| I5 | `binding-agui-a2a: map RunAgentInput to an A2A message` | I3, I4, I11 | Decode slot buffers the request, encode slot builds the v1.0 message JSON and `contentLength`, opens `messageStream` / `messageSend` with the routed slug; `options.context` (`thread` / `scoped` / `none`); resume -> unsealed `taskId`; parse and ignore `tools` and trailing `tool` messages (8.3); mirror `AguiClientFactory`'s run input encoder; decoder states, no phase booleans | 1.1 / 1.2 scenarios pass; oversize request rejected as agui(server) does; slots released on every error path |
| I11 | `binding-agui-a2a: seal interrupt ids to the caller` | I4 | `options.seal`: wrap / unwrap of `{v, kind, agent, taskId, callId?, identity?, expiry, nonce}` through the vault `SecretKeyManager`, mirroring `A2aPushSealer` (a copy, not a cross-module dependency); `with.identity` from `${guarded['x'].identity}` as in http-kafka `overrides`; expiry feeds `Interrupt.expiresAt`; an unseal failure (tampered, expired, other identity, other agent) makes the entry unrecognised | scenarios: resume with a valid id, expired id, another caller's id, tampered id, id from another agent route; each rejected as unrecognised and answered 422 when nothing else is sendable |
| I6 | `binding-agui-a2a: map A2A events to AG-UI frames` | I5 | Incremental event decoder (mirror `AguiEventDecoder`), `chunks` text framing, `CUSTOM` `agent.data` / `agent.file`, neutral `agent_*` error codes and `reason` strings, outcome / abort / reset mapping, sealed interrupt ids with `expiresAt`, flow control honoured both ways; no A2A ids or vocabulary on the client stream | 1.3 scenarios incl. `*.100k` pass; a scenario asserts no `taskId` / `contextId` / `a2a` string appears on the agui side |
| I7 | `binding-agui-a2a: negotiate with the agent card and fall back to non-streaming` | I6 | `agentCard` pre-step with the routed slug, streaming switch, input-mode check, synthesized events | 1.4 and 1.7 scenarios pass |
| I8 | `binding-agui-a2a: cancel the A2A task on run abort` | I6 | `tasksCancel` (routed slug + `taskId`) on abort when `options.abort: cancel` (default) and on `resume.cancelled`; detach otherwise; a `TaskNotCancelable` / not-found answer is ignored (A2A cancel is idempotent) | 1.6 scenarios pass |
| I9 | `build(docker-image): add binding-agui-a2a to the image and the inspect.schema golden` | I7, I8 | **One commit series**: `cloud/docker-image/pom.xml`, `cloud/docker-image/src/main/docker/zpm.json.template`, `incubator/pom.xml` dependencyManagement, `inspect.schema/.github/schema.expected.json` golden (golden must not lead the image, see 53290f62), `NOTICE` regenerated. The golden also carries the agui `routes` schema from A2 / A4 | `./mvnw verify` on `cloud/docker-image`; inspect.schema example green |
| I10 | `example: AG-UI frontend to an A2A agent through agui-a2a` | I9 | `zilla-examples` entry (section 3 sketch) with `.github/test.sh`; also the OPEN-1 check against the reference agent | example test green; lossiness table documented |
| F1 | `binding-agui-a2a: map frontend tools through the AG-UI extension` | E1, I10 | Tier 2 only. `tools` declared in `Message.metadata.agui.tools`; a client-executed call arrives as a tagged part, becomes `TOOL_CALL_*` with a sealed `toolCallId`, and the run ends `SUCCESS` with `pendingToolCallIds`; the next run's trailing `tool` messages are unsealed to one `taskId` and sent as tagged `toolResult` parts (8.3) | scenarios: one call, several calls, call plus interrupt, tampered / foreign `toolCallId`, tier-1 agent ignores tools; no `a2a.idl` or `agui.idl` change |
| F2 | `binding-agui-a2a: state, activity, reasoning, step, subagent and tool-call events` | E1, I10 | Tier 2 only. Tagged `data` / `text` parts and status-message parts -> `STATE_*`, `MESSAGES_SNAPSHOT`, `ACTIVITY_*`, `REASONING_*`, `STEP_*`, `SUBAGENT_*`, agent-executed `TOOL_CALL_*` + `TOOL_CALL_RESULT`, richer `INTERRUPT` (`reason`, `responseSchema`, `toolCallId`); unknown tags dropped with a Zilla warning event | one scenario per event family and per direction |
| F3 | `binding-agui-a2a: store-backed threadId to contextId mapping` | OPEN-1 | `options.context: store` (additive enum value) with an engine `store`, for agents that generate their own `contextId` and so reject `threadId`; also records the latest `taskId` per thread (so a thread can be matched to its task without a sealed id) and tells the proxy whether an agent holds a thread's context (OPEN-6) | continuation across runs without a client-chosen id; entries scoped to the caller's identity |
| E1 | `binding-agui-a2a: specify the AG-UI over A2A extension` | I10 | A document (`incubator/binding-agui-a2a/EXTENSION.md`) defining the URI, `params`, the `Message.metadata.agui` carrier and the part tags of 8.2, with a versioning rule; no code | reviewed against the A2A extension rules (no new fields or enum values, metadata only); the tag table is covered one-to-one by F1 / F2 scenarios |
| F4 | `binding-agui-a2a: working status messages as activity` | I10 | Tier 1. `statusUpdate` `WORKING` with a text message -> `ACTIVITY_SNAPSHOT` (`activityType` `status`, `replace` true, stable `messageId` per run); opt-in `options.status` (`drop` default, `activity`) | scenario with several working updates replacing one activity; text transcript untouched |
| F5 | `binding-agui-a2a: subagent events` | F2 | Tier 2 only: tagged `subagent` events -> `SUBAGENT_*` with `subagentRunId` attribution on every event of the subagent | scenario per outcome (success, suspended, error) |
| F6 | `binding-agui-a2a: reconnect to a running task` | I10 | Only if an agui connect operation is ever specified: `tasksList` (`contextId`) to find the open task, then `tasksSubscribe` (`taskId`), replay as `MESSAGES_SNAPSHOT` / `STATE_SNAPSHOT`; the a2a stream model already has both operations | n/a until AG-UI defines the operation (8.3) |

## 6. Items to report (not worked around)

| Item | Detail |
| --- | --- |
| Untested a2a states | covered by S4 / A6; the a2a(server) / a2a(client) state-string handling is unproven until then |
| a2a abort vs cancel | covered by S5 / A7 |
| Issue text vs idl | `RUN_STARTED` / `RUN_FINISHED` / `RUN_ERROR` are not data events on the `agui` stream (section 0) |
| agui(server) app reset without challenge | a relayed 401 has no `WWW-Authenticate`; sidestepped by mapping INVALID_TOKEN to 502 (1.3, candidate (d)) |
| Unrouted agui(server) request today | with a guard-resolved route missing, `newStream` stays null; S2 / A3 define 404 / 403 once path routes exist |
| AG-UI content parts, A2A v1.0 shapes | verified in revision 3 (section 7); corrections are in 1.1 / 1.2 / 1.3 |

## 7. Sources and verification status (revision 3)

All verified from shallow clones of the repositories; paths are repo-relative. Pins: ag-ui-protocol/ag-ui `7e7c1109f26e` (2026-10-28), CopilotKit/CopilotKit `621f0ec65453` (2026-10-29), a2aproject/A2A `d924e07b0c9d` (2026-10-29). `docs.ag-ui.com`, `docs.copilotkit.ai` and `a2a-protocol.org` are not reachable from this session; the repositories are their sources. AG-UI spec `1.0` and `draft` differ only in link prefixes for the pages used.

| Claim | Source | Result |
| --- | --- | --- |
| `RunAgentInput` fields: `threadId`, `runId`, `parentRunId`, `protocolVersion`, `state`, `messages`, `tools`, `context`, `forwardedProps`, `resume`; closed object; `state` absent = `{}`; `forwardedProps` any JSON but null, intermediaries MUST NOT alter | ag-ui `docs/spec/1.0/schema.mdx#runagentinput`, `docs/spec/1.0/basic/run-input.mdx` | verified |
| `messages` is the complete history a producer is shown | `run-input.mdx` (`## messages`) | **refines** the request mapping: this proxy sends only the new turn (OPEN-6) |
| Message roles `developer`, `system`, `assistant`, `user`, `tool`, `activity`, `reasoning`; activity messages never travel back | `schema.mdx#role`, `run-input.mdx` | verified; `activity` / `reasoning` added to the ignored roles |
| Input content parts | `schema.mdx#contentpart`: `text`, `image`, `audio`, `video`, `document`; `source` is `data {value base64, mimeType}`, `url {value, mimeType?}` or `file {value, provider?, mimeType?}`; no `binary` type, no filename | **corrected** (1.2): revision 1 assumed a `binary` type and a filename |
| `RUN_FINISHED`: `result` (run's return value), `outcome` `success` / `interrupt` / `cancelled`, `usage`; `RUN_ERROR`: `message` required, `code` optional; a stream starts with `RUN_STARTED` or `RUN_ERROR`; input rejected before the run is an HTTP error with no stream | `schema.mdx#runfinishedevent`, `#runerrorevent`; `docs/spec/1.0/events/lifecycle.mdx`; `basic/transports/http-sse.mdx` | verified; **corrected** `result` use (no A2A ids on RUN_FINISHED at all, OPEN-2 dropped, 8.0) and `message` is always filled |
| `Interrupt` (`id`, `reason` required, `message`, `toolCallId`, `responseSchema`, `expiresAt`); `ResumeEntry` (`interruptId`, `status` `resolved` / `cancelled`, `payload`, `metadata`); resume list must cover every interrupt; unrecognised entries are ignored with a warning; producers need not keep state across the gap | `schema.mdx#interrupt`, `#resumeentry`; `docs/spec/1.0/basic/patterns/interrupt-resume.mdx` | verified; matches `run.resume` / `run.outcome.interrupt`; small additions (absent payload, unknown ids) in 1.1 |
| Cancel: no stop in the protocol; an abandoned stream is a truncated run, not a cancelled one; `cancelled` is the producer's report | `lifecycle.mdx` (`### Cancelled runs`), `basic/transports/index.mdx#truncation`, `http-sse.mdx` (`## No resumption`) | verified; makes cancel-on-abort a policy (`options.abort`, OPEN-5) |
| Frontend tool call stops a run as `success` with `pendingToolCallIds`; answered via `tool` messages in the next input | `lifecycle.mdx`, `schema.mdx#runfinishedsuccessoutcome` | verified; recorded for F1 (1.5) |
| `HttpAgent`: `POST`, `Accept: text/event-stream`, URL is whatever is configured; `abortRun()` aborts the fetch via `AbortController` | ag-ui `sdks/typescript/packages/client/src/agent/http.ts` (lines 45-75) | verified |
| CopilotKit runtime routes `agent/:agentId/run`, `/connect`, `/suggest`, `/stop/:threadId`, `/info`, matched by path suffix; a single-route mode takes `POST` with a `{method, params, body}` envelope | CopilotKit `packages/runtime/src/v2/runtime/core/fetch-router.ts`, `core/fetch-handler.ts` (`mode: "single-route"`), `endpoints/express-single.ts` | verified; Zilla's `/agent/{agent}/run` path template matches the multi-route shape, the single-route envelope would need body routing and is out of scope |
| CopilotKit stop: `runner.stop` -> `agent.abortRun()`; optional `{runId}` body (default thread-wide); reports `RUN_ERROR` code `STOPPED` to the browser | CopilotKit `handlers/handle-stop.ts`, `runner/in-memory.ts` (`agent.abortRun()`) | verified; issue numbers #6978 / #7149 from revision 2 not re-checked (the `{runId}` body they asked for is present in this commit) |
| Agent ids do not map to upstream URLs: the runtime holds `agents: {id: new HttpAgent({url})}` and the browser addresses the runtime, not the agent | CopilotKit `packages/core/src/agent.ts:518` (browser -> runtime stop path), `examples/v2/docs/reference/copilotkit-provider.mdx` (`new HttpAgent({url})`) | verified; the route-key decision stands (a path-addressed Zilla endpoint works with any configured `HttpAgent` URL) |
| A2A `Part` oneof `text` / `raw` / `url` / `data` with `metadata`, `filename`, `media_type`; `Role` `ROLE_USER` / `ROLE_AGENT`; `Message` ids; `TaskState` eight values, `REJECTED` terminal, `INPUT_REQUIRED` / `AUTH_REQUIRED` interrupted | a2a `specification/a2a.proto` (`Part`, `Role`, `Message`, `TaskState`) | verified; JSON names (`messageId`, `mediaType`, `TASK_STATE_*`) match the repo's scripts |
| `TaskStatusUpdateEvent` has no `final`; `TaskArtifactUpdateEvent` has `append`, `last_chunk`; `StreamResponse` oneof `task` / `message` / `status_update` / `artifact_update`; `SendMessageResponse` oneof `task` / `message` | `a2a.proto` | verified |
| `SendMessageConfiguration`: `accepted_output_modes`, `history_length` (0 = none), `return_immediately` (default false = wait for terminal or interrupted) | `a2a.proto`, `docs/specification.md` (line 446) | verified |
| `contextId`: agents MAY accept client-provided ids; one that cannot MUST reject and MUST NOT generate a new id; clients SHOULD NOT provide one unless they understand the server; taskId-only message -> server infers `contextId`; mismatching pair rejected | `docs/specification.md` lines 586-628 | **corrected** (1.1, OPEN-1): no CUSTOM mismatch path; resume sends `taskId` only; `options.context` |
| Streaming: message-only stream is exactly one `Message`; task stream begins with `Task`; closes at terminal states (one place) or terminal / interrupted (another); a server MAY resend a final `Task` snapshot before closing | `docs/specification.md` lines 208-210, 3002 | **corrected** (1.3): final snapshot must not duplicate streamed artifacts; interrupted states end the run regardless of stream close |
| `CancelTask` is idempotent, `TaskNotCancelableError` on a terminal task, duplicate may be `TaskNotFoundError` | `docs/specification.md` lines 264-283, 496, 556 | verified; abort-path ignores the answer |
| Card: `capabilities.streaming` is an optional bool, `defaultInputModes` / `defaultOutputModes` required, skills carry `input_modes` / `output_modes`, `AgentInterface.tenant` and `protocol_version` | `a2a.proto` (`AgentCard`, `AgentCapabilities`, `AgentSkill`, `AgentInterface`) | verified; absent `streaming` is treated as false |

### Corrections to revision 2 claims

| Was | Is |
| --- | --- |
| `binary` input part type with `filename` | `text` / `image` / `audio` / `video` / `document` with `data` / `url` / `file` sources; no filename; `file` handles dropped |
| RUN_FINISHED `result` carries `{a2a:{taskId, contextId}}` | `metadata` carried it in revision 3; dropped entirely in revision 4 (8.0): no A2A ids on the client stream |
| a different response `contextId` is surfaced as `CUSTOM a2a.context` | protocol violation (a conforming server keeps or rejects); abort `agent_protocol` |
| resume message carries `taskId` and `contextId` | `taskId` only |
| `task` with artifacts always emits text | emits only unseen `artifactId`s |
| cancel on abort is the mapping | cancel on abort is a policy (`options.abort`), default `cancel` |
| `RunAgentInput.messages` treated as agent-owned history | AG-UI says it is the complete history; sending only the new turn is a documented lossiness (OPEN-6) |
| CopilotKit issue numbers #6978 / #7149 and PRs from search summaries | the `{runId}` stop body exists in `handle-stop.ts` at the pinned commit; issue / PR states not re-checked |

No correction changes the route key, the spec-change list S1-S5, or the candidate (a)-(d) recommendations.

## 8. Full design, including deferred parts (revision 4)

The point of this section is that nothing deferred is allowed to force rework of v1. Each deferred item gets its target
design and the constraints it puts on v1. Decisions made by the user in this round: OPEN-1 agreed (stateless, `threadId`
verbatim, F3 conditional); OPEN-2 dropped; OPEN-4 replace; OPEN-3 stays open until 8.1.

### 8.0 Principle: no A2A-side details on the AG-UI side

Debug detail (task ids, context ids, task states) belongs in Zilla events (`BindingEvent`), not on the client stream.
Every place reviewed:

| Place | Revision 3 | Now |
| --- | --- | --- |
| `RUN_FINISHED` `result` / `metadata` carrying `taskId` / `contextId` | `metadata` | none (`COMPLETED` ends `SUCCESS` with no detail) |
| `CUSTOM` `a2a.context` on a `contextId` mismatch | removed in revision 3 | stays removed; a mismatch aborts with `agent_protocol`, details to a Zilla event |
| `CUSTOM` names for non-text parts | `a2a.data`, `a2a.file` | `agent.data` (value = the data JSON), `agent.file` (value = `{mimeType, name?, url or data}`: AG-UI flavoured field names, no `mediaType` / `filename` / `raw`) |
| Error codes | `task_failed`, `task_rejected` | `agent_failed`, `agent_rejected`; the others were already `agent_*` |
| Interrupt `reason` | `input_required`, `auth_required` (A2A state names) | `input`, `authorization` (open string, neutral) |
| Interrupt id | raw `taskId` | sealed opaque token (8.4), with `expiresAt` |
| Tool call ids (tier 2) | n/a | sealed (8.3, 8.4) |
| Error `message` text | agent's `status.message` | unchanged: it is the agent's message to the user, content rather than protocol vocabulary |

Zilla event catalog (`BindingEvent`, I4 / I6): run routed (`agent`, `threadId`, `runId`), run finished (outcome, `taskId`,
`contextId`, a2a state), run failed (code, message), seal rejected (reason), unknown extension tag dropped (tag). These are
the only places A2A ids appear. The reverse direction is different by design: AG-UI details sent to the agent
(`runId`, `parentRunId`, `forwardedProps`) are data the client supplied, carried in `Message.metadata.agui`.

### 8.1 `system` / `developer` messages (OPEN-3)

Who supplies them: AG-UI describes `developer` as instructions from the application developer, but they travel in the
`RunAgentInput` the consumer assembles, which for a browser consumer is under the control of whoever holds the browser.
The A2A agent owns its prompt. The proxy sits on the boundary, so forwarding client-supplied instructions means granting
the client some authority over the agent. The options:

| Option | Mapping | Agent's authority model | Trust implication | Verdict |
| --- | --- | --- | --- | --- |
| (a) drop | not forwarded | agent-owned prompt only | none; lossy | **default (v1)** |
| (b) tagged `data` part | one `data` part tagged `metadata.agui.part = "instructions"` with `[{role, content}]`, alongside state / context, enabled by listing `system` / `developer` in `options.forward` | agent-owned; the part is advisory input, same standing as user text | no more power than the user message the same client already controls, provided the agent treats it as input; agents that ignore `data` parts see nothing | v1 opt-in, off by default |
| (c) text-part prefix (`[system] ...`) | prepended to the user text | none distinguishable: the agent cannot tell it from user-typed text, and a user can type the same prefix | spoofing and prompt injection by construction, no way to verify provenance | **rejected** |
| (d) `Message.metadata` | `metadata.agui.instructions` | metadata is opaque; an agent only acts on it if its own code does | invisible to an LLM unless the agent reads it; no defined semantics, so no agent can rely on it | used only as the carrier of the tier-2 extension, not on its own |
| (e) declared A2A extension | `metadata.agui.instructions` with defined semantics, active only when the agent declares and the proxy activates the extension | the agent decides, through `params`, which roles it accepts and whether they are `advisory` or `authoritative` | an explicit opt-in by the agent; the agent can still sanitize, and `required: false` keeps tier-1 callers working | **tier 2** |

Recommendation (OPEN-3): drop by default; (b) available to an operator who knows the agent reads it; lossless only through
(e), where the agent names the authority it grants. v1 consequence: `options.forward` already accepts `system` /
`developer` (I1), so (b) and (e) need no config rework.

### 8.2 Two-tier architecture

Is it the right frame? Yes. A2A's core cannot carry what AG-UI adds, and A2A's extension rules say how to add it without
forking the protocol: extensions may not add fields or enum values to core types, only use `metadata`; an agent declares
an extension in its card (`capabilities.extensions[{uri, required, params}]`); a client activates it with the
`A2A-Extensions` header (the stream model's `extensions` BeginEx member); the response SHOULD echo what was activated
(`topics/extensions.md` at the pinned A2A commit). That gives a clean split:

| Capability | Tier 1: any plain A2A agent (v1) | Tier 2: agent declares the AG-UI extension |
| --- | --- | --- |
| Text, errors, outcomes (success / cancelled / error) | yes | yes |
| Interrupt and resume (`input-required`, `auth-required`) | yes: plain `input` / `authorization` interrupts | yes, with `reason`, `responseSchema`, `toolCallId` |
| Cancel | yes | yes |
| `system` / `developer` instructions | dropped (or advisory (b)) | lossless, agent-controlled authority |
| `state`, `context` | one advisory `data` part | `metadata.agui.state` / `.context`, with defined semantics |
| Frontend tools | dropped | declared, called, answered (8.3) |
| `STATE_*`, `MESSAGES_SNAPSHOT` | none; `data` parts as `agent.data` | tagged parts -> native events |
| `ACTIVITY_*`, `REASONING_*`, `STEP_*`, `SUBAGENT_*`, agent-side `TOOL_CALL_*` | working status text as `ACTIVITY` only via F4; otherwise none | tagged parts -> native events |

What the extension (E1) must define:

| Element | Definition |
| --- | --- |
| URI | versioned identifier under an Aklivity-owned namespace, for example `https://www.aklivity.io/a2a/extensions/agui/v1` (OPEN-8); the `a2a-protocol.org/extensions/` prefix is reserved for the A2A organisation |
| Declaration | `capabilities.extensions[{uri, required: false, params}]`; `params.features` lists a subset of `instructions`, `tools`, `state`, `context`, `activity`, `reasoning`, `steps`, `toolCalls`, `subagents`, `interrupts`; `params.instructions` = `{roles: [system, developer], mode: advisory or authoritative}` |
| Carrier, client to agent | `Message.metadata.agui`: `run {runId, parentRunId}`, `forwardedProps`, and, only for activated features, `instructions [{role, content}]`, `tools [{name, description, parameters}]`, `context [{description, value}]`, `state` |
| Part tags, client to agent | `toolResult`: `data` or `text` part with `metadata.agui = {event: "toolResult", toolCallId, error?}` where `toolCallId` is the agent's own id |
| Part tags, agent to client | in `artifactUpdate` parts and `status.message` parts: `metadata.agui = {event: ...}`. `state` (`type`: snapshot, delta, messages; data part = the JSON), `activity` (`type`, `activityType`, `messageId`, `replace`), `reasoning` (text part, `messageId`, `groupId?`), `reasoning.encrypted` (`subtype`, `entityId`), `toolCall` (`toolCallId`, `name`, `parentMessageId?`, `executor`: client or agent; arguments as the data part), `toolResult` (agent-executed), `step` (`name`, `type`), `subagent` (`type`, `subagentRunId`, `name?`, outcome), `interrupt` (`reason`, `responseSchema?`, `toolCallId?`), `custom` / `raw`. Untagged text parts are assistant text; unknown tags are dropped |
| Ordering | stream order of A2A events is the AG-UI event order (the A2A stream is linear); an open text message is closed before any non-text event, as in 1.3 |
| Versioning | breaking changes get a new URI version; the proxy lists the versions it implements |

Negotiation in the proxy, per run, using the card read that 1.7 already performs:
1. If `options.extension` is `none`, tier 1.
2. If the card lists the URI (a version the proxy implements), tier 2; `params.features` intersected with what AG-UI sent decides which `metadata.agui` members are written (a feature the agent did not list is never sent).
3. `extensions` is set on `messageStream` / `messageSend` and on `tasksCancel`.
4. If the card marks it `required: true` and the proxy cannot meet it, the run is refused 502 before the reply begin.
5. Declared but not activated (the server ignored the request) is harmless: the agent sees unknown metadata, and responses stay tier-1 parseable. Knowing whether activation happened needs the echo (S6, 8.5).
6. Tagged parts are interpreted only in a tier-2 run; in tier 1 a tagged part is plain data (`agent.data`).

Spec impact, confirmed: none in `agui.idl`. Every AG-UI event the extension maps to already has a data extension in
`agui.idl` (text, reasoning, reasoningEncrypted, toolCall, toolResult, state with snapshot / delta / messages snapshot,
activity, step, subagent, raw, custom), `pendingToolCallIds` and interrupt detail ride `AguiEndEx.detail`, and
`AguiFunctions` already has builders and matchers for each. None in `a2a.idl` beyond what exists: `extensions` is on
`A2aMessageSendBeginEx`, `A2aMessageStreamBeginEx`, `A2aTasksCancelBeginEx` and the other begin cases, and the extension
data is body JSON the proxy already parses. One optional script-level item, S6 (reply begin reports the activated
extensions), in 8.5.

### 8.3 Deferred items: target design and what v1 must and must not do

| Item | Full target design | v1 must | v1 must not |
| --- | --- | --- | --- |
| F1 Frontend tools | tier 2, walked through below | parse `tools` and ignore them; accept trailing `tool` messages without failing; seal ids in a versioned format now (8.4); keep `tools` in the `options.forward` vocabulary | forward `tools` as text or as `data` in tier 1; treat a `tool` message as a user message |
| F2 State, activity, reasoning, step, tool-call events | tier 2: tagged parts to native events (8.2 table); `STATE_DELTA` JSON Patch passes through verbatim; `MESSAGES_SNAPSHOT` for replay | keep text framing as `chunks` (closes on an empty fin frame) and close an open text message before any other event; name `CUSTOM` events `agent.*` | emit events from untagged data; tie the `messageId` of text to anything but the artifact id or message id |
| Working-status messages | tier 1: `WORKING` status with text -> `ACTIVITY_SNAPSHOT` (`activityType` `status`, `replace` true, one stable `messageId` per run) so it never enters the transcript; tier 2: `step` / `activity` tags | drop them (1.3) | map them to `TEXT_MESSAGE_*`; add a flag to track them (the `options.status` enum in F4 is the additive switch) |
| F3 `threadId` to `contextId` store | `options.context: store`: engine `store` keyed by caller identity + `threadId`, records `contextId` and latest `taskId`; used when an agent generates its own ids | keep `options.context` an enum (`thread`, `scoped`, `none`; `store` is additive); never expose the A2A `contextId` to the client | hard-code `contextId = threadId` outside the one function that derives it |
| Reconnect / resubscribe | AG-UI's HTTP binding has no resumption (`http-sse.mdx`), the base `HttpAgent.connect` throws not-implemented, and CopilotKit's `/connect` is served from its own runner store (`runner.connect`), never forwarded upstream; so nothing reaches agui(server). The a2a stream model already has `tasksSubscribe` and `tasksList` (`contextId`), so a future connect is: list the thread's open task, subscribe, replay as snapshots | nothing | add an `operation` member to `AguiBeginEx` now: appending one later is additive, and no operation exists to put in it. **Not a spec gap on either side** |
| Non-text parts beyond `CUSTOM` | AG-UI 1.0 assistant messages carry `string` content only and media has no native output event except tool-result parts; `CUSTOM agent.file` stays the carrier until AG-UI adds assistant parts; tier 2 may map an agent-executed `toolResult` with media parts to `TOOL_CALL_RESULT` parts | emit `agent.data` / `agent.file` with the neutral shapes of 8.0 | invent a media event |
| Subagent events | tier 2: tagged `subagent` events -> `SUBAGENT_*` with `subagentRunId` on every event of the subagent; the idl has `subagentRunId` in `AguiEventMeta` | nothing | map an A2A agent-to-agent delegation to subagents without a tag |
| Multi-turn tool-result continuation | the frontend-tools walk-through below | as F1 | rely on `resume[]` for tool results |

#### Frontend tools end to end (F1, tier 2)

AG-UI continues a frontend tool call with the next run's `tool` role messages, not `resume[]`: the run that calls the
tool ends as a success with `pendingToolCallIds`, the next input's `messages` carry the assistant message with its
`toolCalls` plus a `tool` message per call keyed by `toolCallId` (`events/tool-calls.mdx`, `lifecycle.mdx`).

1. Request: the proxy writes `tools` into `Message.metadata.agui.tools` (only when the card lists the `tools` feature and the extension is activated).
2. The agent decides a client-side tool is needed. In its task it emits a part tagged `toolCall` with `executor: "client"` (`toolCallId` = its own id, `name`, arguments as the data part) and moves the task to `INPUT_REQUIRED`, because A2A needs the task to wait.
3. The proxy emits `TOOL_CALL_START` / `ARGS` / `END` with **`toolCallId` = a sealed token** encoding `{taskId, callId, agent, identity, expiry}` (8.4), then ends the run `SUCCESS` with `pendingToolCallIds` listing the sealed ids. It does **not** use `INTERRUPT`, because AG-UI forbids reporting a tool-call stop as interrupted.
4. The application executes the tool and posts the next run. `messages` contain the assistant message with `toolCalls[].id` = the sealed ids (the client echoes what it was given) and the `tool` messages, whose `toolCallId` are the same sealed ids.
5. Statelessly recovering the `taskId`: the proxy takes the trailing `tool` messages (after the last assistant message), unseals every `toolCallId`, requires one common `taskId`, the caller's identity, the route's agent and an unexpired seal; any failure makes the run 422 `invalid_run`.
6. It sends one A2A message with `taskId` only (no `contextId`), whose parts are, per `tool` message, a part tagged `toolResult` carrying the agent's original `callId`, the content (text or data) and `error` if set, plus any trailing user messages as ordinary parts.
7. The agent resumes the task and the stream continues as a normal run.

Interaction with interrupts and `input-required`:

| A2A situation | AG-UI outcome | Id used | Answered by |
| --- | --- | --- | --- |
| `INPUT_REQUIRED` whose status message has only text / untagged parts | `INTERRUPT` | sealed interrupt id (`kind: interrupt`) | `resume[]` entry |
| `INPUT_REQUIRED` with tagged `toolCall` parts (`executor: client`) | `SUCCESS` + `pendingToolCallIds` | sealed tool call ids (`kind: tool`) | `tool` messages |
| both in one status message | `INTERRUPT` (the outcome carries no pending list; pending calls are derived from the stream, per AG-UI) | both kinds | `resume[]` plus `tool` messages; the continuation message merges both, and all unsealed `taskId`s must agree |
| `AUTH_REQUIRED` | `INTERRUPT`, `reason` `authorization` | sealed interrupt id | `resume[]` entry |
| tier 1 agent (no tags) | never a tool call | n/a | `tool` messages ignored, with a Zilla warning event |

The sealed format carries `v` and `kind` from v1 (I11), so adding `kind: tool` later changes no existing token.

### 8.4 Sealed ids and what the design relies on

| Aspect | Design |
| --- | --- |
| Token | base64url of the vault-wrapped JSON `{v, kind, agent, taskId, callId?, identity?, expiry, nonce}` through the engine `SecretKeyManager` (`wrap` / `unwrap`), the same mechanism as `A2aPushSealer` in a2a(proxy) push (`push.vault`, `push.key`, `push.ttl`); `options.seal` mirrors those names |
| Opaque to the client | yes: the client only echoes it; `Interrupt.expiresAt` carries the expiry in ISO 8601, which AG-UI consumers already honour |
| Bound to the caller | `identity` comes from the route `with.identity` (`${guarded['jwt0'].identity}`, as http-kafka `overrides` does); an unseal under a different identity fails. With no guard, no identity binding |
| Bound to the agent | `agent` must equal the slug the route resolves now |
| Failure | tampered, expired, other identity, other agent: the entry is unrecognised (AG-UI: proceed without it); if nothing remains to send, 422 `invalid_run` |

What the design relies on, stated plainly:

| Question | Source | Consequence |
| --- | --- | --- |
| Can a caller resume or cancel someone else's `taskId`? | A2A spec section 13.1: servers MUST authorize every task operation (Get, Cancel, Subscribe, push config) against the caller's authorization model, which is agent-defined | an agent that complies prevents it; independently, the proxy only ever sends a `taskId` it unsealed under the caller's own identity, so the client cannot name a task at all |
| Can a caller claim someone else's `threadId` (so someone else's A2A `contextId`)? | A2A scopes task operations and listings to the caller but does **not** require `contextId` to be scoped; agents "MAY use the `contextId` to maintain internal state" (`specification.md` lines 599-601) | with `options.context: thread` (agreed default) the design **relies on the agent scoping contexts to the caller**. Where agents key state by `contextId` alone, set `options.context: scoped`: the proxy sends `<identity>/<threadId>`, which needs no store and no key (OPEN-9) |
| Does agui(server) authenticate the caller? | yes, through `options.authorization` and the route guard | identity for sealing and scoping is the authenticated one; with no guard, ids are unbound and contexts unscoped, and that is documented rather than hidden |

### 8.5 Spec changes re-checked against the full design

S1-S5 stand unchanged (route key in `AguiBeginEx` and the agui routes, a2a task-state scripts, client abort is detach).
Everything deferred was walked for an idl, `AguiFunctions` or `A2aFunctions` change that is cheaper now than later:

| Candidate | Needed by | Verdict |
| --- | --- | --- |
| **S6: reply begin reports the activated `A2A-Extensions`** (scripts and a2a(client) / a2a(server) behaviour only; the reply begin carries the existing `extensions` member of the begin case) | tier 2 activation knowledge, and `required` extensions | **recommended now**: no idl change, a script pair in the a2a spec PR plus behaviour in the a2a(client) / a2a(server) PRs; without it the proxy cannot tell "declared" from "activated". Lands in the a2a spec PR and the a2a(server) / a2a(client) PRs (#2816 / #2817 family; numbers for the coordinator to confirm) |
| `AguiBeginEx.operation` (run or connect) | reconnect | no: no AG-UI operation exists to name, and appending a member later is additive |
| `pendingToolCallIds` as an `AguiEndEx` member | F1 | no: `AguiEndEx.detail` already carries any `RUN_FINISHED` member, and the encoder copies `outcome` specifics from it |
| New `AguiDataEx` cases | F2 | no: every event family is already in `agui.idl` |
| `A2aArtifactUpdateDataEx` flags, `taskId` in the message begin, `realm` / `scope` in `AguiResetEx` | tier 2 | still no (4.2): tier 2 parses part tags from the body anyway, so the DataEx flags still buy nothing |
| `AguiFunctions` / `A2aFunctions` additions | F1 / F2 scripts | no: builders and matchers exist for tool call, tool result, state, activity, reasoning, step, subagent, custom, raw, `extensions` and `matchDataEx` of each a2a case |
| Identity or seal support in an idl | 8.4 | no: sealing is runtime config plus the vault SPI |

Net: one newly justified change, S6, scripts only and optional; no idl, `AguiFunctions` or `A2aFunctions` change beyond S1.

### 8.6 v1 shape, consolidated

| Concern | v1 |
| --- | --- |
| Binding config | `options.forward`, `.context` (`thread`, `scoped`, `none`), `.abort`, `.extension`, `.seal`; routes with `when.agent`, `with.agent`, `with.identity`, `guarded`, `exit` |
| Request | trailing user messages, resume to unsealed `taskId`, `state` + `context` as one advisory `data` part, `forwardedProps` / `runId` / `parentRunId` in `metadata.agui`, `system` / `developer` only if listed in `forward`, `tools` and `tool` messages parsed and ignored |
| Response | text, `CUSTOM` `agent.data` / `agent.file`, neutral errors, `INTERRUPT` with sealed ids, `CANCELLED`, no A2A details on the client stream |
| Not in v1 | tier 2 (E1, F1, F2, F5), working status as activity (F4), store (F3), reconnect (F6) |
| Constraint v1 puts on later work | sealed format versioned with `kind`; `metadata.agui` key shared by both tiers; `options.context` and `options.forward` open enums; `agent.*` custom names reserved |

### 8.7 Issue changes in this revision

Changed: I1 (`seal`, `context`, `extension`, `with.identity`, `forward` vocabulary), I4 (`BindingEvent` catalog), I5 (`context`
modes, unseal, ignore `tools` / `tool`), I6 (neutral names and codes, sealed ids, no A2A ids on the stream), F1-F3
expanded. New: I11 (sealing), A8 (S6, optional), E1 (extension specification), F4 (working status as activity), F5
(subagent events), F6 (reconnect, conditional). The issue table in section 5 carries the bodies.
