# Verbatim validate path — design

Status: design converged; Phase 1 implementation in progress. The `common-json` module **does
build and test** in this environment (it depends only on Maven Central artifacts + agrona, with
no `flyweight-maven-plugin` SNAPSHOT dependency — unlike `model-json`), so the implementation is
verified here against the existing test suite. Tracking branch: `claude/blissful-newton-pcpyx9`.

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
| accessor | `getSegment()` + `consumed()` | `getVerbatim(limit)`, valid on `VERBATIM`/`STRUCTURED` |
| opt-in | `JsonController.segmentable()` | `JsonController.verbatim()` |

> **Correction (implementation finding).** An earlier draft proposed `getSegment(int limit)` as a
> consumed-free bounded pull symmetric with `getVerbatim`. That does **not** hold: the segment accessor
> serves a generator-owned leading separator on nested values (output ≠ source on the first fragment) and,
> more decisively, the **escaped** generator (`GENERATE_ESCAPED`, used by `binding-mcp-http` and projector
> segment tests) is genuinely 1:N — one source byte expands to up to six output bytes. An auto-advancing
> bounded pull would drop un-emitted source bytes there. So `consumed()` correctly **stays** on the segment
> path (it advances the cursor by what the generator actually emitted); only the **verbatim** path is a pure
> 1:1 bounded pull. `consumed()` is therefore scoped to "paths where output width ≠ source width" — escaped
> output, number lexemes, and the nested-segment separator — not removed from segments.

### Delivery axis vs verbatim fidelity (not a third delivery mode)

