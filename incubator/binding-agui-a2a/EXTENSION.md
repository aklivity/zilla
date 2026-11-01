# AG-UI over A2A extension

Status: specification, post-v1. The `agui-a2a` proxy in v1 declares and activates **no extension**; everything in this
document is the contract that post-v1 work builds on. Nothing here changes `agui.idl`, `a2a.idl` or the v1 behavior.

The key words MUST, MUST NOT, SHOULD and MAY are used as in RFC 2119.

## 1. Purpose and scope

The `agui-a2a` proxy maps an AG-UI run to an A2A message and maps the A2A task events back to AG-UI events. With plain
A2A (tier 1) the mapping is lossy: A2A's core cannot carry frontend tools, state, activity, reasoning, steps or subagents.
This extension lets an **agent** opt into a richer mapping (tier 2) without forking A2A.

- It is a **metadata-only** A2A extension: it adds no field and no enum value to any A2A core type. All of its content is in
  `Message.metadata`, `Part.metadata` and the `data` parts that A2A already defines.
- The agent declares it in its agent card. The proxy requests it with the `A2A-Extensions` header. The agent echoes what it
  activated.
- AG-UI clients and CopilotKit consume AG-UI events and never see an A2A extension, a part tag or an A2A id. The extension
  only decides which A2A agents can opt into a richer mapping; it does not change how a client integrates.
- Content plug-ins, such as an A2UI surface, are separate extensions owned by their content type. They share nothing with
  this carrier (section 12).
- The `timestamp` and event `metadata` of an AG-UI event are out of scope: no tag carries them.

## 2. Identification and versioning

| Item | Value |
| --- | --- |
| Extension URI | `https://a2a.aklivity.io/ext/agui/v1` |
| URI pattern | `https://a2a.aklivity.io/ext/<name>/v<major>`. The host scopes every A2A extension Aklivity publishes, the name is `agui` and the major version is part of the URI. It is not `a2ui`: A2UI is a different project, and its content plug-in is a separate extension (section 12) |
| Namespace | Aklivity-owned. The `https://a2a-protocol.org/extensions/` prefix is reserved for the A2A organization and MUST NOT be used |
| Header | `A2A-Extensions`, as the A2A specification defines it. The A2UI extension documents `X-A2A-Extensions` for older clients; the proxy sends `A2A-Extensions` and reads the echo of `A2A-Extensions`, which the a2a stream reports in the reply begin |
| Metadata key | `agui` |
| Required by default | no (`required: false`) |

Versioning rules:

1. The last path segment, `v1`, is the **major version**. A change that removes or reinterprets a feature, a tag, a tag
   member or a `params` member gets a new URI (`v2`). A `v2` agent is not a `v1` agent.
2. Within a major version only additive changes are allowed: a new feature name, a new tag, a new optional tag member or a
   new optional `params` member. A reader MUST ignore a `params` member, a tag member and a feature name it does not know,
   and MUST drop an unknown `event` tag (section 8).
3. An agent MAY list several versions as separate `capabilities.extensions[]` entries. The proxy lists the versions it
   implements and activates **at most one** AG-UI version per run: the highest version it implements that the card lists.
4. A URI the proxy does not implement is ignored, whatever its `required` value, except as stated in section 6.

## 3. Reserved names

| Name | Reserved for |
| --- | --- |
| metadata key `agui` | the proxy, on `Message.metadata`, `Part.metadata` and agent-produced `Part.metadata`. It is already reserved in v1, where a `forwardedProps` object that has an `agui` member is rejected 400 `reserved_key` before the agent is contacted |
| the extension URI as a metadata key | in a run that activated the extension, a `forwardedProps` object with the URI as a member is rejected the same way, so a client cannot forge an extension-defined member |
| feature name `instructions` | reserved and **not defined** by `v1` (sections 4.1, 12) |

`forwardedProps` stays the application-to-agent channel: its members are the top-level members of `Message.metadata`, never
inside `agui`. The `agui` member holds only what this extension defines.

## 4. Card declaration

