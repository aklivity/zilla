# Handoff — Issue #1810: per-toolkit `oauth` for `mcp` proxy

Branch: `claude/kind-wright-P3p6I` (zilla repo). Develop here; push here only.
Issue: https://github.com/aklivity/zilla/issues/1810 — the design/phasing is the
source of truth. This file carries the cross-session context (the remote
environment is ephemeral: each session is a fresh clone with no prior chat).

> **Next session — start here:** Phase 2 is the current task. Read
> [Phase 2 — CONFIRMED DESIGN](#phase-2--confirmed-design-per-route--per-slice)
> first — it supersedes the older "concrete plan" framing below and records two
> design decisions the maintainer confirmed (per-route hydration; per-`(kind,prefix)`
> slice keys) plus the key structural findings. The older "concrete plan" /
> "Verified finding" subsections remain as background (the loopback async
> constraint is still true). Branch is green. Re-grep line numbers before editing.

---

## Status

| Phase | State |
| --- | --- |
| 1 — `resource_metadata` capture + carry + re-render | **DONE, pushed, full module green** |
| 2 — split hydrater from live entry; remove `originId == routedId` loopback | **design CONFIRMED (per-route + per-slice, see below); full code change is one atomic PR — not yet landed. Baseline re-verified green in fresh container.** |
| 3 — `with.cache` static credential over `options.cache.authorization` | not started (depends on 2) |
| 4 — protocol `2025-11-25` + `elicitation.url` negotiation | already landed before this branch (#1820) |
| 5 — guard `NEEDS_PREAUTHORIZE → preauthorize → callback → reauthorize` | not started |
| 6 — `timeout` option + per-request `McpXxxBeginEx` carriage; hold-and-resume | not started |
| 7 — non-blocking `tools/list` / blocking `tools/call` | not started |
| 8 — per-client listing filter (SEP-1488 / operator map / annotations) | not started |
| 2d — live-path baseline test (baseline + per-identity toolkits) | **deferred to Phase 7/8** (maintainer decision) |
| 9 — IT coverage of the preauthorize→elicit→callback→reauthorize flow | not started |

### Phase 1 — what shipped (2 commits on this branch)
- `feat(binding-mcp): capture and re-render RFC 9728 resource_metadata on bearer challenge`
- `test(binding-mcp): cover resource_metadata on the SSE events-resume bearer reject path`

Change set:
- IDL: added `resourceMetadata` (string16, null default) to `McpBearerResetEx`
  in `specs/binding-mcp.spec/src/main/resources/META-INF/zilla/mcp.idl`.
- Capture: `McpClientFactory` — added a `resource_metadata` named group to
  `BEARER_CHALLENGE_PATTERN` and set it on the `McpBearerResetEx` builder.
- Re-render: `McpServerFactory.bearerChallengeHeader(...)` takes
  `resourceMetadata` and emits `resource_metadata="..."`; both reject paths
  (`McpServer.doNetRejectBearer` POST path + `McpEventStream.doNetRejectBearer`
  GET/SSE path) pass it through.
- Spec helpers: `McpFunctions` builder + matcher extended with `resourceMetadata`.
- Scenarios (network + application): `lifecycle.initialize.reject.bearer.resource.metadata`,
  `lifecycle.events.resume.reject.bearer.resource.metadata`.
- IT methods added in `NetworkIT`, `ApplicationIT`, `McpClientIT` (capture),
  `McpServerIT` (both render paths).

Verified: `./mvnw clean verify -pl runtime/binding-mcp` — all UTs + ITs +
checkstyle + license + JaCoCo pass. Do **not** redo Phase 1.

---

## Build / test notes (this environment)

- Java 25 build. The flyweight plugin is a local SNAPSHOT — if it's missing,
  build it once: `./mvnw -q clean install -pl build/flyweight-maven-plugin -am -DskipTests`
- After any `.idl` change, rebuild the spec module so flyweights regenerate.
- IT classes (`*IT`) run under **maven-failsafe at `verify`**, with K3PO started
  in `pre-integration-test`. Running `mvn test` (surefire) will **not** start
  K3PO — you'll see "Failed to connect. Is K3PO ready?". Use:
  `./mvnw -q clean verify -pl <module> -Dit.test='ClassIT#method[,Class2IT#method]'`
- Always pass `clean` — the moditect plugin fails with "File ... already exists"
  / "already modular" if a prior `target/modules` jar is present.
- Useful skips while iterating: `-Dcheckstyle.skip -Dlicense.skip -Djacoco.skip=true`
  (but run a final pass WITHOUT skips before committing).
- Spec ITs run scripts peer-to-peer (no engine); runtime ITs run them against a
  live engine. The `network/` and `application/` script trees are shared
  between client-kind and server-kind ITs. Runtime ITs resolve scripts from the
  spec test-jar on the classpath, so **reinstall the spec module**
  (`./mvnw clean install -pl specs/binding-mcp.spec -DskipTests ...`) after
  adding/editing scripts before running runtime ITs.
- Fresh-container bootstrap that worked this session (NOT offline — local repo
  was empty on clone): `./mvnw -q clean install -pl build/flyweight-maven-plugin
  -am -DskipTests` then `./mvnw -q clean install -pl runtime/binding-mcp -am
  -DskipTests`, then the IT run below without `-o`. After that the offline `-o`
  loop works. Baseline `McpProxyCacheIT` = **27 ITs green (~12s)**.
- Confirmed working loop this session (all offline, `-o`):
  1. `./mvnw -q -o clean install -pl build/flyweight-maven-plugin -am -DskipTests`
     (once per fresh container — without `clean` the moditect step fails
     "already modular").
  2. `./mvnw -q -o clean install -pl specs/binding-mcp.spec -DskipTests ...`
  3. `./mvnw -o clean verify -pl runtime/binding-mcp -Dcheckstyle.skip -Dlicense.skip
     -Djacoco.skip=true -Dit.test='McpProxyCacheIT,McpProxyLifecycleIT,McpProxyIT'
     -Dsurefire.failIfNoSpecifiedTests=false` — **78 ITs green in ~29s**; a single
     `McpProxyCacheIT` (27 ITs) is ~13s. The loop is fast; iterate freely.
- **`FileSystemAlreadyExists` gotcha:** if a prior IT run is interrupted/fails,
  it can leave a mapped engine dir under `runtime/binding-mcp/target/` that makes
  the *next* run error on **every** test with
  `FileSystemAlreadyExists`/`AgentTerminationException` (not a code failure). Fix:
  `rm -rf runtime/binding-mcp/target/test* runtime/binding-mcp/target/zilla* ;
  find runtime/binding-mcp/target -name '*.dump*' -delete` then re-run with `clean`.
  Also: when a real test failure leaves the engine un-shut-down, **subsequent**
  tests in the same run cascade as `receiver is null` errors — read the
  failsafe report top-down and fix the *first real FAILURE*, ignore the cascade.

---

## Architecture map (binding-mcp proxy) — verified current state

Stream factories in
`runtime/binding-mcp/src/main/java/io/aklivity/zilla/runtime/binding/mcp/internal/stream/`:

- `McpProxyFactory` — dispatches by capability to the per-capability factories.
- `McpProxyLifecycleFactory` (~1489 lines) — live lifecycle entry **and** the
  loopback hydration fan-out. `McpLifecycleServer` (inner) holds session state,
  fans out to per-route `McpLifecycleClient`s, aggregates capabilities +
  list-changed.
- `McpProxyListFactory` (~2032 lines) — tools/prompts/resources list
  aggregation across routes (prefixes toolkit names, merges JSON arrays);
  `McpListServer` + `McpListClient` (inner) hold the streaming-JSON merge.
- `McpProxyToolsCallFactory`, `McpProxyPromptsGetFactory`,
  `McpProxyResourcesReadFactory`, `McpProxyItemFactory` — per-item ops.
- `McpClientFactory` — south side (HTTP/JSON-RPC). Resolves credentials
  (guard then static `binding.credentials`) and injects `authorization: Bearer`.
  This is where the Phase-1 bearer-challenge capture lives.
- `McpServerFactory` — north side (HTTP server). Phase-1 bearer re-render lives
  here (two `doNetRejectBearer` paths).
- `cache/` — `McpProxyCache` (shared store: keys `tools`/`resources`/`prompts`,
  lock keys `*.lock`), `McpProxyCacheManager`, `McpProxyCacheHydrater` (~915 lines),
  `McpProxyCacheHandler`, `McpProxyCacheListener`.

> The loopback mechanism (the heart of Phase 2) — its exact flow, the
> discriminators to remove, and the verified async constraint — is documented in
> [Phase 2 — concrete plan](#phase-2--concrete-plan-do-this-next-test-first).
> Don't duplicate it here; that section is authoritative and line numbers there
> were re-verified this session.

---

## Phase 2 — CONFIRMED DESIGN (per-route + per-slice)

This subsection is authoritative and supersedes the "concrete plan" framing that
follows. Two maintainer decisions (confirmed this session):

1. **Hydration unit = per-route slice replace.** Each route hydrates
   independently and replaces only *its* `<prefix>`-prefixed entries; a route
   that now returns an empty list just drops its old prefixed entries
   ("possibly none"). Do **not** reuse `McpListServer`'s all-routes merge for
   hydration.
2. **Cache storage = per-`(kind,prefix)` slice keys.** Store a separate slice
   per `(kind, prefix)`; concatenate slices on read. Slice value = comma-joined,
   prefix-injected item objects **without** the `{"tools":[ … ]}` envelope;
   `""`/absent ⇒ none. Serve = `prelude` + join(non-empty slices, `,`) + `]}`.

### Why this is ONE atomic change (do not try to split into green steps)

The slice **representation** change (envelope blob → item fragments) forces
changing, together: `McpProxyCache`/`McpListCache` storage, `McpCacheListServer`
serve/concat path, the **hydrater** (today it produces the envelope via the
loopback merge), the loopback removal, **and every seeded `*.yaml` + several
`.rpt`/serve ITs**. A hydrate→serve roundtrip breaks the instant the two
representations diverge, so there is no behavior-preserving partial commit. Plan
it as a single green landing (the IT loop is fast — see build notes — so iterate).

### Critical structural findings (verified this session)

- **`routeByPrefix`/`aggregateRoutes` are EMPTY for single-route bindings.**
  `McpBindingConfig` only computes prefixes when `routes.size() > 1` (line ~65).
  Most cache ITs are single-route → today they store *unprefixed* entries under
  the bare `tools`/`resources`/`prompts` keys. The slice scheme needs a
  single-route fallback: one slice with empty prefix `""` → slice key = the bare
  base key (`tools`), so single-route storage/serve stays byte-compatible. Do
  **not** naively set `aggregateRoutes` non-empty for the single-route case —
  `McpLifecycleServer.eventIds` allocation and `aggregating()` key off
  `aggregateRoutes.length > 0` and would change lifecycle/resume behavior.
- **Re-entrancy-safe relocation design (resolves the prior "Verified finding"):**
  the relocated hydrater opens its route-exit streams (lifecycle + list) over the
  **real engine bus** (async, exactly the ids used today: `originId =
  cache.bindingId`, `routedId = route.id`) and **accumulates each slice in
  memory** as a *pure sink* (it never sends WINDOW/RESET back into a merge
  engine). That removes the synchronous re-entrancy that sank the direct-call
  shortcut — there is no in-process fake loopback stream at all. The hydrater
  **reuses the existing decoder states and stream classes directly** —
  `McpListServer` / `McpListClient` / `McpLifecycleClient` and the `decode*`
  states — driven per route, with a pure-sink `MessageConsumer` accumulator as
  the reply target and a pre-granted (large, fixed) reply window so the merge
  engine never needs back-pressure. **Do not introduce new abstractions beyond
  the decoder states and the stream classes** (maintainer constraint): no
  `*Sink`/helper interface, no extracted decoder utility.
- The live no-cache path **keeps** `McpListServer`'s all-routes merge — that path
  is not loopback and must stay. Loopback removal only deletes the
  `originId == routedId` entry-side handling.

### File/line targets (verified current; re-grep before editing)

- `cache/McpProxyCache.java` — `McpListCache` (lines ~235-355): replace single
  `(storeKey, storeLockKey)` with a per-prefix slice map; get/put/acquire/release
  per slice; kind-level concat + per-slice checksum (any slice change ⇒ fire
  kind `list_changed` via `onSettled`). Store key consts lines 43-50.
- `cache/McpProxyCacheManager.java` — `hydrate(kind)` stays the manager-facing
  API; the hydrater internally fans out per route (keep per-kind retry/backoff).
- `cache/McpProxyCacheHydrater.java` (~915 lines) — rewrite: per route open
  lifecycle+list to the route exit, decode+prefix into a slice accumulator,
  `cacheOf(kind).putSlice(prefix, value)`. Remove the loopback lifecycle/list
  stream impersonation.
- `stream/McpProxyListFactory.java` — **reuse** the decoder states
  (`decodeInit…decodeIgnore` + `indexOfByte`, lines ~668-1082, 1811-1826) and the
  existing `McpListServer`/`McpListClient` stream classes **in place** (no
  extraction, no new abstraction). The relocated hydrater (moved into this
  `stream` package so it can reach the package-private inner classes) drives a
  `McpListServer` **per route** (one prefix in `remaining`) whose `lifecycle`
  host is a hydrater-owned `McpLifecycleServer` and whose reply `sender` is a
  pure-sink accumulator; strip the known `prelude`/`postlude` (`{"tools":[` …
  `]}`) to obtain the item-fragment slice. `McpCacheListServer` (lines 1582-1809)
  read path → concat slices. Remove `cache != null && originId != routedId`
  discriminator (line 159) → just `cache != null`.
- `stream/McpProxyLifecycleFactory.java` — delete `hydrating()` (271-274) and its
  uses (`onClientAbort` 1026, `onClientReset` 1074), the loopback `else` branch in
  `onServerBegin` (426-429), and the loopback half of `deferring` in
  `onClientFlush` (936). `aggregating()` (266-269) can drop the `originId !=
  routedId` term once loopback is gone (it's then always a live stream).

### No separable green sub-step — land it atomically (maintainer constraint)

Because we **reuse decoder states + stream classes in place** (no extraction, no
new abstraction), there is no behavior-preserving partial commit to land first.
Relocate the hydrater into the `stream` package, wire it to drive
`McpListServer`/`McpListClient`/`McpLifecycleClient` per route into a pure-sink
accumulator, switch storage to per-slice, update the serve concat path, remove
the loopback discriminators, and fix the affected yamls/ITs — then land green in
one focused pass. The IT loop is fast; iterate.

### Execution decisions (maintainer)

- **Land as ONE atomic commit.** Do the whole relocation in a single green pass
  (hydrater relocation + per-slice storage + serve concat + loopback-discriminator
  removal + affected yamls/ITs). It cannot be split *green* anyway; do not commit
  intermediate broken states.
- **Defer 2d** (the live-path baseline test that proves baseline + per-identity
  toolkits). Keep all existing `cache.*` / lifecycle ITs green and adjust them to
  the slice scheme; do not add the new live-path scenario in this pass — it lands
  with Phase 7/8 (per-identity live listing).

### Kickoff for the next session (self-contained)

Read this whole "CONFIRMED DESIGN" subsection, then execute the Phase 2 atomic
landing test-first, committing only green to `claude/kind-wright-P3p6I`. Honor:
reuse `decode*` states + `McpListServer`/`McpListClient`/`McpLifecycleClient` **in
place** (no new abstractions / no `*Sink` / no extracted decoder); relocate the
hydrater into the `stream` package; per-route hydration into per-`(kind,prefix)`
slice keys with the empty-prefix single-route fallback; pure-sink accumulator with
a pre-granted fixed reply window. One atomic commit; defer 2d.

First action — bootstrap (fresh container has an empty local repo), then confirm
baseline green before changing anything:

```
./mvnw -q clean install -pl build/flyweight-maven-plugin -am -DskipTests
./mvnw -q clean install -pl runtime/binding-mcp -am -DskipTests
./mvnw clean verify -pl runtime/binding-mcp -Dcheckstyle.skip -Dlicense.skip \
  -Djacoco.skip=true -Dit.test=McpProxyCacheIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expect `McpProxyCacheIT` = 27 green (~12s). Then the offline `-o` loop works.

---

## Phase 2 — concrete plan (do this next, test-first)

Goal (issue §1): two distinct flows — background **cache hydrater** (shared,
list-only credential, tolerant, populates the shared store) vs **live entry
point** (per connecting client, eliciting). Hydrater fans out **directly** to
route exits; remove the `originId == routedId` self-stream detection so the
proxy entry only ever handles live client requests.

### ⚠️ Verified finding — do NOT retry the "direct-call decoupling" shortcut

A previous session prototyped a surgical shortcut and **proved it does not
work**; the result was reverted to keep the branch green. Record so it is not
rediscovered:

- **Idea (rejected):** keep `McpLifecycleServer` / `McpListServer` aggregation
  where they are, but have the hydrater stop opening loopback streams via the
  engine bus (`streamFactory.newStream`) and instead call dedicated
  `newHydrationStream(...)` entry points on `McpProxyLifecycleFactory` /
  `McpProxyListFactory` **directly** (in-process), passing the hydrater's own
  `MessageConsumer` as `sender`, plus an explicit `boolean hydrating` flag to
  replace the `originId == routedId` checks.
- **Why it fails:** the hydrater↔proxy-entry path relies on the engine bus
  delivering frames **asynchronously** (each frame on a later `EngineWorker.doWork`
  iteration). A direct call is **synchronous and re-entrant**: inside the
  hydrater's `doLifecycleBegin`, `factory.newHydrationStream(...)` returns the
  host consumer, then `receiver.accept(BEGIN)` synchronously runs the host's
  `onServerBegin → doServerBeginDeferred → doServerBegin`, which sends the reply
  BEGIN straight back into the hydrater's `onLifecycleBegin → doLifecycleWindow →
  doWindow(receiver)` **before** the hydrater's `receiver` field has been
  assigned → `NullPointerException: receiver is null`. (Asserts are disabled at
  runtime so the `assert receiver != null` does not catch it.) Even if that one
  NPE is patched by assigning the field first, the same synchronous re-entrancy
  pervades flow-control / window / DATA-ordering throughout the stream handlers,
  which are all written against the engine's async model. The loopback exists
  precisely to obtain that async decoupling.
- **Conclusion:** the only correct way to remove the loopback is the **full
  relocation** below — the hydrater must open streams to **route exits** (real
  cross-binding streams, so they go through the engine bus and stay async) and
  do the merge itself. There is no flag-swap shortcut.

### Required approach (full relocation)

1. Relocate route fan-out + list aggregation from the loopback path into
   `McpProxyCacheHydrater` (and/or a helper in `cache/`): resolve the routes the
   hydrater should enumerate (see `McpBindingConfig.aggregateRoutes` /
   `resolveAll`), open lifecycle + per-list streams **directly to each route
   exit via the engine bus** (`streamFactory.newStream` with
   `originId = cache.bindingId`, `routedId = route.id` — note these are exactly
   the ids `McpListClient`/`McpLifecycleClient` already use today, so the
   upstream-facing wire behaviour is unchanged), and aggregate the N responses
   into the single cached blob the store expects (mirror the prefix/merge logic
   in `McpProxyListFactory`'s `McpListServer`/`McpListClient`: streaming-JSON
   array merge with per-route `prefix(kind)`). The merge code is the bulk of the
   work (~900 lines in `McpProxyListFactory`); plan to extract it into a shared,
   package-accessible helper rather than duplicate it, OR move the hydration
   driver into `stream` (non-`cache`) so it can reuse the private inner classes.
2. Delete `hydrating()` / `aggregating()`-via-loopback and the
   `originId == routedId` / `originId != routedId` guards listed above; the
   lifecycle/list factories then only serve live client requests.
3. Enforce the correctness constraint: per-client OAuth tokens/lists are
   per-identity and must **never** enter the shared store (cross-user leakage).
   The shared cache is the shareable baseline only.
4. Tests: keep all `cache.*` scenarios green; add a scenario proving a live
   `tools/list` is served from baseline + the client's per-identity toolkits
   (not the degraded cached aggregate). Update `McpProxyCacheIT` /
   `McpProxyLifecycleIT` / `McpProxyListIT` accordingly.

Note: this is a large, tightly-coupled refactor. Treat it as its own PR. Do not
proceed to Phase 3+ until Phase 2 is confidently green (existing suite + new
live-path assertions).

### Architecture confirmed this session (current `develop` + this branch)

- File sizes: `McpProxyLifecycleFactory` 1489 lines, `McpProxyListFactory` 2032,
  `McpProxyCacheHydrater` 915 (the file/line numbers in older notes were stale —
  re-grep before editing).
- The loopback is **purely internal plumbing**. In the loopback case the
  upstream route-exit streams are *already* opened with
  `originId = cache.bindingId`, `routedId = route.id` (see
  `McpListClient` `originId = server.lifecycle.originId`,
  `McpLifecycleClient` `originId = server.routedId`). So "fan out directly to
  route exits" is about removing the hydrater↔entry round-trip, **not** changing
  how upstream streams are opened. Because of this, the **runtime ITs run only
  the south-side (`server`) scripts** against the live engine, so a correct
  relocation that preserves the route-exit frame sequence should keep them green
  **without rewriting `.rpt` scripts**; the spec-level peer-to-peer `ProxyCacheIT`
  scripts are likewise unaffected unless you change choreography.
- Flow: hydrater `acquireLock` → opens loopback **lifecycle** stream
  (`originId==routedId==bindingId`) → `McpLifecycleServer` mints `sessionId`,
  registers in `binding.sessions`, replies BEGIN(sessionId) which the hydrater
  captures as `cache.sessionId` → `onOpened` → for each kind opens a loopback
  **list** stream carrying `cache.sessionId` → `McpProxyListFactory` looks up the
  host by sessionId, runs `McpListServer` aggregate (fan out via
  `McpListClient` → `server.lifecycle.supplyClient(routedId)`), streams the
  merged JSON back as DATA on the loopback reply → hydrater accumulates and
  `cache.cacheOf(kind).put(value, …)`.
- Discriminators to remove (verified): `McpProxyLifecycleFactory`
  `aggregating()` (`eventIds != null && originId != routedId`), `hydrating()`
  (`originId == routedId`), the `onServerBegin` branch
  `if (binding.cache != null && originId != routedId)`, and `server.hydrating()`
  uses in `onClientReset`/`onClientAbort`; `McpProxyListFactory.newStream`
  `if (cache != null && originId != routedId)`.

---

## Phases 3–9 — sketch (each depends on 2)

- **3**: add `with.cache.credentials` (per-route static) to `McpWithConfig` +
  `McpWithConfigAdapter` + schema `binding-mcp.schema.json`; precedence `with`
  over `options.cache.authorization`; list-only usage. Wire the chosen
  credential into the hydrater's per-route stream (clean once Phase 2 fans out
  per route). Add a `McpWithConfigAdapter`/`McpOptionsConfigAdapter` unit test
  and a `cache.hydrate.credentials`-style per-route scenario.
- **5**: single guard-agnostic path per route —
  `reauthorize(inbound-bearer-or-null)` → valid → `credentials()` → stamp;
  `NEEDS_PREAUTHORIZE` → `preauthorize(callback = Zilla connect URL)` → authorize
  URL; on callback feed callback URL into async `reauthorize`. `binding-mcp`
  owns only the MCP surface (elicitation emission, `McpAuthCallbackHandler`,
  state correlation). Store execution tokens per `(identity, route)` — never in
  the shared store.
- **6**: `timeout` option on the mcp **server** binding (default `0`);
  `0` → emit `URLElicitationRequiredError (-32042)` and retry; `>0` → hold the
  request open up to `timeout` via the resumable stream, fall back to `-32042`
  on expiry. Server resolves the effective timeout (gated by client's negotiated
  `elicitation.url` + hold/resume capability) and stamps it on each request's
  `McpXxxBeginEx` (IDL change + carriage).
- **7**: non-blocking `tools/list` (return authorized toolkits' tools
  immediately + emit per-unauthorized-toolkit `elicitation/create` URL-mode +
  fire `notifications/tools/list_changed`); blocking `tools/call` honors the
  per-request timeout.
- **8**: per-client listing filter — consume SEP-1488 `securitySchemes` when
  present, else operator-declared tool→scope map, else annotation default
  (`readOnlyHint`/`destructiveHint`); per-identity live listing where metadata
  is absent. Distinguish acquirable (list + prompt on use) from non-acquirable
  (filter out).
- **9**: IT coverage of preauthorize→elicit→callback→reauthorize using the
  engine's `type: test` guard exercising `NEEDS_PREAUTHORIZE`/`preauthorize`
  (no live OAuth provider). Use the engine test-jar's `TestGuard` — do not pull
  a production guard SPI into test scope.

References: SEP-1036 (URL elicitation), SEP-1488 (per-tool securitySchemes,
draft), RFC 9728, RFC 6750; related #1793, #1795, #1818, #1820.

---

## Housekeeping

- No PR opened (none requested). No issue comments posted.
- Delete this `HANDOFF.md` before opening the eventual PR.
