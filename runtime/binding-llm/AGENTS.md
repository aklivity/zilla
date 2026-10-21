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
  Dialect-specific members use the `<dialect>:<name>` prefix. (planned for the text
  guard, which currently collects them and re-emits them after the text)
- Anything that a transform might need to adjust or exclude rides in **range
  attributes** (below), not in the body.
- Response-level and request-level members that belong to no section ride in
  `CONTROL` sections, one member per section. Control sections bypass guardrails.

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
| not identity | **implemented**: dropped for the section |
| not identity, bytes unchanged up to the first edit | planned: kept until the first byte that differs from the input, verified in lockstep, then dropped for the rest of the section |

Attributes that a stage could have changed are dropped, never forwarded. A later
refinement may let a stage report omitted, injected and retained bytes per range so
that ranges after an edit survive.

The ranges pending for a section are bounded by what one frame extension can carry,
and are dropped for the rest of the section once they exceed it.

## Per section type

The **model-visible value** is what a guardrail stage inspects. Everything else in
the section passes through. (planned; the guard currently passes the whole payload
of non-text sections to the stage)

| Section | Model-visible value | Streaming | Non-streaming |
|---|---|---|---|
| `SYSTEM_INSTRUCTION`, `USER_TEXT`, `TOOL_RESULT` | `text` | request side, whole | request side, whole |
| `ASSISTANT_TEXT`, `ASSISTANT_REASONING`, `ASSISTANT_REFUSAL` | `text` | one section, one range per delta | one section, one range |
| `TOOL_CALL`, `SERVER_TOOL_CALL` | `arguments` (the JSON value), the same value mcp(proxy) validates for `tools/call` | partial JSON fragments in frames, one range per delta | one frame, one range |
| `USER_IMAGE`, `USER_DOCUMENT`, `USER_AUDIO`, `USER_VIDEO` | the decoded media data, **verdict-only** (`identity()` stages); a URL reference is not inspected | request side, whole (may be split by credit) | same |
| `TOOL_DEFINITION`, `SERVER_TOOL_DEFINITION` | none | request side, whole | same |
| `SERVER_TOOL_RESULT` | none yet | not covered | whole |
| `CITATIONS` | none; every member is prefixed | not covered | request history only today |
| `CONTROL` | none; bypasses guardrails | one section per member | same |

Streaming and non-streaming differ only in how many frames and ranges carry the same
section. The section type, body shape and members are identical.

## Per-delta members

A member that varies per delta, such as a token log probability, is carried as an
attribute on that delta's range, in one section. Producers emit ranges. Consumers
also accept the older shape, a section per delta with a control section after each,
until it is retired. The older shape splits the text across guardrail chains, so a
pattern that straddles two deltas is not seen whole.