The agent adds one entry to `capabilities.extensions[]` of its agent card:

```json
{
  "capabilities": {
    "streaming": true,
    "extensions": [
      {
        "uri": "https://a2a.aklivity.io/ext/agui/v1",
        "description": "Maps this agent's tasks to AG-UI events",
        "required": false,
        "params": {
          "features": ["tools", "state", "context", "activity", "reasoning", "steps", "toolCalls", "subagents", "interrupts"]
        }
      }
    ]
  }
}
```

| Member | Type | Meaning |
| --- | --- | --- |
| `uri` | string | the extension URI of section 2 |
| `required` | boolean | `true` when the agent cannot work without the extension. Default `false` |
| `params.features` | string array | the features the agent supports. Absent or empty means no feature: the extension is declared and nothing is enabled |
| `params.history` | string | `context` (default) or `always`, when the `history` feature is listed (section 6.3). An unknown value means `context` |

### 4.1 Features

A feature is an independent switch. A feature the agent did not list is never sent to it and its tags are not interpreted.

| Feature | Direction | Enables |
| --- | --- | --- |
| `tools` | AG-UI to agent | `RunAgentInput.tools[]` in `metadata.agui.tools`, and `toolResult` parts (section 7.2) |
| `state` | both | `RunAgentInput.state` in `metadata.agui.state`; `state` tags (snapshot, delta, messages) from the agent |
| `context` | AG-UI to agent | `RunAgentInput.context[]` in `metadata.agui.context` |
| `history` | AG-UI to agent | the advisory `history` part of the earlier messages (section 6.3) |
| `activity` | agent to AG-UI | `activity` tags |
| `reasoning` | agent to AG-UI | `reasoning` and `reasoning.encrypted` tags |
| `steps` | agent to AG-UI | `step` tags |
| `toolCalls` | agent to AG-UI | `toolCall` tags (client-executed and agent-executed) and agent-executed `toolResult` tags |
| `subagents` | agent to AG-UI | `subagent` tags, `text` tags and the `subagentRunId` member on other tags |
| `interrupts` | agent to AG-UI | `interrupt` tag enrichment of an interrupt (`reason`, `responseSchema`, `toolCallId`) |
| `custom` | agent to AG-UI | `custom` and `raw` tags |

`tools` on the agent side implies it can pair with `toolCalls`; an agent that lists `tools` without `toolCalls` receives the
declarations and has no way to call them, which is permitted.

## 5. Negotiation

The proxy already reads the agent card before it opens the message stream (`capabilities.streaming`, input modes). The same
read drives the negotiation, once per run.

| Step | Behavior |
| --- | --- |
| 1 | If the card has no `capabilities.extensions[]` entry whose `uri` is an AG-UI URI the proxy implements, the run is **tier 1**: no `A2A-Extensions` is sent, no `metadata.agui` feature member is written and no tag is interpreted |
| 2 | Otherwise the proxy selects the highest implemented version (section 2, rule 3). The run is **requested tier 2** |
| 3 | The proxy sets the stream begin extension `extensions` (the `A2A-Extensions` header) to the URI on `messageStream` or `messageSend`, and on `tasksCancel` for the same run. `agentCard` is read before the extension is requested and carries none |
| 4 | The set of enabled features is `params.features` intersected with what the proxy implements and what the AG-UI request supplied. A member of `metadata.agui` is written only for an enabled feature |
| 5 | The agent echoes the activated URIs in the reply begin (the `A2A-Extensions` response header, reported by the stream model as the `extensions` member of the reply begin extension). The run is **active tier 2** only if the echo contains the URI |
| 6 | Tags are interpreted only in an active tier 2 run. In a requested-only run (no echo) and in a tier 1 run a tagged part is plain data and maps as `CUSTOM agent.data` or `agent.file`, exactly as an untagged one |

What the echo means: it is the agent's statement that it understood the request metadata and will tag its output. Its absence
is **not an error**. The agent saw unknown metadata, and its response stays tier 1 parseable, which is why a tagged part
without an echo degrades to `agent.data`.

