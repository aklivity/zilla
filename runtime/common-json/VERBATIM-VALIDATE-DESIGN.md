# Verbatim validate path — design

Status: design converged; Phase 1 implementation pending (build not runnable in the
authoring environment). Tracking branch: `claude/blissful-newton-pcpyx9`.

## Problem

A `ValidatorHandler` must forward the payload to `next` **unchanged** (per its SPI
contract). The JSON validator instead drives `parser -> schema.validator() ->
JsonEx.createSink(generator)` and forwards the generator's output, which re-serializes
the parsed tokens canonically — dropping insignificant whitespace
(`{"id": 1}` -> `{"id":1}`). This silently broke the `asyncapi.*` / `openapi.*` example
tests once the Docker Hub throttling that had been masking them was removed.

The fix must let the validator parse structure (to apply schema rules) while reproducing
the original bytes verbatim — without resorting to the existing opaque `SEGMENT` mechanism,
which suppresses the structured events the validator needs.

## Two primitives (do not conflate)

| | `SEGMENT` (existing) | `VERBATIM` (new) |
|---|---|---|
| shape | hierarchical — whole subtree | linear — coalesced run of events |
| relation to structure | substitutive (replaces inner events) | additive (rides alongside events) |
| consumer sees | structure **or** bytes | structure **and** bytes |
| purpose | skip tokenizing (nobody inspects) | preserve bytes while inspecting (validate) |
| accessor | `getSegment()`, valid on `segmented()` | `getVerbatim()`, valid on `VERBATIM` |
| opt-in | `JsonController.segmentable()` | `JsonController.verbatim()` |

`VERBATIM` events are **not** `segmented()`. A `VERBATIM` event is best understood as a
**`STRUCTURED` event that also carries a coalesced byte run** — the two are one integrated
event stream, and structured events may be injected between `VERBATIM` events (for mutation).
The generator's only branch is "do I have verbatim bytes to copy here, or do I generate."

## Contract surface

- `JsonEvent.VERBATIM` — `segmented()` returns false; add `isVerbatim()`. Treated as a
  `STRUCTURED` flavor (see `getPointer()` below).
- `JsonSource.getVerbatim()` — covering source slice; valid on a `STRUCTURED` **or**
  `VERBATIM` event (they are integrated; a `VERBATIM` event is the coalesced-run case).
- `JsonSource.getPointer()` — lazy position "at" the current value in its parent (see Q-pointer
  below); valid on `STRUCTURED` or `VERBATIM` events; only consumed to seed the generator on a
  verbatim→inject transition (Phase 3).
- `JsonController.verbatim()` — opt-in: indicates the caller is **willing to receive**
  `VERBATIM` events (peer of `segmentable()`); when unset the source does no verbatim tracking.
- Mediating validator: **absorbs** the request upstream (still takes structured events from
  the parser to validate), **re-asserts** it downstream (emits `VERBATIM` to the generator).
- Default stays `STRUCTURED` (canonical) for generic consumers; only the model-json validate
  handler opts in.

Reused, not new: `JsonController.consumed(int)` (output backpressure drain, Q3),
`JsonSource.deferredBytes()` (a `VERBATIM` run delivered in output-sized pieces),
`reset()` (resets the verbatim cursor + frame stack per document).

Correction vs an earlier draft: there is **no** `JsonParserEx.Mode.VERBATIM`. `SEGMENTED`
*substitutes* opaque bytes for structured events; verbatim is **additive** — the parser keeps
delivering structured events and `getVerbatim()`/`getPointer()` are accessors over them, so
there is no parser delivery-mode switch.

Deferred to later phases: `JsonController.skip()` (Phase 2 prune — advance the cursor past a
dropped value **without** marking it emitted, so the new-first surviving sibling's leading
separator is trimmed); generator "seed context from `getPointer()` without emitting" + sink
copy-vs-generate dispatch (Phase 3 inject).

## Q1 — the verbatim cursor (no extra skip signal)

