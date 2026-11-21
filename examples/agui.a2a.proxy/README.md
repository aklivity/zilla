# agui.a2a.proxy

Puts an [AG-UI](https://docs.ag-ui.com) endpoint in front of an [A2A](https://a2a-protocol.org) agent. Zilla
terminates the AG-UI HTTP + SSE protocol on port `7117`, maps each run to an A2A streaming message and maps the
agent's task events back to AG-UI events. The AG-UI client never sees an A2A id or extension.

The agent is a small reference A2A agent, [`agent/server.mjs`](agent/server.mjs), that serves an agent card with
`streaming: true`, answers `SendStreamingMessage`, keeps a context per `contextId`, can stop at `input-required`
and honors `CancelTask`.

```text
                    ┌───────────────────────────────── Zilla ──────────────────────────────────┐
AG-UI client ─────► │ tcp(7117) → http → agui(server) → agui-a2a(proxy) → a2a(proxy) → a2a(client) │ ──► agent:8080
  (SSE)             │                     /agents/{agent}                                         │     (reference A2A agent)
                    └──────────────────────────────────────────────────────────────────────────┘
```

| Binding | Kind | Role in this example |
| --- | --- | --- |
| `agui` | `server` | Terminates AG-UI over HTTP + SSE; `routes[].when.path` turns `/agents/{agent}` into the agent slug |
| `agui-a2a` | `proxy` | Maps a run to an A2A message, remembers the agent's context per thread, maps task events to AG-UI events |
| `a2a` | `proxy` | Routes by agent slug |
| `a2a` | `client` | Talks JSON-RPC to the agent over HTTP |

## Requirements

- docker compose
- curl

## Setup

```bash
docker compose up -d
```

## Verify

Run the automated check that the build workflow uses:

```bash
./.github/test.sh
```

Or post a run yourself. The request is a regular AG-UI `RunAgentInput`; the thread and run ids are yours:

```bash
curl -N http://localhost:7117/agents/assistant \
  -H 'content-type: application/json' -H 'accept: text/event-stream' \
  -d '{"threadId":"thread-1","runId":"run-1","messages":[{"id":"msg-1","role":"user","content":"hello"}]}'
```

The reply is an AG-UI event stream: `RUN_STARTED` and `RUN_FINISHED` carry your `threadId` and `runId`, and the
agent's text arrives as `TEXT_MESSAGE_START`, `TEXT_MESSAGE_CONTENT` and `TEXT_MESSAGE_END`. The reference agent
prefixes its reply with the A2A context and turn it answered, e.g. `[ctx-1 turn 1] you said: hello`.

### Context

Post a second run with the same `threadId` and the reply says `turn 2` of the same context. Only the latest user
turn is forwarded; the agent owns the history through its `contextId`, which the proxy remembers per thread. A
thread the proxy has no context for, but whose request already holds an assistant message, starts a new context
and the proxy sends a `MESSAGES_SNAPSHOT` ahead of the reply, so the client's transcript matches what the agent
now knows.

### Interrupt and resume

A message starting with `ask:` makes the agent stop at `input-required`. The run finishes with an `interrupt`
outcome whose `interrupts[0].id` is an opaque handle, and `message` is the agent's question. Resume the run on the
same thread:

```bash
curl -N http://localhost:7117/agents/assistant \
  -H 'content-type: application/json' -H 'accept: text/event-stream' \
  -d '{"threadId":"thread-1","runId":"run-2","messages":[],"resume":[{"interruptId":"<id>","status":"resolved","payload":"the blue one"}]}'
```

### Cancel

A message starting with `slow:` streams its reply one chunk a second. Aborting the request while it streams
cancels the A2A task:

```bash
curl -N --max-time 2 http://localhost:7117/agents/assistant \
  -H 'content-type: application/json' -H 'accept: text/event-stream' \
  -d '{"threadId":"thread-1","runId":"run-3","messages":[{"id":"msg-3","role":"user","content":"slow: wait"}]}'
docker compose exec agent wget -qO- http://127.0.0.1:8080/cancelled
```

## Lossiness

AG-UI and A2A do not model the same things. What the proxy drops or approximates:

### AG-UI to A2A

| AG-UI | A2A | Lossiness |
| --- | --- | --- |
| `threadId` | never sent as `contextId`; the first run sends none and the `contextId` the agent returns is remembered per (identity, agent, `threadId`) | none |
| `runId`, `parentRunId` | not forwarded; each run is a new A2A task and A2A ids are server-assigned | lossy |
| `resume[].interruptId` | `Message.taskId` of the interrupted task, with no `contextId` | none |
| `resume[].payload` | message parts (string to `text`, other JSON to `data`) | lossy for a status other than `resolved` |
| `resume[].status: cancelled` | `CancelTask`, the run ends `CANCELLED`, no message is sent | none |
| trailing `user` messages | one `Message`; earlier turns are not resent, the agent owns history through `contextId` | lossy |
| `assistant`, `tool`, `activity`, `reasoning` messages | dropped | lossy |
| `system`, `developer` messages | dropped; forwarding them would be prompt injection across a trust boundary | lossy |
| text content | `text` part | none |
| `image`, `audio`, `video`, `document` with `data` or `url` source | `raw` or `url` part | none |
| the same parts with a provider `file` source | dropped | lossy |
| non-empty `state` and `context` | one `application/json` data part, only when the agent card accepts `application/json` | lossy, the agent may ignore it |
| `forwardedProps` | `Message.metadata` | none, `agui` is a reserved key |
| `tools` | dropped | lossy |

### A2A to AG-UI

| A2A | AG-UI | Lossiness |
| --- | --- | --- |
| task `SUBMITTED`, `WORKING` without a message | nothing | lossy |
| `WORKING` with a message | kept as the reply only when the run completes with no other text | lossy |
| artifact text, first chunk | `TEXT_MESSAGE_START` and `TEXT_MESSAGE_CONTENT` | none |
| artifact text, `append: true` | `TEXT_MESSAGE_CONTENT` | none |
| artifact text, `append: false` on a streamed artifact | a new text message; streamed text cannot be retracted | lossy |
| `lastChunk`, or a terminal or interrupt state | `TEXT_MESSAGE_END` | none |
| `data` and `file` parts | `CUSTOM` events `agent.data` and `agent.file` | lossy |
| `COMPLETED` | `RUN_FINISHED`; the task and context ids are not placed on the stream | none |
| `COMPLETED` with a message | a text message, de-duplicated against artifact text | none |
| `FAILED` | `RUN_ERROR` 502 `agent_failed` | lossy |
| `REJECTED` | `RUN_ERROR` 422 `agent_rejected` | lossy |
| `CANCELED` | `RUN_FINISHED` with the `cancelled` outcome; its message is ignored | lossy |
| `INPUT_REQUIRED` | `RUN_FINISHED` with an `interrupt` outcome carrying an opaque handle; the message is the interrupt message | lossy |
| `AUTH_REQUIRED` | the same interrupt with reason `authorization`; the client can only resume | lossy |
| end of stream without a terminal or interrupt state | `RUN_ERROR` 502 `agent_incomplete` | n/a |

An error before the first event is an HTTP status instead: a rejected agent token is 502 `agent_unauthorized`,
an agent that is not found is 404, other 4xx are 422 `invalid_run`, and 5xx or no answer is 502.

### Streaming

The agent card selects the operation. `capabilities.streaming: true` streams events as they arrive. Otherwise the
proxy sends a non-streaming message and synthesizes the events from the one `task` or `message` it gets back, so
there is no incremental text.

## Known limits

- The remembered context lives in memory on one worker. A restart, a worker rescale or an expiry loses it; the
  next run starts a new agent context and the `MESSAGES_SNAPSHOT` above tells the client. A history the client
  trimmed holds no assistant message, so that loss is not announced.
- An agent that returns a `contextId` on every message without keeping memory causes one spurious reset per thread
  per restart.
- Interrupt handles are worker-local too. An unknown, expired or foreign handle rejects the run with 409
  `handle_unknown` before the agent is contacted.
- Aborting a run cancels the A2A task once its id is known. Before the first event the remote task may keep
  running. A Stop button and a closed tab look the same to Zilla.
- Frontend tools, `STATE_*`, activity, reasoning and tool call events, and the A2A extensions that would carry them
  are not mapped. The proxy declares no A2A extension.
- An agent slug with no route on `agui-a2a`, such as `/agents/unknown` here, is answered 502 because nothing can be reached.
- `routes[].when.path` on `agui` is the only way to pick an agent; CopilotKit's `/connect` and `/stop` routes are
  not AG-UI and are answered 404.