A requested-only run still carries the `metadata.agui` members written in step 4; an agent that ignored the request ignores
them. The proxy never sends A2A details to the AG-UI client in any case: whether the run is tier 1 or tier 2 is visible only to
operators, in a Zilla event.

### 5.1 `required: true`

| Condition | Result |
| --- | --- |
| the card marks the URI `required: true` and the proxy implements a listed version | negotiated as above |
| the card marks it `required: true` and the proxy implements no listed version | the run is refused before the AG-UI reply begin: `AguiResetEx` status 502 |
| the card does not mark it required, the agent returns A2A error `extension.required` | `AguiResetEx` 422 `invalid_run`, as for any other A2A 4xx |

## 6. The metadata key `agui`

### 6.1 `Message.metadata.agui` (AG-UI to agent)

`Message.metadata` is the verbatim `forwardedProps` (an object's members at the top level; a non-object value as
`{"forwardedProps": value}`), plus the `agui` member. The `agui` member MUST appear only when it has at least one member.

| Member | Written when | Value |
| --- | --- | --- |
| `tools` | `tools` enabled and `RunAgentInput.tools` is non-empty | `[{name, description, parameters}]`, the AG-UI tool declarations verbatim (`parameters` is a JSON Schema object) |
| `context` | `context` enabled and `RunAgentInput.context` is non-empty | `[{description, value}]`, verbatim |
| `state` | `state` enabled and `RunAgentInput.state` is non-empty | the AG-UI state, verbatim |
| `resume` | a `resume[]` entry is being continued and carries `metadata` | the entry's `metadata`, as in v1 |

Rule for `state` and `context`: for an enabled feature the value travels **only** in `metadata.agui`; the tier 1 advisory
`data` part (`metadata.agui.part = "input"`) is not written for that member. A member whose feature is not enabled still
travels in the advisory part when the card accepts `application/json`, as in tier 1.

`instructions` (AG-UI `system` and `developer` messages) is **not** a member of this version. Those roles are dropped in
tier 1 and tier 2 alike (section 12).

Example, a run with a frontend tool and state, on the thread's next turn:

```json
{
  "message": {
    "messageId": "msg-3",
    "role": "ROLE_USER",
    "contextId": "ctx-1",
    "parts": [{ "text": "where am I?" }],
    "metadata": {
      "tenant": "acme",
      "agui": {
        "tools": [
          {
            "name": "get_location",
            "description": "Reads the device location",
            "parameters": { "type": "object", "properties": {} }
          }
        ],
        "state": { "units": "metric" }
      }
    }
  },
  "configuration": { "historyLength": 0, "acceptedOutputModes": ["text/plain"] }
}
```

### 6.2 `Part.metadata.agui` (tags)

A tag is the `agui` member of a part's `metadata`. It identifies the AG-UI event a part carries.

| Member | Type | Meaning |
| --- | --- | --- |
| `event` | string, required | the event kind of section 7 |
| `subagentRunId` | string, optional | the part belongs to that subagent run (needs `subagents`); the proxy puts it on the AG-UI event |
| other members | by `event` | section 7 |

The scope of a subagent is **explicit**: a part belongs to a subagent run only when its own tag carries `subagentRunId`.
The proxy does not remember a `started` event and does not apply its id to the parts that follow, because the stream of
one task may interleave the output of several subagents and a remembered scope would attribute a part to the wrong run.
Without `subagents` enabled the member is ignored and the event is mapped without it.

A part without a tag, or whose `metadata.agui` has no `event`, is not tagged. The tier 1 member `part` is the proxy's own
request marker and is never read from an agent part.

### 6.3 The `history` part

The `history` feature lets an agent that lists it receive the earlier messages of the AG-UI thread. A2A has no portable
way to send history and the assistant turns of a client can be forged, so the proxy sends it only as an **advisory,
non-authoritative** part and only to an agent that asked for it. A plain agent never receives it.