The genuine delivery fork is **structured vs segmented** — the parser `Mode`. `SEGMENTABLE` switches the
parser to `Mode.SEGMENTED` (it *substitutes* opaque runs for a value's structure). **Verbatim is not a
peer of segmented**: it lives on the *structured* side as a **fidelity modifier** — the parser keeps
delivering typed events; verbatim only adds the ability to reproduce the original bytes. So:

| sink intent | parser `Mode` | fidelity |
|---|---|---|
| `Delivery.STRUCTURED` | `STRUCTURED` | canonical re-render |
| `Delivery.STRUCTURED` + `verbatim` flag | `STRUCTURED` | byte-preserving |
| `Delivery.SEGMENTABLE` | `SEGMENTED` | opaque runs |

Accordingly `JsonSink.Delivery = {STRUCTURED, SEGMENTABLE}` and verbatim is not a third `Delivery`
value. The per-event signal is `JsonEvent.VERBATIM` (`isVerbatim()`), emitted by a mediator (the
validator), so the sink renders canonically for normal structured events and copies bytes for `VERBATIM`
ones. Because verbatim is per-event, a mediator can interleave injected canonical events with `VERBATIM`
ones (Phase 3). The terminal sink does **not** track completion on the verbatim path — the mediator owns
structure (and so completion); the sink is only the byte conduit.

### The terminal sink prefers bytes by default

The terminal generator sink **always opts into both** `segmentable()` and `verbatim()` (unless explicitly
made canonical via `Delivery.STRUCTURED`). It exists to emit bytes, so byte-preserving is both faster (no
re-quote/escape) and faithful, and the requests are self-resolving by negotiation:

| pipeline | outcome |
|---|---|
| bare `parser → sink` | parser honors `segmentable()` → whole value as one opaque `SEGMENT` → byte passthrough |
| `parser → validator → sink` | validator declines `segmentable()` (needs structure), absorbs `verbatim()` → emits `VERBATIM` → byte passthrough |
| `parser → projector/converter → sink` | transform honors neither (it drops/substitutes) → emits structured events → canonical |

So bytes wherever the pipeline is pass-through or validate; canonical only where a transform actually
changes content. Canonical is the explicit **opt-out** (`Delivery.STRUCTURED`).

### The mediating-transform rule (generalized)

A structure-inspecting transform behind a byte-preferring sink must **intercept** the downstream's
byte-delivery opt-ins (`segmentable()` *and* `verbatim()`) — supplying its own controller rather than
passing the downstream's through — so its own upstream keeps delivering structured events for it to
inspect, and then **re-assert** verbatim toward its downstream so the terminal sink still reproduces the
original bytes. This is the same pattern in the validator (`JsonSchemaImpl.Validator`) and the model-json
`JsonExtractor`. A transform that *restructures* (e.g. the projector) absorbs the opt-ins but does **not**
re-assert — it emits structured events and the sink renders them canonically, which is correct because its
output is not the original bytes. (In the unified `ModelHandler`, this mediation belongs in shared
infrastructure rather than re-implemented per transform.)

### Cross-window value fragmentation

A value spanning input windows can't be held until validated *and* pulled verbatim before its window is
replaced. The resolution: the validator keeps **declining** fragments (`consumed(0)`, accumulating the
decoded value for `eval`), while the pump **always drains the sink via `flush()` when the loop exits
because the window was consumed** — so the verbatim run pulls the bytes consumed so far and its cursor
tracks the parse frontier across windows. Validation still happens on the whole value at completion; output
is byte-copied with no re-render (emit-then-reject for the fragmented case, as with structural events).

### Verification

Built and tested locally end-to-end: `common-json` (4811), `model-json` (25 — validator forwards verbatim,
converters forward verbatim, extractor mediates), `binding-mcp-http` (11 unit + 28 k3po IT). `model-avro`/
`model-protobuf` use `createGenerator()` directly (not the verbatim sink) and are unaffected;
`binding-asyncapi`/`binding-openapi` parse schemas via `common-json` but don't use `createSink`, and their
example regressions are fixed through the model-json validator.

Consequence: a **non-mediating** transform that inspects or accumulates *decoded* values (not just
forwards them) must **decline** `segmentable()` — as the validator does — or it will be handed opaque
`SEGMENT` runs instead of structured events. Forwarding `segmentable()` blindly downstream opts the whole
document into segmentation. (Several unit tests that probe/accumulate decoded values pass
`Delivery.STRUCTURED` for exactly this reason.)

### Bounded pull vs `consumed()` — the ratio decides

Only the pure 1:1 path is a consumed-free bounded pull; every path where output width can differ from
source width keeps a post-hoc `consumed()` report.

| path | source→output | accessor | flow control |
|---|---|---|---|
| `VERBATIM` (raw run bytes) | 1:1 always | `getVerbatim(int limit)` | bounded pull; **no `consumed()`** |
| `SEGMENT` (raw subtree bytes) | 1:1 at top level, but 1:(N) once a generator-owned separator or an escaped generator is involved | `getSegment()` | **`consumed()`** kept |
| structured scalar generate/escape | 1:N (quote, escape, lexeme) | char view + `writeScalar` | **`consumed(N)`** kept |

A `VERBATIM` run is pure source bytes spliced raw with no generator-owned separator (the run carries its
own `{ , : }`), so the pull is pre-bounded to the generator's free output space, the copy always fits whole,
and the source advances its cursor by exactly what it returned — no `consumed()`. The `SEGMENT` path cannot
join it: a nested kept value carries a generator-emitted leading separator, and the escaped generator
(`GENERATE_ESCAPED`) expands one source byte to several output bytes, so the cursor must advance by what the
generator *emitted*, which only `consumed()` knows. So `consumed()` is scoped to "output width ≠ source
width" — escaped output, number/string generation, and the nested-segment separator — and the verbatim path
is the one place it is not needed.

`VERBATIM` events are **not** `segmented()`. A `VERBATIM` event is best understood as a
**`STRUCTURED` event that also carries a coalesced byte run** — the two are one integrated
event stream, and structured events may be injected between `VERBATIM` events (for mutation).
The generator's only branch is "do I have verbatim bytes to copy here, or do I generate."

## Contract surface

- `JsonEvent.VERBATIM` — `segmented()` returns false; add `isVerbatim()`. Treated as a
  `STRUCTURED` flavor (see `getPosition()` below).
- `JsonSource.getVerbatim(int limit)` — **bounded pull**: returns at most `limit` source bytes of
  the run (the generator passes its free output space, so it never over-pulls); valid on a
  `STRUCTURED` **or** `VERBATIM` event. The return amount is the source-consumption signal, so
  `controller.consumed()` is **not** invoked on this path.
- `JsonSource.getSegment()` — the existing `SEGMENT` accessor, **unchanged**: it re-exposes the
  unconsumed remainder of the segment slice and `controller.consumed()` advances the cursor by what the
  generator emitted. This stays consumed-based by necessity — a nested segment carries a generator-owned
  leading separator and the escaped generator expands bytes 1:N, so the cursor cannot be pre-advanced by a
  source-byte pull. (An earlier `getSegment(int limit)` symmetry proposal was abandoned for this reason.)
- `JsonSource.getPosition()` — lazy `JsonPosition` (container-anchored; see section below); valid
  on `STRUCTURED` or `VERBATIM` events; only consumed to seed the generator on a verbatim→inject
  transition (Phase 3).
- `JsonController.verbatim()` — opt-in: indicates the caller is **willing to receive**
  `VERBATIM` events (peer of `segmentable()`); when unset the source does no verbatim tracking.
- Mediating validator: **absorbs** the request upstream (still takes structured events from
  the parser to validate), **re-asserts** it downstream (emits `VERBATIM` to the generator).
- Default stays `STRUCTURED` (canonical) for generic consumers; only the model-json validate
  handler opts in.

- `JsonSink.flush(JsonController, JsonSource)` — **end-of-feed drain hook**. The pump calls it when the
  input window is consumed before a terminal value (the `event == null` break, just before `STARVED`).
  A verbatim sink pulls `getVerbatim()` here for bytes the parser consumed during end-of-window
  lookahead (e.g. a separator after the last value) that no event pulled, writing them out **before the
  window is replaced**. This is how cross-window fidelity is achieved **without any retention buffer**:
  un-pulled bytes simply stay in the source's own input buffer until pulled, and `flush()` guarantees the
  trailing lookahead bytes are pulled before that buffer goes away. Returns `SUSPENDED` if the bounded
  output fills mid-drain (resume continues it, since `getVerbatim` is frontier-relative, not event-relative)
  or `ADVANCED` otherwise. Default no-op.

There is **no** `verbatimPending()` peek: `getVerbatim(limit)` is self-signalling — a return shorter than
`limit` means the run reached the parse frontier (drained); a full-`limit` return means the output bound
capped it (suspend, resume). This is the same `available − consumed` signal `writeChunk`/`writeScalar`
already use, so no extra accessor is introduced.

Reused, not new: `JsonController.consumed(int)` — but **re-scoped**: no longer the raw-copy
backpressure signal (the bounded `getSegment`/`getVerbatim` pulls replace it there); it now fires
*only* on the structured generate/escape path where one source unit expands to several output
bytes. `JsonSource.deferredBytes()` (a `VERBATIM` run delivered in output-sized pieces),
`reset()` (resets the verbatim cursor + frame stack per document).

Correction vs an earlier draft: there is **no** `JsonParserEx.Mode.VERBATIM`. `SEGMENTED`
*substitutes* opaque bytes for structured events; verbatim is **additive** — the parser keeps
delivering structured events and `getVerbatim()`/`getPosition()` are accessors over them, so
there is no parser delivery-mode switch.

Deferred to later phases: `JsonController.skip()` (Phase 2 prune — advance the cursor past a
dropped value **without** marking it emitted, so the new-first surviving sibling's leading
separator is trimmed); generator "seed context from `getPosition()` without emitting" + sink
copy-vs-generate dispatch (Phase 3 inject).

## Q1 — the verbatim cursor (no extra skip signal)

The parser holds one **verbatim cursor**. `getVerbatim(limit)` returns up to `limit` bytes from
`[cursor, producedFrontier)` and advances `cursor` by the amount returned. Per source-backed value
(or per coalesced run) the sink does exactly one of:

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
- **A2 structural-effect handoff** — on a verbatim→inject transition the generator seeds itself
  from `getPosition()` (a `JsonPosition`; see below) so its state is correct for the injected
  structured events. (Lazy/pull, not carried eagerly on every event.)
- **A3 separator ownership** — every value contributes its own leading separator; the **first**
  value in each container contributes none. A per-container "has-a-sibling-been-emitted-yet"
  first-flag is **shared** between the verbatim source and the generator (also fixes prune).
- **A4 boundary points** — hand off only at well-defined points: a value boundary (between
  complete members/elements) **or** the after-key value-expected boundary (verbatim through a
  `KEY_NAME`, value replaced by injection — terminal `KEY_NAME`, see below). Never mid-token.
- **A5 balance verification** — the generator asserts structural balance on injected `END_*`:
  the close kind matches the innermost open it tracks, it does not pop below the seed baseline
  (an injected run must not close the container it was seeded into — that's verbatim's job), and
  `END_DOCUMENT` leaves no injected container open. Scoped to injected runs; verbatim bytes are
  copied raw (well-formed from the validated original), so whole-document validity stays the
  validator's responsibility. Fail-fast (`IllegalStateException`) against a buggy transform.

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

## Q3 — bounded output across `feed()` calls (push back to the source)

Output is **always** drained through the bounded sink — generated bytes and copied verbatim
bytes alike. Validate is just the transform whose run is identity-sized; the pipeline must not
lean on that.

Crucially, output backpressure is a **cross-`feed()` concern, not a within-call loop.** Downstream
credit (`WINDOW`) arrives over later reactor turns, and the reactor is non-blocking, so a
partially-flushed run cannot spin inside one `feed()` waiting for credit — and holding the input
window resident to do so would wedge the buffer so no new bytes can arrive for incremental decode.

Two backpressure levels:

1. **within-call** — the generator's bounded scratch fills while downstream still has credit:
   `SUSPENDED` -> drain to `next` -> `resume()` -> continue.
2. **cross-call** — downstream `WINDOW` is exhausted: stop, push back to the source, **return from
   `feed()`**, resume on a later `WINDOW`/`feed()`.

The cross-call path pushes back via a **bounded pull**, not a post-hoc consumed report:
`getVerbatim(int limit)` — the generator passes its free output space, so the source returns at
most `limit` bytes of the run and the generator copies **all** of them (fits by construction).
This keeps things clean:

- **`controller.consumed(N)` is re-scoped, not widened** — with `getSegment` now also a bounded
  pull, *no* raw-copy path calls `consumed()` any more. It survives only on the structured
  generate/escape path (1:N), where the bound is hit mid-value and the consumed source-unit count
  is genuinely needed. There is no partial-consumption to report on the verbatim/segment paths
  because the pull is pre-bounded.
- `getVerbatim(limit)` is **source-byte-denominated and 1:1** (a raw copy), so the source advances
  its run cursor by *exactly what it returned* — no output→input translation, and **no transform
  maintains an output stream position or added/removed math**. The return amount *is* the
  consumption signal: handing out X bytes ⇒ X source bytes flushable ⇒ source discards
  `[runStart, runStart+X)`, retains `[X, validatedEnd)` for the next pull. (Source-byte
  consumption overall = `getVerbatim`-returned + `skip()`-ed; injected bytes are generated
  separately and never touch this.)
- **Retention splits cleanly:** un-pulled bytes stay in the source's input buffer
  `[pullCursor, validatedEnd)`; pulled-but-not-yet-drained bytes sit in the generator's bounded
  scratch (it only pulled `limit` = its free space). Both bounded; neither needs reconciliation.
  `limit` need not respect token boundaries — a raw copy splits anywhere.

Two backpressure levels still apply: **within-call** the generator pulls `getVerbatim(limit)` to
fill its scratch and drains to `next`; **cross-call**, when `WINDOW` is exhausted it stops pulling,
returns from `feed()`, and resumes on a later `WINDOW`/`feed()` — the source meanwhile holds the
un-pulled remainder.

Consequence — **bound the parse/coalesce-ahead.** The retained un-pulled region occupies the
source's buffer, so validation must not run arbitrarily far past `pullCursor`; the verbatim build
is bounded by a retention budget. ("Anticipate sink space" was right after all — as a retention
cap, not per-run pre-sizing.) This is Zilla's normal flow control (decode-slot retention,
`WINDOW`-gated emission) applied to the flush; the binding<->model contract still carries
"bytes still pending" across calls so the un-flushed output is re-driven, but via the bounded pull
rather than a widened `consumed()`.

