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
the sampling, tool choice, response format and reasoning members below are promoted for every dialect, the `gemini`,
`anthropic` and `openai/responses` ones lifted out of their native containers)

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
passed through and left to the client to reject. (**implemented** for `openai`, `openai/responses`, `anthropic`
and `gemini`)

| `x:` member | Range | Class | `openai` | `openai/responses` | `anthropic` | `gemini` (inside `generationConfig`) |
|---|---|---|---|---|---|---|
| `temperature` | 0..2 | A, C | `temperature` | `temperature` | `temperature`, clamped into 0..1 | `temperature` |
| `top_p` | 0..1 | A | `top_p` | `top_p` | `top_p` | `topP` |
| `top_k` | integer | A | dropped | dropped | `top_k` | `topK` |
| `stop` | array of strings | B | `stop`; a decoded string becomes a one-entry array | rejects | `stop_sequences` | `stopSequences` |
| `seed` | integer | A | `seed` | dropped | dropped | `seed`, dropped outside int32 |
| `frequency_penalty`, `presence_penalty` | -2..2 | A | same names | dropped | dropped | `frequencyPenalty`, `presencePenalty` |

- `temperature` and the penalties are advisory (A), `temperature` is clamped (C) only where the target range is
  narrower than the canonical one. A value outside the canonical range is not clamped by a target that shares
  that range; the upstream provider judges it.
- `stop` is behavioural (B). A client without a stop on the wire rejects with
  `x:stop cannot be converted from <dialect> to <dialect>`. The `openai` client also rejects more than four
  entries and the `gemini` client more than five on a cross-dialect hop, and both write a same-dialect hop as
  received. A `null` `stop` is the absence of
  the member and is dropped.
- A same-dialect hop restores every native member exactly. The one normalisation is the `openai` `stop` string,
  which comes back as a one-entry array; `anthropic` `stop_sequences` round-trips unchanged through `x:stop`.
- A decoder emits the member at its source position inside the control section it belongs to, so a run such as
  `{"x:temperature":0.7,"openai:store":false}` stays one section.
- `seed` is an integer; the `gemini` client drops a value that is not an integral number within int32 (A) and
  passes a `null` seed as absent.

### Promoted tool choice members

`x:tool_choice` carries the OpenAI Chat value, `x:parallel_tool_calls` a boolean. A choice is one of `"none"`, `"auto"`,
`"required"`, a named function `{"type":"function","function":{"name":N}}`, or
`{"type":"allowed_tools","allowed_tools":{"mode":"auto"|"required","tools":[{"type":"function","function":{"name":N}}]}}`.
Only function entries have a canonical form. (**implemented** for `openai`, `openai/responses`, `anthropic` and `gemini`)

| `x:` value | `openai` | `openai/responses` | `anthropic` | `gemini` (`toolConfig.functionCallingConfig`) |
|---|---|---|---|---|
| `"auto"` | `tool_choice` `"auto"` | `"auto"` | `{"type":"auto"}` | `mode` `AUTO` |
| `"required"` | `"required"` | `"required"` | `{"type":"any"}` | `mode` `ANY` |
| `"none"` | `"none"` | `"none"` | `{"type":"none"}` | `mode` `NONE` |
| named function | unchanged | `{"type":"function","name":N}` | `{"type":"tool","name":N}` | `mode` `ANY`, `allowedFunctionNames` `[N]` |
| `allowed_tools` | unchanged | flat `{"type":"allowed_tools","mode":M,"tools":[{"type":"function","name":N}]}` | rejects | `mode` `ANY` (required) or `AUTO`, `allowedFunctionNames` |
| `x:parallel_tool_calls` | `parallel_tool_calls` | `parallel_tool_calls` | `tool_choice.disable_parallel_tool_use`, negated | `true` dropped, `false` rejects |