| Rule | Behavior |
| --- | --- |
| When | the card lists `history` and accepts `application/json`, the run is not a continuation (no `taskId`, so not a resume and not a tool result answer) and there is an earlier turn. With `params.history` `context` (the default) it is sent only when the run starts a new A2A context: the first run of a thread that already has earlier turns, or a run whose remembered context is lost. With `always` it is sent on every such run, for an agent that does not keep its own history |
| Shape | one `data` part: `{"data": {"messages": [...]}, "mediaType": "application/json", "metadata": {"agui": {"part": "history"}}}` |
| Content | the AG-UI messages before and including the last `assistant` or `tool` message, verbatim and in order. `system` and `developer` messages are dropped, as everywhere. The trailing messages are not repeated: they are the message itself |
| Order | the first part, then the trailing user parts, then the advisory `input` part. The history never replaces the trailing user message, which stays the only authoritative input |
| Independence | `state`, `context` and `tools` are written exactly as without `history` |
| Compaction | the proxy-originated `MESSAGES_SNAPSHOT` of an implicit context reset is unchanged and still precedes every reply frame; history is the request direction only |
| Bound | history is never buffered whole beyond the proxy's retention capacity. It has the lowest priority: it is all-or-nothing, and when keeping it would prevent any other part of the request from being retained, or the history alone does not fit, the history part is omitted and the request continues exactly as it would without it. The omission is never partial and never a rejection, and a Zilla event records the reason |

## 7. Part tags

### 7.1 Overview

| Direction | `event` | Feature | A2A part | AG-UI |
| --- | --- | --- | --- | --- |
| agent to AG-UI | `state` | `state` | `data` | `STATE_SNAPSHOT`, `STATE_DELTA`, `MESSAGES_SNAPSHOT` |
| agent to AG-UI | `activity` | `activity` | `data` | `ACTIVITY_SNAPSHOT`, `ACTIVITY_DELTA` |
| agent to AG-UI | `reasoning` | `reasoning` | `text` | `REASONING_*` message events |
| agent to AG-UI | `reasoning.encrypted` | `reasoning` | `text` | `REASONING_ENCRYPTED_VALUE` |
| agent to AG-UI | `toolCall` | `toolCalls` | `data` | `TOOL_CALL_START`, `TOOL_CALL_ARGS`, `TOOL_CALL_END` |
| agent to AG-UI | `toolResult` | `toolCalls` | `data` | `TOOL_CALL_RESULT` (agent-executed) |
| agent to AG-UI | `step` | `steps` | `data` | `STEP_STARTED`, `STEP_FINISHED` |
| agent to AG-UI | `subagent` | `subagents` | `data` | `SUBAGENT_STARTED`, `SUBAGENT_FINISHED`, `SUBAGENT_ERROR` |
| agent to AG-UI | `text` | `subagents` | `text` | a text message that carries `subagentRunId` |
| agent to AG-UI | `interrupt` | `interrupts` | `data` | enriches the interrupt outcome |
| agent to AG-UI | `custom` | `custom` | `data` | `CUSTOM` |
| agent to AG-UI | `raw` | `custom` | `data` | `RAW` |
| AG-UI to agent | `toolResult` | `tools` | `data` | the answer to a client-executed tool call (section 7.2) |

Tagged parts appear where A2A parts appear in an agent's output: `artifactUpdate` parts, `task` artifacts, and
`status.message` parts of a `statusUpdate` or `task`. A tagged part in a `message` reply (no task) maps the same way.

### 7.2 AG-UI to agent: `toolResult`

AG-UI continues a frontend tool call with the next run's `tool` role messages, not with `resume[]`. For each trailing `tool`
message the proxy writes one `data` part in the continuation message:

```json
{
  "data": { "type": "tool-result", "toolCallId": "call-7", "payload": "{\"lat\":40.7,\"lon\":-74.0}" },
  "mediaType": "application/json",
  "metadata": { "agui": { "event": "toolResult" } }
}
```

| Member | Value |
| --- | --- |
| `data.type` | `tool-result` |
| `data.toolCallId` | **the agent's own call id**, not the handle the AG-UI client saw |
| `data.payload` | the `tool` message content, copied verbatim as a JSON string; an agent that expects an object parses it |
| `data.error` | present only when the AG-UI `tool` message carries an error |