## Phasing

**Status:** Phase 1 implemented and verified in `common-json` (4811 tests + dedicated verbatim/validator
tests green). Phases 2 and 3 are **deferred** — see the note at the end of this section.

- **Phase 1 — validate fidelity (the CI fix). DONE.** `JsonEvent.VERBATIM` + `getVerbatim(int)` +
  `JsonController.verbatim()`; the validator emits `VERBATIM` events when its downstream opts in
  (canonical fallback otherwise); the sink copies a `VERBATIM` run into the **bounded sink**, draining via
  `SUSPENDED`/`resume()` and a `flush()` end-of-feed hook (Q3). The terminal sink **prefers bytes by
  default** (opts into both `segmentable()` and `verbatim()`), so the negotiation yields byte-preserving
  output for pass-through/validate and canonical only where a transform changes content. No mutation, so no
  `getPosition()`/inject interleaving yet.
- **Phase 2 — prune.** `JsonController.skip()` (or `getVerbatim`-and-discard from the transform) for the
  new-first/occupancy bookkeeping (per-container first-flag + leading-separator trim); `getVerbatim`-and-
  discard already covers middle/last/all drops, so `skip()` is scoped to drops that change a later sibling's
  separator or a later injection's occupancy.
- **Phase 3 — inject.** Generator renders injected members + join commas using A2/A3; this is
  where `VERBATIM … STRUCTURED×N … VERBATIM` interleaving is fully exercised.

