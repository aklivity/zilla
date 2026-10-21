# binding-llm — Section Contract

Guidance scoped to `runtime/binding-llm` and the matching `specs/binding-llm.spec`.
Repo-wide guidance lives in the root [AGENTS.md](../../AGENTS.md); runtime
conventions live in [runtime/AGENTS.md](../AGENTS.md).

A request or response is carried as a sequence of **sections**. Each section is a
JSON payload with a type (`LlmSectionType`) and a message index, delivered in one or
more DATA frames: `init` on the first frame, `fin` on the last.

Status key: **implemented** is in the IDL, spec functions and runtime today;
**planned** is the agreed contract, not yet implemented.

## Body members and attributes

- Members that sit beside a section's primary value are **opaque**. A proxy relays
  them unmodified and in order. They are never presented to a guardrail stage.
  Dialect-specific members use the `<dialect>:<name>` prefix. (**implemented** for the
  text sections and for tool calls: the guard relays the bytes before the primary
  value as they arrive and the bytes after it once the chain has finished the value)
- Anything that a transform might need to adjust or exclude rides in **range
  attributes** (below), not in the body.
- Response-level and request-level members that belong to no section ride in
  `CONTROL` sections. Control sections bypass guardrails. A run of request-level
  members adjacent in the source forms one `CONTROL` section whose payload object
  holds every member of the run (`{"x:temperature":0.7,"openai:store":false}`),
  streamed key by key in source order with no holding. A decoder closes the section
  when a section-bearing member or the end of the body intervenes, or when a member
  that is not request-level control (the model, the output token limit, `stream`,
  `stream_options`) does. A client keeps or drops each key on its own and writes the
  members it keeps into the request in order. (**implemented** for the request path
  of every dialect; the response path still carries one member per section)

## Shared members (`x:`)

A member carried as `<dialect>:<member>` is restored only by a client of that dialect and dropped by every
other client. A member that two or more dialects have with the same meaning is carried as `x:<member>`
instead. (**implemented** as the `x:` namespace in `LlmToolTransform`: `shared()`, `share()` and `reject()`;
the sampling and length members below are promoted for the flat dialects)

- `x` is reserved as a dialect name. `LlmDialectFactory` refuses to register a dialect named `x`.
- A decoder emits `x:<member>` in place of `<dialect>:<member>` for each member it understands, including a
  leaf lifted out of a native container. The value is canonical (OpenAI-shaped); the decoder converts it. A
  prefixed member keeps its native value unchanged.
- A client writes its own `<dialect>:` members, translates the `x:` members it supports into native form,
  and drops an `x:` member it does not know. Each key of a control section is kept or dropped on its own.
- `x:` appears only at depth 1 of a control section's object and at depth 1 of a section payload. Native
  containers are never `x:`-prefixed; their promotable leaves are lifted out.
- A decoder may emit an `x:` member after the request's sections, so a client treats every `x:` member as
  possibly arriving up to the end of the request and does not rely on the order of `x:` members.
- A client applies one **legality class** to an `x:` member the target cannot honour:
  - **A**, advisory: dropped silently.
  - **B**, behavioural: the request is rejected, and the message names the member and the dialect pair
    (`x:<member> cannot be converted from <dialect> to <dialect>`).
  - **C**, advisory numeric with a narrower range on the target: clamped into the target's range, never
    rescaled.
- **Graduation.** An `x:` member becomes an unprefixed canonical member when all hold: every registered
  dialect either supports it natively or documents the provider as lacking it; one value domain and one
  clamp table across dialects; one legality class with no pair-specific exceptions; per-pair scripts pass
  across two minor releases; and the decision is recorded in an issue.
- Members that carry content or grant a capability are sections, not `x:` members, and a guardrail stage
  never sees an `x:` member.

### Promoted sampling members

The canonical value follows OpenAI: numbers for `temperature`, `top_p`, `top_k`, `frequency_penalty` and
`presence_penalty`, an integer for `seed` and `top_k`, an array of strings for `stop`. Each decoder converts
the members it understands and leaves every other member prefixed. The maximum output tokens limit is not an
`x:` member; it stays on the dialect flush. A native value that does not parse as the canonical value is
passed through and left to the client to reject. (**implemented** for `openai`, `openai/responses` and
`anthropic`; the `gemini` container members are not promoted yet)

| `x:` member | Range | Class | `openai` | `openai/responses` | `anthropic` |
|---|---|---|---|---|---|
| `temperature` | 0..2 | A, C | `temperature` | `temperature` | `temperature`, clamped into 0..1 |
| `top_p` | 0..1 | A | `top_p` | `top_p` | `top_p` |
| `top_k` | integer | A | dropped | dropped | `top_k` |
| `stop` | array of strings | B | `stop`; a decoded string becomes a one-entry array | rejects | `stop_sequences` |
| `seed` | integer | A | `seed` | dropped | dropped |
| `frequency_penalty`, `presence_penalty` | -2..2 | A | same names | dropped | dropped |

- `temperature` and the penalties are advisory (A), `temperature` is clamped (C) only where the target range is
  narrower than the canonical one. A value outside the canonical range is not clamped by a target that shares
  that range; the upstream provider judges it.
- `stop` is behavioural (B). A client without a stop on the wire rejects with
  `x:stop cannot be converted from <dialect> to <dialect>`. The `openai` client also rejects more than four
  entries on a cross-dialect hop, and writes a same-dialect hop as received. A `null` `stop` is the absence of
  the member and is dropped.