The message is `ROLE_USER` and carries the `taskId` of the interrupted task and **no** `contextId`, because A2A requires the
server to infer the context from the task and to reject a mismatching pair. Trailing user messages become ordinary parts of
the same message. The part is written only when `tools` is enabled; in tier 1 `tool` messages are dropped.

### 7.3 Agent to AG-UI: tags

In each table the part value is the `data` JSON unless stated. `*` marks an optional member.

**`state`**

| Tag member | Meaning |
| --- | --- |
| `type` | `snapshot`, `delta` or `messages` |

The part `data` is the JSON: the state object for `snapshot`, an RFC 6902 JSON Patch array for `delta` (passed through
verbatim), the AG-UI messages array for `messages`. `messages` is an agent-originated `MESSAGES_SNAPSHOT`: the agent has
compacted its own memory and conveys it, and the client replaces its transcript with it.

**`activity`**

| Tag member | Meaning |
| --- | --- |
| `type` | `snapshot` or `delta` |
| `activityType` | the AG-UI activity type (for example `status`) |
| `messageId` | the activity message id |
| `replace`* | boolean, default `true` |

The part `data` is the activity content (snapshot) or a JSON Patch array (delta).

**`reasoning`**: a `text` part; tag members `messageId` and `groupId`*. The text is the reasoning message content.
**`reasoning.encrypted`**: a `text` part holding the encrypted value; tag members `subtype` (`message` or `toolCall`) and
`entityId`.

**`toolCall`**

| Tag member | Meaning |
| --- | --- |
| `executor`* | `client` (default) or `agent` |
| `parentMessageId`* | the assistant message the call belongs to |

The part `data` is `{"type": "tool-call", "id": "<agent call id>", "name": "...", "arguments": {...}}`, the shape agents of
`@ag-ui/a2a` already emit. See section 7.4.

**`toolResult`** (agent-executed): tag member `messageId`*; the part `data` is `{"type": "tool-result", "toolCallId": "...",
"payload": ...}`. It is the result of a call whose `executor` was `agent`.

**`step`**: tag members `name` and `type` (`started` or `finished`); the part `data` is `{}`.

**`subagent`**

| Tag member | Meaning |
| --- | --- |
| `type` | `started`, `finished` or `error` |
| `subagentRunId` | the subagent run id (common member, required here) |
| `name`*, `description`* | labels |
| `parentSubagentRunId`*, `parentToolCallId`*, `parentMessageId`* | placement |
| `outcome`* | `success` or `suspended`, on `finished` |
| `code`*, `message`* | on `error` |

The part `data` is optional. On `finished` it is the result, a JSON value carried as the payload of the event; when it is
absent the payload is empty. On `started` and `error` it is ignored. `suspended` maps to the AG-UI outcome `SUSPENDED` with
an **empty** `interruptIds` list: interrupt ids on the AG-UI side are opaque handles the proxy issues and a client sends
back in `resume[]`, so an id the agent defined could not be resolved by the proxy (409 `handle_unknown`). The interrupts of
a suspended subagent are therefore not resumable by their own ids; the `INPUT_REQUIRED` interrupt of the enclosing task,
with its handle, is the resume path.

**`text`**: a `text` part; tag members `subagentRunId` (required) and `messageId`*. It maps to a text message like an
untagged text part, whose `messageId` defaults to the artifact id or the A2A message id, and whose events carry
`subagentRunId`. An untagged text part cannot carry a subagent id, which is why this tag exists.

**`interrupt`**: tag members `reason`* (an open string; default `input`, or `authorization` for `AUTH_REQUIRED`),
`toolCallId`*. The part `data`, when present, is the `responseSchema`*.

**`custom`**: tag member `name`; **`raw`**: tag member `source`*. The part `data` is the event value.

### 7.4 Frontend tools end to end