> **Deferral decision.** Phases 2 and 3 are deferred until a concrete mutating-transform consumer (field
> masking, JSON Patch, redaction) exists to define the semantics. Two reasons: (1) no consumer needs them
> yet — the validate path was the real fix; (2) prune has a genuine **semantic fork** that only a consumer
> can settle: dropping the *first* surviving sibling leaves a dangling separator on the new first sibling
> (`{"a": 1, "b": 2}` drop `a` → `{,"b": 2}` naively), and the trim could yield `{"b": 2}` (trim comma +
> following whitespace) or `{ "b": 2}` (comma only). Building either phase now would bake in that guess as
> speculative API. The design above stands as the record; revisit when the consumer lands. (Phase 1 already
> proves the verbatim primitive generalizes — the sink/generator path it exercises is the converter path,
> not a validate special case.)

## Tests (Phase 1, test-first)

- model-json `JsonValidatorTest.shouldForwardValidatedBytesVerbatimPreservingWhitespace`
  (already added) — byte-identical forwarding of `{"id": "123", "status": "OK"}`.
- common-json `JsonPipeline`-level: byte-identity; coalescing (a multi-field object that fits the
  sink drains as one run, not one write per token); **bounded-sink drain** (output buffer smaller
  than the run → run drained in pieces via `SUSPENDED`/`resume()`, reassembling byte-identical);
  fragmented input across windows reassembles exact; malformed -> `REJECTED` with no partial
  forward; lexeme fidelity (`1.50`, `1e5`, `"A"`).