- **Decoding.** `openai` renames both members and keeps the value. `openai/responses` renames `parallel_tool_calls` and
  nests the flat function and `allowed_tools` forms into the canonical value; a hosted tool choice
  (`file_search`, `mcp`, ...) or any other shape stays `openai:tool_choice` unchanged. `anthropic` splits the native
  choice into `x:tool_choice` and `x:parallel_tool_calls` (the negation of `disable_parallel_tool_use`, which is
  consumed); a member it does not know stays under `anthropic:tool_choice` as residue, which is also where a choice
  with an unknown `type` or a `tool` without a `name` stays. `gemini` lifts `functionCallingConfig` out of
  `toolConfig` (camel or snake case): `AUTO` is `"auto"`, `ANY` is `"required"`, a single name is the function form,
  several names are `allowed_tools` with mode `required`, `AUTO` with names is `allowed_tools` with mode `auto`,
  `NONE` is `"none"`. `VALIDATED`, a `NONE` with names, a missing mode or a member this table does not know keep the
  whole `functionCallingConfig` in the residue `gemini:toolConfig`, which also holds every other `toolConfig`
  member and keeps the source spelling.
- **Holding.** The forms that convert are held by the decoder (`LlmToolTransform` `CAPTURE_VALUE`) and rewritten
  once the value is complete, because the target form depends on members that arrive in any order. The hold is
  bounded so the canonical form always fits the encode slot: 4096 units for `anthropic`, 2048 for `openai/responses`
  and 768 for `gemini` (a unit is a captured event plus the length of its text), and a value beyond the bound rejects
  the request. Each held value runs through its own pipeline and is appended to the control section like the
  `openai` `stop` conversion.
- **Encoding.** `openai` and the `openai/responses` parallel member pass through. `openai/responses` flattens the
  canonical value. `anthropic` and `gemini` write the choice as a one-member native container fragment
  (`tool_choice`, `toolConfig`) that `LlmMemberCollector` holds and writes once at the end of the request; the
  `anthropic` client also holds `x:parallel_tool_calls` and writes `disable_parallel_tool_use` (negated, and
  `true` as `false`) into the same `tool_choice` at the end, with `{"type":"auto"}` when no choice arrived, and
  omits it for `none`, which has no such field. A same-dialect `anthropic` hop restores
  `{"type":"auto","disable_parallel_tool_use":true}` exactly; the `gemini` container is written after
  `generationConfig`.
- **Legality.** Both members are behavioural (B). A canonical value that is not one of the four forms, an
  `allowed_tools` or an entry that is not a function rejects for `openai/responses`, `anthropic` and `gemini`
  (`x:tool_choice cannot be converted from <dialect> to <dialect>`); `allowed_tools` always rejects for `anthropic`;
  `x:parallel_tool_calls` false rejects for `gemini`
  (`x:parallel_tool_calls cannot be converted from <dialect> to gemini`) and a value that is not a boolean rejects
  for `anthropic` and `gemini`. Models that refuse a forced choice are model capability data, not part of this
  contract.
- Function names are matched against the tool definitions unchanged: no client maps tool names, so the name in
  `x:tool_choice` is the name the definitions carry. A same-dialect hop restores the choice, with the exception that
  `ANY` with several names comes back as `ANY` with the same names and `allowed_tools` with mode `required` and one
  name comes back as the function form.

### Promoted response format member

`x:response_format` carries the OpenAI Chat value: `{"type":"text"}`, `{"type":"json_object"}` or
`{"type":"json_schema","json_schema":{"name":N,"description":D,"schema":S,"strict":B}}`. (**implemented** for `openai`,
`openai/responses`, `anthropic` and `gemini`)