1. The AG-UI request lists `tools`; with `tools` enabled the proxy writes them in `metadata.agui.tools`.
2. The agent needs a client-side tool. Its task emits a `toolCall` part (`executor: client`, which is the default) and moves to
   `INPUT_REQUIRED`, because A2A needs the task to wait.
3. The proxy emits `TOOL_CALL_START`, `TOOL_CALL_ARGS`, `TOOL_CALL_END` with `toolCallId` = **an opaque random handle** that
   it remembers owner-locally against (identity, agent, `threadId`, `taskId`, agent call id), then ends the run `SUCCESS` with
   `pendingToolCallIds` listing the handles. It does **not** end the run as an interrupt, because AG-UI forbids reporting a
   tool call stop as interrupted.
4. The application runs the tool and posts the next run. Its `messages` carry the assistant message with the handles and one
   `tool` message per handle.
5. The proxy resolves every `toolCallId` to the one `taskId`, consumes the handles, and sends the continuation message of
   section 7.2. An unknown, expired or foreign handle, or handles that disagree on the task, reject the run before the agent
   is contacted with 409 `handle_unknown`.
6. The agent resumes the task and the stream continues as a normal run.

Interaction with interrupts:

| A2A situation | AG-UI outcome | Id used | Answered by |
| --- | --- | --- | --- |
| `INPUT_REQUIRED`, `status.message` has only untagged or non-`toolCall` parts | `INTERRUPT` | interrupt handle | `resume[]` entry |
| `INPUT_REQUIRED` with `toolCall` parts (`executor: client`) | `SUCCESS` with `pendingToolCallIds` | tool call handles | `tool` messages |
| both in one status message | `INTERRUPT` (the outcome has no pending list; pending calls are derived from the stream) | both | `resume[]` plus `tool` messages, merged into one continuation message; every looked-up `taskId` must agree |
| `AUTH_REQUIRED` | `INTERRUPT`, reason `authorization` | interrupt handle | `resume[]` entry |
| agent-executed `toolCall` (`executor: agent`) | `TOOL_CALL_*` and the run continues | agent call id as received | not answered by the client |

A tag in a tier 1 run is never a tool call: `tool` messages are ignored there.

## 8. Ordering and framing

1. **Stream order is event order.** The A2A stream is linear; the AG-UI events are emitted in the order the parts arrive. Parts
   of one message keep their order.
2. **An open text message is closed first.** Before any non-text AG-UI event, including one from a tagged part, an open text
   message is ended (`TEXT_MESSAGE_END`), as for `CUSTOM` in tier 1. Text that continues afterwards starts a new message.
3. **`MESSAGES_SNAPSHOT` goes first.** The proxy-originated snapshot of an implicit context reset precedes every other reply
   frame. An agent-originated `state` tag of type `messages` is emitted in stream order and MUST NOT arrive inside a text
   message the agent keeps appending to; the proxy ends the open message first, and a client replaying with event compaction
   reorders a snapshot that interrupts a text stream.
4. **Untagged parts are text.** An untagged `text` part is assistant text; its `messageId` is the artifact id (or the A2A
   message id), as in tier 1. An untagged `data` or `file` part is `agent.data` or `agent.file`.
5. **Run end.** The outcome is derived from the last task state. The `INPUT_REQUIRED` rules of section 7.4 apply once the status
   is seen; tagged parts in the same `status.message` are processed first.
6. **Working status.** Tagged parts of a `WORKING` `status.message` map like artifact parts. Untagged working text keeps the
   tier 1 behavior, an `ACTIVITY_SNAPSHOT` of type `status`.

### 8.1 Reading tagged parts

The proxy decodes the A2A event once, as it arrives, and finds a part's tag before it maps the part.

1. The tag, and for `toolCall` and `toolResult` the `id`, `name` and `toolCallId` members of `data`, MUST be readable
   inside the proxy's decode window. A `metadata` member and a `data` member may appear in either order, but a part whose
   tag is not readable inside the window, such as a `data` value larger than the window that precedes `metadata`, is not
   recognized as tagged and maps as `agent.data`.