- opt-out assertion: no `verbatim()` request -> canonical output (back-compat).
- `JsonPipelineBM`: validate path allocation approaches passthrough (no re-encode copy).

## `getPosition()` semantics — container-anchored `JsonPosition`

`getPosition()` is anchored on the **current (innermost open) container** — the thing an
injected member would be added *to* — not on the last value. This follows JSON Patch
(RFC 6902) `add`, whose destination path is *container + slot*, and it dissolves the `/a`
ambiguity: "sibling after `a` at root" anchors at container `` (depth 1), "first child inside
`a`" anchors at container `/a` (depth 2) — distinct, no extra bit needed for depth. Close
events fall out: after `END_OBJECT` of `a`'s value the current container is again root.
(RFC 6902, like Merge Patch RFC 7386, re-serializes and so never places a comma itself; the
separator stays our byte-splice burden — encoded here in the step kind.)

It returns a **`JsonPosition`**: a single list of **`JsonStep`**, root → insertion point, one
step per descended level, each carrying container kind **and** occupancy:

- `START_OBJECT` / `START_ARRAY` — entered, **empty** (no child yet).
- `CONTINUE_OBJECT` / `CONTINUE_ARRAY` — in a container that **already has a child** (occupied →
  the next sibling needs a leading separator).
- `KEY_NAME` (**terminal only**) — verbatim emitted a key, its **value is expected** (an
  after-key cut, replacing the value). The generator writes `:value` — no comma, no key; it owns
  the colon (the `: ` spacing canonicalizes; the key stays verbatim). This is the one place a
  `KEY_NAME` step is needed — for ancestor path levels the key name is never carried.