| `x:` value | `openai` | `openai/responses` (`text.format`) | `anthropic` (`output_config.format`) | `gemini` (`generationConfig.responseFormat.text`) |
|---|---|---|---|---|
| `{"type":"text"}` | `response_format` unchanged | `{"type":"text"}` | omitted | omitted |
| `{"type":"json_object"}` | unchanged | `{"type":"json_object"}` | rejects | `mimeType` `APPLICATION_JSON` |
| `json_schema` | unchanged | flat `{"type":"json_schema","name":N,"description":D,"schema":S,"strict":B}` | `{"type":"json_schema","schema":S}`; `name`, `description` and `strict` dropped | `mimeType` `APPLICATION_JSON`, `schema` S; `name`, `description` and `strict` dropped |

- **Decoding.** `openai` renames the member and keeps the value. `openai/responses` nests the flat `text.format` under
  `json_schema` and keeps the other `text` members (`verbosity`) as `openai:text` residue. `anthropic` lifts
  `output_config.format` as `json_schema` with the `name` defaulted to `"response"` and keeps every other
  `output_config` member (`effort`) as `anthropic:output_config` residue; a `type` other than `json_schema` rejects the
  request. `gemini` lifts `application/json` (or `APPLICATION_JSON`) without a schema as `json_object` and with a schema
  as `json_schema` with the name `"response"`, from `responseFormat.text` (`mimeType`, `schema`) and from the deprecated
  `responseMimeType` with `responseSchema` or `responseJsonSchema`; camel and snake spellings are decoded. Other mime
  types (`text/x.enum`, `text/plain`) and every member this table does not know stay in `gemini:generationConfig`.
  As for any native container, the residue is split around a lifted member, so one section may hold the same residue key
  several times, and a container whose every member is lifted emits no residue.
- **Streaming and bounds.** Values are rewritten key by key and never held, with two exceptions. `openai/responses`
  needs `type` to open the `json_schema` wrapper, so the members that precede `type` are held until it arrives, bounded by
  2048 units (a unit is a captured event plus the length of its text); a flat format that exceeds the bound, has an
  unknown `type` or has no `type` rejects the request (`The 'text.format' parameter is invalid, or its 'type' follows
  members that are too large.`). `anthropic` always writes `type` first, so its source order is free. `gemini` decides
  at the first member of `responseFormat.text`, or at the first of `responseMimeType` and the schema leaf: a schema that
  precedes its mime type is not held, and the whole `responseFormat`, or the schema and the mime type, stay in the
  residue, so a same-dialect hop restores them and another dialect does not receive them. A second schema leaf stays
  in the residue, and members of a lifted `responseFormat.text` other than `mimeType` and `schema` are not carried.
- **Encoding.** `openai` passes the member through. The other clients write the native form as a container fragment
  (`text`, `output_config`, `generationConfig`) that `LlmMemberCollector` holds and writes once at the end of the request,
  merged with the residue fragments and, for `gemini`, `maxOutputTokens`, so the held contents are bounded by one pool
  slot like every other container: a format larger than the slot is flow-controlled to a stop. The members of the
  canonical value may arrive in any order: `json_schema` before `type` is written as when `type` is first, and a
  `type` that disagrees with the members rejects.
- **Gemini form.** The client writes the documented current form, a single object
  `{"text":{"mimeType":"APPLICATION_JSON","schema":S}}` inside `generationConfig.responseFormat`. The array form used
  on another surface is not produced, and the deprecated `responseMimeType`, `responseSchema` and `responseJsonSchema`
  are never written. A same-dialect hop therefore normalises the deprecated shape to the current one and restores the
  current shape exactly; the `anthropic` format restores exactly, the decoder adds the default name `"response"` to the
  canonical value and the client drops it.
- **Legality.** The member is behavioural (B). It rejects naming the member and the pair
  (`x:response_format cannot be converted from <dialect> to <dialect>`) when the value is not one of the three forms,
  when the type and the members disagree, and for `json_object` towards `anthropic`. A schema keyword that the target
  does not support rejects naming the keyword
  (`x:response_format keyword <keyword> cannot be converted from <dialect> to <dialect>`). `strict` is dropped where the
  provider always constrains decoding (`anthropic`, `gemini`).