2. The `data` of `toolCall` maps to the `TOOL_CALL_ARGS` of its `arguments` member and the `data` of `toolResult` to the
   content of its `payload` member. Both members are carried as their JSON text, so a string payload keeps its quotes.
3. `reasoning` and `reasoning.encrypted` map the `text` of the part; `step` maps no content.
4. A tag for a feature that is not enabled is plain data (section 9). The features the proxy enables are the ones it
   implements: `interrupts` is not enabled yet, so its tags map as `agent.data`. An unknown `event`
   is dropped and recorded by the Zilla event `binding.agui.a2a.tag.dropped`.
5. A `toolCall` with `executor` `client` (the default) maps to `TOOL_CALL_START`, `TOOL_CALL_ARGS` and `TOOL_CALL_END`
   with a handle as `toolCallId`. When the task then reaches `INPUT_REQUIRED` with no untagged text in its status message,
   the run ends `SUCCESS` and the end detail is `{"pendingToolCallIds": [...]}`, the handles in call order. With untagged
   text the run ends as an interrupt.

## 9. Error handling

| Condition | Behavior |
| --- | --- |
| tag for a feature that is not enabled | the part is not interpreted: plain data (`agent.data` / `agent.file`) |
| unknown `event` | the part is dropped; an informational Zilla event records the tag |
| unknown tag member | ignored |
| a tagged part whose value does not have the shape of its tag (for example a `toolCall` without `id`) | the run aborts `error{status 502, code "agent_protocol"}`, details to a Zilla event |
| a `toolResult` whose handle is unknown, expired or foreign | 409 `handle_unknown`, before the agent is contacted |
| `contextId` mismatch | `agent_protocol`, as in tier 1 |
| agent does not echo the extension | tier 1 reading of the reply (section 5, step 6) |
| agent returns `extension.required` | 422 `invalid_run` (section 5.1) |
| the card marks the extension required and the proxy cannot activate it | 502 before the reply begin (section 5.1) |
| `forwardedProps` has `agui`, or the URI in an active run | 400 `reserved_key`, before the agent is contacted |

No A2A id, task state, extension URI or tag name is placed on the AG-UI client stream. They go to Zilla events.

## 10. What each tier enables

| AG-UI feature | Tier 1: plain A2A (v1) | Tier 2: this extension |
| --- | --- | --- |
| Text, errors, outcomes (success, cancelled, error) | yes | yes |
| Interrupt and resume (`input-required`, `auth-required`) | plain `input` / `authorization` interrupts | `reason`, `responseSchema`, `toolCallId` through the `interrupt` tag |
| Cancel on abort | yes | yes (`tasksCancel` carries the extension) |
| `state` and `context` | one advisory `application/json` data part, when the card accepts it | `metadata.agui.state` / `.context` with defined semantics |
| `system` / `developer` messages | dropped | dropped (section 12) |
| `STATE_SNAPSHOT`, `STATE_DELTA` | none; data parts are `agent.data` | `state` tag |
| `MESSAGES_SNAPSHOT` | proxy-originated only, on an implicit context reset | also agent-originated (`state` tag, `messages`) |
| `ACTIVITY_*` | `ACTIVITY_SNAPSHOT` `status` for the text of a `WORKING` status message, never a transcript message | `activity` tag |
| `REASONING_*` | none | `reasoning`, `reasoning.encrypted` tags |
| `STEP_*` | none | `step` tag |
| agent-side `TOOL_CALL_*` | none | `toolCall`, `toolResult` tags |
| frontend tools | dropped | declared, called, answered (section 7.4) |
| `SUBAGENT_*` | none | `subagent` tag |
| `CUSTOM`, `RAW` | `CUSTOM agent.data` / `agent.file` for non-text parts | `custom`, `raw` tags |
| history replay | latest user turn only | latest user turn only, plus an advisory `history` part for an agent that declares `history` (section 6.3) |

## 11. Interoperability with `@ag-ui/a2a`