- A same-dialect hop restores every native member exactly. The one normalisation is the `openai` `stop` string,
  which comes back as a one-entry array; `anthropic` `stop_sequences` round-trips unchanged through `x:stop`.
- A decoder emits the member at its source position inside the control section it belongs to, so a run such as
  `{"x:temperature":0.7,"openai:store":false}` stays one section.

## Ranges (`LlmSectionDataEx.ranges`)

A DATA frame's extension may carry an ordered list of ranges, each with a `length`,
a `closing` flag (default `1`) and a list of `LlmAttribute` (name, value). (the IDL,
`llm:dataEx()` builder and `llm:matchDataEx()` matcher are **implemented**)

- The ranges of a frame cover that frame's content in order, so their lengths add
  up to the frame's content length. A range of length `0` is allowed, for an
  attribute that belongs to a delta with no content.
- A range that is split across frames by flow control is carried as parts. Every
  part except the last has `closing = 0` and no attributes. The last part has
  `closing = 1` and carries the attributes, so a range's attributes are delivered
  only once the whole range has been delivered.
- Coordinates:
  - the `text` value of the six text sections, `SYSTEM_INSTRUCTION`, `USER_TEXT`,
    `ASSISTANT_TEXT`, `ASSISTANT_REASONING`, `ASSISTANT_REFUSAL` and `TOOL_RESULT`,
    in UTF-8 bytes of the **decoded** text, independent of JSON escaping;
  - the `arguments` value of `TOOL_CALL` and `SERVER_TOOL_CALL`, in bytes of the JSON
    value exactly as it is carried, so the bytes of the members around it are not
    covered by any range;
  - every other section, in payload bytes.
- A producer should align frames to its source's chunks, so one frame carries one
  range, and split a range only when credit forces it. A consumer must handle both.

### Through llm(proxy)

Only the init frame of a section carries the section type, message and attributes. A
later frame's extension carries only ranges, and is omitted when it has none.

| Guardrail pipeline for the section type | Ranges and attributes |
|---|---|
| none | relayed with the frames |
| `identity()` | **implemented**: relayed, re-cut to the output frames by cumulative byte count; parts of one range that leave in the same frame are merged |
| not identity | **implemented**: kept until the first byte that differs from the input, then dropped for the rest of the section |

A chain that is not identity is followed in lockstep: what it produces is compared, byte
for byte, with what it consumed at the same position, however far its output lags its
input. Ranges are kept while they match. A range that contains the first differing byte,
and every range after it, is dropped, so a frame that holds that byte carries ranges only
for the bytes before the range that contains it. A range that was left open by an earlier
frame carries no attributes.

Attributes that a stage could have changed are dropped, never forwarded. A later
refinement may let a stage report omitted, injected and retained bytes per range so
that ranges after an edit survive.

The ranges pending for a section are bounded by what one frame extension can carry,
and are dropped for the rest of the section once they exceed it.

## Per section type

The **model-visible value** is what a guardrail stage inspects. Everything else in
the section passes through. (**implemented** for the text sections, tool calls and
media; the guard currently passes the whole payload of every other section to the
stage)

| Section | Model-visible value | Streaming | Non-streaming |
|---|---|---|---|
| `SYSTEM_INSTRUCTION`, `USER_TEXT`, `TOOL_RESULT` | `text` | request side, whole | request side, whole |
| `ASSISTANT_TEXT`, `ASSISTANT_REASONING`, `ASSISTANT_REFUSAL` | `text` | one section, one range per delta | one section, one range |
| `TOOL_CALL`, `SERVER_TOOL_CALL` | `arguments` (the JSON value), the same value mcp(proxy) validates for `tools/call` | partial JSON fragments in frames, one range per delta | one frame, one range |
| `USER_IMAGE`, `USER_DOCUMENT`, `USER_AUDIO`, `USER_VIDEO` | the decoded media data, **verdict-only**; a URL reference is not inspected | request side, whole (may be split by credit) | same |
| `TOOL_DEFINITION`, `SERVER_TOOL_DEFINITION` | none | request side, whole | same |
| `SERVER_TOOL_RESULT` | none yet | not covered | whole |
| `CITATIONS` | none; every member is prefixed | not covered | request history only today |
| `CONTROL` | none; bypasses guardrails | one section per member | same |

The media data is the base64 in `image_url.url` (a `data:` URI that declares base64),
`file.file_data` (a `data:` URI that declares base64), `input_audio.data` and
`input_video.data` (base64 on its own). A `data:` URI without base64 and any URL or file
id reference are not inspected, and the chain is not started for them. The chain is
presented the decoded bytes, with `init` on its first fragment and `fin` once the value
has ended, and only its verdict applies: what it produces is discarded and the bytes of
the section are relayed unmodified. The bytes that carry the end of the media wait for
the verdict, so a rejected section never completes downstream. Base64 that cannot be
decoded rejects the section. Ranges are in payload bytes.

Streaming and non-streaming differ only in how many frames and ranges carry the same
section. The section type, body shape and members are identical.

## Per-delta members

A member that varies per delta, such as a token log probability, is carried as an
attribute on that delta's range, in one section. Producers emit ranges. Consumers
also accept the older shape, a section per delta with a control section after each,
until it is retired. The older shape splits the text across guardrail chains, so a
pattern that straddles two deltas is not seen whole.