- **Keyword scan.** The `anthropic` and `gemini` clients scan the schema while it streams through, with no hold
  (`LlmSchemaScanner`), tracking which objects are schemas, maps of named schemas (`properties`, `patternProperties`,
  `$defs`, `definitions`, `dependentSchemas`), lists of schemas (`allOf`, `anyOf`, `oneOf`, `prefixItems`) and data
  (`enum`, `const`, `default`, `examples` and unknown members), so a property named like a denied keyword is not
  rejected. The checked sets are these lists; extend them by adding keys to the per-target arrays.

  | Target | Rejected keywords |
  |---|---|
  | `anthropic` | `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minLength`, `maxLength`, `maxItems`, `uniqueItems`, `contains`, `minContains`, `maxContains`; `minItems` other than 0 or 1; `$ref` that does not start with `#`; `$ref` that is exactly `#` |
  | `gemini` | `patternProperties`, `not`, `if`, `then`, `else`, `dependentSchemas`, `unevaluatedProperties`; `$ref` that does not start with `#` |
  | `openai`, `openai/responses` | none |

  Recursion is detected only as the plain `"$ref":"#"` (a reference to the schema root); a recursive reference through
  `$defs` or `definitions` is not detected while streaming and is left to the provider.

### Promoted reasoning members

`x:reasoning_effort` is one of `none`, `minimal`, `low`, `medium`, `high`, `xhigh` and `max`; `x:thinking_budget_tokens` is
an integer of 0 or more. A decoder emits a member only when the source carries it. A `null` value is the absence of the
member: the nested decoders drop it, the flat `openai` rename passes it through and every client reads it as absent. A value
that is not one of the above rejects at the client (class B,
`x:reasoning_effort cannot be converted from <dialect> to <dialect>`, and likewise for `x:thinking_budget_tokens`).
(**implemented** for `openai`, `openai/responses`, `anthropic` and `gemini`)

| Native member | Decoded as |
|---|---|
| `openai` `reasoning_effort` | `x:reasoning_effort`, value unchanged |
| `openai/responses` `reasoning.effort` | `x:reasoning_effort`; the rest of `reasoning` (`summary`, `generate_summary`) stays `openai:reasoning` |
| `anthropic` `output_config.effort` | `x:reasoning_effort`; the rest of `output_config` stays `anthropic:output_config` |
| `anthropic` `thinking.type` `disabled` | `x:reasoning_effort` `none` |
| `anthropic` `thinking.budget_tokens` | `x:thinking_budget_tokens`; the `enabled` type is consumed with it |
| `gemini` `generationConfig.thinkingConfig.thinkingLevel` | `x:reasoning_effort`, `MINIMAL`, `LOW`, `MEDIUM` and `HIGH` in lower case |
| `gemini` `thinkingConfig.thinkingBudget` | `0` is `x:reasoning_effort` `none`, a positive value is `x:thinking_budget_tokens`, `-1` (automatic) stays residue |

| `x:` value | `openai`, `openai/responses` | `anthropic` | `gemini` |
|---|---|---|---|
| `x:reasoning_effort` `none` | `none` | per thinking form (below) | `MINIMAL`, or `thinkingBudget` 0 on a model that takes a budget |
| `minimal` | `minimal` | `output_config.effort` `low` | `MINIMAL` |
| `low`, `medium`, `high` | same | same | `LOW`, `MEDIUM`, `HIGH` |
| `xhigh` | `xhigh` | `xhigh` | `HIGH` |
| `max` | `xhigh` | `max` | `HIGH` |
| `x:thinking_budget_tokens` | derives an effort from the ladder when no effort is present | `thinking` `enabled` with `budget_tokens`, clamped | `thinkingBudget`, clamped into the model range |

- **Class C.** Each client clamps the effort to the nearest value it offers: `openai` and `openai/responses` offer `none` to
  `xhigh`, `anthropic` offers `low` to `max` and `gemini` offers `MINIMAL` to `HIGH`. `anthropic` budgets are clamped to at
  least the model minimum (1024 by default) and below `max_tokens`; `gemini` budgets are clamped into the model range.