Agents written for the `@ag-ui/a2a` precedent emit `data` parts shaped `{type: "tool-call", ...}` and
`{type: "tool-result", ...}`. Those agents stay **tier 1** when their card does not list the URI: their parts arrive as
`agent.data`. An agent that lists the URI, with the same part shapes plus the tag of section 7.3, becomes tier 2 without
changing its payloads. The one deliberate difference is that a `toolResult` answer travels in a `ROLE_USER` message.

## 12. Where the follow-up work lands

The extension gives each deferred item a place; it does not specify the implementation.

| Follow-up | Place in this document |
| --- | --- |
| #2885 working status messages as activity | tier 1 behavior of an untagged `WORKING` message, an `ACTIVITY_SNAPSHOT` `status` that replaces the earlier fallback reply; in tier 2 the `activity` and `step` tags |
| #2886 A2UI content plug-in | not part of this extension. A plug-in is its own A2A extension owned by its content type; it adds its media type to `acceptedOutputModes` and its URI to `A2A-Extensions` when the card declares it, and maps to an AG-UI activity type. Adding one needs no change to this carrier |
| #2887 durable state tier | not part of the protocol: handles and contexts stay owner-local on the proxy, behind the state interface |
| #2888 frontend tools through the extension | features `tools` and `toolCalls`; `metadata.agui.tools`; `toolCall` and `toolResult` tags; section 7.4 |
| #2889 state, activity, reasoning, step and tool-call events | features `state`, `activity`, `reasoning`, `steps`, `toolCalls`, `custom`; section 7.3 |
| #2890 agent-declared history feature | feature `history`; section 6.3 |
| #2891 subagent events | feature `subagents`; `subagent` tag and the `subagentRunId` member |
| `instructions` (`system` / `developer`) | feature name reserved and not defined. The proxy drops those roles because forwarding them lets a client assert privileged instructions across a trust boundary. Any definition needs its own security review and is not planned |

## 13. Decisions

Where the source design was silent or contradicted itself, these decisions were taken and are reflected above.

| # | Point | Decision |
| --- | --- | --- |
| 1 | The URI | `https://a2a.aklivity.io/ext/agui/v1`: host `a2a.aklivity.io`, path `ext/<name>/v<major>` (section 2) |
| 2 | `instructions` | reserved name, not defined (sections 3, 12) |
| 3 | `history` | defined as the advisory history part of section 6.3 |
| 4 | `custom` and `raw` tags | enabled by a feature named `custom` (section 4.1) |
| 5 | Request alone vs the reply echo | tags are honored only after the reply echoes the URI (section 5, step 6) |
| 6 | Tier 2 `state` / `context` vs the tier 1 advisory part | they replace it, per enabled feature (section 6.1) |
| 7 | The part carrying the `interrupt` enrichment | a `data` part in `status.message` (section 7.3) |
| 8 | Part values for `step`, `reasoning.encrypted` and agent-executed `toolResult` | `{}`, the encrypted text, and the `tool-result` data part (section 7.3) |
| 9 | A malformed tagged part | the run aborts 502 `agent_protocol` (section 9) |
| 10 | The card requires the extension and the proxy cannot activate it | 502 before the reply begin (section 5.1) |
| 11 | `AguiEventMeta` `timestamp` and `metadata` | not carried, out of scope for the extension (section 1) |
| 12 | The header name | `A2A-Extensions` (section 2) |
| 13 | History part | one advisory `data` part with `metadata.agui.part` `history`, sent first, only to an agent that lists `history`, by default only when the run starts a new A2A context and never on a continuation (section 6.3) |
| 14 | History yields | history is all-or-nothing and has the lowest priority: when retaining it would keep any other part of the request from being retained, or it cannot fit, it is omitted whole, never partial and never a rejection, and a Zilla event records the reason (section 6.3) |
| 15 | Subagent scope | explicit only: a part carries its own `subagentRunId`, a `text` tag exists so that a text message can carry it, and a remembered scope is not used because subagents may interleave (section 6.2) |
| 16 | Suspended subagent | `suspended` maps with an empty `interruptIds` list: agent-defined interrupt ids would not resolve through the opaque handles the proxy issues (section 7.3) |

Unresolved: none.
