# Verbatim validate path — design

Status: design converged; Phases 1, 2 (prune), and 3 (inject) implemented and verified in
`common-json`. The module **does build and test** in this environment (it depends only on Maven
Central artifacts + agrona, with no `flyweight-maven-plugin` SNAPSHOT dependency — unlike
`model-json`), so the implementation is verified here against the existing test suite. Tracking
branch: `claude/blissful-newton-pcpyx9`.

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
  `STRUCTURED` flavor (its structural steps are read via `getSteps()`; see step-tracking below).
- `JsonSource.getVerbatim(int limit)` — **bounded pull**: returns at most `limit` source bytes of
  the run (the generator passes its free output space, so it never over-pulls); valid on a
  `STRUCTURED` **or** `VERBATIM` event. The return amount is the source-consumption signal, so
  `controller.consumed()` is **not** invoked on this path.
- `JsonSource.getSegment()` — the existing `SEGMENT` accessor, **unchanged**: it re-exposes the
  unconsumed remainder of the segment slice and `controller.consumed()` advances the cursor by what the
  generator emitted. This stays consumed-based by necessity — a nested segment carries a generator-owned
  leading separator and the escaped generator expands bytes 1:N, so the cursor cannot be pre-advanced by a
  source-byte pull. (An earlier `getSegment(int limit)` symmetry proposal was abandoned for this reason.)
- `JsonSource.getSteps()` — the structural `JsonSteps` the current verbatim run represents (even when
  forwarded relabeled as `VERBATIM`): an indexed batch of `count()` steps, each a `step(i)` (`JsonEvent`
  kind) with its `separated(i)` source occupancy (whether a separator preceded that member/element in the
  source). A terminal sink reads it once on the run's first chunk and passes it to
  `generator.writeVerbatim(bytes, index, length, steps)` so the generator advances structural state across
  the whole run and decides separator synthesis (step-tracking, see below). The returned instance is
  non-owning and reused across calls (on-stack only). This replaced the earlier per-event `event()` +
  `separated()` pair (themselves replacing `getPosition()`/`JsonPosition`); the batch shape is what lets a
  single run carry the multiple structural steps true byte-coalescing needs.

**Precondition contract** (the accessors are kind-gated like the other `get*` accessors):

- `getVerbatim(int)` and `getSteps()` — valid only when the current event is **not segmented**
  (`!lastEvent.isSegmented()`): a verbatim run or a structured event the consumer opted to read verbatim.
  Never on a `SEGMENT` event (read that via `getSegment()`).
- `getSegment()` — valid only when the current event **is** segmented.
- `skipValue()` — valid only on a `KEY_NAME` (an object member). Independent of verbatim vs structured
  mode: the source folds the leading-separator trim either way.

Because the steps are **relative/incremental** (concatenated across run boundaries), they cannot be merged
naively: the first run might carry `START_OBJECT, START_ARRAY, CONTINUE_ARRAY` and the next `END_ARRAY,
START_OBJECT`; delivered as one batch that would collapse to `START_OBJECT, START_OBJECT` (the canceling
`END_ARRAY`/re-open lost). So `getSteps()` is scoped to exactly the run `getVerbatim` returns, and a sink
that coalesces runs must concatenate their step batches, not re-derive from a single absolute position.
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
delivering structured events and `getVerbatim()`/`getSteps()` are accessors over them, so
there is no parser delivery-mode switch.

