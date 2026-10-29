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
| A2A v1.0 wire shapes in the a2a spec were written from memory (caveat in the #2814 comment) | Part / state field names here follow the repo's scripts (`ROLE_USER`, `TASK_STATE_*`, `parts[].text/raw/url/data`); the AG-UI input content-part schema is also from memory. Both need a review against the specs when the proxy scripts are written |

## 1. Mapping table

Legend — lossiness: `none` exact; `lossy` information dropped or approximated; `n/a` v1 does not map.

### 1.1 Identity and continuation (AG-UI -> A2A)

| AG-UI | A2A | Lossiness | Notes |
| --- | --- | --- | --- |
| `threadId` | `Message.contextId` | none (see OPEN-1) | sent verbatim on every run; the proxy is stateless |
| `runId` | new task per run; `Message.metadata.agui.runId` | lossy | A2A task ids are server-assigned, so `runId` cannot be the task id. Returned `taskId` is surfaced in `RUN_FINISHED.result.a2a.taskId` and as the interrupt id |
| `parentRunId` | `Message.metadata.agui.parentRunId` | lossy | informational only; never used for continuation |
| `resume[].interruptId` | `Message.taskId` | none | interrupt id **is** the A2A `taskId` (set when the proxy emits the interrupt), so continuation of an `input-required` / `auth-required` task needs no proxy state. The agent slug is equally stateless on resume: the client re-posts `RunAgentInput` to the same URL, so the same `routes[].when.path` yields the same `AguiBeginEx.agent` |
| `AguiBeginEx.agent` (new, revision 2) | `A2aBeginEx.agent` (slug), on `agentCard`, `messageStream` / `messageSend` and `tasksCancel` | none | pass-through by default; `routes[].with.agent` on the proxy rewrites it. Null stays null (the tenant-less A2A endpoint) |
| `resume[].payload` | message `parts` (string -> `text`, other JSON -> `data`) | lossy for `status` other than `resolved` | `status: cancelled` on a resume -> `tasksCancel` of that `taskId`, run ends `CANCELLED`, no message sent |
| `AguiBeginEx` (reply) | reply begin echoes request `threadId` / `runId` / `parentRunId` | none | the A2A `contextId` is **not** returned as `threadId` (the client owns it). If the agent returns a different `contextId`, emit a `CUSTOM` event `a2a.context` and carry on |

### 1.2 Request (AG-UI `RunAgentInput` -> A2A `SendMessage` / `SendStreamingMessage` params)

| `RunAgentInput` | A2A | Lossiness | Notes |
| --- | --- | --- | --- |
| `messages[]` trailing `user` messages (after the last `assistant` / `tool` message) | one `Message{role: ROLE_USER, messageId, contextId, taskId?, parts[], metadata}` | lossy | the agent owns history through `contextId`; earlier turns are not resent. `messageId` = id of the last trailing user message (idempotent retries); `resume`-only run uses `runId` |
| `messages[]` earlier turns, `assistant`, `tool` roles | dropped | lossy | `tool` role is the frontend-tool result path (n/a v1, see 1.5) |
| `messages[]` `system` / `developer` roles | dropped | lossy | the agent owns its system prompt; forwarding would be prompt injection across a trust boundary |
| user content `string` / `{type: text}` | `{text}` | none | consecutive parts keep order |
| user content binary / image / audio / document with inline data | `{raw: base64, mediaType, filename?}` | none | |
| user content with `url` | `{url, mediaType, filename?}` | none | |
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
| `task` carrying `artifacts[]` | same as `artifactUpdate` per artifact, in order | none | shared path with `messageSend` |
| `artifactUpdate`, text part, first sight of `artifactId` | `TEXT_MESSAGE_START` (`messageId = artifactId`, role ASSISTANT) + `CONTENT` | none | `text` data frame, init flag + `AguiTextDataEx` |
| `artifactUpdate`, text part, `append: true` | `TEXT_MESSAGE_CONTENT` on the open message | none | payload-only frames, `chunks` framing |
| `artifactUpdate`, `append: false` on an already streamed `artifactId` | new text message `messageId = artifactId + "#n"` | lossy | AG-UI cannot retract streamed text; earlier text stays |
| `artifactUpdate` `lastChunk: true`, or terminal / interrupt state with a text message open | `TEXT_MESSAGE_END` (empty fin frame) | none | the proxy uses `run.text.chunks` framing because `lastChunk` can arrive after the last text |
| `artifactUpdate` / `task` / `message` non-text part (`data`, `file`) | `CUSTOM` `a2a.data` / `a2a.file` with the part JSON (`raw` as base64, `url`) | lossy | v1 keeps data visible rather than silently dropping it; an open text message is closed first |
| `message` (agent reply with no task) | text message from its text parts (`messageId` = `Message.messageId`), non-text as `CUSTOM`; run ends `SUCCESS` | none | |
| `statusUpdate` `COMPLETED` | end `SUCCESS`, `detail = {"result":{"a2a":{"taskId","contextId"}}}` | lossy (result is an addition) | |
| `statusUpdate` `FAILED` | abort `AguiAbortEx.error{status 502, code "task_failed", message = status.message text}` | lossy | AG-UI `RUN_ERROR` |
| `statusUpdate` `REJECTED` | abort `error{status 422, code "task_rejected", message}` | lossy | |
| `statusUpdate` `CANCELED` | end `CANCELLED` | none | whoever cancelled it |
| `statusUpdate` `INPUT_REQUIRED` | end `INTERRUPT`, `detail = {"outcome":{"interrupts":[{"id":"<taskId>","reason":"input_required","message":"<status.message text>"}]}}` | lossy | the prompt travels in the interrupt message, not as a transcript message |
| `statusUpdate` `AUTH_REQUIRED` | end `INTERRUPT`, same shape, `reason: "auth_required"` | lossy | A2A auth-required is out-of-band credentials for the agent's own downstream; the client can only resume |
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
| `RunAgentInput.tools[]`, `tool` role messages, `TOOL_CALL_*` | no client-tool concept | v1: dropped (lossy, documented). Follow-up: an A2A extension (URI declared in the card `capabilities.extensions`, activated through `A2aMessageStreamBeginEx.extensions`) carrying tool declarations in `metadata` and tool calls as tagged `data` parts, with the `input-required` round trip providing the result path. No new idl case needed |

### 1.6 Cancellation

| Trigger | Proxy action |
| --- | --- |
| agui stream abort / reset after `taskId` is known (first `task` / `statusUpdate` / `artifactUpdate` DataEx) | open `tasksCancel` (`agent`, `taskId`) on the exit, discard its result; abort the `messageStream` |
| abort before any DataEx (no `taskId` yet) | abort the `messageStream` only; the remote task may keep running (documented lossy) |
| `resume[].status = cancelled` | `tasksCancel` for the interrupt id, end `CANCELLED` |
| a2a `CANCELED` state not initiated here | end `CANCELLED` |

AG-UI facts that bear on this (checked, see section 7 for sources): the AG-UI JS `HttpAgent` posts `RunAgentInput` to an arbitrary configured URL and `abortRun()` is a client-side `AbortController` abort; the protocol pages found define no stop / cancel endpoint. So on the wire, cancellation is exactly what the `agui` stream already models: the HTTP request is aborted, agui(server) aborts / resets the app stream. CopilotKit's runtime additionally exposes `POST /agent/:agentId/stop/:threadId`, but it is a runtime route (handled in CopilotKit's `handle-stop.ts`), thread-wide by default, with open reports that a delayed stop can cancel the next run (CopilotKit issues #6978, #7149). It is not part of AG-UI and is not routed to agui(server); with `routes[].when.path` an unmatched `/connect` or `/stop/...` path is a 404. Cancelling by `taskId` (run-scoped) avoids that thread-wide race.

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
| 1 Identity | `threadId` -> `contextId` verbatim; new task per run, `runId` / `parentRunId` in `metadata.agui`; continuation only via `resume` with interrupt id = `taskId` | `parentRunId` is not a continuation key (the `resume[]` array with `interruptId` is the actual AG-UI continuation signal; the idl already carries it in `run.resume`). **OPEN-1** |
| 2 Request | trailing user messages after the last assistant / tool message, merged into one `Message`; `system` / `developer` / history dropped; `state` + `context` -> one tagged `data` part; `forwardedProps` -> `Message.metadata`; binary -> `raw`, url -> `url` | "last user message" widened to trailing user messages (several user messages without an assistant reply between them are one turn); `state` / `context` go in a `data` part (agents see them as input) while `forwardedProps` goes to `metadata` (opaque) |
| 3 Response | per 1.3. Lifecycle is begin / end / abort / reset, not data events. `canceled` -> end `CANCELLED` (the idl has it). `input-required` / `auth-required` -> end `INTERRUPT` with `interrupts[0].id = taskId` | `RUN_STARTED` / `STEP_*` are not derived from `working`; "decide" items resolved: `data` / `file` parts -> `CUSTOM` in v1 (not `STATE_SNAPSHOT`, which needs an A2A convention) |
| 4 Frontend tools | dropped in v1; follow-up via a declared A2A extension | as the issue's candidate, but through the existing `extensions` BeginEx field rather than an unmarked `data` part |
| 5 Streaming | card-driven: `messageStream` when the card says streaming, else `messageSend` + synthesized events | the issue says "always `SendStreamingMessage`"; since Q7 reads the card anyway, the proxy follows `capabilities.streaming` and streams whenever the card allows it |
| 6 Cancellation | `tasksCancel` when `taskId` is known, else abort only. No AG-UI stop endpoint exists to map (1.6) | no cancel before the first event, and a2a abort alone is not assumed to cancel |
| 7 Card | yes: read card, choose streaming, refuse unsupported input modes with 415 | recommended exit is a2a(proxy) for its shared cache |
| 8 Authorization | pass the engine `authorization` through; `routes[].guarded` for role gates (now available on agui(server) routes too); no end-user claims in the message; agent-side token rejection is 502, not 401 | `AguiResetEx` cannot carry `WWW-Authenticate` realm / scopes; judged sufficient (candidate (d)) |
| 9 v1 scope | 1.9. One addition over the recommended scope: `CUSTOM` passthrough of non-text parts and the interrupt / resume path | interrupt / resume is included because the stateless `taskId` design makes it cheap and the idl already has the outcome and `resume` scripts |
| Q-route (agent slug) | **route key on the stream** (user decision, revision 2): `AguiBeginEx.agent`; agui(server) `routes[].when.path` -> `with.agent`; agui-a2a `routes[].when.agent` -> optional `with.agent` -> `A2aBeginEx.agent`; agui(client) `routes[].when.agent` -> `with.path` | replaces revision 1's static `options.agent` (dropped). Routing by `forwardedProps` still rejected: client-controlled, forces body parse before route resolve |

### OPEN decisions needing the user's call

| ID | Question | Recommendation |
| --- | --- | --- |
| OPEN-1 | A2A servers are not obliged to accept a client-chosen `contextId` for a new conversation, and the agent may answer with its own `contextId` (the proxy cannot rewrite the client's `threadId`) | ship v1 stateless, `threadId` verbatim; verify against the reference agent used in the example; if it rejects client ids, add an optional `options.store` mapping `threadId` -> `contextId` as a follow-up issue |
| OPEN-2 | `RUN_FINISHED.result` carrying `{a2a: {taskId, contextId}}` is an addition AG-UI clients may render | keep (debuggability, lets a client cancel / resume out of band); drop if it surprises a client in the example |
| OPEN-3 | Dropping `system` / `developer` messages silently | keep dropped in v1; revisit only with an explicit `options.forward: [system]` |
| OPEN-4 | agui(client) `with.path`: replace the path of `options.server`, or append to it | replace (an HTTP `:path` of its own, scheme and authority kept from `options.server`), so one client can address `/agent/{agent}/run` style servers and a bare `/agent` server by the same rule; with no routes the `options.server` path is used unchanged |


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
    routes:
      - when:
          - agent: weather                    # AGUI-scoped
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
- `type: agui-a2a`, `kind: proxy`: `options.forward` (array of `state` | `context` | `forwardedProps`); `routes[].when[]` `{agent: string}`, `with` `{agent: string}`, `guarded`, `exit`; no `options.agent`; `vault`, `catalog`, `entry` false.

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
| input content-part schema and A2A v1.0 part shapes written from memory | verify when I3 / I5 scripts are written (section 6) |

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
| I1 | `binding-agui-a2a.spec: scaffold spec module and config schema` | A1 | Module `incubator/binding-agui-a2a.spec` (mirror `binding-a2a-http.spec`: no idl, uses `agui` and `a2a`); schema patch for `type: agui-a2a`, `kind: proxy`, `options.forward`, `routes[].when.agent` / `with.agent` / `guarded` / `exit` (section 3); no `options.agent` | valid / invalid fixtures (`when` unknown member, `with` unknown member, route without `exit`, `forward` unknown value, `kind` invalid, unknown option); `SchemaTest` green; `NOTICE` generated; module in `incubator/pom.xml` |
| I2 | `binding-agui-a2a.spec: run lifecycle, text, error and routing scenarios` | I1, A6 | Paired `.rpt` scenarios under `streams/application/{agui,a2a}`: slug pass-through and `with.agent` rewrite onto `A2aBeginEx.agent`, null slug, text chunks, append / replace, multi-artifact, `message` reply, non-streaming `messageSend`, `COMPLETED` / `FAILED` / `REJECTED` / `CANCELED`, a2a abort / `A2aErrorResetEx` / bearer reset (502 / 403 per 1.3) -> agui reset / abort | every row of 1.3 and its reset table covered; ITs per side (mirroring `A2aServerIT` / `HttpClientIT`) green |
| I3 | `binding-agui-a2a.spec: request mapping, interrupt / resume, cancel and card scenarios` | I2, A7 | 1.2 (trailing user messages, binary / url parts, state + context data part, forwardedProps metadata, empty -> 422), `resume` -> `taskId`, `INPUT_REQUIRED` / `AUTH_REQUIRED` -> `INTERRUPT`, cancel with and without `taskId`, card streaming switch, 415, card failure | rows of 1.1, 1.2, 1.6, 1.7 each covered; request bodies checked verbatim |
| I4 | `binding-agui-a2a: scaffold runtime module and config adapter` | I1 | Module `incubator/binding-agui-a2a` + `.conf` (failing ITs first), factory SPI, config adapter for `options.forward` and the `when.agent` / `with.agent` / `guarded` routes, `module-info` | config tests; ITs fail for the right reason before I5; `incubator/pom.xml` modules + dependencyManagement |
| I5 | `binding-agui-a2a: map RunAgentInput to an A2A message` | I3, I4 | Decode slot buffers the request, encode slot builds the v1.0 message JSON and `contentLength`, opens `messageStream` / `messageSend` with the routed slug; resume -> `taskId`; mirror `AguiClientFactory`'s run input encoder; decoder states, no phase booleans | 1.1 / 1.2 scenarios pass; oversize request rejected as agui(server) does; slots released on every error path |
| I6 | `binding-agui-a2a: map A2A events to AG-UI frames` | I5 | Incremental event decoder (mirror `AguiEventDecoder`), `chunks` text framing, `CUSTOM` passthrough, outcome / abort / reset mapping, flow control honoured both ways | 1.3 scenarios incl. `*.100k` pass |
| I7 | `binding-agui-a2a: negotiate with the agent card and fall back to non-streaming` | I6 | `agentCard` pre-step with the routed slug, streaming switch, input-mode check, synthesized events | 1.4 and 1.7 scenarios pass |
| I8 | `binding-agui-a2a: cancel the A2A task on run abort` | I6 | `tasksCancel` (routed slug + `taskId`) on abort / `resume.cancelled`; detach otherwise | 1.6 scenarios pass |
| I9 | `build(docker-image): add binding-agui-a2a to the image and the inspect.schema golden` | I7, I8 | **One commit series**: `cloud/docker-image/pom.xml`, `cloud/docker-image/src/main/docker/zpm.json.template`, `incubator/pom.xml` dependencyManagement, `inspect.schema/.github/schema.expected.json` golden (golden must not lead the image, see 53290f62), `NOTICE` regenerated. The golden also carries the agui `routes` schema from A2 / A4 | `./mvnw verify` on `cloud/docker-image`; inspect.schema example green |
| I10 | `example: AG-UI frontend to an A2A agent through agui-a2a` | I9 | `zilla-examples` entry (section 3 sketch) with `.github/test.sh`; also the OPEN-1 check against the reference agent | example test green; lossiness table documented |
| F1 | `binding-agui-a2a: map frontend tools through a declared A2A extension` | I10 | card-declared extension, tool declarations in `metadata`, tool-call parts, `input-required` round trip | scenarios per 1.5; no `a2a.idl` change unless demonstrably required |
| F2 | `binding-agui-a2a: state, activity, reasoning and tool-call events` | I10 | tagged `data` parts and status messages -> `STATE_*`, `ACTIVITY_*`, `REASONING_*`, `TOOL_CALL_*` | one scenario per event family |
| F3 | `binding-agui-a2a: store-backed threadId to contextId mapping` | OPEN-1 | `options.store`, only if agents reject client-chosen ids | continuation across runs without a client-chosen id |

## 6. Items to report (not worked around)

| Item | Detail |
| --- | --- |
| Untested a2a states | covered by S4 / A6; the a2a(server) / a2a(client) state-string handling is unproven until then |
| a2a abort vs cancel | covered by S5 / A7 |
| Issue text vs idl | `RUN_STARTED` / `RUN_FINISHED` / `RUN_ERROR` are not data events on the `agui` stream (section 0) |
| agui(server) app reset without challenge | a relayed 401 has no `WWW-Authenticate`; sidestepped by mapping INVALID_TOKEN to 502 (1.3, candidate (d)) |
| Unrouted agui(server) request today | with a guard-resolved route missing, `newStream` stays null; S2 / A3 define 404 / 403 once path routes exist |
| AG-UI content parts, A2A v1.0 shapes | the input content-part schema and the A2A v1.0 part shapes were written from memory; verify when I3 / I5 are written |

## 7. Sources and verification status

| Claim | Source | Status |
| --- | --- | --- |
| AG-UI `HttpAgent` is configured with a URL, "any endpoint that accepts POST requests with a body of type `RunAgentInput`"; the architecture page defines no stop / cancel | github.com/ag-ui-protocol/ag-ui `docs/concepts/architecture.mdx` (fetched) | verified |
| `HttpAgent.abortRun()` cancels the in-flight HTTP request through an `AbortController` (client-side only) | docs.ag-ui.com "http agent" SDK page, via search result summary | secondary: `docs.ag-ui.com` is not reachable from this session (not in the allowed network domains), so not fetched directly |
| CopilotKit runtime routes `POST …/agent/:agentId/run`, `…/connect`, `…/stop/:threadId` | docs.copilotkit.ai "Runtime HTTP endpoints" via search result summary; CopilotKit/CopilotKit issues #6978, #7149 and PRs #6982, #7150 (stop is thread-wide, `handle-stop.ts`) | secondary: `docs.copilotkit.ai` not reachable; GitHub pages seen only as search summaries; merge state of the PRs unknown |
| AG-UI interrupt / resume semantics | none external; relied on this repo's `run.resume` and `run.outcome.interrupt` scripts | not verified externally |

Allowing `docs.ag-ui.com` and `docs.copilotkit.ai` in the session's network settings would let these be checked at the source.
