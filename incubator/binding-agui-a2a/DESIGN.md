# agui-a2a (proxy) — AG-UI to A2A mapping design

Design spike for #2822. Accepts `agui` streams (#2821 / PR #2850 server, #2856 / PR #2857 client), produces `a2a`
streams (#2814 stream model, PRs #2825-#2835). No code, no `.rpt`, no spec change is part of this document.

Shape mirrored: `a2a-http` / `mcp-http` (proxy kind adapting one stream type to another) and llm's
documented-lossiness cross-dialect approach (#2496). Request transform mirrors the inverse of `AguiClientFactory`'s
run input encoder; response decode mirrors `AguiEventDecoder` (incremental JSON, decode slot, decoder states).

## 0. What the branch actually gives us (and where it departs from the issue)

| Fact in the branch | Consequence for the design |
| --- | --- |
| `RUN_STARTED` / `RUN_FINISHED` / `RUN_ERROR` are **not** data events on the `agui` stream: they are the `AguiBeginEx` (`threadId`, `runId`, `parentRunId`), the `AguiEndEx` (`outcome` NONE/SUCCESS/INTERRUPT/CANCELLED + `detail`) and abort (`AguiAbortEx.error`) / reset (`AguiResetEx.error`) | The issue's "`working` -> `RUN_STARTED`/`STEP_*`" does not apply. The proxy never writes lifecycle data frames; it opens the reply (begin) and finishes it with end / abort / reset. `STEP_*` is not driven by A2A states |
| The `agui` request body is `RunAgentInput` JSON, forwarded verbatim by agui(server); `AguiBeginEx` only carries the ids | The proxy must parse the body (`messages`, `resume`, `state`, `context`, `forwardedProps`, `tools`) itself |
| agui(server) has `routes: false`, one `exit`, no path or header visible on the `agui` stream | There is no route key on the stream. The A2A agent slug cannot come from the AG-UI endpoint path unless it is bound statically (decision Q-route below) |
| `a2a` application streams are half-duplex with `contentLength` in `A2aMessageSend/StreamBeginEx`; request and response bodies are v1.0 JSON verbatim; `A2aDataEx` tags `task` / `message` / `statusUpdate` / `artifactUpdate` with ids and `state` only | The proxy must buffer and transform the request to know `contentLength`; it must parse each response event body for `parts`, `append`, `lastChunk`, `status.message` |
| `A2aMessageStreamBeginEx` has `agent` (slug) and `extensions`; `A2aTasksCancelBeginEx` has `agent` + `taskId`; `A2aAgentCardBeginEx` has `agent` | Everything the mapping needs is routable with the existing BeginEx; no new case |
| No application script exercises `FAILED`, `REJECTED`, `INPUT_REQUIRED`, `AUTH_REQUIRED` over `messageStream` / `messageSend` (the enum has them; only `cache.serve.card.retry` and `push.deliver.slots` mention a non-happy state) | **Report, not a design change:** the state mapping below is untested at the a2a spec layer. The implementation issues add the scripts on the proxy spec, but a2a(server) / a2a(client) pass-through of these states is unproven (see section 6) |
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
| `resume[].interruptId` | `Message.taskId` | none | interrupt id **is** the A2A `taskId` (set when the proxy emits the interrupt), so continuation of an `input-required` / `auth-required` task needs no proxy state |
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
| `A2aBearerResetEx` `INVALID_TOKEN` / `INVALID_REQUEST` | status 401 | lossy: `AguiResetEx` has no realm / scopes, so no `WWW-Authenticate` challenge detail |
| `A2aBearerResetEx` `INSUFFICIENT_SCOPE` | status 403 | lossy, same |
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
| 6 Cancellation | `tasksCancel` when `taskId` is known, else abort only | no cancel before the first event, and a2a abort alone is not assumed to cancel |
| 7 Card | yes: read card, choose streaming, refuse unsupported input modes with 415 | recommended exit is a2a(proxy) for its shared cache |
| 8 Authorization | pass the engine `authorization` through; `routes[].guarded` for role gates; no end-user claims in the message | `AguiResetEx` cannot carry `WWW-Authenticate` realm / scopes |
| 9 v1 scope | 1.9. One addition over the recommended scope: `CUSTOM` passthrough of non-text parts and the interrupt / resume path | interrupt / resume is included because the stateless `taskId` design makes it cheap and the idl already has the outcome and `resume` scripts |
| Q-route (agent slug) | **static per binding**: `options.agent`; one agui-a2a binding per agent, selected by the HTTP route path in front of agui(server) | the issue did not ask; no route key exists on the `agui` stream. Routing by `forwardedProps` rejected: client-controlled, forces body parse before route resolve |

### OPEN decisions needing the user's call

| ID | Question | Recommendation |
| --- | --- | --- |
| OPEN-1 | A2A servers are not obliged to accept a client-chosen `contextId` for a new conversation, and the agent may answer with its own `contextId` (the proxy cannot rewrite the client's `threadId`) | ship v1 stateless, `threadId` verbatim; verify against the reference agent used in the example; if it rejects client ids, add an optional `options.store` mapping `threadId` -> `contextId` as a follow-up issue |
| OPEN-2 | `RUN_FINISHED.result` carrying `{a2a: {taskId, contextId}}` is an addition AG-UI clients may render | keep (debuggability, lets a client cancel / resume out of band); drop if it surprises a client in the example |
| OPEN-3 | Dropping `system` / `developer` messages silently | keep dropped in v1; revisit only with an explicit `options.forward: [system]` |

## 3. Config sketch and routing

```yaml
name: example
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
    options:
      authorization:
        jwt0:
          credentials:
            headers:
              authorization: Bearer {credentials}
    routes:
      - when:
          - headers:
              ":path": /agents/weather
        exit: agui_server_weather
      - when:
          - headers:
              ":path": /agents/travel
        exit: agui_server_travel
  agui_server_weather:
    type: agui
    kind: server
    exit: agui_a2a_weather
  agui_a2a_weather:
    type: agui-a2a
    kind: proxy
    options:
      agent: weather
      forward: [ state, context ]
    routes:
      - guarded:
          jwt0: [ agents:weather ]
        exit: a2a_proxy0
  # agui_server_travel / agui_a2a_travel: same shape, agent: travel
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
          - agent: travel
        exit: a2a_client0
  a2a_client0:
    type: a2a
    kind: client
    options:
      server: https://agents.example.internal/a2a
    exit: tcp_client0
```

Routing: HTTP path selects the agui(server) binding -> agui-a2a binding fixes the agent slug (`options.agent`, written into
every a2a `BeginEx.agent`) -> a2a(proxy) routes `when.agent` to the a2a(client) and enforces its own `guarded`.
No route key is added to the `agui` stream. Schema: `kind: proxy` only; `options.agent` required (string),
`options.forward` optional (`state`, `context`, `forwardedProps` subset), `routes[]` optional with `guarded` and `exit`
only (no `when`), `exit` required when no routes; `vault`, `catalog`, `entry` false, as in the a2a-http patch.

## 4. Spec changes

**None required** in `agui.idl` or `a2a.idl`. Considered and rejected (all would touch already open spec PRs, so not made):

| Candidate | Where | Why not |
| --- | --- | --- |
| `append` / `lastChunk` / `parts` in `A2aArtifactUpdateDataEx` | `a2a.idl` (additive, PR #2825-#2835 range) | the proxy parses the JSON body it already has to read for parts; duplicating fields in the DataEx buys nothing |
| route key (`agent` / `path`) in `AguiBeginEx` | `agui.idl` (PR #2850) | static per-binding agent solves routing without widening the stream |
| `taskId` in `A2aMessageStreamBeginEx` | `a2a.idl` | continuation `taskId` is a field of the message JSON, as the a2a scripts show |
| `realm` / `scopes` in `AguiResetEx` | `agui.idl` (PR #2850) | only improves 401 / 403 challenge fidelity; defer until a client needs it |

New proxy spec content needs no new functions: scripts use `AguiFunctions` and `A2aFunctions` as they are.

## 5. Drafted follow-up issues (stack order)

Each is justified by the `agui-a2a` binding's own contract. Parent: aklivity/zilla-platform#1312. Repo rules apply:
test-first, Aklivity Community License files per new project, `mvnw` / `mvnw.cmd`, `./mvnw license:format`,
`./mvnw notice:generate --projects ...`, `module-info`, `incubator/pom.xml` modules + dependencyManagement.

| # | Title | Body (summary) | Acceptance criteria | Depends on |
| --- | --- | --- | --- | --- |
| I1 | `binding-agui-a2a.spec: scaffold spec module and config schema` | Module `incubator/binding-agui-a2a.spec` (mirror `binding-a2a-http.spec`: no idl, uses `agui` and `a2a`), schema patch for `type: agui-a2a`, `kind: proxy`, `options.agent`, `options.forward`, `routes[].guarded` | valid / invalid config fixtures (agent missing, unknown option, `when` rejected, `kind` invalid, `forward` unknown value); `SchemaTest` green; `NOTICE` generated; module in `incubator/pom.xml` | #2822 |
| I2 | `binding-agui-a2a.spec: run lifecycle, text and error scenarios` | Paired `.rpt` scenarios under `streams/application/{agui,a2a}`: text chunks, text append / replace, multi-artifact, `message` reply, non-streaming `messageSend`, `COMPLETED` / `FAILED` / `REJECTED` / `CANCELED`, a2a abort / `A2aErrorResetEx` / bearer reset -> agui reset / abort | scenarios for every row of 1.3 and the reset table; ITs per direction (agui-side and a2a-side, mirroring `A2aServerIT` / `HttpClientIT`); `./mvnw verify -pl incubator/binding-agui-a2a.spec` green | I1 |
| I3 | `binding-agui-a2a.spec: request mapping, interrupt / resume, cancel and card scenarios` | Scenarios for 1.2 (trailing user messages, binary / url parts, state + context data part, forwardedProps metadata, empty -> 422), `resume` -> `taskId`, `INPUT_REQUIRED` / `AUTH_REQUIRED` -> `INTERRUPT`, cancel with and without `taskId`, card streaming switch, 415, card failure | each row of 1.1, 1.2, 1.6, 1.7 covered by a paired scenario; request bodies golden-checked verbatim | I2 |
| I4 | `binding-agui-a2a: scaffold runtime module and config adapter` | Module `incubator/binding-agui-a2a` + `.conf` (failing ITs from I2 / I3 first), binding factory SPI, `options.agent` / `forward` / `routes[].guarded` config, `module-info` | config unit tests; ITs fail for the right reason before I5; module wired in `incubator/pom.xml` (modules + dependencyManagement) | I1 |
| I5 | `binding-agui-a2a: map RunAgentInput to an A2A message` | Decode slot buffers the request, encode slot builds the v1.0 message JSON, computes `contentLength`, opens `messageStream` / `messageSend`; resume -> `taskId`; mirror `AguiClientFactory` run input encoder; decoder-state fields only, no phase booleans | 1.1 / 1.2 scenarios pass; oversize request rejected as agui(server) does; slots released on every error path | I3, I4 |
| I6 | `binding-agui-a2a: map A2A events to AG-UI frames` | Incremental event decoder (mirror `AguiEventDecoder`), text message framing with `chunks`, `CUSTOM` passthrough, outcome / abort / reset mapping, flow-control credit honoured in both directions | 1.3 scenarios and reset table pass including `*.100k`; no early return of window credit | I5 |
| I7 | `binding-agui-a2a: negotiate with the agent card and fall back to non-streaming` | `agentCard` pre-step, streaming switch, input-mode check, synthesized events for `messageSend` | 1.4 and 1.7 scenarios pass | I6 |
| I8 | `binding-agui-a2a: cancel the A2A task on run abort` | `tasksCancel` on abort / `resume.cancelled` once `taskId` is known, detach otherwise | 1.6 scenarios pass | I6 |
| I9 | `build(docker-image): add binding-agui-a2a to the image and the inspect.schema golden` | **One commit series**: `cloud/docker-image/pom.xml` dependency, `cloud/docker-image/src/main/docker/zpm.json.template` entry, `incubator/pom.xml` dependencyManagement, `inspect.schema/.github/schema.expected.json` golden entry (golden must not lead the image, see 53290f62), `NOTICE` regenerated | `./mvnw verify` on `cloud/docker-image`; inspect.schema example green | I7, I8 |
| I10 | `example: AG-UI frontend to an A2A agent through agui-a2a` | `zilla-examples` entry (agui server + agui-a2a + a2a client / proxy) with its `.github/test.sh`; also the check for OPEN-1 against the reference agent | example test green; documents the lossiness table | I9 |
| F1 | `binding-agui-a2a: map frontend tools through a declared A2A extension` | declare extension in the card, tool declarations in `metadata`, tool-call parts, `input-required` round trip | scenarios per 1.5; no `a2a.idl` change unless demonstrably required | I10 |
| F2 | `binding-agui-a2a: state, activity, reasoning and tool-call events` | tagged `data` parts and status messages -> `STATE_*`, `ACTIVITY_*`, `REASONING_*`, `TOOL_CALL_*` | one scenario per event family | I10 |
| F3 | `binding-agui-a2a: store-backed threadId to contextId mapping` | `options.store`, only if OPEN-1 shows agents reject client-chosen ids | continuation across runs without a client-chosen id | OPEN-1 |

## 6. Items to report (not worked around)

| Item | Detail |
| --- | --- |
| Untested a2a states | `FAILED`, `REJECTED`, `INPUT_REQUIRED`, `AUTH_REQUIRED` are in `A2aTaskState` but no `messageStream` / `messageSend` script drives them; I2 / I3 add them at the proxy layer, but a2a(server) / a2a(client) behavior for them should get their own scenarios under #2816 / #2817 |
| a2a abort vs cancel | No script shows what a2a(client) does to the remote task on `messageStream` abort; this design assumes nothing and cancels explicitly |
| Issue text vs idl | `RUN_STARTED` / `RUN_FINISHED` / `RUN_ERROR` are not data events on the `agui` stream (section 0) |
| AG-UI content parts | the input content-part schema (`binary` vs typed image / audio / document with `source`) is from memory and needs checking against the AG-UI spec before I3 |