Implemented in later phases (the `JsonController.skip()` sketch was dropped in favor of a source-side
primitive): `JsonSource.skipValue()` (Phase 2 prune — advance the parser and verbatim cursor past a
dropped member, folding in the new-first survivor's leading-separator trim); `JsonSource.getSteps()`
driving `generator.writeVerbatim(bytes, index, length, steps)` for continuous generator step tracking
(Phase 3 inject — replaced the earlier `getPosition()`/`seed()` one-shot re-sync).

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
- **A2 structural-effect handoff** — the generator's state is kept correct for injected structured events by
  **applying each verbatim run's steps** (`getSteps()`) to its state machine as the run is copied — so at any
  verbatim→inject transition the state is already current, with no separate seed. (Originally sketched as a
  one-shot `getPosition()` re-sync; realized as continuous step tracking — see below.)
- **A3 separator ownership** — every value contributes its own leading separator; the **first**
  value in each container contributes none. The "has-a-sibling-been-emitted" occupancy is the step's
  `separated(i)` for the inject side (and the tokenizer's `memberSeparated` first-flag for the prune side).
- **A4 boundary points** — hand off only at well-defined points: a value boundary (between
  complete members/elements). Never mid-token.
- **A5 balance** — verbatim bytes are copied raw (well-formed from the validated original), so whole-document
  validity stays the validator's responsibility; the generator tracks depth across verbatim steps so injected
  structured events compose against a correct depth. (The earlier seed-baseline `writeEnd` assertion went with
  `seed()`; an injected run still cannot under/over-close because it threads the live tracked depth.)

### Structural effect of a step — `getSteps()`

What an earlier draft sketched as eager per-event metadata (`depthDelta` / `openedKinds` / `separatorPending`)
is realized as the run's structural steps, read lazily off the source as a `JsonSteps` batch: `step(i)` gives
each kind (so the generator updates depth and member occupancy exactly as the structured write would) and
`separated(i)` gives the source occupancy that decides the one synthesized separator. "expects key vs value"
needs no field — per A1/A4 a cut is between complete members/elements, so the innermost container kind implies
it.

Worked example — inject `"x": 9` between `b` and `c` of `{"a": 1, "b": 2, "c": 3}`:

- verbatim runs `{`, `"a"`, `: 1`, `, "b"`, `: 2` each apply their step; after them the generator tracks
  `depth=1`, root object `hasMembers=true`.
- injected `KEY_NAME(x)` via the normal `writeKey` path sees `hasMembers=true` -> emits `,` then `"x":9`
  (canonical) -> `…"b": 2,"x":9`.
- verbatim run `, "c"` has step `KEY_NAME`, `separated=true` -> no synthesized separator (its comma is in the
  bytes) -> `…9, "c"` (no double comma).
- result: `{"a": 1, "b": 2,"x":9, "c": 3}` — verbatim except the injected member.

Contrast (inject before the first member `a`): the displaced `"a"` run has step `KEY_NAME`, `separated=false`,
and the output container is already occupied by the injected `x` -> the generator synthesizes the `,` the
bytes lack -> `{"x":9,"a": 1}`.

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

**Status:** Phases 1, 2, and 3 implemented and verified in `common-json` (4820 tests green, JaCoCo
class + 90% instruction gate met, checkstyle clean; model-json 25 + EventIT and mcp-http 11 unit
unaffected). The prune semantic fork flagged below was settled when building Phase 2 (see the note),
and the symmetric inject-before-first-member separator case is handled too.

**Generator step-tracking (converged realization).** The earlier `getPosition()`/`JsonPosition`/`seed()`
one-shot re-sync and the standalone `writeVerbatimSeparator()` were replaced by **continuous step
tracking**: a verbatim run carries the structural `JsonSteps` it represents — `source.getSteps()`, an indexed
batch where each `step(i)` is the `JsonEvent` kind and `separated(i)` is whether that member/element was
preceded by a separator in the source — and the generator's `writeVerbatim(bytes, index, length, steps)`
applies every step to its own state machine (depth, member occupancy, after-key) as the bytes splice through.
Because the generator's state stays coherent across verbatim runs, an injected value just uses the normal
`writeKey`/`write` path (no re-sync), and the displaced-first-member separator falls out of the same
machinery: the generator synthesizes a leading separator when a member-start step is **not** `separated` in
the source yet the container already holds a member in the output. Source occupancy — not the run bytes — is
the signal, so it is robust to a separator being split into a prior run across input frames. `JsonPosition`,
`JsonStep`, `seed()`, the per-event `event()`/`separated()` accessors, and `writeVerbatimSeparator()` were
removed. (Under the current per-event forwarding a run's batch holds one step; the `JsonSteps` shape is the
batch primitive true multi-step byte-coalescing — one `getVerbatim` copy spanning a run — needs, a deferred
follow-up, see the note at the end of Phasing.)

- **Phase 1 — validate fidelity (the CI fix). DONE.** `JsonEvent.VERBATIM` + `getVerbatim(int)` +
  `JsonController.verbatim()`; the validator emits `VERBATIM` events when its downstream opts in
  (canonical fallback otherwise); the sink copies a `VERBATIM` run into the **bounded sink**, draining via
  `SUSPENDED`/`resume()` and a `flush()` end-of-feed hook (Q3). The terminal sink **prefers bytes by
  default** (opts into both `segmentable()` and `verbatim()`), so the negotiation yields byte-preserving
  output for pass-through/validate and canonical only where a transform changes content. No mutation, so no
  inject interleaving yet.
- **Phase 2 — prune. DONE.** Implemented as `JsonSource.skipValue()` (not the earlier `JsonController.skip()`
  sketch): called on a matched object `KEY_NAME`, the source advances past the whole member (key, separator,
  value — scalar or container-to-close) and the verbatim cursor past its bytes. Occupancy is owned by the
  source: the tokenizer tracks `memberSeparated` (was this member reached after a comma) per the shared
  first-flag, and skipValue defers a one-shot leading-separator trim (whitespace + a single comma, bounded to
  the parse frontier) onto the next verbatim pull — so an unoccupied container's dropped first member leaves
  the new-first survivor well-formed. The semantic fork below was settled here: trim whitespace **and** the
  single comma, so an empty container renders `{}` and a first-of-many drop keeps the survivor's spacing.
  Covered by `JsonSkipTest` (drop middle / last / first / object-value / only-field).
- **Phase 3 — inject. DONE.** Realized via continuous generator step tracking (see the status note above):
  the terminal sink reads `source.getSteps()` for each verbatim run and hands the generator
  `writeVerbatim(bytes, index, length, steps)`, which applies each step's structural effect to its state
  machine. An injected member is then fed through the ordinary `writeKey`/`write` path — the generator's
  occupancy is already current, so the injected member's leading separator is correct with no re-sync — and a
  displaced former-first member (inject before a container's first) gets a synthesized leading separator from
  the same `needsSeparator` rule (member-start step, not `separated` in source, container occupied in output).
  Covered by `JsonInjectTest` (the worked example — inject a number member between two verbatim runs — a
  string-member variant, and inject-before-first for the only-field and first-of-many cases). The
  `VERBATIM … STRUCTURED×N … VERBATIM` interleaving is exercised end-to-end, and the cross-frame validate
  case (`JsonValidatorVerbatimTest`) guards the source-occupancy (not byte-inspection) separator decision.

> **Resolution (Phases 2 & 3 built).** Phases 2 and 3 were implemented to validate that the verbatim
> abstraction generalizes beyond validate. The **semantic fork** prune raised — dropping the first surviving
> sibling leaves a dangling separator (`{"a": 1, "b": 2}` drop `a` → `{,"b": 2}` naively) — is settled by the
> trim rule "skip leading whitespace, then a single comma, bounded to the parse frontier": a first-of-many
> drop keeps the survivor's spacing (`{ "b" : 2 }`) and an only-field drop collapses to `{}`. The symmetric
> inject case is now handled too: injecting *before* a container's first member makes the previously-first
> member non-first, so its verbatim run (which owns no leading separator) needs one synthesized — the mirror
> of the prune trim, on the generator side. It falls out of the continuous step tracking: the displaced run's
> step is a member-start with `separated()` false (it was first in the source), and the generator's tracked
> occupancy already shows the container holding the injected member, so `needsSeparator` synthesizes the
> compact `,` the bytes lack. The synthesized comma matches the canonical injected member (`"x":9`); there is
> nothing to preserve since it has no source bytes. The one position not covered is injecting into a genuinely
> *empty* container (no member to displace), a different operation (append) whose step at the container's close
> reports the popped, outer context — left for a consumer that needs it.

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

## Step-tracking semantics — `getSteps()` (as built)

> An earlier draft anchored injection on `getPosition()`, an absolute container-anchored `JsonPosition`
> (a list of `JsonStep` carrying kind + occupancy) consulted once on a verbatim→inject transition to `seed()`
> the generator. That was replaced by **continuous, incremental** step tracking, first as a per-event
> `event()` + `separated()` pair and then converged onto the `getSteps()` batch; this section records the
> realization. The absolute-position write-up is preserved in git history.

A verbatim run carries the structural `JsonSteps` it represents, read off the source as the bytes splice
through. `JsonSteps` is an indexed batch — `count()`, `step(i)`, `separated(i)`:

- `step(i)` — the `JsonEvent` the i-th step represents (`START_OBJECT` / `START_ARRAY` / `END_*` / `KEY_NAME`
  / `VALUE_*`). The terminal sink passes the batch to `generator.writeVerbatim(bytes, index, length, steps)`,
  and the generator applies each step's structural effect to the same `depth` / `hasMembers` / `pending` state
  machine the structured `writeKey`/`writeStartObject`/`writeEnd`/`preValue` calls drive — but **state only**,
  emitting no bytes, because the run already carries its braces, commas, colons, and whitespace.
- `separated(i)` — whether the member/element at the i-th step was preceded by a separator in the **source**
  (a sibling came before it in its container). This is source occupancy, not a property of the run bytes — so
  it is robust to the original separator being split into a prior verbatim run across input frames.

The steps are **relative** (incremental from the run's start), so coalescing runs concatenates their batches;
it does not collapse to an absolute position (which would lose a canceling close/re-open across the seam).

Because the generator's state is maintained continuously across verbatim runs, there is no separate re-sync:
an injected value is fed through the ordinary `writeKey`/`write` path and gets the correct leading separator
from the generator's already-current `hasMembers`. The one synthesized separator — for a member-start whose
bytes carry none because it was the source's first member but an injection took that slot — falls out of a
single rule in `writeVerbatim`:

```
needsSeparator = depth > 0 && hasMembers[depth-1] && !separated &&
                 (inArray[depth-1] ? isValue(step) : step == KEY_NAME)
```

i.e. the container already holds a member in the output, this run begins a member/element, and the source did
**not** separate it. Examples (root object, after injecting `"x":9` into its first slot):

- displaced former-first key `"a"`: step `KEY_NAME`, `separated=false`, output occupied → synthesize `,`.
- a normal non-first key `"b"`: `separated=true` → no synthesis (its comma is in the bytes, this run or a
  prior one).
- a normal first key with nothing injected: output `hasMembers=false` → no synthesis.

A chunked run applies its steps once, on the first byte-copying chunk (the sink clears its held `JsonSteps`
after); resumed chunks are a pure byte conduit via the no-steps `writeVerbatim(bytes, index, length)`
overload. `END_*` steps only decrement depth; framing and segments are not verbatim steps.

## Open items

- Names, as built: `getVerbatim()` / `skipValue()` / `getSteps()` (→ `JsonSteps`: `count()` / `step(i)` /
  `separated(i)`) / `JsonController.verbatim()` / `JsonEvent.VERBATIM` / generator
  `writeVerbatim(bytes, index, length, steps)` (first chunk) + `writeVerbatim(bytes, index, length)`
  (continuation). The Phase-2 prune primitive landed as `JsonSource.skipValue()` rather than the earlier
  `JsonController.skip()` sketch (the occupancy/trim is the source's, not a controller signal). The Phase-3
  inject mechanism landed as continuous step tracking — first per-event `event()` + `separated()`, then
  converged onto the `getSteps()` batch driving the generator's state machine — rather than the earlier
  `getPosition()`/`JsonPosition`/`seed()` one-shot re-sync, which — along with `JsonStep`,
  `writeVerbatimSeparator()`, and the per-event `event()`/`separated()` accessors — was removed.
- **Follow-up — `skipObject()` / `skipArray()`:** peers to `skipValue()` for dropping a whole container at its
  `START_*` rather than at a member `KEY_NAME`. Deferred; the contract mirrors `skipValue()`.
- **Byte-coalescing (deferred):** today a forwarded verbatim run carries a one-step `JsonSteps` batch (the
  sink pulls `getVerbatim` once per forwarded event). True coalescing — one `getVerbatim` copy spanning a
  multi-step run, with the run's steps applied as a batch — is the design doc's Q1 efficiency goal but reworks
  the pump's completion/flush timing (the mediator reports `COMPLETED` at the top-level close, where the pump
  does not flush) and needs a hole-aware `getVerbatim` for the prune cursor, so it is a focused follow-up. The
  `getSteps()` batch is the primitive it generalizes to (runs concatenate their step batches) when it lands.
- Still open: injecting into a genuinely **empty** container (append, no member to displace) — distinct from
  inject-before-first, which is implemented; at the container's close the parser has popped it, so the step
  reports the outer context and it needs its own handling.