- **Decoding.** The leaves are lifted out of their native containers without holding, like the response format members, so
  one control section may hold the same residue key several times. `anthropic` `thinking` and `output_config`,
  `openai/responses` `reasoning` and `gemini` `thinkingConfig` are the containers; the `gemini` residue is carried as
  `gemini:thinkingConfig` (or `gemini:thinking_config`, in the source spelling), a container of its own inside
  `generationConfig`. An `anthropic` `thinking.type` of `enabled` is held as a flag until the container ends and is kept
  as residue only when no `budget_tokens` came; any other `type` (`adaptive`) is residue at once. When `thinking.type` is
  `disabled` and `output_config.effort` are both present, both are emitted and the client keeps the last to arrive.
- **Encoding.** A client records the effort and the budget while the control sections stream, the last one to arrive
  winning, and writes the native form once at the end of the request, where the output token limit is known. `openai`
  writes `reasoning_effort` before `max_completion_tokens`; `openai/responses` merges `effort` into the `reasoning`
  container (before the residue fragments); `anthropic` writes `output_config`, `tool_choice`, `thinking` and
  `max_tokens`, with `effort` first in `output_config` and `type` and `budget_tokens` first in `thinking`; `gemini` writes
  `thinkingConfig` inside `generationConfig`, after the other fragments and before `maxOutputTokens`, with
  `thinkingLevel` or `thinkingBudget` first. A same-dialect hop restores every member in content, with the effort and the
  thinking members moving to the end of the request.
- **Level versus budget.** `gemini` never writes both. A model that takes a level writes `thinkingLevel` for an effort,
  whatever the budget; a model that takes a budget writes `thinkingBudget`, the explicit budget when one arrived and the
  ladder value of the effort otherwise. A budget on a model that takes a level, with no effort, is written as
  `thinkingBudget`. A budget of 0 is `none`.
- **Anthropic thinking forms.** `adaptive` accepts adaptive thinking only, `adaptive-disabled` adds an explicit
  `disabled`, `adaptive-enabled` adds the deprecated `enabled` form and `enabled` accepts `enabled` with a budget only.
  `none` becomes `thinking` `disabled` on `adaptive-disabled` and `adaptive-enabled`, the lowest effort (`low`) on
  `adaptive`, and plain omission on `enabled`, because some models turn adaptive thinking on when `thinking` is omitted and
  only an explicit `disabled` turns it off. `x:thinking_budget_tokens` is honoured only on `adaptive-enabled` and `enabled`
  and is dropped (class A) elsewhere. An `enabled`-only model takes no `output_config.effort`, so its effort becomes a
  budget from the ladder. Neither effort nor budget writes `thinking` `adaptive`; the form only decides `none` and the
  budget. A model with no entry takes `adaptive-disabled`, the form of the current Claude models.
- **Ladder.** A target that takes a budget instead of an effort turns an effort into tokens through a ladder, clamping to
  the nearest entry present. `gemini` and the inverse used by `openai` and `openai/responses` (a budget with no effort
  becomes the highest effort whose entry fits, `minimal` below the first and `none` for 0) default to the documented
  ladder `low` 1024, `medium` 8192, `high` 24576. `anthropic` derives its default ladder from the model minimum and the
  request's own limit: `minimal` and `low` take the minimum, `medium` the midpoint and `high` and above the largest budget
  that fits below `max_tokens`. A ladder with no entry that fits drops the effort (class A); an effort is never guessed.

#### Model capability options

The options of a client carry a `models` map of capabilities, keyed by model identifier or `*` glob pattern and matched
against the model of each request. The schema of each dialect admits only the properties that dialect uses. A setting
that an entry does not give falls back to the next matching entry: configured entries first (exact names before globs, in
declaration order), then the built-in defaults of the dialect, which are data
(`config/binding-llm.conf/src/main/resources/.../models/<dialect>.json`) and are overridden entry by entry.