Invariant: every **non-terminal** step is necessarily `CONTINUE_*` (descending into a child
makes each ancestor non-empty), and only the **terminal** step is consulted by the generator for
a single injection — so the list's earlier steps are diagnostic/headroom, not load-bearing.

Occupancy lives in the step kind, so there is no trailing boolean (which would be meaningless for
scalars). No member key **names** are carried (the originals were emitted verbatim); the terminal
step tells the generator what to write next — `*_OBJECT` ⇒ a member (key+value), `*_ARRAY` ⇒ an
element, terminal `KEY_NAME` ⇒ just the value of a pending key. `JsonStep` has a natural
equivalence to `JsonEvent` (`START_*`/`KEY_NAME` parallel the events; `CONTINUE_*` are the
occupancy-bearing additions — a *state*, where the event is a *transition*).

The generator **seeds** itself from the `JsonPosition` (push a `{kind, occupied}` frame per step)
without emitting — reusing the `SUSPENDED`-resume context preservation, so the brackets the
verbatim copy already wrote are not re-emitted — then injects the member normally. Examples:

- `[CONTINUE_OBJECT]` → inject emits `,"x":9` (occupied root object).
- `[CONTINUE_OBJECT, START_OBJECT]` → inject emits `"x":9` (descended into an empty object, depth 2).
- innermost `*_ARRAY` → inject emits an element (no key); `START`/`CONTINUE` decides the comma.

Computed lazily, only on a verbatim→inject transition (Phase 3); bounded by nesting depth. Within
an injected run the generator tracks its own state and re-reads `getPosition()` at the next
transition. Verbatim bytes own their separators, so a verbatim run following an injection ignores
the generator's pending flag (no double comma).

## Open items

- Names: `getVerbatim()` / `getPosition()` / `JsonPosition` / `JsonStep` /
  `JsonController.verbatim()` / `JsonController.skip()` / `JsonEvent.VERBATIM` — confirm.
  (Accessor renamed `getPointer()` -> `getPosition()` to match the `JsonPosition` return type.)
- `JsonPosition` is only consumed at inject (Phase 3); Phases 1–2 never call `getPosition()`, so
  its exact form can be finalized when building inject. The generator-side `seed(JsonPosition)`
  (push frames, no emit) is the one new capability inject needs; it extends the existing
  context-preservation and does not touch Phase 1.
