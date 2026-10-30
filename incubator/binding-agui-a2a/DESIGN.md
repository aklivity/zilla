# agui-a2a (proxy) — AG-UI to A2A mapping design

Design spike for #2822. Accepts `agui` streams (#2821 / PR #2850 server, #2856 / PR #2857 client), produces `a2a`
streams (#2814 stream model, PRs #2825-#2835). No code, no `.rpt`, no spec change is made by this document; spec changes it proposes are listed in section 4.

**Final form (revision 10).** Section 10 is the authoritative issue list and supersedes the issue drafts of earlier
revisions. Scope of v1:

| Decision | v1 |
| --- | --- |
| Protocol level | tier 1 only: plain A2A, **no extension declared or activated**. Everything that needs a carrier extension (E1, frontend tools, `STATE_*` / activity / reasoning / tool-call events, an agent-declared history feature, agent-originated `MESSAGES_SNAPSHOT`, handle and part tags) is post-v1. AG-UI and CopilotKit clients consume AG-UI events and never see A2A extensions, so a carrier only changes which A2A agents can opt into a richer mapping; it does not affect client integration |
| Configuration | no `options` in the binding yaml: routes (`when.agent`, `with.agent`, `with.identity`, `guarded`, `exit`) only. TTLs, capacity and the key-to-affinity function are binding configuration properties in the style of mcp's `session.id` / `session.id.affinity` (8.4) |
| `system` / `developer` messages | dropped, no option: forwarding them is insecure (8.1) |
| History | only the latest user turn is forwarded; a documented lossiness (1.2); full-history replay only through a later agent-declared history feature |
| Abort | always cancel the A2A task once its id is known (1.6) |
| `WORKING` text | hardcoded: the latest working text becomes the reply at `COMPLETED` only if nothing else was emitted (9.2) |
| Multi-node | a pluggable `thread.affinity` property, no engine change (8.4) |
| Reading guide | 8.1 to 8.3 and 9.5 describe post-v1 designs kept so v1 does not block them; sections 6 and 7 are history and sources |

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
| `threadId` | `Message.contextId`, indirectly | none | part of the owner key (identity, agent, `threadId`); never sent as a `contextId`. A thread's first run sends none (the A2A spec lets the agent generate it); the one the agent returns is remembered by the owner (8.4) and sent on later runs; an agent that returns none stores nothing. No option |
| `runId` | new task per run; not forwarded | lossy | A2A task ids are server-assigned, so `runId` cannot be the task id, and A2A `taskId` / `contextId` are its equivalents, so no run id travels in A2A metadata (9.4). Correlation goes to Zilla events and trace ids. No A2A id is surfaced to the AG-UI client (8.0); the task id travels only in owner-local handles (8.4) and in Zilla events |
| `parentRunId` | not forwarded | lossy | informational only; never used for continuation; correlation in Zilla events |
| `resume[].interruptId` | `Message.taskId` (and **no** `contextId`) | none | the interrupt id is an opaque random handle the proxy issued and remembered owner-locally against (identity, agent, `threadId`, `taskId`) (8.4); on resume the proxy looks it up and sends the message with that `taskId` only, because A2A requires the server to infer `contextId` from the task and to reject a mismatching pair. An unknown, expired or foreign handle rejects the run before the agent is contacted (409 `handle_unknown`, 8.4). The agent slug is equally stable on resume: the client re-posts `RunAgentInput` to the same URL, so the same `routes[].when.path` yields the same `AguiBeginEx.agent` and so the same owner |
| `AguiBeginEx.agent` (new, revision 2) | `A2aBeginEx.agent` (slug), on `agentCard`, `messageStream` / `messageSend` and `tasksCancel` | none | pass-through by default; `routes[].with.agent` on the proxy rewrites it. Null stays null (the tenant-less A2A endpoint) |
| `resume[].payload` | message `parts` (string -> `text`, other JSON -> `data`; absent payload on `resolved` -> one empty `data` part `{}`) | lossy for `status` other than `resolved` | `status: cancelled` on a resume -> `tasksCancel` of that `taskId`, run ends `CANCELLED`, no message sent. An entry naming an interrupt id other than the one being continued is unrecognised and ignored (AG-UI: proceed without it); `resume[].metadata` -> `Message.metadata.agui.resume` |
| `AguiBeginEx` (reply) | reply begin echoes request `threadId` / `runId` / `parentRunId` | none | the A2A `contextId` is **not** returned as `threadId` (the client owns it). A conforming server either keeps the `contextId` it was sent or rejects the request, so a different `contextId` in a response to a run that sent one is a protocol violation: abort `error{status 502, code "agent_protocol"}`. The agent-issued id is never surfaced on the client stream |

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
| `state` (non-empty) and `context[]` (non-empty) | one `{data: {state, context}, mediaType: application/json, metadata: {agui: {part: "input"}}}` appended after content parts | lossy (agent may ignore) | forwarded whenever non-empty, no option |
| `forwardedProps` | `Message.metadata`, verbatim at the top level (an object is the metadata, minus proxy-reserved keys; a non-object value becomes `{"forwardedProps": value}`) | none, except reserved keys | AG-UI's opaque application-to-agent channel stays an application-to-agent channel; reserved keys are `agui` and any extension URI the proxy activates, rejected, never stripped (400 `reserved_key`), so a client cannot forge proxy-owned or extension-defined metadata and AG-UI's rule that intermediaries MUST NOT alter `forwardedProps` holds (9.4). Not used for routing |
| `tools[]` | dropped (v1) | lossy | see 1.5 |
| no trailing user message and no `resume` | reset 422 `invalid_run` before reply begin | n/a | mirrors `run.rejected` |
| `SendMessageConfiguration` | `{"historyLength": 0, "acceptedOutputModes": ["text/plain"]}`; `acceptedOutputModes` grows only with enabled content plug-ins (9.3) | n/a | AG-UI never needs history in the response; the agent is asked to tailor its output to what the proxy can map |

### 1.3 Response (A2A events -> AG-UI)

Applies to `messageStream` events; `messageSend` uses the same decode on the single `task` / `message` body (1.4).

| A2A event | AG-UI | Lossiness | Notes |
| --- | --- | --- | --- |
| a2a reply begin (`connected`) | reply begin, then agui(server) emits `RUN_STARTED` | none | |
| `task` `SUBMITTED` / `statusUpdate` `SUBMITTED`, `WORKING` without message | nothing | lossy | A2A working is not an AG-UI step; no `STEP_*` in v1 |
| `statusUpdate` `WORKING` / `SUBMITTED` with `status.message` text | retained as a fallback reply (9.2); never a transcript message by itself; `ACTIVITY_SNAPSHOT` via F4 | lossy | progress chatter must not pollute the transcript, but a reply an agent delivers only through working messages must not be lost |
| `task` carrying `artifacts[]` (the first event, or the final snapshot a server MAY resend before closing) | text for artifacts whose `artifactId` has not been streamed yet; skipped for ids already streamed | none | reconcile by `artifactId` so a final snapshot does not duplicate text; shared path with `messageSend`, where every artifact is unseen |
| `artifactUpdate`, text part, first sight of `artifactId` | `TEXT_MESSAGE_START` (`messageId = artifactId`, role ASSISTANT) + `CONTENT` | none | `text` data frame, init flag + `AguiTextDataEx` |
| `artifactUpdate`, text part, `append: true` | `TEXT_MESSAGE_CONTENT` on the open message | none | payload-only frames, `chunks` framing |
| `artifactUpdate`, `append: false` on an already streamed `artifactId` | new text message `messageId = artifactId + "#n"` | lossy | AG-UI cannot retract streamed text; earlier text stays |
| `artifactUpdate` `lastChunk: true`, or terminal / interrupt state with a text message open | `TEXT_MESSAGE_END` (empty fin frame) | none | the proxy uses `run.text.chunks` framing because `lastChunk` can arrive after the last text |
| `artifactUpdate` / `task` / `message` non-text part (`data`, `file`) | `CUSTOM` `agent.data` (value = the data JSON) / `agent.file` (value = `{mimeType, name?, url or data}`) | lossy | AG-UI flavoured names and shapes, no A2A vocabulary (8.0); an open text message is closed first. Tier 2 maps tagged parts to native events instead (8.2) |
| `message` (agent reply with no task) | text message from its text parts (`messageId` = `Message.messageId`), non-text as `CUSTOM`; run ends `SUCCESS` | none | |
| `statusUpdate` / `task` `COMPLETED` with `status.message` text | text message (`messageId` = the A2A message id, assistant role), deduplicated against text already emitted in the run (9.2) | none | the A2A spec says results SHOULD be artifacts, but agents do put the reply here; the digest rule keeps a repeat of artifact text from showing twice |
| run reaches `COMPLETED` with no text emitted and a retained working message | the latest retained working text as one text message (9.2, OPEN-10) | lossy | hardcoded, no option |
| `FAILED` / `REJECTED` `status.message` text | the abort `message` (never the transcript) | none | |
| `INPUT_REQUIRED` / `AUTH_REQUIRED` `status.message` text | the interrupt `message` (never the transcript) | none | |
| `CANCELED` `status.message` text | ignored | lossy | `cancelled` has no message |
| `statusUpdate` `COMPLETED` | end `SUCCESS`, no `detail` | none | `result` is the run's return value and A2A completion has none; no A2A ids are placed on the client stream (8.0), they go to a Zilla event |
| `statusUpdate` `FAILED` | abort `AguiAbortEx.error{status 502, code "agent_failed", message = status.message text, default "Agent failed"}` | lossy | AG-UI `RUN_ERROR`; `message` is required there, the idl's is nullable, so the proxy always fills it |
| `statusUpdate` `REJECTED` | abort `error{status 422, code "agent_rejected", message, default "Agent declined"}` | lossy | |
| `statusUpdate` `CANCELED` | end `CANCELLED` | none | whoever cancelled it |
| `statusUpdate` `INPUT_REQUIRED` | end `INTERRUPT`, `detail = {"outcome":{"interrupts":[{"id":"<handle>","reason":"input","message":"<status.message text>","expiresAt":"<handle expiry>"}]}}` | lossy | the prompt travels in the interrupt message, not as a transcript message; `reason` is a neutral open string. The run ends when the state is seen, whether or not the server closes the stream (the A2A spec says the stream closes at terminal states in one place and at terminal or interrupted in another); the proxy then ends its own a2a stream without cancelling the task. Tier 2 enriches `reason`, `responseSchema`, `toolCallId` (8.2) |
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
| agui stream abort / reset after `taskId` is known (first `task` / `statusUpdate` / `artifactUpdate` DataEx) | open `tasksCancel` (`agent`, `taskId`) on the exit, ignore its result; always abort the `messageStream`; there is no detach option |
| abort before any DataEx (no `taskId` yet) | abort the `messageStream` only; the remote task may keep running (documented lossy) |
| `resume[].status = cancelled` | `tasksCancel` for the interrupt id, end `CANCELLED` |
| a2a `CANCELED` state not initiated here | end `CANCELLED` |

AG-UI and CopilotKit facts that bear on this (verified, section 7): the AG-UI HTTP + SSE binding is a POST of `RunAgentInput` to whatever URL the consumer is configured with, and defines no stop / cancel; a consumer that abandons the stream has a *truncated run* and "MUST NOT synthesize a `RUN_FINISHED`", while `cancelled` is a producer's report about a run it stopped (`lifecycle.mdx`). The JS `HttpAgent.abortRun()` is an `AbortController` abort of the fetch. CopilotKit's runtime exposes `POST /agent/:agentId/stop/:threadId` to its own browser client; the handler calls `runner.stop`, which ends in `agent.abortRun()` on the upstream agent, i.e. the same fetch abort. It is thread-wide unless an optional `{runId}` body is sent, and reports the stop to the browser as `RUN_ERROR` with code `STOPPED` (a CopilotKit convention, not AG-UI's `cancelled` outcome). None of that reaches Zilla: Zilla sees only the aborted request. A Stop button and a closed tab are indistinguishable there, so cancelling is the proxy's policy, always cancel and no option (OPEN-5 resolved). `/connect` and `/stop/...` are CopilotKit runtime routes, not AG-UI; with `routes[].when.path` they are unmatched and answered 404. Cancelling by `taskId` is run-scoped, so it avoids a thread-wide stop cancelling the next run.

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
| 1 Identity | `threadId` -> `contextId` verbatim (agreed, OPEN-1); new task per run, no run ids on the A2A side (correlation in Zilla events); continuation only via `resume` with an opaque handle the proxy remembers against the `taskId` | `parentRunId` is not a continuation key (the `resume[]` array with `interruptId` is the actual AG-UI continuation signal; the idl already carries it in `run.resume`). The interrupt id is an opaque random handle, never a raw `taskId` (8.4) |
| 2 Request | trailing user messages after the last assistant / tool message, merged into one `Message`; `system` / `developer` / history dropped; `state` + `context` -> one tagged `data` part; `forwardedProps` -> `Message.metadata`; binary -> `raw`, url -> `url` | "last user message" widened to trailing user messages (several user messages without an assistant reply between them are one turn); `state` / `context` go in a `data` part (agents see them as input) while `forwardedProps` goes to `metadata` (opaque) |
| 3 Response | per 1.3. Lifecycle is begin / end / abort / reset, not data events. `canceled` -> end `CANCELLED` (the idl has it). `input-required` / `auth-required` -> end `INTERRUPT` with an opaque `interrupts[0].id` handle | `RUN_STARTED` / `STEP_*` are not derived from `working`; `data` / `file` parts -> `CUSTOM` `agent.data` / `agent.file` in v1 (not `STATE_SNAPSHOT`, which needs an A2A convention: tier 2, 8.2); no A2A ids or vocabulary on the client stream (8.0) |
| 4 Frontend tools | dropped in v1; follow-up via a declared A2A extension | as the issue's candidate, but through the existing `extensions` BeginEx field rather than an unmarked `data` part |
| 5 Streaming | card-driven: `messageStream` when the card says streaming, else `messageSend` + synthesized events | the issue says "always `SendStreamingMessage`"; since Q7 reads the card anyway, the proxy follows `capabilities.streaming` and streams whenever the card allows it |
| 6 Cancellation | `tasksCancel` when `taskId` is known, else abort only; no option. AG-UI has no stop endpoint and treats an abandoned stream as a truncated run, so cancel is this proxy's policy, not a protocol rule (1.6, OPEN-5) | no cancel before the first event, and a2a abort alone is not assumed to cancel |
| 7 Card | yes: read card, choose streaming, refuse unsupported input modes with 415 | recommended exit is a2a(proxy) for its shared cache |
| 8 Authorization | pass the engine `authorization` through; `routes[].guarded` for role gates (now available on agui(server) routes too); no end-user claims in the message; agent-side token rejection is 502, not 401 | `AguiResetEx` cannot carry `WWW-Authenticate` realm / scopes; judged sufficient (candidate (d)) |
| 9 v1 scope | 1.9. One addition over the recommended scope: `CUSTOM` passthrough of non-text parts and the interrupt / resume path | interrupt / resume is included because the stateless `taskId` design makes it cheap and the idl already has the outcome and `resume` scripts |
| Q-route (agent slug) | **route key on the stream** (user decision, revision 2): `AguiBeginEx.agent`; agui(server) `routes[].when.path` -> `with.agent`; agui-a2a `routes[].when.agent` -> optional `with.agent` -> `A2aBeginEx.agent`; agui(client) `routes[].when.agent` -> `with.path` | replaces revision 1's static `options.agent` (dropped). Routing by `forwardedProps` still rejected: client-controlled, forces body parse before route resolve |

### OPEN decisions needing the user's call

| ID | Question | Recommendation |
| --- | --- | --- |
| ~~OPEN-1~~ | resolved (user decision, revision 7) | one behaviour, no `options.context`: never send a client-chosen `contextId`, remember the agent-issued one per (identity, agent, `threadId`) (8.4, 9.6) |
| ~~OPEN-2~~ | dropped (user decision, revision 4) | no `result` and no `metadata` ids on RUN_FINISHED: A2A-side details do not leak to the AG-UI side (8.0) |
| ~~OPEN-3~~ | resolved (user decision, revision 10) | `system` / `developer` messages are dropped with no option; forwarding them is insecure (8.1) |
| OPEN-4 | agui(client) `with.path`: replace the path of `options.server`, or append to it | replace (an HTTP `:path` of its own, scheme and authority kept from `options.server`), so one client can address `/agent/{agent}/run` style servers and a bare `/agent` server by the same rule; with no routes the `options.server` path is used unchanged |
| ~~OPEN-5~~ | resolved (user decision, revision 10) | always cancel, no knob (1.6) |
| ~~OPEN-6~~ | resolved (user decision, revision 10) | v1 forwards only the latest user turn, a documented lossiness; full-history replay only through a later agent-declared history feature (F8) |
| OPEN-7 | Interrupt and tool-call ids: where the `taskId` lives | decided by the user: in affinity-owned local state on the proxy, with the proxy issuing the engine `Redirect` (8.4); ids are opaque random handles. Replaces sealed ids, `options.seal`, vault wrap / unwrap and I11-as-sealing. State is volatile in v1; no store (revision 7) |
| OPEN-8 | Naming: carrier extension URI and metadata key; plug-in URIs | deferred to E1 (post-v1); v1 declares no extension, and the `agui` key used for the informational part tag stays reserved |
| ~~OPEN-9~~ | resolved (revision 7) | no context modes; `thread`, `none`, `scoped`, `masked`, `server` are listed as considered and rejected in 9.6 |
| ~~OPEN-10~~ | resolved (user decision, revision 10) | the `WORKING` text fallback reply is hardcoded, no `options.status` (9.2) |
| OPEN-11 | Which A2UI generation the first content plug-in targets (9.5), post-v1 (F7) | the shape CopilotKit's `a2ui-surface` renderer reads today (`a2ui_operations`, `createSurface` / `updateComponents` operations); v0.8 only if an agent needs it |
| ~~OPEN-12~~ | resolved (user decision, revision 8) | always start a new context plus implicit compaction by `MESSAGES_SNAPSHOT` (8.4); no `fail`, no 409 `context_lost`, no per-agent flag; conditional on the CopilotKit check, which aligned (9.10) |
| ~~OPEN-13~~ | resolved (user decision, revision 7) | no store in v1; `options.store` is a documented follow-up (F3) |
| ~~OPEN-14~~ | resolved (user decision, revision 10) | a pluggable `thread.affinity` property, no engine change (8.4) |
| OPEN-15 | Unverified residue of 9.10 and two accepted limitations: CopilotKit's managed (Intelligence) persistence and other AG-UI clients were not checked; trimmed histories cannot trigger the reset; an agent that returns a `contextId` on every message without memory causes one spurious reset per thread per restart | accept for v1 and keep the informational operator event (it counts resets; it never informs the client); revisit if a managed-mode or channels client shows a different snapshot behaviour |
| OPEN-16 | Request size bound: the whole request is buffered in one slot (32 KiB by default), so full-history requests are rejected 400 once they outgrow it | v1: document it, map it to 400 `Request too large`, and raise `buffer.slot.capacity` for AG-UI deployments; recommend a history-skipping streaming decode as a follow-up (parse `messages[]` incrementally, keep only the messages since the last assistant turn plus `state`, `context` and `forwardedProps`), since the A2A request needs only those |
| OPEN-17 | Confirm the three pre-reply rejections: 503 `retention_unavailable` (retention cannot be arranged, retryable), 409 `handle_unknown` (unknown, expired or foreign handle), 400 `reserved_key` (reserved key in `forwardedProps`) | recommended as written in 8.4; not yet answered by the user |


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
    routes:
      - when:
          - agent: weather                    # AGUI-scoped
        with:
          identity: ${guarded['jwt0'].identity}  # part of the owner key (8.4)
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
- `type: agui-a2a`, `kind: proxy`: no `options`; `routes[].when[]` `{agent: string}`, `with` `{agent: string, identity: string}`, `guarded`, `exit`; no `options.agent`; `vault`, `catalog`, `entry` false. TTLs, capacity and the key-to-affinity function are binding configuration properties (8.4), not yaml.

## 4. Spec changes

The bar is "does the spec support agui-a2a sufficiently well". PR attribution: `agui.idl`, `AguiFunctions` and the agui schema patch / fixtures / scripts live in the agui(server) stack (#2821 / PR #2850; `agui.idl` was introduced there, commit baf64421); the client-only schema, scripts and chain scenarios live in #2856 / PR #2857; `a2a.idl`, `A2aFunctions` and the a2a scripts live in the a2a spec PR at the base of #2825-#2835 (the exact number of that PR and of the a2a(server) / a2a(client) PRs is for the coordinator to confirm; the issues are #2814 stream model, #2816 server, #2817 client).

### 4.1 Adopted

| # | Change | Exact change | Affects | Lands in |
| --- | --- | --- | --- | --- |
| S1 | route key on the stream (candidate (b)) | `agui.idl`: `struct AguiBeginEx { threadId; runId; parentRunId = null; string16 agent = null; }`, appended **last**: the required ids keep their offsets and the new field has the same shape as the `agent = null` on every `A2aBeginEx`. Reply begin does not need it (agui(server) only reads the reply ids); producers leave it null | `AguiFunctions`: `AguiBeginExBuilder.agent(String)` after `parentRunId` (callers skipping `parentRunId` rely on the same default-filling as `a2a:beginEx().agentCard()` without `agent`; covered by a new test), `AguiBeginExMatcherBuilder.agent(String)` + `matchAgent` (null = unconstrained). `AguiFunctionsTest`: add `shouldGenerateBeginExWithAgent`, `...WithAgentWithoutParentRunId`, `shouldMatchBeginExWithAgent`. **Existing `.rpt` scripts need no edit**: builders default `agent` to null, matchers ignore an unset member, and `AguiFunctionsTest` asserts fields, not byte layouts | PR #2850 |
| S2 | agui(server) routes | schema patch drops `"routes": false` for `kind: server` and adds `routes[].when.path` / `with.agent` / `guarded` / `exit`. Fixture `config/server.routes.invalid.yaml` (asserts routes are rejected) flips to a valid `server.routes.yaml`; add invalid fixtures for unknown `when` / `with` members and a route without `exit`. New scripts: network `request.path.not.found` (404), `request.path.forbidden` (403), and paired network + application `run.routed` (`/agent/weather/run` -> application BeginEx `.agent("weather")`) | `SchemaTest`, `NetworkIT` / `ApplicationIT` of the server | PR #2850 |
| S3 | agui(client) routes | same for `kind: client`: `config/client.routes.invalid.yaml` flips to valid `client.routes.yaml`; `required` relaxes to `exit` or `routes`; new paired scenario `run.routed` (application BeginEx `.agent("weather")` -> network request path `/agent/weather/run`) and a `chain` scenario `run.routed` (server routes by path, client routes by agent) | `SchemaTest`, client ITs, chain ITs | PR #2857 |
| S4 | a2a non-happy task states (flagged in revision 1) | scripts only: `stream.message.failed`, `.rejected`, `.input.required`, `.auth.required`, `.canceled` and `send.message.task.failed` / `.input.required`, asserting the `A2aStatusUpdateDataEx` / `A2aTaskDataEx` `state` tag for each `TASK_STATE_*` string, including the terminal `statusUpdate` followed by end, and `status.message` text on `COMPLETED`, `FAILED`, `INPUT_REQUIRED` and a final `Task` snapshot resend (no current script puts text in `status.message`: replies are artifacts or a task-less `message`). They belong with the a2a spec (the state-string to `A2aTaskState` tag is an a2a(server) / a2a(client) contract that is independent of any proxy) and are driven through the a2a(server) / a2a(client) ITs; agui-a2a adds only its own mapping scripts on top | a2a spec PR + a2a(server) / a2a(client) PRs (#2816 / #2817) | a2a PRs |
| S5 | client abort is detach (flagged in revision 1) | script `stream.message.client.aborted` (client aborts the application stream; assert the HTTP request is torn down and **no** `tasksCancel` is issued), making "abort = detach, cancel is explicit" an a2a contract rather than an assumption. No idl change | a2a spec PR + a2a(client) PR | a2a PRs |
| S6 | the reply Begin reports the activated `A2A-Extensions` (**adopted**, user decision) | scripts only plus behaviour, no idl change: the reply Begin carries the existing `extensions` member of the begin case; `send.message.extensions` and `stream.message.extensions` gain a matched reply Begin; a2a(client) reads the `A2A-Extensions` response header into it, a2a(server) writes the header from it (A8) | the a2a spec scripts, `A2aServerIT`, `A2aClientIT` | #2825 (scripts), #2828 (a2a(server)), #2829 (a2a(client)) |
| S7 | agui(server) handles `Redirect` (agui(server) is the initiator of the `agui` stream) | runtime in `AguiServerFactory` (A10) mirroring http(server) `onRequestRedirect`: re-begin from `redirect.extension()`, the Begin extension the receiver echoed, as http and mcp do; scripts `run.redirect` and `run.redirect.100k` (A9). **No idl, `AguiFunctions` or harness change** (8.4) | the agui(server) ITs | PR #2850 |

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

## 5. Drafted follow-up issues

Superseded by section 10, which carries the final list (same ids: A = amendment, I = v1 issue, E / F = post-v1).

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
| Cancel: no stop in the protocol; an abandoned stream is a truncated run, not a cancelled one; `cancelled` is the producer's report | `lifecycle.mdx` (`### Cancelled runs`), `basic/transports/index.mdx#truncation`, `http-sse.mdx` (`## No resumption`) | verified; makes cancel-on-abort a policy: always cancel, no option (OPEN-5) |
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
| cancel on abort is the mapping | cancel on abort is a policy: always cancel, no option |
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
| Interrupt id | raw `taskId` | opaque random handle remembered owner-locally (8.4), with `expiresAt` |
| Tool call ids (tier 2) | n/a | opaque random handles (8.3, 8.4) |
| Error `message` text | agent's `status.message` | unchanged: it is the agent's message to the user, content rather than protocol vocabulary |

Zilla event catalog (`BindingEvent`, I4 / I6): run routed (`agent`, `threadId`, `runId`), run finished (outcome, `taskId`,
`contextId`, a2a state), run failed (code, message), redirected (affinity), handle rejected (reason), context lost (policy), unknown extension tag dropped (tag). These are
the only places A2A ids appear. The reverse direction is different by design: the only AG-UI details sent to the agent are what the client supplied (`forwardedProps`, as `Message.metadata`) and, in tier 2, the activated feature members of `Message.metadata.agui`; run ids are not sent (9.4).

### 8.1 `system` / `developer` messages (decided: dropped, no option)

Dropped, with no option (user decision, revision 10). Trust reason: `system` and `developer` carry elevated authority over an
agent's behaviour, they arrive in the `RunAgentInput` the consumer assembles, and for a browser consumer that is under the
control of whoever holds the browser, so the proxy cannot verify their provenance. The A2A agent owns its prompt. Forwarding
them in any form (a role, a prefix, a data part, metadata) lets the client assert privileged instructions across a trust
boundary, which is prompt injection with an elevated framing.

| Option | Verdict |
| --- | --- |
| (a) drop | **adopted**, no `options.forward` entry exists |
| (b) tagged advisory `data` part | rejected: an agent that reads `data` parts would treat client-supplied instructions as input with a privileged label |
| (c) text-part prefix | rejected: indistinguishable from user text, spoofable |
| (d) `Message.metadata` | rejected on its own: opaque, no defined semantics |
| (e) agent-declared extension | post-v1 and not planned for instructions; any future path needs an agent-side declaration and a separate security review |

`state` and `context` are not instructions: they are data the application supplies, forwarded whenever non-empty as one
advisory `data` part with no option, at the same trust as the user's own message content.

### 8.2 Two-tier architecture (post-v1 reference design)

In v1 only tier 1 exists: no extension is declared or activated. This section is the design that post-v1 work (E1, F1, F2, F5, F8) builds on; it is kept so v1 does not block it.

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
| `system` / `developer` instructions | dropped, no option | lossless, agent-controlled authority |
| `state`, `context` | one advisory `data` part | `metadata.agui.state` / `.context`, with defined semantics |
| Frontend tools | dropped | declared, called, answered (8.3) |
| `STATE_*`, `MESSAGES_SNAPSHOT` | proxy-originated `MESSAGES_SNAPSHOT` only on a context reset (8.4); otherwise none, and `data` parts as `agent.data` | tagged parts -> native events, including agent-originated `MESSAGES_SNAPSHOT` (agent-side compaction) |
| `ACTIVITY_*`, `REASONING_*`, `STEP_*`, `SUBAGENT_*`, agent-side `TOOL_CALL_*` | working status text as `ACTIVITY` only via F4; otherwise none | tagged parts -> native events |

What the extension (E1) must define:

| Element | Definition |
| --- | --- |
| URI | versioned identifier under an Aklivity-owned namespace, for example `https://www.aklivity.io/a2a/extensions/agui/v1` (OPEN-8); the `a2a-protocol.org/extensions/` prefix is reserved for the A2A organisation |
| Declaration | `capabilities.extensions[{uri, required: false, params}]`; `params.features` lists a subset of `instructions`, `tools`, `state`, `context`, `activity`, `reasoning`, `steps`, `toolCalls`, `subagents`, `interrupts`; `params.instructions` = `{roles: [system, developer], mode: advisory or authoritative}` |
| Carrier, client to agent | `Message.metadata.agui`, only for activated features: `instructions [{role, content}]`, `tools [{name, description, parameters}]`, `context [{description, value}]`, `state` |
| Part tags, client to agent | `toolResult`: a `data` part whose value is `{type: "tool-result", toolCallId, payload, error?}` (the `@ag-ui/a2a` shape), tagged `metadata.agui.event = "toolResult"`, where `toolCallId` is the agent's own id; the message role is `ROLE_USER` (the precedent uses the agent role) |
| Part tags, agent to client | in `artifactUpdate` parts and `status.message` parts: `metadata.agui = {event: ...}`. `state` (`type`: snapshot, delta, messages; data part = the JSON), `activity` (`type`, `activityType`, `messageId`, `replace`), `reasoning` (text part, `messageId`, `groupId?`), `reasoning.encrypted` (`subtype`, `entityId`), `toolCall` (a `data` part `{type: "tool-call", id, name, arguments}` as `@ag-ui/a2a` agents emit it; tag adds `parentMessageId?` and `executor`: client by default, or agent), `toolResult` (agent-executed), `step` (`name`, `type`), `subagent` (`type`, `subagentRunId`, `name?`, outcome), `interrupt` (`reason`, `responseSchema?`, `toolCallId?`), `custom` / `raw`. Untagged text parts are assistant text; unknown tags are dropped |
| Ordering | stream order of A2A events is the AG-UI event order (the A2A stream is linear); an open text message is closed before any non-text event, as in 1.3 |
| Versioning | breaking changes get a new URI version; the proxy lists the versions it implements |

Negotiation in the proxy, per run, using the card read that 1.7 already performs:
1. In v1 there is no extension, so every run is tier 1; the steps below are post-v1.
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
| F1 Frontend tools | tier 2, walked through below | parse `tools` and ignore them; accept trailing `tool` messages without failing; issue interrupt handles from the owner-local state now (8.4); ignore `tools` in v1 | forward `tools` as text or as `data` in tier 1; treat a `tool` message as a user message |
| F2 State, activity, reasoning, step, tool-call events | tier 2: tagged parts to native events (8.2 table); `STATE_DELTA` JSON Patch passes through verbatim; `MESSAGES_SNAPSHOT` for replay | keep text framing as `chunks` (closes on an empty fin frame) and close an open text message before any other event; name `CUSTOM` events `agent.*` | emit events from untagged data; tie the `messageId` of text to anything but the artifact id or message id |
| Working-status messages | tier 1: `WORKING` status with text -> `ACTIVITY_SNAPSHOT` (`activityType` `status`, `replace` true, one stable `messageId` per run) so it never enters the transcript; tier 2: `step` / `activity` tags | drop them (1.3) | map them to `TEXT_MESSAGE_*`; add a flag to track them (F4 is post-v1) |
| F3 Durable state | `options.store`: the engine store is the authoritative copy, the worker-local map the fast path (8.4) | keep the owner-local map behind one state interface so a store can sit under it; never expose the A2A `contextId` or `taskId` to the client | write state anywhere but through that interface |
| Reconnect / resubscribe | AG-UI's HTTP binding has no resumption (`http-sse.mdx`), the base `HttpAgent.connect` throws not-implemented, and CopilotKit's `/connect` is served from its own runner store (`runner.connect`), never forwarded upstream; so nothing reaches agui(server). The a2a stream model already has `tasksSubscribe` and `tasksList` (`contextId`), so a future connect is: list the thread's open task, subscribe, replay as snapshots | nothing | add an `operation` member to `AguiBeginEx` now: appending one later is additive, and no operation exists to put in it. **Not a spec gap on either side** |
| Non-text parts beyond `CUSTOM` | AG-UI 1.0 assistant messages carry `string` content only and media has no native output event except tool-result parts; `CUSTOM agent.file` stays the carrier until AG-UI adds assistant parts; tier 2 may map an agent-executed `toolResult` with media parts to `TOOL_CALL_RESULT` parts | emit `agent.data` / `agent.file` with the neutral shapes of 8.0 | invent a media event |
| Subagent events | tier 2: tagged `subagent` events -> `SUBAGENT_*` with `subagentRunId` on every event of the subagent; the idl has `subagentRunId` in `AguiEventMeta` | nothing | map an A2A agent-to-agent delegation to subagents without a tag |
| Multi-turn tool-result continuation | the frontend-tools walk-through below | as F1 | rely on `resume[]` for tool results |

#### Frontend tools end to end (F1, tier 2)

AG-UI continues a frontend tool call with the next run's `tool` role messages, not `resume[]`: the run that calls the
tool ends as a success with `pendingToolCallIds`, the next input's `messages` carry the assistant message with its
`toolCalls` plus a `tool` message per call keyed by `toolCallId` (`events/tool-calls.mdx`, `lifecycle.mdx`).

1. Request: the proxy writes `tools` into `Message.metadata.agui.tools` (only when the card lists the `tools` feature and the extension is activated).
2. The agent decides a client-side tool is needed. In its task it emits a `data` part `{type: "tool-call", id, name, arguments}` tagged `metadata.agui = {event: "toolCall", executor: "client"}` and moves the task to `INPUT_REQUIRED`, because A2A needs the task to wait.
3. The proxy emits `TOOL_CALL_START` / `ARGS` / `END` with **`toolCallId` = an opaque random handle** the proxy remembers against `{identity, agent, threadId, taskId, callId}` (8.4), then ends the run `SUCCESS` with `pendingToolCallIds` listing the handles. It does **not** use `INTERRUPT`, because AG-UI forbids reporting a tool-call stop as interrupted.
4. The application executes the tool and posts the next run. `messages` contain the assistant message with `toolCalls[].id` = the handles (the client echoes what it was given) and the `tool` messages, whose `toolCallId` are the same handles.
5. Recovering the `taskId`: the request reaches the key's owner (8.4), which takes the trailing `tool` messages (after the last assistant message), looks every `toolCallId` up under (identity, agent, `threadId`), and requires one common `taskId` and unexpired handles; any failure rejects the run before the agent is contacted with 409 `handle_unknown` (8.4). A handle is consumed on use.
6. It sends one A2A message with `taskId` only (no `contextId`), whose parts are, per `tool` message, a `data` part `{type: "tool-result", toolCallId: <the agent's original id>, payload, error?}`, plus any trailing user messages as ordinary parts.
7. The agent resumes the task and the stream continues as a normal run.

Interaction with interrupts and `input-required`:

| A2A situation | AG-UI outcome | Id used | Answered by |
| --- | --- | --- | --- |
| `INPUT_REQUIRED` whose status message has only text / untagged parts | `INTERRUPT` | interrupt handle | `resume[]` entry |
| `INPUT_REQUIRED` with tagged `toolCall` parts (`executor: client`) | `SUCCESS` + `pendingToolCallIds` | tool call handles | `tool` messages |
| both in one status message | `INTERRUPT` (the outcome carries no pending list; pending calls are derived from the stream, per AG-UI) | both kinds | `resume[]` plus `tool` messages; the continuation message merges both, and all looked-up `taskId`s must agree |
| `AUTH_REQUIRED` | `INTERRUPT`, `reason` `authorization` | interrupt handle | `resume[]` entry |
| tier 1 agent (no tags) | never a tool call | n/a | `tool` messages ignored (documented lossiness, 1.2; an informational Zilla event for operators only) |

Handles carry a `kind` in the owner-local entry from v1 (I11), so adding tool-call handles later changes no existing entry.

### 8.4 Owner-local state, handles and the Redirect (revision 7)

The proxy owns the conversation state for each key and makes sure runs for a key reach the worker that holds it, by issuing
the engine `Redirect`, as mcp(server) does for sessions. Nothing is held by the client: no sealed ids, no vault dependency,
no dependence on how a client trims its history.

**One behaviour for the context id** (no `options.context`): the proxy never sends a client-chosen `contextId`. A thread's
first run sends none, which the A2A spec allows (the agent may generate one); the `contextId` the agent returns, if any, is
remembered by the owner under (identity, A2A agent slug, `threadId`) and sent on later runs. An agent that never returns a
`contextId` (the spec says it MAY generate one) simply stores nothing, and each run is then stateless on that side.

| Entry | Key | Value | Lifetime |
| --- | --- | --- | --- |
| context | (identity, A2A agent slug, `threadId`) | the `contextId` the agent issued | `thread.ttl`, sliding; least-recently-used eviction at `thread.capacity` (binding configuration properties) |
| interrupt handle | random 128-bit id | `{key, kind: interrupt, taskId, expiresAt}` | `handle.ttl` (binding configuration property); single use; a task interrupted again gets a new handle |
| tool-call handle (tier 2) | random 128-bit id | `{key, kind: tool, taskId, callId, expiresAt}` | same |

The map is worker-local (no locks, no cross-thread access), bounded, and swept by a timer on the worker's signaler plus lazily
on access. Handles are unguessable but are not capabilities: a lookup succeeds only under the same (identity, agent,
`threadId`) that issued the handle. Identity comes from the route's `with.identity` (`${guarded['jwt0'].identity}`, as
http-kafka's `overrides` does); a route with no guard has a null identity, so any caller who knows (agent, `threadId`) shares
that state, which is documented rather than hidden.

#### Placement and the receiver-issued Redirect

| Step | Detail |
| --- | --- |
| Key | identity (route `with.identity`), the A2A slug (after `with.agent`), `threadId` from `AguiBeginEx`; all three are known at Begin |
| Affinity | `affinityOf(key)`: by default `(affinity() with its worker bits cleared) + floorMod(placementHash(key), workers)`, with `workers` from `EngineConfiguration.workers()`; the engine's affinity value is `(nodeId << 24) + workerIndex` (`EngineWorker`, lines 331 and 2313) and `supplyInitialId(routedId, affinityId)` honours a local value below `workers`. A deployment replaces it with the pluggable `thread.affinity` property (multi-node, below). The canonical key string passed to it is the identity, the A2A slug and the `threadId` joined by U+001F (identity empty when unguarded) |
| Check | in `newStream`, when `affinityOf(key) != context.affinity()`, return a redirect handler that ignores every frame and, on Begin, answers `Redirect(affinity = affinityOf(key), extension = the Begin extension it received)` (below); the shape of `McpRedirectHandler` / `doRedirect` in `McpServerFactory`, with no window granted and no state touched |
| Difference from mcp | mcp's affinity is embedded in a session id it minted (`defaultSessionIdAffinity` parses the last 8 hex characters); ours is computed from a client-chosen key because `threadId` belongs to the client |
| Cost | none with one worker (`workers = 1` is always aligned); with W workers and round-robin accept, `(W - 1) / W` of runs take one in-process hop (Begin, Redirect, reset, Begin), the cost mcp pays for sessions on other workers. Same-key runs then execute on one worker, so the state needs no synchronization; they still interleave, and two concurrent first runs of a thread create two agent contexts (the first completion is kept, the other orphaned on the agent), which AG-UI clients normally avoid by serializing runs per thread |

Considered and not recommended: the initiator computing the affinity itself (agui(server) begins on the owner directly): it
saves the hop and the redirect handling, but puts the key and identity semantics in agui(server)'s configuration and only
helps initiators configured that way; a receiver redirect keeps the state owner in charge for any initiator.

#### S7: agui(server) handles `Redirect` (PR #2850)

agui works the way http and mcp do: the receiver echoes the Begin extension it received back in the `Redirect`
(`doRedirect(..., extension)` as `McpRedirectHandler` does), and the initiator re-begins from `redirect.extension()` exactly as
`HttpServerFactory.onRequestRedirect` does, so agui(server) keeps no copy of the `AguiBeginEx`. The k3po harness already echoes
the channel's Begin extension, and the scripts use the plain 8-byte form `read advise zilla:redirect <affinity>`.

Rationale, recorded: the aim that the initiator keeps no local copy of the Begin fields is already met by the echo. A typed
`AguiRedirectEx` would only add the ability for the receiver to amend, and that buys nothing now: agui(server) frames
`RUN_STARTED` / `RUN_FINISHED` from the ids it decoded from the request (`AguiServer.decodedThreadId`, `decodedRunId`,
`decodedParentRunId`, used by `AguiEventEncoder`), so `threadId`, `runId` and `parentRunId` cannot be amended, and agui-a2a
relays `agent` unchanged in v1. If a receiver-amend need appears later, a typed `AguiRedirectEx` can be added additively as a
new extension type beside `AguiBeginEx`. No idl, `AguiFunctions` or harness change.

**agui(server) must handle the `Redirect`**, mirroring http(server)'s `onRequestRedirect` (`HttpServerFactory`, lines
3257-3300 and 6563-6600):

1. `AguiStream.onAppMessage` handles `RedirectFW.TYPE_ID` (the type is generated from `core.idl`, as in mcp).
2. Reset the old reply (`doAppReset`) and abort the old initial (`doAppAbort`); the receiver's redirect handler ignores both. Record the redirect in `AguiState` (a bit, not a boolean).
3. Create a new `AguiStream` with `routedId = context.resolveRoutedId(oldRoutedId, newAffinity)` and `initialId = context.supplyInitialId(routedId, newAffinity)`; send Begin with `redirectedId = oldRoutedId`, `affinity = newAffinity` and `redirect.extension()` as the extension; replace `server.stream`. The `newStream` helper gains `redirectedId` and `affinity` parameters (it passes affinity 0 today).
4. agui(server) must retain the unsent request payload until the receiver accepts, and it already does (below).
5. Defensive guard, runtime only: a conformant receiver never redirects twice, after the reply has begun, or after it has granted window; that is off-spec for the engine itself, so no script asserts any behaviour for it. If it happens anyway, agui(server) aborts the stream (reset / abort, release the decode slot) with no HTTP status mapping. A unit test may cover the guard if a test is wanted; no `.rpt` script does (`specs/AGENTS.md`: scripts pin protocol behaviour, not implementation choices).

Payload retention and replay are feasible as-is, verified in `AguiServerFactory`: (a) `decodeStart`, `decodeNext`, `decodeId` and `decodeSkip` return the incoming `offset`, so the decoder only advances the JSON parser and never consumes buffer; (b) after `onDecodeBegin` the decoder is `decodeBody`, whose `doAppData` forwards `min(initialMax - initialNoAck - initialPad, remaining)`, which is zero until the receiver grants window; (c) `decodeNet` copies the unforwarded `[progress, limit)` into the decode slot, here the whole prefix, and the net window `decodeMax - decodeSlotReserved` stops granting once the slot is full, so any remainder stays upstream in flow control; (d) when the new stream grants window, `onAppWindow` calls `server.decodeNet`, which resumes `decodeBody` from the slot start; (e) a net End that arrived before the redirect is deferred by `doDecodeEnd` while the slot drains (`forwarding`), so the new stream still receives its End. Nothing is replayed explicitly: swapping the stream is enough. agui(client) (PR #2857) needs nothing: it receives `agui` streams, issues no `Redirect`, and starts HTTP client streams that never redirect.

Spec scripts (A9), plain form copied from http `redirect/request.redirect/server.rpt` (receiver: `read advise zilla:redirect 0x...L`, then accept the second stream), not run here: `run.redirect` (the second accept begins with the same ids, matched with `agui:matchBeginEx()`, the request body arrives on it, an ordinary reply follows) and `run.redirect.100k` (body larger than the decode slot).

#### Volatility and the implicit compaction rule (revision 8)

Worker-local state is lost on restart, rescale or rehash (the placement hash depends on `workers`), TTL expiry and eviction.
The next run then looks like a first turn and, because only the latest turn is forwarded, the agent starts a new context.
mcp avoids this because an unknown MCP session is an error the client recovers from by re-initializing; AG-UI has no such
contract. The proxy does not fail the run and keeps no per-agent flag: it always starts a new context when none is
remembered, and treats the loss as an **implicit compaction of the agent's memory to the latest message**, conveyed to the
client with `MESSAGES_SNAPSHOT` so the client's transcript matches what the agent now knows.

Trigger, all three:
1. `messages[]` holds an assistant message ahead of the trailing user turn(s), so this is not a first turn;
2. no `contextId` is remembered for (identity, agent, `threadId`);
3. the agent's reply carries a fresh `contextId`: evidence that the agent is stateful and has started a new context. A stateless agent never returns one, so it never triggers.

When it fires, before the first reply text or data frame the proxy emits the state data frame (`AguiStateDataEx` type
`MESSAGES_SNAPSHOT`) whose payload is the JSON messages array holding **only the trailing user message(s), as received**
(same ids, full content including multimodal parts), then streams the reply as normal, stores the new `contextId`, and raises
an informational Zilla event (`context reset`), operator-visible only (a `BindingEvent` through the engine event system, seen in exporters and logs, never by the AG-UI client; it exists for observability, for example counting the threads that lost state after a restart). The existing spec scenario `run.messages.snapshot` (network and application) already
pins the protocol side, so no new agui script is needed; the encoder writes the payload as the `messages` member
(`AguiEventEncoder`, JSON member mode).

Why a snapshot: the reference client replaces its local copy with it (verified in 9.10): a user, assistant or tool message not
in the snapshot is dropped, a message in it is updated in place, and only client-only `activity` / `reasoning` messages are
kept. The earlier statement that a snapshot only merges was never in this document, and it would be wrong anyway.

| Aspect | Detail |
| --- | --- |
| Holding the first frame | no hold is needed: the proxy writes no agui data frame before the first a2a data frame, whose `A2aTaskDataEx` / `A2aMessageDataEx` / `A2aStatusUpdateDataEx` / `A2aArtifactUpdateDataEx` already carries `contextId`, so conditions 2 and 3 are decided from the extension alone, before any body is parsed. The agui reply Begin (so `RUN_STARTED`) goes out as before; the snapshot is the first data frame after it. An error, abort or reset before the first a2a data frame emits nothing |
| Re-emitting the user message | condition 1 is known when the request is decoded (the proxy parses `messages[]` to build the A2A message); condition 2 is known at the owner. When both hold, the proxy **must be able to retain** the byte ranges of the trailing user message objects until the first a2a data frame, and it does so by keeping the decode slot it already holds for the request (no copy, no second allocation); otherwise it releases the slot as soon as the A2A request is sent. The snapshot payload is `[` + those raw objects + `]`, written as state data frames (fragmentable, as `run.state.snapshot.100k`) |
| Fail closed on retention | if retention cannot be arranged at request time, the run is **rejected before anything is sent to the agent**, so nothing executes and nothing is wasted: the reset is conveyed or the run does not happen. The rejection takes the normal pre-reply path, an `AguiResetEx`, which agui(server) turns into an HTTP status (`onAppReset` -> `doEncodeResponseRejected`, which accepts any status). Chosen: **503 with code `retention_unavailable`, message "Service Unavailable"**, retryable. Mapping rationale, mirroring the existing agui rejects: an agent that cannot be reached is 502 (`run.unavailable`, "Bad Gateway"), but this is local overload, not a bad upstream, so 503; no `Retry-After` can be sent because `AguiResetEx` carries only status, code and message, so retry timing is the client's. The failure points are slot exhaustion when acquiring the decode slot or the encode slot (the second is needed to build the A2A message while the decode slot is retained); all occur before the agent is contacted. A request larger than the slot is a separate, permanent rejection: 400 `Request too large`, as agui(server)'s `request.too.large` does (below) |
| Cost and window | retention extends the lifetime of a slot that is already held, from "until the A2A request is sent" to "until the first a2a data frame" (time to first event: typically sub-second to a few seconds for an LLM agent, unbounded in the worst case until the a2a stream times out). It is not an extra allocation. Stateless agents satisfy conditions 1 and 2 on every turn after the first, so all their turns hold the slot for that window, though no snapshot would have fired. The pool is `worker.capacity` slots per worker, 32 KiB each by default (`buffer.slot.capacity`), shared by every binding on the worker; the added occupancy is about the arrival rate of such runs times the time to first frame (illustrative: 100 runs a second times 2 s is 200 slots, 6.4 MiB). Under pool pressure the longer hold makes it more likely that a new arrival cannot acquire a slot and is rejected 503, stateless agents included; the rejected run is the new arrival, not one already retaining |
| Request size | the whole request is buffered in one decode slot to build the A2A message, so its bound is the slot capacity (32 KiB by default, engine-wide): larger requests are rejected 400 `Request too large`. AG-UI sends the full history, so this bites after a dozen turns or with attachments; raise `buffer.slot.capacity` for AG-UI deployments. A history-skipping streaming decode would lift it (OPEN-16). Trailing user messages are part of the request, so they can never exceed the slot on their own |
| Ordering | the snapshot is emitted before any `TEXT_MESSAGE_START`, never inside a stream. AG-UI's `compactEvents` reorders a snapshot that interrupts a text stream to after the stream's events, which would overwrite the reply on replay; a snapshot ahead of the stream is unaffected, which is why it must precede the first reply frame |
| Interrupt, tool and handle state | handles are worker-local and not carried in message ids, so the snapshot cannot invalidate them. When the trigger fires the state was lost, so there are no open handles to preserve; a run whose first event is `INPUT_REQUIRED` with a fresh `contextId` emits the snapshot, then ends `INTERRUPT` with a new handle stored under the new `contextId`. A run with `resume` entries whose handles are unknown has them treated as unrecognised (422 `invalid_run` when nothing else is sendable) and emits no snapshot |
| Why handles stay worker-local | state carried in message ids is fragile: the snapshot replaces ids (only ids in the snapshot survive), so any id-borne handle or context would be erased exactly when it is needed. This is another reason, beside history trimming, for owner-local state |
| Trimmed histories | a history trimmed by the client (CopilotKit's `messageFilter`) holds no assistant turn, so condition 1 fails and the snapshot never fires; the client's transcript is intact and the agent's lost memory is not conveyed (silent). This is a known limitation: the rule cannot detect what the history does not show, and it needs no detection while the state is intact |
| Spurious reset | an agent that returns a `contextId` on every message without keeping memory satisfies condition 3 on every run; after a restart, the first run of each thread then triggers one reset that the agent did not need. Known limitation, one per thread per restart |
| Unknown handle | an unknown, expired or foreign `resume` handle, or a tier-2 tool-call handle, rejects the run **before the agent is contacted** with 409 `handle_unknown` ("Interrupt no longer available"); the client can drop the entry and resend. AG-UI says an entry for an interrupt the producer never raised should be ignored, but the proxy cannot tell "never raised" from "state lost", and silently dropping the user's answer is data loss, so it fails closed |

The agent-side counterpart is tier 2: an agent that compacts its own memory can convey it with a tagged `state` part of type
`messages`, mapped to `MESSAGES_SNAPSHOT` (8.2); the proxy-originated reset above is its tier-1 form.

#### Fail-closed audit

Every path that lets a run through must deliver what the doc advertises; a run that cannot is rejected before the agent is
contacted. The Zilla events are for operators only and never compensate for the client.

| Path | Behaviour |
| --- | --- |
| reset snapshot cannot be retained | rejected 503 `retention_unavailable`, retryable |
| request larger than the slot | rejected 400 `Request too large` |
| unknown, expired or foreign handle | rejected 409 `handle_unknown` |
| reserved key in `forwardedProps` | rejected 400 `reserved_key` |
| part outside `acceptedOutputModes` | delivered as `agent.data` / `agent.file`, (no option) |
| tier-1 `tools` and `tool` messages | documented lossiness (1.2), not a warning-compensated degradation |
| unknown tier-2 tags | stripped as AG-UI's processing model requires |
| **undetectable, known limitations** | history trimmed by the client (no assistant turn, so the reset cannot fire and the agent's lost memory is not conveyed); an agent that returns a `contextId` on every message without memory (one spurious reset per thread per restart) |

#### Durable tier: documented follow-up, not v1

A store (F3, post-v1) would make the engine store (`StoreHandler`: async `get`, `put`, `putIfAbsent`, `getAndDelete`, with TTL)
the authoritative copy and the worker-local map the fast path, for durability across restart and rescale. Keys
`agui-a2a/ctx/<hash(identity, agent, threadId)>` (value `{contextId}`) and `agui-a2a/h/<handle>` (value `{keyHash, kind,
taskId, callId?}`); the values hold A2A ids and never reach the client; store TTLs follow the state TTLs. Restart restores
lazily by read-through on a local miss (the run waits in the decode slot while the store answers, the pattern of
a2a(proxy)'s card cache); handles are consumed with `getAndDelete`; with a shared store the redirect is needed only for the
cache hit. v1 keeps the state behind one interface so this can be added without rework.

#### Multi-node: a pluggable `thread.affinity` property (no engine change)

Mirror mcp, whose `McpConfiguration` has pluggable properties `session.id` and `session.id.affinity`: each is decoded from a
`Class::method` static handle (`decodeSessionIdAffinity` resolves `MethodHandles.publicLookup().findStatic(class, method,
(String) -> long)`), and the defaults embed and parse the affinity in the session UUID, so a cluster-aware deployment plugs its
own behaviour with no binding code change. agui-a2a defines the equivalent in the same style: the binding configuration property
`thread.affinity` (`zilla.binding.agui.a2a.thread.affinity`), a `ToLongFunction<String>` over the canonical key string decoded from
a `Class::method` static handle of signature `long method(String)`. When it is not set, the binding computes the local default
above; a cluster deployment plugs a function that also sets the node byte (`affinity >>> 24`). The binding code only uses the
returned affinity with `context.resolveRoutedId` and `supplyInitialId` and in the `Redirect`; the engine is unchanged (the
former engine item S8/A11 is dropped). Per the coordinator, zilla-plus's `HazelcastRouterContext.resolveRoutedId`
(`runtime/router-hazelcast`) treats the node byte as the owner and routes non-local affinities to the cluster client binding;
that is unverified here, because no zilla-plus checkout is available. The state itself stays worker-local per node, so
cluster durability is the store follow-up (F3), and the zilla-plus function is F10.

#### What the design relies on

| Question | Source | Consequence |
| --- | --- | --- |
| Can a caller resume or cancel someone else's `taskId`? | A2A section 13.1: servers MUST authorize every task operation per caller | the client never names a task: handles resolve only under its own (identity, agent, `threadId`), and an agent that complies adds a second barrier |
| Can a caller claim someone else's `threadId`? | the `contextId` is issued by the agent and stored under the caller's own key | another caller's `threadId` selects nothing of theirs, and no client-chosen `contextId` is ever sent |
| Does agui(server) authenticate the caller? | yes, `options.authorization` and the route guard | identity is the authenticated one; with no guard the state is shared by key (documented) |

### 8.5 Spec changes re-checked against the full design

S1-S5 stand unchanged (route key in `AguiBeginEx` and the agui routes, a2a task-state scripts, client abort is detach). S6 is
optional; S7 (agui(server) handling `Redirect`) is new and required. Everything
deferred was walked for an idl, `AguiFunctions` or `A2aFunctions` change that is cheaper now than later:

| Candidate | Needed by | Verdict |
| --- | --- | --- |
| **S6: reply begin reports the activated `A2A-Extensions`** (scripts and a2a(client) / a2a(server) behaviour only; the reply begin carries the existing `extensions` member of the begin case) | extensions are post-v1, but the change is cheap while the a2a PRs are open | **adopted** (user decision): no idl change; scripts in #2825, behaviour in #2828 and #2829 (A8) |
| **S7: agui(server) handles `Redirect`**, two scripts | owner-local state (8.4) | **required for the state design**: runtime change in `AguiServerFactory` and `run.redirect` / `run.redirect.100k`, PR #2850; **no idl, `AguiFunctions` or harness change** (the Begin extension is echoed, as in http and mcp) |
| `AguiBeginEx.operation` (run or connect) | reconnect | no: no AG-UI operation exists to name, and appending a member later is additive |
| `pendingToolCallIds` as an `AguiEndEx` member | F1 | no: `AguiEndEx.detail` already carries any `RUN_FINISHED` member, and the encoder copies `outcome` specifics from it |
| New `AguiDataEx` cases | F2 | no: every event family is already in `agui.idl` |
| `A2aArtifactUpdateDataEx` flags, `taskId` in the message begin, `realm` / `scope` in `AguiResetEx` | tier 2 | still no (4.2): tier 2 parses part tags from the body anyway, so the DataEx flags still buy nothing |
| `AguiFunctions` / `A2aFunctions` additions | F1 / F2 / S7 scripts | no: builders and matchers exist for tool call, tool result, state, activity, reasoning, step, subagent, custom, raw, `extensions` and `matchDataEx` of each a2a case |

Net: S6 (adopted, a2a scripts and behaviour) and S7 (required: runtime and two scripts on PR #2850) are what the full design
needs. No idl, `AguiFunctions`, `a2a.idl`, `A2aFunctions`, engine or harness change beyond S1, and none for the implicit
compaction: `AguiStateDataEx` already has `MESSAGES_SNAPSHOT` and the scenario `run.messages.snapshot` already exists.

### 8.6 v1 shape, consolidated

| Concern | v1 |
| --- | --- |
| Binding config | no yaml `options`; routes with `when.agent`, `with.agent`, `with.identity`, `guarded`, `exit`; binding configuration properties `thread.ttl`, `thread.capacity`, `handle.ttl`, `thread.affinity` |
| Request | trailing user messages only (documented lossiness), resume to the `taskId` behind the handle, the remembered `contextId` (never a client-chosen one), non-empty `state` + `context` as one advisory `data` part, `forwardedProps` as top-level `Message.metadata` (no run ids), `acceptedOutputModes` `text/plain`; `system` / `developer` dropped, `tools` and `tool` messages parsed and ignored |
| Response | text, `CUSTOM` `agent.data` / `agent.file`, neutral errors, `status.message` text on `COMPLETED` with digest dedupe, `INTERRUPT` with opaque handles, `CANCELLED`, no A2A details on the client stream |
| State | owner-local map, affinity redirect echoing the Begin extension, volatile; implicit compaction by `MESSAGES_SNAPSHOT` when the context is lost (8.4) |
| Not in v1 | every extension (E1, F1, F2, F5, F8), working status as activity (F4), durable store (F3), A2UI plug-in (F7), streaming request decode (F9, OPEN-16), the zilla-plus cluster affinity function (F10); reconnect (F6) is not filed |
| Constraint v1 puts on later work | state behind one interface so a store can sit under it; handles carry `kind`; the `agui` metadata key stays reserved; `agent.*` custom names reserved |

### 8.7 Issue changes in this revision

Superseded by section 10, which lists every issue with repository, body, acceptance, dependencies and class.

## 9. Precedent and revisions (revision 5)

Pins: ag-ui-protocol/ag-ui `7e7c1109`, CopilotKit/CopilotKit `621f0ec6`, a2aproject/A2A `d924e07b`. Items marked
"reported" come from the coordinator and were not re-verified (the AG-UI issue tracker is outside this session's scope).

### 9.1 Precedent: `@ag-ui/a2a` and what we do differently

`integrations/a2a/typescript/src/{agent.ts,utils.ts}` at ag-ui `7e7c1109` is an experimental client-side library: one
`A2AClient` per agent URL, no routing or guard concept, written against the A2A JS SDK's v0.3 shapes (`kind`,
`mimeType`, `blocking`). Verified facts and our position:

| Precedent behaviour (verified) | agui-a2a | Why we differ, or agree |
| --- | --- | --- |
| `contextId: input.threadId`, verbatim (`agent.ts` `createSendParams`) | first run sends none, the agent-issued id is remembered owner-locally, no option (9.6) | differ: the spec says SHOULD NOT for client-generated ids |
| sends only the latest user message; drops `system` / `developer` (mapped to role `user` in `ROLE_MAP`, `utils.ts`, but only the latest user message is sent) | trailing user messages; `system` / `developer` dropped or advisory (8.1) | agree on the mechanism, add opt-in and tier 2 |
| streams, then falls back to blocking on **any** error, including after events were already emitted (`run()` try / catch around `streamMessage`) | card-driven choice (1.4); no fallback after the first event | the precedent can emit a run twice; a fallback is only safe before anything was sent |
| always ends `RUN_FINISHED`; a failed task is not `RUN_ERROR`, no interrupt, no cancel | `RUN_ERROR`-equivalent abort, `INTERRUPT`, `CANCELLED` (1.3) | AG-UI's outcomes exist to be told apart (`lifecycle.mdx`) |
| `status.message` text -> `TEXT_MESSAGE_CHUNK`; non-`input-required` states share one aliased message id `<taskId>:status`, text diffed against the cumulative text | COMPLETED text deduplicated by digest; working text retained as a fallback reply (9.2) | same observation (agents put replies there); explicit per-state rules instead of aliasing every state |
| `artifactUpdate` and `task` -> `RAW` events; artifacts are not mapped to text | artifact text -> text messages; `task` snapshots reconciled by `artifactId` (1.3) | the A2A spec says results SHOULD be artifacts |
| `acceptedOutputModes: ["text"]` (not a media type) | `["text/plain"]` plus enabled plug-in media types (9.3) | the spec's field is a list of media types |
| always declares the A2UI extension through `X-A2A-Extensions` (`https://a2ui.org/a2a-extension/a2ui/v0.8`) | extensions only when enabled and declared in the card (9.5) | opt-in, and not tied to one extension |
| A2UI `data` parts (`beginRendering`, `surfaceUpdate`, `dataModelUpdate`) -> `ACTIVITY_SNAPSHOT` / `ACTIVITY_DELTA`, `activityType: "a2ui-surface"`, content `{operations}`; `forwardedProps.a2uiAction` -> a `data` part of `application/json+a2ui` | same pair as a plug-in (9.5), with the content shape CopilotKit's renderer reads | see the mismatch in 9.5 |
| tool messages -> `data` parts `{type: "tool-result", toolCallId, payload}` with role `agent`; assistant tool calls -> `{type: "tool-call", id, name, arguments}`; inbound parts of those shapes -> `TOOL_CALL_*` / `TOOL_CALL_RESULT` | the same data shapes in tier 2 (8.2), role `ROLE_USER` for client-to-agent, opaque handles as ids on the AG-UI side | agents written for the precedent interoperate; the agent never sees a handle |
| `RUN_STARTED.threadId` = the input's; agent never adopts a server id | same | verified below |
| ag-ui issue #762 (reported): late-bound `threadId`, interrupts on `input-required`, task / artifact to state / activity, opt-in `system` / `developer` cues, `getTask` / resubscribe, stop leaking `threadId` / `runId` into A2A metadata | interrupts (1.3), opt-in cues (8.1), no run ids in metadata (9.4), resubscribe not needed (8.3); late binding replaced by owner-local state (8.4, 9.6) | late-bound `threadId` cannot work: `AbstractAgent` owns `threadId` (`sdks/typescript/packages/client/src/agent/agent.ts` lines 116-266, getter creates a uuid, setter assigns) and `apply/default.ts` handles `RUN_STARTED` only for subscriber callbacks and the input echo, with no thread id adoption |

Also verified: ag-ui `middlewares/a2a-middleware` exists as a second precedent (an orchestrator exposing
`send_message_to_a2a_agent` as a tool); it is not an agent-to-A2A mapping and changes nothing here. The A2A spec nuance the
coordinator recorded is right: verbatim `threadId` is a SHOULD NOT with an explicit reject fallback
(`docs/specification.md` lines 586-628), not a violation, and "non-compliant" overstates it.

### 9.2 `status.message` (decided: map it)

Where agents put the reply: no script in the a2a spec puts text in `status.message` (replies are `artifactUpdate`,
`task.artifacts`, or a task-less `message`); the A2A spec says `Messages SHOULD NOT be used to deliver task outputs`, calls
status messages informational, and says they are not a reliable delivery mechanism (`specification.md` section 3.7); the
precedent shows real agents do put replies there. So the mapping is defined per state and kept conservative:

| State carrying the message | Text parts become | Non-text parts | Notes |
| --- | --- | --- | --- |
| `COMPLETED` (on a `statusUpdate` or in a final `task` snapshot) | an assistant text message, `messageId` = the A2A message id (generated if absent or already used) | `CUSTOM` per 1.3 | deduplicated below |
| `FAILED`, `REJECTED` | the abort `message` | ignored | never the transcript |
| `INPUT_REQUIRED`, `AUTH_REQUIRED` | the interrupt `message` | ignored in tier 1 | never the transcript, even when the same text was streamed as an artifact |
| `CANCELED` | ignored | ignored | `cancelled` carries no message |
| `WORKING`, `SUBMITTED` | retained as `pendingReply` (the latest text-bearing one, replacing the previous) | ignored | emitted only by the fallback rule |

Deduplication (per run, no extra buffering): every text message the proxy emits keeps a running SHA-256 of its text as it
streams, and the run holds the set of finished digests. Then:
1. A `COMPLETED` `status.message` text whose digest is already in the set is skipped.
2. A final `Task` snapshot is reconciled by `artifactId` (1.3); its `status.message` goes through rule 1; its `history` is ignored (`historyLength` is 0).
3. A task-less `message` event records its digest.
4. Fallback: at `COMPLETED`, if no text message was emitted in the run and `pendingReply` exists, emit it once as a text message; discarded on every other end state.

The table and the fallback are hardcoded, with no option; F4 (post-v1) would add `activity`. The fallback assumes each working message is cumulative or final: an
agent that sends incremental fragments loses the earlier ones (OPEN-10; the precedent assumes cumulative text too, by
diffing). Spec: a2a scripts for this belong in S4 (scripts only, a2a PRs).

### 9.3 `acceptedOutputModes` and unaccepted output (decided)

| Aspect | Design |
| --- | --- |
| Value | `SendMessageConfiguration.acceptedOutputModes` (media types, per `a2a.proto`): `["text/plain"]` by default; each enabled content plug-in (9.5) adds its media type (A2UI: `application/json+a2ui`); nothing is added by default |
| Source | the proxy's own capabilities, not intersected with the card (the agent tailors; asking for more than an agent offers is harmless) |
| Option | none in v1: the accepted set is `text/plain` only; `options.content.<name>` arrives with F7 (post-v1) |
| Unaccepted part | a part whose media type is outside the set (text without a media type is `text/plain`, data without one is `application/json`) is not failed and not dropped: it is delivered as `agent.data` / `agent.file`, no option; an informational Zilla event records it for operators |
| Tier 2 | tagged parts of an activated extension are accepted by that activation and bypass the check |

### 9.4 `forwardedProps` and metadata hygiene (decided)

| Rule | Detail |
| --- | --- |
| `forwardedProps` object | becomes `Message.metadata` verbatim at the top level, minus reserved keys |
| `forwardedProps` non-object (AG-UI allows any JSON but null) | `{"forwardedProps": value}` |
| Reserved keys | `agui` and every extension URI the proxy activates, a run whose `forwardedProps` contains one is rejected before the agent is contacted with 400 `reserved_key`, so a client cannot forge the tier-2 carrier or extension-defined fields such as `instructions` or `tools`, and nothing the client sent is altered |
| Run ids | `runId` and `parentRunId` are not forwarded: A2A `taskId` / `contextId` are the equivalents, correlation is in Zilla events and trace ids (a precedent issue #762 request, reported) |
| `messageId` | the last trailing user message id, so a retry is idempotent; the only client id on the A2A side |
| Where `forwardedProps` lives in tier 2 | top level, **not** inside `agui`. Reason: it is the application-to-agent contract (the precedent reads `a2uiAction` from it), so putting it under a proxy-owned key would make agents depend on our extension to read their own application props; the carrier holds only members the extension defines (`instructions`, `tools`, `context`, `state`) |
| A plug-in that consumes a prop | removes it from the metadata it forwarded (A2UI: `forwardedProps.a2uiAction` becomes a `data` part, not metadata as well) |

### 9.5 Extension strategy: carrier plus content plug-ins

Recommendation: do not be tied to A2UI. Two layers that share nothing:

| Layer | What it is | Who defines it | Opt-in | Negotiation |
| --- | --- | --- | --- | --- |
| (a) AG-UI carrier extension (E1) | control and flexibility: tools, state, context, reasoning, activity, steps, interrupts, advisory `system` / `developer` | us (Aklivity-owned URI, metadata key `agui`, 8.2) | post-v1 | card lists the URI -> `extensions` BeginEx |
| (b) content plug-ins | a content type an agent produces or consumes, mapped to an AG-UI activity type | the content type's own specification (A2UI first; Open-JSON-UI, MCP Apps later) | post-v1 (F7) | feeds `acceptedOutputModes` (9.3) and adds the plug-in's A2A extension URI to `extensions` when the card declares it |

A plug-in is defined by: media type(s), A2A extension URI(s) and the version pinned, the AG-UI `activityType`, the mapping in
both directions, and the prop it consumes (A2UI: `forwardedProps.a2uiAction`). Adding Open-JSON-UI or MCP Apps later needs
a new plug-in definition and no new carrier, no idl change and no change to tier 1.

Verified against CopilotKit `621f0ec6` (what the renderers expect):

| Content | Activity type and shape | Source |
| --- | --- | --- |
| A2UI (renderer built in when `a2ui` is enabled) | `activityType: "a2ui-surface"`, content `{a2ui_operations: [...], ...lifecycle}`; the container key is `a2ui_operations`, matching `@ag-ui/a2ui-middleware` and the Python `copilotkit.a2ui`; operations use `createSurface` / `updateComponents`; `messageId` `a2ui-surface-<key>` | `packages/react-core/src/v2/a2ui/A2UIMessageRenderer.tsx` (container key, schema, `activityType` line 252); `middlewares/a2ui-middleware/src/index.ts` (`A2UIActivityType`) |
| CopilotKit's own generative UI | `generative-ui-surface` (and `generative-ui-function-call`), content `{surfaceId, target, catalogId, operations, status, rejected}`, `A2UI_VERSION` `v1.0` | `packages/shared/src/generative-ui/types.ts`, `runtime/.../surface-writer.ts` |
| Open generative UI | `open-generative-ui` | `packages/react-core/src/v2/components/OpenGenerativeUIRenderer.tsx` |
| MCP Apps | `mcp-apps` | `packages/mcp-apps-renderer/src/constants.ts` |

Mismatch to settle (OPEN-11): the `@ag-ui/a2a` precedent emits `a2ui-surface` with content `{operations}` and A2UI v0.8
operations (`beginRendering`, `surfaceUpdate`, `dataModelUpdate`), while CopilotKit's current renderer reads
`a2ui_operations` with `createSurface` / `updateComponents`. The plug-in must emit the shape the renderer reads; which A2A-side
A2UI generations it accepts is pinned by the extension URI version. Unverified: whether CopilotKit's renderer also accepts the
v0.8 operations, whether any CopilotKit middleware translates A2A `data` parts (the A2UI middleware works on tool calls and
explicit `a2ui_operations`, not A2A parts), and the A2A-side mapping for `mcp-apps` / `open-generative-ui` (names only).

Sequence: F7 (A2UI plug-in) right after v1 because it needs no carrier; E1 and tier 2 after. OPEN-8 revisited: the carrier URI
and key stay Aklivity-owned and `agui`; plug-in URIs and media types stay with their owners, so there is nothing to
reconcile between layers. Agents written for `@ag-ui/a2a` that do not declare our URI stay tier 1 (their tool-call parts
arrive as `agent.data`); declaring the URI in the card, with the same part shapes, makes them tier 2.

### 9.6 Context id: one behaviour (revision 7)

Spec facts (`specification.md` lines 586-628, verified): an agent MAY generate a `contextId`, MAY accept a client-provided one,
MUST reject (and not substitute) one it cannot accept; clients SHOULD NOT send one unless they understand the server;
server-generated ids SHOULD be opaque to clients; a message with a `taskId` and no `contextId` has it inferred.

The behaviour (8.4): never send a client-chosen `contextId`; send none on a thread's first run; remember the one the agent
returns under (identity, agent, `threadId`) in owner-local state and send it afterwards; an agent that returns none stores
nothing. There is no option.

Why the state is held on the proxy: the AG-UI client keeps assistant messages with the ids the producer gave
(`apply/default.ts` around line 518) and sends the whole thread, but CopilotKit's `messageFilter`
(`packages/core/src/agent.ts` lines 121-123, `core/message-filter.ts`) rewrites the outbound history for backends that store the
conversation, its own documentation using `messages.slice(-1)`; an A2A agent is exactly such a backend, so any scheme that
needs the client to send something back would silently lose the context.

Considered and rejected:

| Alternative | Why not |
| --- | --- |
| `thread`: `threadId` verbatim | a client-chosen id the spec says clients SHOULD NOT send; an agent that cannot accept it rejects the run; no isolation beyond the agent's own |
| `none`: never carry a context | loses the conversation; the issued context already gives that when an agent returns none |
| `scoped`: `<identity>/<threadId>` | still client-originated; isolation is already part of the owner key |
| `masked`: deterministic keyed mask of `threadId` | still client-originated, buys nothing the key does not, and needs a vault primitive the SPI lacks |
| `server`: contextId sealed into ids the client round-trips | breaks silently under history trimming (the `messageFilter` case) and needs the vault |
| state round trip: a blob in AG-UI `state` | leaks into application state, collides with the app's schema, dropped by apps that replace state wholesale |
| cookie on the SSE response | browser-only, size-limited, and needs response headers on the `agui` stream (an idl change) |
| a store under the proxy | the durability answer, documented as the follow-up F3, not a different behaviour |

OPEN-1 and OPEN-9 are resolved by this section.

### 9.7 Precedent-derived items, where each landed

| Item | Landed in |
| --- | --- |
| (1) `status.message` | 1.3 rows, 9.2, I6, S4 |
| (2) A2UI and content types | 9.5, F7 (post-v1), OPEN-11 |
| (3) tool part shapes | 8.2 part tags, 8.3 steps 2 and 6, F1, E1 |
| (4) `acceptedOutputModes` | 1.2 `SendMessageConfiguration` row, 9.3, I5 |
| (5) metadata hygiene | 1.1 / 1.2 rows, 9.4, E1 |
| (6) context late-binding | 9.1, 9.6, 8.4 |

### 9.8 Spec changes after revision 10

S1-S5 unchanged; **S6 adopted** (a2a scripts and behaviour, no idl); **S7**: agui(server) handles `Redirect`, re-beginning from
the echoed `redirect.extension()`, plus `run.redirect` and `run.redirect.100k` (PR #2850), no idl, `AguiFunctions` or harness
change. S8 (engine key-to-affinity) and S9 (harness) are dropped. No `a2a.idl`, `A2aFunctions`, `agui.idl` or `AguiFunctions`
change beyond S1 results from any revision (the implicit compaction reuses `run.messages.snapshot`). The issue list is in section 10.

### 9.9 Verified and unverified

| Verified (path@pin) | Unverified |
| --- | --- |
| precedent behaviours in 9.1 (`integrations/a2a/typescript/src/{agent.ts,utils.ts}`@ag-ui `7e7c1109`) | ag-ui issue #762 text (reported) |
| no thread id adoption (`apply/default.ts`, `agent/agent.ts`@ag-ui `7e7c1109`) | whether CopilotKit's `a2ui-surface` renderer accepts A2UI v0.8 operations |
| CopilotKit activity types and the `a2ui_operations` container key (`A2UIMessageRenderer.tsx`, `generative-ui/types.ts`, `OpenGenerativeUIRenderer.tsx`, `mcp-apps-renderer/src/constants.ts`@CopilotKit `621f0ec6`) | any A2A-side mapping for `mcp-apps` and `open-generative-ui` |
| `messageFilter` (`packages/core/src/agent.ts`, `core/message-filter.ts`@CopilotKit `621f0ec6`) | how many deployments set a `messageFilter` |
| A2A `contextId` rules, `status.message` guidance, extension rules (`docs/specification.md`, `docs/topics/extensions.md`@A2A `d924e07b`) | whether real agents send incremental or cumulative working text (OPEN-10) |
| `Redirect` and `Begin.redirectedId` in `core.idl`; mcp(server) redirect (`McpServerFactory` `McpRedirectHandler`, `doRedirect`, `isSessionIdAligned`; `McpConfiguration.defaultSessionIdAffinity`; spec script `lifecycle.redirect.session`) at this checkout | the k3po directive direction for the redirect scripts (copy from the precedents, not run) |
| http(server) redirect (`HttpServerFactory.onRequestRedirect`; http spec `redirect/request.redirect`) at this checkout | any router implementing `resolveRoutedId` across nodes (zilla-plus `router-hazelcast`, not available) |
| engine affinity layout `(nodeId << 24) + index`, `supplyInitialId(routedId, affinityId)`, `resolveRoutedId` default identity, `Router` / `RouterContext` SPI, only a test router in OSS (`EngineWorker`, `EngineContext`, `router/`) | zilla-plus CLAUDE.md "Reactor Thread Safety" text |
| k3po harness: `read advise zilla:redirect` is `ZillaSource` -> `ZillaStreamFactory.doRedirect` (affinity from exactly 8 bytes, extension = the channel's Begin extension); mcp and http echo the Begin extension; no `RedirectEx` anywhere (`git grep`); `AguiServer.decodedThreadId` / `decodedRunId` / `decodedParentRunId` feed `AguiEventEncoder` | that the redirect scripts for agui run green (not run) |
| agui(server) request path: decoders return `offset`, `doAppData` is window-bound, `decodeNet` keeps the unforwarded prefix in the decode slot, `onAppWindow` resumes `decodeNet` (`AguiServerFactory`) | behaviour under a redirect that arrives after a partial net End with a large body (covered by `run.redirect.100k`, not run) |
| engine `StoreHandler` (async `get`, `put`, `putIfAbsent`, `getAndDelete`, TTL) | |

### 9.10 `MESSAGES_SNAPSHOT` in ag-ui and CopilotKit: verified, verdict ALIGNS

Pins: ag-ui-protocol/ag-ui `7e7c1109`, CopilotKit/CopilotKit `621f0ec6`. Verdict: **ALIGNS** with the ag-ui default apply for
everything the design relies on; the unverified residue is listed at the end.

| Question | Finding (file:line) |
| --- | --- |
| ag-ui reducer | `sdks/typescript/packages/client/src/apply/default.ts` `case EventType.MESSAGES_SNAPSHOT` L1059-1150: `messages.filter(m => snapshotMap.has(m.id) \|\| isPreservedClientOnly(m)).map(m => snapshotMap.get(m.id) ?? m)` (about L1119-1127), then new snapshot messages are appended, then `keepToolResultsWithCalls`. User, assistant and tool messages not in the snapshot are dropped; matching ids update in place; `activity` / `reasoning` messages are kept unless the snapshot itself carries that role (L1087-1117) |
| (a) CopilotKit uses the default apply | the browser agent is an ag-ui `AbstractAgent`; CopilotKit only subscribes: `packages/core/src/core/state-manager.ts` L393-405 `onMessagesSnapshotEvent` calls `handleMessagesSnapshot` (L612-631, associates message ids with runs) and `pruneRawEvents`, neither sets `stopPropagation` or edits `messages`, so the reducer's replacement stands. CopilotKit itself relies on replace semantics: `packages/core/src/agent.ts` L738-756 resets the view for a connect replay with `{MESSAGES_SNAPSHOT, messages: []}`. The React chat state follows `agent.messages` (`react-core/src/v2/hooks/use-agent.tsx` L398 `onMessagesChanged`) |
| (b) persistence | `InMemoryAgentRunner` stores per run the compacted events (`packages/runtime/src/v2/runtime/runner/in-memory.ts` L966-980, `compactEvents`) and a thread-level `messagesSnapshot = [...request.agent.messages]` after the run (L975-977, replaced at L514-530), where `request.agent.messages` is the post-apply list, so the snapshot replaces rather than duplicates. Replay (`connect`, L1114-1171) re-applies the compacted events of all runs in order, so a replay reaches the same transcript as the live run (the run's `RUN_STARTED.input` messages, then our snapshot, then the reply). `sqlite-runner/src/sqlite-runner.ts` stores events per run the same way |
| (c) next run's `messages` | the browser's own list, post-snapshot: `AbstractAgent.prepareRunAgentInput` (`sdks/typescript/packages/client/src/agent/agent.ts` L611-631) deep-clones `this.messages`, strips `activity`, re-pairs tool results; CopilotKit's `#prepareOutboundMessages` (`packages/core/src/agent.ts` L335-354) then applies the optional filter to that list. The runner's stored history is not used for the next run (the runtime takes `request.input.messages`; `messagesSnapshot` is served only by the thread-messages endpoint, L1273) |
| (d) re-injection | none: the filter and `ɵrepairToolCallPairs(kept, full)` are anchored on `full`, the client's post-snapshot list, and restore only tool-call pairs from it; nothing dropped by a snapshot comes back |
| (e) reasoning and activity | client-only `activity` and `reasoning` messages survive a snapshot that carries none of their role (default.ts L1087-1117); `activity` is stripped from outbound input (agent.ts L628-630); stale local `reasoning` stays and is sent, and agui-a2a ignores both roles (1.2) |
| compaction order | `compact/compact.ts` L29-32 notes that a snapshot interrupting a text stream is emitted after that stream's events on replay; ours precedes the reply stream, so it is unaffected |

Unverified: CopilotKit's managed (Intelligence) mode persistence, which is platform-side (the client path uses the same
reducer, but how the platform reconciles a snapshot against its stored thread is not in this repository); other AG-UI
clients (terminal, Slack and Teams through the channels SDK) were not checked; nothing was run.

## 10. Final issue list (revision 10)

Ready to file from this document. Every issue: type Task, assignee jfallows, parent aklivity/zilla-platform#1312, created in
the repository named below. "Depends on" ids map to issue numbers at filing. Bodies justify each shared-component change
only in terms of that component's own contract. Nothing has been filed.

### 10.1 PR and branch mapping (verified from the PRs and branches)

| PR / issue | Branch (base) | Holds | Amended by |
| --- | --- | --- | --- |
| #2825 / #2814 | `claude/upbeat-wozniak-d3ubn8` (develop) | `incubator/binding-a2a.spec`: `a2a.idl`, schema patch, 44 fixtures, `A2aFunctions` + test, 47 network, 34 application and 6 cache scenario pairs (94, 68 and 12 `.rpt` files), `NetworkIT`, `ApplicationIT`, `ProxyCacheIT` | A6, A7, A8 scripts |
| #2826 / #2815 | `claude/a2a-binding-scaffold-2815` | `binding-a2a` scaffold; the base of #2828 | none |
| #2828 / #2816 | `claude/a2a-server-2816` (the #2826 branch) | a2a(server): `A2aServerIT` 35, `A2aServerVersionIT` 9, `A2aServerCardIT` 4, plus spec-only network scripts added on this branch (`reject.bearer.invalid`, `reject.method.not.allowed`) | A6, A7, A8 behaviour and tests |
| #2829 / #2817 | `claude/a2a-client-2817` (the #2828 branch) | a2a(client): `A2aClientIT` 49, `A2aChainIT` 2, 48 generated `discover.*` network scenarios, 4 application scenarios | A6, A7, A8 behaviour and tests |
| #2850 / #2821 | `claude/gifted-pasteur-tkbfge` (the #2834 openapi branch `claude/a2a-openapi-2824`) | agui spec, conf and server runtime, docker image and golden entries | A1, A2, A3, A9, A10 |
| #2857 / #2856 | `claude/agui-client-2856` (the #2850 branch) | agui(client), `run.text.deltas`, chain scenarios | A4, A5 |

Stack order, from #2850's description: #2814/#2825, #2815/#2826, #2816/#2828, #2817/#2829, #2820/#2830, #2818/#2832, #2819/#2833,
#2823/#2835, #2824/#2834, #2821/#2850, then #2856/#2857. The coordinator's reading holds with these notes. The a2a runtime tests
live in the single module `incubator/binding-a2a` (`.../stream/A2aServerIT.java`, `A2aClientIT.java`), not in the spec
module. The file counts of #2825 follow from its description (scenario pairs times two); the counts for #2828 and #2829's scripts
were not recounted. Every amendment below is a commit on the named branch; because the user rebases the stack in GitHub, a commit
on #2825 or #2850 is picked up by every branch above it at the next rebase. Each is also filed as an issue (class
"amendment") so the work is tracked; the commit references it.

### 10.2 Amendments to existing issues and PRs (v1; repository aklivity/zilla)

| Id | Title | Amends / branch | Depends on |
| --- | --- | --- | --- |
| A1 | `binding-agui.spec: add agent to AguiBeginEx` | #2821 / PR #2850, `claude/gifted-pasteur-tkbfge` | none |
| A2 | `binding-agui.spec: routes on the agui server (when.path, with.agent)` | #2821 / PR #2850 | A1 |
| A3 | `binding-agui: resolve agui server routes by path` | #2821 / PR #2850 | A2 |
| A4 | `binding-agui.spec: routes on the agui client (when.agent, with.path)` | #2856 / PR #2857, `claude/agui-client-2856` | A1 |
| A5 | `binding-agui: resolve agui client routes by agent` | #2856 / PR #2857 | A4 |
| A6 | `binding-a2a.spec: task state and status message scenarios for message send and stream` | #2814 / PR #2825 (scripts), #2816 / PR #2828 and #2817 / PR #2829 (tests) | none |
| A7 | `binding-a2a.spec: a client abort detaches without cancelling` | #2814 / PR #2825 (scripts), #2817 / PR #2829 and #2816 / PR #2828 (behaviour and tests) | none |
| A8 | `binding-a2a.spec: reply begin reports the activated extensions` | #2814 / PR #2825 (scripts), #2816 / PR #2828 and #2817 / PR #2829 (behaviour and tests) | none |
| A9 | `binding-agui.spec: redirect scenarios` | #2821 / PR #2850 | A1 |
| A10 | `binding-agui: agui server handles Redirect` | #2821 / PR #2850 | A9 |

| Id | Files | Body | Acceptance criteria |
| --- | --- | --- | --- |
| A1 | `incubator/binding-agui.spec/src/main/resources/META-INF/zilla/agui.idl`, `.../internal/AguiFunctions.java`, `incubator/binding-agui.spec/src/test/java/io/aklivity/zilla/specs/binding/agui/internal/AguiFunctionsTest.java` | An AG-UI endpoint serves many agents, addressed by URL, but the `agui` stream carries only run ids, so a binding downstream of agui(server) cannot tell which agent a run addresses. Add `string16 agent = null` as the last member of `AguiBeginEx` (the same shape as `agent = null` on every `A2aBeginEx`), `agent(String)` on the begin builder, `agent(String)` and `matchAgent` on the begin matcher (null means unconstrained) | idl regenerates; `shouldGenerateBeginExWithAgent`, `...WithAgentWithoutParentRunId`, `shouldMatchBeginExWithAgent` pass; the whole existing spec suite passes with no `.rpt` edited |
| A2 | `.../agui/schema/agui.schema.patch.json`, `.../agui/config/server.routes.invalid.yaml` (flipped to valid `server.routes.yaml`, new invalid fixtures), `.../streams/network/request.path.not.found`, `.../request.path.forbidden`, `run.routed` (network and application), `NetworkIT`, `ApplicationIT`, `SchemaTest`, `examples/inspect.schema/.github/schema.expected.json` in the same commit series | The agui server resolves a single exit by authorization only. Enable `routes[].when.path` (an HTTP path template with `{name}` and `*`), `with.agent` (static or `${params.name}`), `guarded`, `exit`, mirroring http-kafka's route shape; a path that matches nothing is 404 and a matched path with no authorized route is 403 | fixtures valid and invalid cases pass `SchemaTest`; `request.path.not.found`, `request.path.forbidden`, `run.routed` paired and green; golden matches `zilla inspect schema` |
| A3 | `incubator/binding-agui/src/main/java/io/aklivity/zilla/runtime/binding/agui/internal/stream/AguiServerFactory.java`, `.../internal/config/AguiBindingConfig.java`, `AguiRouteConfig.java`, the route adapters in `incubator/binding-agui.conf/src/main/java/io/aklivity/zilla/config/binding/agui/internal/` with tests, `AguiServerIT` | Runtime for A2: resolve the route after the existing method, content-type and accept checks with a path-template matcher and `${params.x}` with-resolver mirroring `HttpKafkaConditionMatcher` and `HttpKafkaWithResolver`, write `agent` in the app Begin, reject 404 and 403 through `AguiRejectHandler`; no routes keeps today's behaviour | A2 scenarios pass in `AguiServerIT`; no-routes behaviour unchanged |
| A4 | the client halves of the same schema patch and fixtures (`client.routes.invalid.yaml` flipped), `run.routed` network and a `chain/run.routed` scenario, `ChainIT`, golden | The agui client resolves a single exit. Enable `routes[].when.agent` (a template), `with.path` (resolved against `options.server`, replacing its path), `guarded`, `exit`, mirroring the a2a-http route shape (a protocol-scoped `when`, an HTTP-scoped `with`) | fixtures and scenarios green; golden matches |
| A5 | `.../stream/AguiClientFactory.java`, `AguiClientBindingConfig`, conf adapters, `AguiClientIT`, `AguiChainIT` | Runtime for A4: read `AguiBeginEx.agent`, template-match, resolve `with.path` against `options.server`; no routes keeps the `options.server` path | A4 scenarios pass in `AguiClientIT` and `AguiChainIT` |
| A6 | `incubator/binding-a2a.spec/src/main/scripts/io/aklivity/zilla/specs/binding/a2a/streams/{application,network}/` (`stream.message.failed`, `.rejected`, `.input.required`, `.auth.required`, `.canceled`, `.status.message`, `send.message.task.failed`, `.input.required`), `NetworkIT` / `ApplicationIT` in the spec; new methods in `incubator/binding-a2a/src/test/java/io/aklivity/zilla/runtime/binding/a2a/internal/stream/A2aServerIT.java` and `A2aClientIT.java` | The mapping of the `TASK_STATE_*` strings to the `A2aTaskDataEx` / `A2aStatusUpdateDataEx` `state` tag, the stream end at terminal and interrupted states, and the placement of text in `status.message` on `COMPLETED`, `FAILED`, `INPUT_REQUIRED` and a final `Task` snapshot are a2a(server) and a2a(client) contracts, and only the happy path is scripted today | each scenario pair green; the pass-through methods green in both ITs |
| A7 | `.../streams/application/stream.message.client.aborted` and the network pair (#2825); `A2aClientFactory` behaviour (#2829), `A2aServerFactory` (#2828); methods in `A2aClientIT`, `A2aServerIT` | When a caller aborts a message stream, the a2a(client) must tear the HTTP request down and must not issue `CancelTask`; the a2a(server) must abort the application stream when the caller aborts. Today only a server-initiated abort is scripted, so detach versus cancel is an assumption | script pairs green; a2a(client) issues no `tasksCancel` on abort; a2a(server) aborts the application stream |
| A8 | `.../streams/application/send.message.extensions`, `stream.message.extensions` and the network pairs (#2825); `A2aClientFactory` (#2829), `A2aServerFactory` (#2828); methods in both ITs | The A2A specification says a response SHOULD echo the activated `A2A-Extensions`, but the stream model has no way to report it. Let the reply Begin carry the existing `extensions` member of the begin case: a2a(client) reads the response header into it, a2a(server) writes the header from it. No idl change | the extension scripts gain a matched reply Begin; green in `A2aServerIT` and `A2aClientIT` |
| A9 | `.../streams/application/run.redirect`, `run.redirect.100k` (and network pairs as the spec ITs require) in `incubator/binding-agui.spec` | agui(server) is the initiator of the `agui` stream and a receiver may answer with the engine `Redirect`. Scenarios copied from http `redirect/request.redirect` (receiver: `read advise zilla:redirect <affinity>`, then accept the second stream): `run.redirect` (the second Begin carries the same ids and the request body arrives on it) and `run.redirect.100k` (body larger than the decode slot). No script asserts behaviour for a second or late redirect: a conformant receiver never does that. No idl, `AguiFunctions` or harness change | scenarios green in the agui(server) ITs |
| A10 | `AguiServerFactory`, `AguiState`, `AguiServerIT` | Mirror http(server) `onRequestRedirect`: handle `RedirectFW`; reset the old reply and abort the old initial; `context.resolveRoutedId` and `context.supplyInitialId(routedId, affinity)`; a new Begin with `redirectedId`, the new affinity and `redirect.extension()` (no copy of the Begin extension is kept); the unsent request payload stays in the decode slot; a `Redirect` bit in `AguiState` (no boolean); `newStream` gains `redirectedId` and `affinity`. Defensive guard: a second redirect, a redirect after the reply began or after window aborts the stream with no status mapping, covered by a unit test if any, never by a script | A9 scenarios pass; the guard aborts and releases the decode slot |

### 10.3 New v1 issues

| Id | Repo | Title | Depends on |
| --- | --- | --- | --- |
| I1 | aklivity/zilla | `binding-agui-a2a.spec: scaffold spec module and config schema` | A1 |
| I2 | aklivity/zilla | `binding-agui-a2a.spec: run lifecycle, text, error and routing scenarios` | I1, A6 |
| I3 | aklivity/zilla | `binding-agui-a2a.spec: request mapping, interrupt and resume, cancel, card and context reset scenarios` | I2, A7 |
| I4 | aklivity/zilla | `binding-agui-a2a: scaffold runtime and conf modules, configuration properties and events` | I1 |
| I5 | aklivity/zilla | `binding-agui-a2a: map RunAgentInput to an A2A message` | I3, I4, I11 |
| I6 | aklivity/zilla | `binding-agui-a2a: map A2A events to AG-UI frames` | I5 |
| I7 | aklivity/zilla | `binding-agui-a2a: negotiate with the agent card and fall back to non-streaming` | I6 |
| I8 | aklivity/zilla | `binding-agui-a2a: cancel the A2A task on run abort` | I6 |
| I9 | aklivity/zilla | `build(docker-image): add binding-agui-a2a to the image and the inspect.schema golden` | I7, I8 |
| I10 | aklivity/zilla-examples (not among the three repositories named for this work; confirm) | `example: AG-UI frontend to an A2A agent through agui-a2a` | I9 |
| I11 | aklivity/zilla | `binding-agui-a2a: owner-side state, handles, implicit compaction and affinity redirect` | I4, A9, A10 |

| Id | Body | Acceptance criteria |
| --- | --- | --- |
| I1 | New module `incubator/binding-agui-a2a.spec` mirroring `binding-a2a-http.spec` (no idl; uses `agui` and `a2a`), with `COPYRIGHT`, `LICENSE`, `NOTICE.template`, generated `NOTICE`, `mvnw`, `mvnw.cmd`, registered in `incubator/pom.xml`. Schema patch for `type: agui-a2a`, `kind: proxy`: no `options`; `routes[].when[].agent`, `routes[].with.agent`, `routes[].with.identity`, `guarded`, `exit`; `vault`, `catalog`, `entry` false | valid and invalid fixtures (unknown `when` / `with` member, route without `exit`, any `options`, `kind` invalid); `SchemaTest` green; `./mvnw license:format` and notice generation clean |
| I2 | Paired scenarios under `streams/application/{agui,a2a}` run by one IT per side, mirroring `A2aServerIT` and `HttpClientIT` of `binding-a2a-http.spec`: slug pass-through, `with.agent` rewrite, null slug; text chunks, append and replace, several artifacts, a task-less `message`; non-streaming `messageSend`; `COMPLETED`, `FAILED`, `REJECTED`, `CANCELED`; `status.message` on `COMPLETED`, `FAILED`, `INPUT_REQUIRED`, a final `Task` resend and the working-text fallback; a2a abort, `A2aErrorResetEx` and bearer reset mapped to agui reset or abort (502, 403, 404, 422) | every row of 1.3 and the reset table has a scenario; `./mvnw verify -pl incubator/binding-agui-a2a.spec` green |
| I3 | Scenarios for 1.2 (trailing user messages only, binary and url parts, `file` handles dropped, non-empty `state` and `context` as one data part, `forwardedProps` as metadata, `reserved_key` 400, no user message 422, `system` / `developer` dropped), resume to the handle's `taskId`, `INPUT_REQUIRED` and `AUTH_REQUIRED` to `INTERRUPT`, cancel with and without `taskId`, the card streaming switch, 415, card failure, the context reset (`run.messages.snapshot` reused for the protocol side), a misaligned run answered with `Redirect` (mirroring mcp `lifecycle.redirect.session`), request larger than the slot 400 | each row of 1.1, 1.2, 1.6, 1.7 and 8.4 covered; request bodies checked verbatim |
| I4 | New modules `incubator/binding-agui-a2a` and `incubator/binding-agui-a2a.conf` (failing ITs first), binding factory SPI, `module-info`, route adapters, `AguiA2aConfiguration` with properties `thread.ttl`, `thread.capacity`, `handle.ttl` and `thread.affinity` (a `Class::method` static handle, mirroring `McpConfiguration` `session.id.affinity`), the `BindingEvent` catalog (run routed, finished, failed, redirected, context reset, handle rejected) | config tests; ITs fail for the right reason before I5; `incubator/pom.xml` modules and dependencyManagement |
| I5 | Decode slot buffers the request, encode slot builds the v1.0 message JSON and `contentLength`, opens `messageStream` or `messageSend` with the routed slug; the remembered `contextId` (never a client-chosen one); `forwardedProps` as top-level `Message.metadata` and 400 `reserved_key`; `acceptedOutputModes` `["text/plain"]`; resume to the `taskId` behind the handle; `tools` and `tool` messages parsed and ignored; `system` / `developer` dropped; when a prior assistant turn exists and nothing is remembered, retain the trailing user messages' byte ranges until the first a2a data frame, rejecting 503 `retention_unavailable` before the agent is contacted when that cannot be arranged; 400 `Request too large` beyond the slot. Mirror `AguiClientFactory`'s run input encoder; decoder states, no phase booleans | I3 scenarios pass; slots released on every error path; the retention failure is covered by a unit test with a bounded buffer pool |
| I6 | Incremental event decoder (mirror `AguiEventDecoder`), `chunks` text framing, `CUSTOM` `agent.data` / `agent.file`, `status.message` mapping with digest dedupe and the hardcoded fallback reply, neutral `agent_*` error codes and `reason` strings, outcome, abort and reset mapping, the implicit compaction snapshot ahead of the first reply frame, opaque interrupt handles with `expiresAt`, flow control honoured both ways | I2 and I3 scenarios incl. `*.100k` pass; a scenario asserts no `taskId`, `contextId` or `a2a` string reaches the agui side |
| I7 | `agentCard` pre-step with the routed slug, the streaming switch, the input-mode check (415), synthesized events for `messageSend` | 1.4 and 1.7 scenarios pass |
| I8 | `tasksCancel` (routed slug and `taskId`) on abort once the id is known, always, no option; `resume` entries with status `cancelled` cancel the interrupted task; a `TaskNotCancelable` or not-found answer is ignored | 1.6 scenarios pass |
| I9 | One commit series: `cloud/docker-image/pom.xml`, `cloud/docker-image/src/main/docker/zpm.json.template`, `incubator/pom.xml` dependencyManagement, `examples/inspect.schema/.github/schema.expected.json` (the golden must not lead the image), `NOTICE` regenerated | `./mvnw verify` on `cloud/docker-image`; the inspect.schema example is green |
| I10 | A `zilla-examples` entry for the section 3 sketch (tcp, http, agui server with a path route, agui-a2a, a2a proxy, a2a client) with `.github/test.sh`; checks the client-chosen-id behaviour against a reference agent | example test green; the lossiness table is documented |
| I11 | The proxy owns the state for (identity, agent, `threadId`) and issues the engine `Redirect`, echoing the Begin extension, when a run reaches the wrong worker, as mcp(server) does: a worker-local bounded map with a TTL sweep through the signaler holding the agent-issued `contextId` and interrupt handles (random 128-bit, a `kind` field, single use); the affinity function (`thread.affinity` or the local default) and a redirect handler mirroring `McpRedirectHandler`; a new context is always started when none is remembered; when a prior assistant turn exists and the reply carries a fresh `contextId`, `MESSAGES_SNAPSHOT` with the trailing user messages comes first, plus an informational operator-only Zilla event; unknown handles rejected 409 `handle_unknown`; state kept behind one interface so a store (F3) can sit under it | scenarios: aligned run, misaligned run answered with `Redirect` echoing the Begin extension, a second run reuses the `contextId`, an agent that returns none stores nothing, resume with a valid, unknown, expired or foreign handle, reset fires (snapshot first) and does not fire (first turn, remembered context, stateless agent, trimmed history), reset on a run that ends `INTERRUPT`, TTL expiry and capacity eviction |

### 10.4 Post-v1 follow-ups

| Id | Repo | Title | Depends on | Notes |
| --- | --- | --- | --- | --- |
| E1 | aklivity/zilla | `binding-agui-a2a: specify the AG-UI over A2A extension` | I10 | document `incubator/binding-agui-a2a/EXTENSION.md`; OPEN-8 naming decided here |
| F1 | aklivity/zilla | `binding-agui-a2a: map frontend tools through the AG-UI extension` | E1 | 8.3; tool part shapes aligned to `@ag-ui/a2a`; no idl change |
| F2 | aklivity/zilla | `binding-agui-a2a: state, activity, reasoning, step and tool-call events` | E1 | includes agent-originated `MESSAGES_SNAPSHOT` (agent-side compaction) |
| F3 | aklivity/zilla | `binding-agui-a2a: durable state tier` | I11 | `options.store`; the engine store authoritative, the worker-local map the fast path (8.4) |
| F4 | aklivity/zilla | `binding-agui-a2a: working status messages as activity` | I10 | `ACTIVITY_SNAPSHOT` `status` instead of the hardcoded fallback |
| F5 | aklivity/zilla | `binding-agui-a2a: subagent events` | F2 | tagged `subagent` events |
| F7 | aklivity/zilla | `binding-agui-a2a: A2UI content plug-in` | I10 | `a2ui-surface`, content `{a2ui_operations}`; OPEN-11 pins the A2UI generation; needs no carrier |
| F8 | aklivity/zilla | `binding-agui-a2a: agent-declared history feature` | E1 | full-history replay, only when the agent declares it |
| F9 | aklivity/zilla | `binding-agui-a2a: streaming request decode` | I5 | conditional on OPEN-16: parse `messages[]` incrementally, keep only the messages since the last assistant turn plus `state`, `context` and `forwardedProps`, lifting the slot-sized request bound |
| F10 | aklivity/zilla-plus | `agui-a2a: cluster-aware thread.affinity function` | I11 | conditional on multi-node: a function that sets the node byte, the equivalent of the `session.id.affinity` plug-in; unverified against zilla-plus (no checkout) |

Not filed: reconnect (F6): AG-UI's HTTP binding defines no connect operation (8.3).

### 10.5 Counts

| | aklivity/zilla | aklivity/zilla-examples | aklivity/zilla-plus | aklivity/zilla-platform | Total |
| --- | --- | --- | --- | --- | --- |
| Amendments, v1 (A1-A10) | 10 | 0 | 0 | 0 | 10 |
| New, v1 (I1-I11) | 10 | 1 | 0 | 0 | 11 |
| Post-v1 follow-ups (E1, F1-F5, F7-F10) | 9 | 0 | 1 | 0 | 10 |
| Total | 29 | 1 | 1 | 0 | 31 |

aklivity/zilla-platform holds only the parent #1312. Amendments are 10, new issues 21, v1 issues 21, post-v1 10.