| Property | Dialects | Meaning |
|---|---|---|
| `sampling` | all | `false` for a model that rejects sampling: it receives no `x:temperature`, `x:top_p` or `x:top_k` (class A). A model with no entry receives them unchanged. |
| `thinking` | `anthropic`: `adaptive`, `adaptive-disabled`, `adaptive-enabled`, `enabled`; `gemini`: `level`, `budget` | The thinking form the model takes. Defaults: `adaptive-disabled`, `level`. |
| `budget` | `anthropic`, `gemini` | `minimum` and `maximum` thinking budget tokens the model accepts. A `minimum` of 0 admits a budget of 0 for `none`. The `anthropic` minimum defaults to 1024, and its maximum is always below `max_tokens`. |
| `ladder` | all | Budget tokens by `minimal`, `low`, `medium`, `high`, `xhigh` and `max`, overriding the default ladder. |

The built-in defaults mark `claude-opus-4-7*` as `adaptive` and rejecting sampling, `claude-opus-4-6*` and
`claude-sonnet-4-6*` as `adaptive-enabled`, `claude-3-*`, `claude-sonnet-4-5*`, `claude-haiku-4-5*` and `claude-opus-4-1*`
as `enabled`, `gemini-2.5-pro*` as a budget of 128 to 32768, `gemini-2.5-flash*` as a budget of 0 to 24576, and the `o1*`,
`o3*` and `o4-mini*` models as rejecting sampling. They are best-effort data: override an entry whose provider documentation
has moved. An `anthropic` model with no entry takes `adaptive-disabled`, so its `thinking.budget_tokens` is dropped until an
entry gives it a form that accepts `enabled`.

### Native containers

A dialect whose promoted members live inside a native object (`gemini` `generationConfig` and `thinkingConfig`, `anthropic`
`output_config` and `thinking`, `openai/responses` `text` and `reasoning`) carries the container in two forms. Containers are never `x:`-prefixed.

- **Decoding.** Each promoted leaf is lifted out of the container and emitted as `x:<member>` at depth 1 of the
  control section the container belongs to, without holding anything. The residue stays under
  `<dialect>:<container>`. A leaf that appears mid-container closes the residue object, emits the `x:` member and
  re-opens `<dialect>:<container>` for the next residue member, so one section may hold the same
  `<dialect>:<container>` key several times (deliberate). A container whose every member is lifted emits no
  `<dialect>:<container>`; an empty container is kept as `{}`; a `null` container is dropped by `gemini` and kept as
  residue by `anthropic` and `openai/responses`. Both camel and snake
  spellings of the container and its leaves are decoded, and the residue keeps the container's source spelling.
- **Encoding.** A client translates each supported `x:` member into a one-member native fragment such as
  `"generationConfig":{"temperature":0.7}`, and passes the same-dialect residue fragments as they come. The
  fragments of a container are not written where they arrive: `LlmMemberCollector` streams every other member
  through unchanged, drops the separators it cuts around a container, and holds the object contents of each
  container fragment in a pool slot. At the end of the request the client writes each container once, in arrival
  order of its fragments, then appends the members it owns at that point (the cross-dialect `maxOutputTokens`),
  so the output holds exactly one `generationConfig` key, after the other request members.
- A same-dialect hop restores the container exactly in content, and in member order inside it; the container moves
  after the other request members.
- The held contents count against the client's encode room, so the credit granted to the application shrinks as the
  hold grows. A container larger than one pool slot is flow-controlled to a stop, and a request that cannot get a
  hold slot is rejected naming the container (`generationConfig cannot be converted from <dialect> to gemini`).
- `maxOutputTokens` is not promoted. It stays in the residue (`gemini:generationConfig`) and is also carried on the
  flush, which is how another dialect's client receives it.

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