The parser holds one **verbatim cursor**. `getVerbatim()` returns `[cursor, currentEventEnd)`
and advances `cursor`. Per source-backed value (or per coalesced run) the sink does exactly
one of:

- **passthrough** — call `getVerbatim()`, copy the bytes (cursor advances).
- **mutate** — call `getVerbatim()`, ignore the bytes, write canonical (cursor still advances,
  so those source bytes are not later double-emitted).
- **injected event** (no source span) — do not call `getVerbatim()` (cursor does not move).

`getVerbatim()` *is* the skip; no separate signal is required. Invariant: every source byte is
accounted for **exactly once** (no gap, no overlap). Coalescing = calling `getVerbatim()`
lazily, once at a run boundary.

## Q2 — generator correctness across `VERBATIM … STRUCTURED×N … VERBATIM`

A verbatim copy bypasses the generator's state machine (it dumps bytes containing their own
`{ , : }`), while injected `STRUCTURED` events drive that machine. Four assumptions make the
seam always well-formed:

- **A1 boundary alignment** — mode switches occur only at value boundaries (between complete
  members/elements), never mid-token/mid-value.
- **A2 structural-effect handoff** — each `VERBATIM` event carries its net structural effect so
  the generator's state is correct for the following structured events.
- **A3 separator ownership** — every value contributes its own leading separator; the **first**
  value in each container contributes none. A per-container "has-a-sibling-been-emitted-yet"
  first-flag is **shared** between the verbatim source and the generator (also fixes prune).
- **A4 member parity** — verbatim chunk and injected run each emit complete members (key+value);
  never hand off mid-member.

### Structural-effect metadata — shape and example

Minimal sufficient metadata on a `VERBATIM` event:

```
depthDelta       : int               // net containers opened (+) / closed (-) by this chunk
openedKinds      : [OBJECT|ARRAY]     // kinds of still-open containers (innermost last); empty if depthDelta <= 0
separatorPending : boolean           // does the innermost open container already have a child,
                                     //   so the next sibling needs a leading comma?
```

"expects key vs value" needs no field: per A1/A4 a cut is always between complete
members/elements, so the next expected token is implied by the innermost kind.

Worked example — inject `"x": 9` between `b` and `c` of `{"a": 1, "b": 2, "c": 3}`:

- `VERBATIM` V1 = `{"a": 1, "b": 2` -> `{depthDelta:+1, openedKinds:[OBJECT], separatorPending:true}`
- generator copies V1, adopts metadata; injected `KEY_NAME(x)` sees `separatorPending:true` ->
  emits `,` then `"x":9` (canonical) -> `…"b": 2,"x":9`
- `VERBATIM` V2 = `, "c": 3}` carries c's own leading comma -> `…9, "c": 3}` (no double comma)
- result: `{"a": 1, "b": 2,"x":9, "c": 3}` — verbatim except the injected member

Contrast: V1 = `{` -> `separatorPending:false` (injected first member emits no comma);
V1 = `{"a":1,"b":{"x":1}}` -> `{depthDelta:0, openedKinds:[], separatorPending:true}`.

## Q3 — output backpressure without backing up the input

Separate three forward-only positions; conflating them is what appears to require a rewind:

1. **validation cursor** — advances token-by-token as the parser validates; never rewinds
   (preserves the existing invariant).
2. **verbatim-flush cursor** — how much of the validated span has been written out; lags (1).
3. **buffer residency** — when the input window may be released (held for the whole `feed`
   call, including every `SUSPENDED` cycle).

Key fact: **validation-consumed != buffer-released**. A verbatim flush is a `memcpy` over
resident bytes, decoupled from parse state, so re-emitting an earlier piece needs no parser
rewind. This is exactly the existing `JsonParserEx.consumed(int)` re-exposure paired with
`JsonSink.resume()`; a `VERBATIM` event may therefore be delivered in output-sized physical
pieces (`deferredBytes()` until the last), like `SEGMENT` fragmenting.

Preferred realization for pure validate: **do not route verbatim through the bounded generator
scratch buffer at all** — forward the resident source slice directly to `next`
(`next.accept(inputBuffer, flushCursor, len)`). Validation is identity, so output size ==
input size; if the input fit the incoming stream window, the output fits the outgoing one, and
stream-window flow control is handled by the binding as today. The bounded
`OUTPUT_CAPACITY`/`SUSPENDED` path then only exists for canonical generation (the small
injected/mutated bits), where `consumed()`/`resume()` already handle it.

Residual constraint: a verbatim run cannot extend past buffer residency — flush the pending
span at a feed-window boundary before the window is released. Runs are bounded by
`min(run, resident window)` and output capacity; always forward-only.

## Phasing

- **Phase 1 — validate fidelity (the CI fix).** `JsonEvent.VERBATIM` + `getVerbatim()` +
  `JsonController.verbatim()` + `Mode.VERBATIM`; validator emits coalesced `VERBATIM` events
  when its downstream opts in (structured fallback otherwise); generator copies on `VERBATIM`;
  validate handler forwards the resident source slice directly to `next`. No mutation, so no
  structural-effect interleaving exercised yet (single run per window).
- **Phase 2 — prune.** Per-container first-flag + leading-separator trim on the new-first kept
  value; only first-drop needs the trim (middle/last/all are pure omission).
- **Phase 3 — inject.** Generator renders injected members + join commas using A2/A3; this is
  where `VERBATIM … STRUCTURED×N … VERBATIM` interleaving is fully exercised.

## Tests (Phase 1, test-first)

- model-json `JsonValidatorTest.shouldForwardValidatedBytesVerbatimPreservingWhitespace`
  (already added) — byte-identical forwarding of `{"id": "123", "status": "OK"}`.
- common-json `JsonPipeline`-level: byte-identity; coalescing (a multi-field object yields one
  `next` span, not one per token); fragmented across windows reassembles exact; malformed ->
  `REJECTED` with no partial forward; lexeme fidelity (`1.50`, `1e5`, `"A"`).
- opt-out assertion: no `verbatim()` request -> canonical output (back-compat).
- `JsonPipelineBM`: validate path allocation approaches passthrough (no re-encode copy).

## `getPointer()` semantics (the structural-effect carrier)

The structural effect a verbatim run hands to the generator is expressed lazily via
`getPointer()` — the position "at" the current value **in its parent container**, reflecting
the document up to and including the current event but not advanced into the next slot. For a
close event it points at the value that just closed, at its parent depth (e.g. after `[{"a":1}`
it is `/0`, depth 1 in the array — not inside the closed object). The generator reads it only
to seed itself on a verbatim→inject transition (silently, reusing `SUSPENDED`-resume context
preservation, so it does not re-emit the brackets the verbatim copy already wrote); within an
injected run it then tracks its own state, and re-seeds at the next transition. Verbatim bytes
own their separators, so a verbatim run following an injection ignores the generator's
pending flag (no double comma).

## Open items

- Names: `getVerbatim()` / `getPointer()` / `JsonController.verbatim()` / `JsonEvent.VERBATIM`
  / `JsonController.skip()` — confirm.
- `getPointer()` return type, deferred to Phase 3 (not consumed before inject). A path-from-root
  is the right backbone — segment types (string key vs integer index) encode the container-kind
  stack for free — but it is short by one bit: RFC 6901 addresses *values*, not insertion points,
  so it cannot distinguish "at scalar `a`" from "inside `a`'s empty container" (it has `-` for the
  array append slot but no object equivalent), and from a coalesced `VERBATIM` event there is no
  last-inner-event kind to fall back on. So `getPointer()` = pointer backbone **plus a one-bit
  terminal marker** (`at-value` | `entered-empty` | `entered-after-child`), from which
  separator-pending follows; the alternative is a frame-stack position (kind + ordinal/emitted per
  level). Decide when building inject.
