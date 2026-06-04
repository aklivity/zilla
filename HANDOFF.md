# Handoff — Issue #1810: per-toolkit `oauth` for `mcp` proxy

Branch: `claude/kind-wright-P3p6I` (zilla repo). Develop here; push here only.
Issue: https://github.com/aklivity/zilla/issues/1810 — the design/phasing is the
source of truth. This file carries the cross-session context (the remote
environment is ephemeral: each session is a fresh clone with no prior chat).

> **Next session — start here:** #1810 is now titled **"per-toolkit elicitation for
> `mcp` proxy"** (reframed from "oauth" — the binding does elicitation; OAuth lives in
> the guard/remote). Phases 1, 2, 2e, 3, 6 DONE+pushed; Phases 4, 5, 9 landed in
> `develop`. **7a DONE+pushed** (partial-auth `tools/list` + relay elicit + relay
> `list_changed`). **Bearer handling DONE+pushed** (proxy absorbs a route's bearer
> reset → skip + partial-open + eager reply-open; never relays bearer to the aggregate
> client). **Server-side symmetric strip + `context` IDL field DONE+pushed.**
> **The active work is 7b: per-toolkit elicitation callback routing via an in-band
> `context` field — design CONVERGED, IDL field landed, runtime/proxy/scenario remain.
> Read "Phase 7b — CONVERGED DESIGN (context field)" — it is authoritative and
> SUPERSEDES the older `<toolkit>__` state-injection mechanism.** Phase 8 split out to
> #1831. Branch is green. Re-grep line numbers before editing.
>
> **Prior session's commits (newest last):** `d199b467` bearer-skip + eager reply-open;
> `e665f996` server strips its own elicitation state prefix on the callback; `bb3f74d6`
> `context` IDL field on the elicit challenge + callback extensions.
>
> **7b progress (this session, newest last):** `6a3e93cb` 7b-step-1 — `McpFunctions` `context`
> builder+matcher (4 spots) + 6 tests; `7ef4988b` 7b-step-2a — server echoes `context` on the
> held-stream `elicitCallback`. **Step 1 DONE; step 2a DONE. Remaining: 2b (non-blocking
> lifecycle elicit handling — NOT started, grounded findings in "Phase 7b … REMAINING" below),
> 2c (TTL), step 3 (proxy push/route/pop + reauthorize-off-lifecycle), step 4 (proxy IT).**
> 2b is the next intricate piece: the north server must handle a `ChallengeFW` on the lifecycle
> stream + render `elicitation/create` on the lifecycle SSE (new net encoding) + forward the
> callback up the lifecycle. Branch is green at `7ef4988b`.
>
> **Track A (OSS relay) status:** **N1 (origin-conditional passthrough) + N2
> (persistent per-route lifecycle / `Mcp-Session-Id`+`MCP-Protocol-Version`
> replay / resume) are DONE+pushed** — see the "Track A OSS critical path"
> section. N2 is locked in by a face-based session-id convention swept across the
> whole corpus (net=`transport-N`, app=`session-N`; proxy per-toolkit exits
> `session-1a`/`session-1b` under aggregate `session-1`).
> **N3 — the `examples/mcp.proxy` relay example + e2e ITs — is
> SPLIT OUT of #1810** (separate effort, maintainer 2026-06-04); it can ship
> independently against real upstreams. P8 + the zilla-plus OAuth guard remain
> out. Full no-skip green: spec 199 IT/76 UT, runtime 212 IT/26 UT.

---

## Status

| Phase | State |
| --- | --- |
| 1 — `resource_metadata` capture + carry + re-render | **DONE, pushed, full module green** |
| 2 — split hydrater from live entry; remove `originId == routedId` loopback | **DONE+pushed. Commit 1: relocation + loopback removal + per-route fragment assembly (behavior-preserving). Commit 2: keep-stale — per-route fragments live on `McpProxyCache.McpListCache`; failed route (abort/reset/timeout/bearer challenge) keeps last-known-good, success replaces, empty→empty; new `cache.refresh.toolkit.keep.stale.on.failure` scenario + `shouldRefreshToolkitKeepingStaleOnFailure`. Runtime no-skip green: McpProxyCacheIT 28, McpProxyLifecycleIT 7, UT 20, checkstyle/license/jacoco pass.** |
| 2e — spec-module jacoco 0.95<0.96 (pre-existing Phase-1 `McpFunctions` gap) | **DONE+pushed. Extended `McpFunctionsTest` (50→69) → 0.98; full spec verify green (184 ITs + UT + jacoco/checkstyle/license).** |
| 3 — `with.cache` static credential over `options.cache.authorization` | **DONE+pushed.** Per-route `with.cache.credentials` added to `McpWithConfig` (+`McpWithCacheConfig`/builder), `McpWithConfigAdapter`, and the route `with` schema block. **Per-route credential is resolved once in `McpLifecycleClient.doClientBegin` (hydration only): if the route has `with.cache.credentials` it is reauthorized through the cache guard into the lifecycle client's `authorization`, else it inherits `server.authorization` (= the binding cache authorization from `options.cache.authorization`) → precedence `with` > `options`.** The route's **lifecycle AND list streams share that single per-route authorization**: `McpListClient`'s upstream (route-exit) frames now use `lifecycle.authorization` instead of `server.authorization`. `McpBindingConfig.routeCacheCredentials(routedId)` looks up the override. Reauthorize is once-per-route (lifecycle client is `supplyClient`-memoized, `doClientBegin` idempotent), so all list kinds for a route reuse the same session. New `McpWithConfigAdapterTest` (4) + scenario `cache.hydrate.credentials.toolkit` (single accept @ `authorization`=1L covers lifecycle+toolsList since they now match) wired into `McpProxyCacheIT#shouldHydrateToolkitWithRouteCredentials` (server-only, hydrate filter=tools) and peer-to-peer `ProxyCacheIT#shouldHydrateToolkitWithRouteCredentials`. Negative-checked (reverting the reauthorize → IT times out). Full no-skip green: runtime 200 ITs/UT + jacoco/checkstyle/license/notice; spec 185 + gates. |
| 4 — protocol `2025-11-25` + `elicitation.url` negotiation | already landed before this branch (#1820) |
| 5 — guard `NEEDS_PREAUTHORIZE → preauthorize → callback → reauthorize` | **CORE in `develop` (#1739/#1752); the live-identity follow-up (Gap A+B) DONE+pushed on THIS branch (commits `73c45825`, `b1addfd1`, `d0d8b480`, `88f5bab3`, `e2cafd12`) — see the "Phase 5 Gap A+B" section.** Core: `McpClientFactory` `reauthorize(null)`→`MASK_AUTHORIZED`→`credentials()`→stamp; `NEEDS_PREAUTHORIZE`→`guard.preauthorize`→`elicitCreate` challenge→async `reauthorize` on callback; `McpServerFactory` `McpAuthCallbackHandler`/state correlation. **Gap A (inbound identity) RESOLVED on this branch:** the shared `McpRequestStream.proceedWithRequest` (~2628) reuses the inbound `authorization` long when already authorized (`MASK_AUTHORIZED`)→`guard.credentials(authorization)`; else falls through to `reauthorize(...,null)`→elicit. Upstream net opened with `authorization 0L` (`HttpStream.doNetBegin` ~3829) — identity consumed at the binding, conveyed via the `Authorization` bearer. Covered by `tools.call/prompts.get/resources.read.identity` + `client.identity.yaml` (`McpClientIT#shouldCallToolWithIdentity` etc. + peer `NetworkIT`). **Gap B (elicit) RESOLVED:** elicit machinery lives in the base `McpRequestStream` (shared by tools/call+prompts/get+resources/read). **Schema:** proxy kind disallows `options.authorization` (`SchemaTest#shouldRejectProxyWithAuthorization`). **Superseded (won't-do, maintainer):** the older "(a) per-route `McpRouteConfig` guard/creds; (b) `reauthorize` with inbound bearer string" framing — the locked design carries the inbound authorization *long* (not a bearer string) and the live tools/call follows one route by prefix; future zilla-plus uses `options.cache.authorization` + token exchange. No open Phase-5 work remains on this branch. |
| 6 — `timeout` option + per-request `McpXxxBeginEx` carriage; hold-and-resume | **DONE+pushed (6a plumbing + 6a server stamping + 6b behavior).** Per-binding `timeout` option (config/builder/adapter/schema); `int64 timeout` on the six request BeginEx variants + `McpFunctions`; server stamps effective timeout (gated by `CLIENT_ELICITATION_URL`) on each request BeginEx; client honors it as the elicit hold budget; server holds (`elicitation/create` SSE) when `timeout>0` and emits `-32042 URLElicitationRequiredError` (url in `data`) on expiry/`timeout==0`; explicit DECLINE stays `-32000`. See the "Phase 6 — 6b" section for the 3 commits + k3po teardown notes. |
| 7 — non-blocking `tools/list` / blocking `tools/call` (per-toolkit elicitation) | **7a DONE+pushed (partial-auth `tools/list` + relay elicit + relay `list_changed`). Bearer handling DONE+pushed (proxy absorbs a route's bearer reset → settle-unauthorized + skip + partial-open; eager reply-open; never relays bearer to the aggregate client — the faithful bearer relay stays in the non-proxy `server`→`client` topology only). Server-side symmetric strip DONE+pushed (`McpAuthCallbackHandler` strips its own `<sessionId>.<elicitationId>.` from the callback so the route-exit guard sees the bare nonce). `context` IDL field DONE+pushed. 7b (per-toolkit elicitation callback routing) IN PROGRESS — design CONVERGED on an in-band `context` field; see "Phase 7b — CONVERGED DESIGN (context field)". The older `<toolkit>__` state-injection / symmetric-strip mechanism is SUPERSEDED by it.** |
| 8 — per-client listing filter (SEP-1488 / operator map / annotations) | **SPLIT OUT of #1810 to its own issue #1831 (2026-06-04). No longer in #1810 scope.** |
| 2d — live-path baseline test (baseline + per-identity toolkits) | **deferred to Phase 7/8** (maintainer decision) |
| 9 — IT coverage of the preauthorize→elicit→callback→reauthorize flow | **DONE in `develop` (#1739/#1752), NOT this branch.** Scenarios: `tools.call.elicit.{completed,declined,timeout}` × {plain, `.guarded`, `.proxied`}, `reject.auth.callback.unknown.elicitation`, `lifecycle.initialize.elicitation.{url,form}`. (The Gap-A inbound-identity reuse IS covered by `tools.call/prompts.get/resources.read.identity` ITs on this branch; the superseded per-route guard/creds has no IT, by design.) |

### Phase 5 residual (Gap A + Gap B) — CONFIRMED DESIGN (DONE+pushed this branch)

Maintainer-confirmed model (supersedes the older Phase-5 "per-route guard / inbound-bearer" framing):

- **Cache/list path** (Phase 3, done): list caches hydrated with per-route `with.cache.credentials` → fallback `options.cache.authorization`; `tools/list` etc. served from cache, no backend hit. Future zilla-plus: a single `options.cache.authorization` + OAuth-guard **token exchange** maintains identity per remote server (per-route cache creds then unnecessary).
- **Live `tools/call` / `prompts/get` / `resources/read`**: each follows **exactly one route** by tool-name prefix (already done — `McpProxyItemFactory.resolve(beginEx, authorization)` + `route.strip`/`route.prefix`). The **inbound authorization long** (the connecting client's guard session) must be **used** on the client-kind path; may trigger elicitation; client completes it; call proceeds.

**Decisions locked:**
1. **Carry the authorization *long* only — NOT a bearer string.** The inbound `authorization` already flows north→proxy→south unchanged on every `BEGIN`; no IDL field, no `Authorization`-header capture at the server, no `McpFunctions` field. `McpClientFactory.proceedWithRequest`'s `authorization` param already *is* the inbound `begin.authorization()`; today it is ignored (`reauthorize(...,initialId,null)` + `binding.credentials` fallback).
2. **Mechanism (idiomatic, per HTTP/MQTT precedent):** when the inbound `authorization` is already authorized by `binding.guard` (`(authorization & GuardHandler.MASK_AUTHORIZED) != 0`), reuse it → `credentials = guard.credentials(authorization)` and proceed without elicit. Otherwise fall through to the existing `reauthorize(...,null)` → `NEEDS_PREAUTHORIZE` → `preauthorize`/elicit path. The inbound-reuse branch belongs in the shared base so all three request kinds get it.
3. **Gap B:** push the elicit machinery (`pendingAuth`, `elicit*`, buffered body, `elicitCompletion`/`onElicitCompleted`/`onElicitFailed`, timeout signal) down from `McpToolsCallStream` into the shared `McpRequestStream` so `prompts/get` + `resources/read` can elicit too. Must keep existing `tools.call.elicit.*` ITs green.
4. **Schema:** in the proxy `then` branch (`mcp.schema.patch.json` ~lines 240-261) add `options.properties.authorization: false` — disallow the whole `authorization` block for `kind: proxy`. **Keep** `McpAuthorizationConfig.credentials` + adapter + builder + `McpBindingConfig.credentials` in Java untouched — `server`/`client` kinds still use `options.authorization.credentials` (unified syntax). Safe because **no proxy-kind factory reads `binding.guard`** — only `binding.cache.guard` (verified). Mirror of the existing `cache: false` constraint in the server/client `else` branch.

**Verified facts (2026-06-03 grounding):**
- Proxy kinds use only `binding.cache.guard`, never `binding.guard` → schema disallow is safe.
- Live request streams open with `server.authorization` (inbound long), not a cache value (`McpProxyItemFactory.onLifecycleSettled` ~624; `McpProxyLifecycleFactory.doClientBegin` ~839 — cache reauthorize only when `server.hydration`).
- `TestGuardHandler`: `reauthorize` matches on the credential string (ignores `contextId`); `credentials(sessionId)` returns the configured token regardless of id; `verify` authorizes if session in map.
- **Test mechanism:** `option zilla:authorization 1L` on the app-layer connect presents an authorized session (HTTP binding's `rfc7230/authorization/*` app scripts use exactly this). With guard `credentials: "test-token"`, an authorized inbound → `guard.credentials(1L)` = `"test-token"` injected upstream, no elicit.

**Edit points (`McpClientFactory.java`):** base `McpStream.proceedWithRequest` ~1749-1768; `McpToolsCallStream.proceedWithRequest` ~2933-2993 + elicit machinery ~2898-3138; `McpPromptsGetStream`/`McpResourcesReadStream` ~3200-3278 (no override today); `injectAuthorization` ~3342-3349 (11 call sites); `credentials` field on `McpStream` ~1525.

**Test plan (test-first):** new app+net scenarios `tools.call.identity`, `prompts.get.identity`, `resources.read.identity` (client connects `option zilla:authorization 1L`, no challenge expected; net side asserts `Authorization: Bearer test-token` on lifecycle + request); new `client.identity.yaml` (guard `oauth` type test, `credentials: "test-token"`); IT methods in `McpClientIT` + peer `ApplicationIT`/`NetworkIT`; config-validation IT rejecting `authorization` under `kind: proxy`.

### Phase 5 Gap A+B — progress (this session)

**DONE + pushed:** `feat(binding-mcp): forward inbound client identity to upstream tools/call bearer`.
- `McpToolsCallStream.proceedWithRequest`: when inbound `authorization` is already
  authorized (`(authorization & MASK_AUTHORIZED) != 0`), reuse it →
  `credentials = guard.credentials(authorization)`, skip elicit; else the existing
  `reauthorize→elicit` path. (`McpClientFactory.java` ~2949.)
- **Upstream-auth decision (maintainer): consume the inbound identity at the binding;
  open upstream net streams with `authorization 0`.** Implemented in the shared
  `HttpStream.doNetBegin` (~3822) — `newStream(..., 0L, ...)`. The external server sees
  only the bearer (from `injectAuthorization`); the internal authorization long does not
  leak onto the external connection. This also resolved a discovered stall: a non-zero
  authorization long propagated to the client-kind upstream stalled the lifecycle after
  `initialize` (no `notifications/initialized`); no prior test exercised a non-zero
  upstream authorization. The base `McpStream.proceedWithRequest` was left unchanged
  (lifecycle stays unguarded upstream).
- Tests: `tools.call` app scripts parameterized with `authorization` (default `0L`,
  overridden `1L` via `@ScriptProperty` — reuses existing scripts per maintainer
  guidance); new `tools.call.identity` network scenario asserts upstream
  `Authorization: Bearer test-token`; `client.identity.yaml`; `NetworkIT` +
  `McpClientIT` green; full spec + runtime `install` green.

**Work unit COMPLETE — all shipped + pushed, full spec & runtime builds green:**
1. **Schema constraint** — `feat(binding-mcp): disallow options.authorization for kind: proxy`.
   Proxy `then` branch adds `options.properties.authorization: false`; removed the unused
   `options.authorization` from `proxy.options.yaml`; added `proxy.authorization.invalid.yaml`
   + `SchemaTest.shouldRejectProxyWithAuthorization`. server/client keep the field.
2. **Gap A for `prompts/get` + `resources/read` (+ `tools/list`)** —
   `feat(binding-mcp): forward inbound client identity for prompts/get and resources/read`.
   Inbound-reuse added to the shared `McpRequestStream.proceedWithRequest` (NOT `McpStream`,
   so the lifecycle stays unguarded). New `prompts.get.identity` / `resources.read.identity`
   network scenarios (app scripts parameterized with `authorization`, `@ScriptProperty` override).
3. **Gap B (elicit) for `prompts/get` + `resources/read`** —
   `refactor(binding-mcp): share elicit flow across request streams`. The full elicit
   machinery moved from `McpToolsCallStream` into `McpRequestStream`; `tools/call`,
   `prompts/get`, `resources/read` inherit one implementation. New
   `prompts.get.elicit.completed.guarded` / `resources.read.elicit.completed.guarded`
   scenarios; existing `tools.call.elicit.*` ITs cover the moved code (no regression).

### Phase 6 — `timeout` + per-request carriage + hold-and-resume (IN PROGRESS)

Design: Issue #1810 §5–§6. Two sub-steps (maintainer-approved). `timeout` is **elicitation-scoped**:
no elicitation (authorized/credentialed request) → no timer, no `-32042`, proceeds immediately.

**6a plumbing — DONE + pushed** (`feat(binding-mcp): add server timeout option and per-request
McpXxxBeginEx timeout carriage`): `int64 timeout = 0` on the six operation BeginEx variants;
`McpFunctions` timeout builders/matchers (+ McpFunctionsTest); `options.timeout` Duration option
(`McpOptionsConfig`/Builder/`McpOptionsConfigAdapter` — absent→null, runtime treats null as 0; +
`McpOptionsConfigAdapterTest`); schema `timeout` (format duration) in the shared options block.
No behavior yet (nothing stamps/consumes it).

**6a server stamping — DONE + pushed** (`feat(binding-mcp): stamp effective elicitation timeout
on request BeginEx at the server`). `McpServerFactory` resolves effective = `options.timeout`
millis gated to `0` without `CLIENT_ELICITATION_URL` (client caps persisted per session at
initialize — added `int`... actually stored as the resolved `long requestTimeout` on
`McpLifecycleStream`, set at initialize); `McpServer` carries the configured timeout (ctor param
from `binding.options.timeout`); the six `onDecodeXxx` stamp `.timeout(session.requestTimeout)`.
Proven by `tools.call.timeout` (initialize negotiates `elicitation.url` → app toolsCall BeginEx
`timeout=30000`) via McpServerIT (`server.timeout.yaml`) + peer NetworkIT/ApplicationIT. Still
behavior-neutral downstream.

**6b behavior — DONE + pushed (3 commits on this branch).** Maintainer decisions implemented:
(1) expiry/no-hold → `-32042 URLElicitationRequiredError`; explicit user DECLINE stays
`-32000 "Authorization declined"`. (2) `timeout==0` → emit `-32042` directly, NO
`elicitation/create` first. Full no-skip green: runtime 208 ITs + UT + jacoco/checkstyle/license;
spec 195 ITs + UT + gates.

Commits:
1. `feat(binding-mcp): emit -32042 URLElicitationRequiredError on elicitation expiry` — server
   CANCELLED→`-32042` (SSE encoder + `elicitUrl` remembered from the elicitCreate challenge,
   data array `[{"mode":"url","url":…,"elicitationId":…}]`); net `tools.call.elicit.timeout`
   scripts assert `-32042`.
2. `feat(binding-mcp): honor per-request elicitation timeout on the client` — `McpRequestStream.timeout`
   read per kind in `newStream`; elicit hold armed with per-request `timeout` (not the engine-wide
   inactivity); `timeout==0` → no hold (challenge then reset+abort). Guarded elicit scenarios carry a
   stamped `.timeout(...)`; new `tools.call.elicit.reject.guarded` (client `connect aborted` ↔
   server `rejected`) covers the zero-timeout client path.
3. `feat(binding-mcp): reject tools/call with -32042 when elicitation hold disabled` — server
   `onAppChallengeElicitCreate` gates on `session.requestTimeout`: `>0` hold (elicitation/create SSE,
   today), `==0` → plain `-32042` (`doEncodeResponseUrlRequired`, deferred end via
   `doEncodeResponseEnd` so the buffered body survives a not-yet-granted reply window) + tear down.
   Server-kind hold scenarios (completed/declined/timeout) migrated to negotiate `elicitation.url` +
   `server.timeout.yaml` so they keep holding; new `tools.call.elicit.reject` (net+app) covers the
   immediate reject; the proxied timeout test got its own upstream script
   (`tools.call.elicit.timeout.proxied/server`) since `tools.call.elicit.timeout/server` is now
   `elicitation.url`.

k3po teardown notes (for future reference): a connect whose reply resets before `connected`
registers is expressed as `connect aborted` ↔ `rejected`; a reply that begins then aborts is
`connected` + `read aborted`. The runtime coalesces begin+challenge+reset into one turn, so the
guarded reject app client uses `connect aborted`.

Audited current flow (verbatim line refs may drift — re-grep):
- **Client** `McpClientFactory.McpRequestStream.proceedWithRequest` (~2603-2669): NEEDS_PREAUTHORIZE →
  `guard.preauthorize` → send `elicitCreate` CHALLENGE(url) → `pendingAuth`, buffer body, arm timer
  with **global `inactivityTimeoutMillis`** (~2652). Timer/expiry → `onAppSignal`
  ELICIT_TIMEOUT (~2769) → `emitElicitComplete(CANCELLED)` (~2834) + abort. Callback FLUSH →
  async reauthorize → `onElicitCompleted` (COMPLETED→replay buffered body / DECLINED→abort).
- **Server** `McpServerFactory`: on `elicitCreate` CHALLENGE → `doEncodeElicitCreateDataEvent`
  (~2248) emits SSE `{"jsonrpc":"2.0","method":"elicitation/create","params":{"mode":"url",
  "elicitationId":…,"url":…}}` and holds the connecting client's SSE open. On `elicitComplete`
  FLUSH → `onAppFlushElicitComplete` (~4560): DECLINED→`doEncodeElicitErrorEvent(-32000,
  "Authorization declined")`, CANCELLED→`-32000 "Authorization timed out"`, COMPLETED→proceed.
  Error encoders `doEncodeElicitErrorEvent` (~2288, SSE) / `doEncodeResponseError` (~2350, plain)
  emit only `{code,message}` — no `data`. `session.requestTimeout` (on `McpLifecycleStream` ~3067)
  is reachable at the CHALLENGE/elicitComplete handling point.

Implementation:
1. **Client per-request timer.** In `McpClientFactory.newStream` (~1488-1504, the `switch
   (mcpBeginEx.kind())` that sets `contentLength`) also read `.timeout()` per variant into a new
   `McpRequestStream.timeout` field. In `proceedWithRequest`, arm the elicit timer with
   `this.timeout` (not `inactivityTimeoutMillis`) when `timeout>0`; when `timeout==0` do NOT
   hold/buffer — send the CHALLENGE then abort the upstream attempt (server emits `-32042`). NOTE:
   `McpBeginExFW` has no polymorphic `timeout()` — switch on `kind()`.
2. **Server hold-vs-reject.** Gate the CHALLENGE response on `session.requestTimeout`: `>0` →
   `elicitation/create` SSE + hold (today), and remember the url (store `elicitUrl` on the server
   request stream); `==0` → emit `-32042` (url in data) + end, NO `elicitation/create`. On
   `CANCELLED` (expiry) → `-32042` with the remembered url (not `-32000`). DECLINED → `-32000`
   (unchanged).
3. **`-32042` encoder.** Add an encoder variant emitting
   `{"jsonrpc":"2.0","id":<id>,"error":{"code":-32042,"message":"URL elicitation required",
   "data":[{"mode":"url","url":"<url>","elicitationId":"<id>"}]}}` (SSE variant for the held path,
   plain variant for the no-hold path — mirror the existing two encoders). Single-entry array
   (tools/call targets one toolkit; multi-entry optional per §6).
4. **Re-touch existing elicit scenarios.** `tools.call.elicit.{completed,declined,timeout}.guarded`
   currently rely on the global `inactivity.timeout` for the hold (via `@Configure
   MCP_INACTIVITY_TIMEOUT`). Under 6b the hold budget is the per-request `timeout`, so these must
   carry a stamped `timeout>0` (app driver `.toolsCall()....timeout(N)`) to keep holding;
   the `timeout` (CANCELLED) case now asserts `-32042` not `-32000`. New scenarios: `timeout==0`
   immediate `-32042` (no elicitation/create); `timeout>0` expiry `-32042`; `timeout>0`
   completed-in-time proceeds. Cover McpServerIT (server emits the error to the connecting client)
   + McpClientIT (client honors the per-request timer) + peer ITs.
5. Gate with full spec + runtime `install` (jacoco/checkstyle/license).

### Phase 7b — CONVERGED DESIGN (context field) — AUTHORITATIVE (2026-06-05 maintainer session)

**This section supersedes "Phase 7 — CONFIRMED MECHANISM" and "Phase 7 — SETTLED DESIGN
CONCLUSIONS" below for the elicitation-callback routing. Those described a `<toolkit>__`
state-injection + symmetric-strip-in-the-URL mechanism — DO NOT implement that. The converged
design routes via an in-band `context` field that never enters the OAuth `state`.**

**Reframe of #1810:** retitled "per-toolkit **elicitation** for `mcp` proxy" (was "oauth").
The binding owns the MCP surface (emit/relay `elicitation/create`, route the callback, fire
`list_changed`); OAuth (token exchange/DCR/storage) lives in the guard (zilla-plus) or the
remote (Track A). Phase 8 split to **#1831** (per-client listing filter).

**Conceptual breakthroughs that drove the design (don't relitigate):**
1. An `elicitation/create` is its **own** JSON-RPC exchange, independent of the `tools/list` or
   `tools/call` that triggered it. So the originating request being **gone** (closed non-blocking
   list stream) does NOT invalidate completing the elicitation. The completion's effect is
   **authorize the route + fire `notifications/tools/list_changed`** (and *optionally* resume a
   held request) — it is NOT a response to the original request. **Correlate by `(session, route)`,
   never the request.**
2. Blocking `tools/call` "works" only because the held request stream is a live, route-connected
   path the callback rides back on (and auto-resumes) — the `elicitationId → held stream` map is
   that path. Non-blocking `tools/list` has no held stream, so there's nothing to ride. Same `(session,
   route)` target; the held stream is just an optional resume hook.
3. The server-minted `elicitationId` is minted **north of the proxy**, so the proxy never sees it
   and cannot key on it. The proxy (the only party that knows routes) needs its own breadcrumb.

**The mechanism (in-band `context`, NOT in the OAuth state):**
- IDL (LANDED, `bb3f74d6`): `string8 context = null` on `McpElicitCreateChallengeEx` (UP carrier,
  proxy→server) and `McpElicitCallbackFlushEx` (BACK carrier, server→proxy). Optional/null default,
  so non-proxy flow is byte-identical. **`context` is in-band only — it is never serialized into the
  OAuth `state`.** So the `state` stays `<sessionId>.<elicitationId>.<nonce>` (no `__` composite).
- **UP:** proxy pushes the route's **opaque prefix** (from `routeByPrefix` / the `McpAggregateEventId`
  scheme — avoids leaking toolkit config names; reuses the existing reverse map) into
  `elicitCreate.context`. (Single-route bindings have no `aggregateRoutes`/prefix → `context` null →
  proxy forwards to its sole route.) The server records `context` in its **open-elicitation record
  keyed by `elicitationId`, for ALL elicitations** (today it only stores when `timeout>0` — must
  store for non-blocking too), and renders the elicit as today.
- **BACK (callback):** server parses `state` → `elicitationId`; looks up the record:
  - **unrecognized** (forged/expired/replay/unparsable) → **410 Gone** (keep `reject.auth.callback.unknown.elicitation`);
  - **recognized + held stream** → resume it (blocking, as today);
  - **recognized + no stream** → forward via the **session/lifecycle path** + drive `list_changed`.
  The server **echoes `context` (from the record) on the forwarded `elicitCallback`** and strips its
  own `<sessionId>.<elicitationId>.` from the URL `state` (server strip already LANDED, `e665f996`),
  so the route-exit guard gets the bare nonce.
- proxy routes by the echoed `context` → `routeByPrefix` → route, **pops its segment**, forwards.
  **Proxy is stateless for elicit routing** (push on UP, route by echoed context on BACK; no map).
- **`context` stays internal** (in-band + server memory) → URL-safety is moot (only needs to fit
  `string8` and match `routeByPrefix`). Each binding strips exactly what it injected (server its
  `<sessionId>.<elicitationId>.`; proxy its `context`); route-exit/guard sees only the nonce.
- **Nested proxies (deferred):** because `context` is in-band + opaque to the server, each proxy
  can choose its own aggregation syntax for its nested segment. Not built now.

**LANDED this session (commits in the start-here block):**
- Bearer handling (`d199b467`): `McpProxyLifecycleFactory.onClientReset` bearer branch →
  `settleLifecycle` (skip + partial-open), `onClientBearerReset` deleted; `doEstablishToolkitClients`
  opens the unified reply eagerly (sessionId + config-derived `serverCapabilities`, not gated on all
  routes). Reframed `reject.bearer.toolkit.multi` → `skip.bearer.toolkit.multi` + new single-route
  `skip.bearer`; `McpProxyIT`/`McpProxyLifecycleIT` repointed.
- Server strip (`e665f996`): `McpServerFactory.stripElicitState` + `onNetEnd` strips
  `<sessionId>.<elicitationId>.` before `doAppFlushElicitCallback`. Updated the 6 non-toolkit elicit
  scenarios (callback `state` `transport-1.elicit-1.7f3a9b1c` → `7f3a9b1c`); network inbound callback
  keeps full state.
- `context` IDL field (`bb3f74d6`).

**REMAINING (ordered, test-first):**
1. ✅ **DONE+pushed (`6a3e93cb`).** `McpFunctions`: `context` builder + matcher for `elicitCreate`
   + `elicitCallback` (4 spots) + 6 `McpFunctionsTest` cases (gen/match/mismatch × 2). String8FW
   import added. McpFunctionsTest 69→75.
2. **PARTIAL.**
   - ✅ **2a DONE+pushed (`7ef4988b`): held-case context echo.** `McpRequestStream.elicitContext`
     field set from `elicitCreate.context().asString()` in `onAppChallengeElicitCreate`;
     `doAppFlushElicitCallback` gained a `context` param → `.elicitCallback(b -> b.url(u).context(c))`;
     the `McpAuthCallbackHandler.onNetEnd` held-stream caller passes `resolved.elicitContext`. New
     `tools.call.elicit.completed.context` app scenario; ApplicationIT peer +
     `McpServerIT#shouldCallToolElicitCompletedWithContext` (server echoes context). `context` null
     default → non-proxy flow byte-identical. McpServerIT 68→69; full McpServerIT+McpClientIT green.
   - ⏳ **2b NOT STARTED: non-blocking (no-held-stream) callback routing.** GROUNDED FINDINGS this
     session (read before implementing):
     * Today `session.elicitations` is `Map<String, McpRequestStream>` (held streams only); populated
       in `McpRequestStream.onAppChallengeElicitCreate` **only when `session.requestTimeout > 0`**
       (`~4482`); removed on elicitComplete (`~4645`); read in `McpAuthCallbackHandler.resolveElicitation`
       (`~2974`, parses `<sessionId>.<elicitationId>.<nonce>` from `state`).
     * The **lifecycle stream does NOT handle `ChallengeFW`** (`McpLifecycleStream.onAppMessage` `~3255`
       has no `ChallengeFW.TYPE_ID` case). The non-blocking partial-auth list elicit is relayed by the
       proxy UP the **lifecycle** (7a `McpProxyLifecycleFactory.doServerChallenge`), so the north server
       must learn to handle a `ChallengeFW` on the lifecycle stream: mint elicitationId, manipulate URL,
       record `{context, elicitUrl, held=null, session}` keyed by elicitationId, and **render
       `elicitation/create` on the lifecycle events SSE** — this is NEW net encoding (no existing encoder;
       `doEncodeElicitCreateDataEvent` `~2254` uses the request `decodedId`, not the lifecycle SSE; model
       a new encoder on `doEncodeNotifyEvent` / `LIFECYCLE_STREAM_ID_PREFIX` `~3413`).
     * Callback resolution must become: refactor `elicitations` value to a record (held may be null);
       `resolveElicitation` returns the record; held≠null → request-stream flush (2a, done) + 200;
       held==null → **flush `elicitCallback` UP the lifecycle** (new `McpLifecycleStream.doAppFlushElicitCallback`
       echoing `context`) + 200; unknown/expired → 410 (keep `reject.auth.callback.unknown.elicitation`).
     * **TTL caveat:** a lazy `expiresAt` check on resolve adds an uncovered boolean operand →
       runtime/binding-mcp **jacoco** will flag it unless exercised; testing expiry needs clock control
       (no k3po clock hook found). Plan: either a `McpServerFactoryTest` unit around an extracted helper,
       or a config with a tiny TTL + a deterministic late-callback scenario. Do NOT add the TTL branch
       without its covering test.
     * Test surface for 2b: a NEW `McpServerIT` scenario where the app downstream (proxy stand-in)
       connects the lifecycle, opens the events SSE, **issues an `elicitCreate` CHALLENGE on the lifecycle**
       (`read advise zilla:challenge` w/ `context`), the server renders `elicitation/create` on the
       lifecycle SSE to the net client, the net client GETs the callback, and the server **forwards
       `elicitCallback` UP the lifecycle** (app reads `read advised zilla:flush ...elicitCallback().context(...)`)
       + 200. Plus peer ApplicationIT.
3. Proxy (`McpProxyItemFactory` + `McpProxyLifecycleFactory`): push `context = route prefix` on the
   relayed `elicitCreate`; route by echoed `context` → `routeByPrefix` → route + pop on the
   `elicitCallback`; non-blocking callback rides the lifecycle/session path with `reauthorize` driven
   off it (new plumbing — `reauthorize` is currently only triggered from the request stream's
   `onAppFlush` in `McpClientFactory`). NOTE: the proxy challenge relay already exists
   (`McpProxyItemFactory.onClientChallenge`→`doServerChallenge`; `McpProxyLifecycleFactory.onClientChallenge`
   →`doServerChallenge`); 3 adds the `context` push there + the `elicitCallback` echo/route/pop.
4. Scenario + IT: a **purpose-built multi-route non-blocking-list-elicit** proxy scenario (one
   authorized toolkit + one needs-auth; partial list → relayed elicit → callback routes by `context`
   → reauthorize → `list_changed` → re-list full) in `McpProxyIT` + peer `ApplicationIT`.
   GOTCHA (still valid): the existing `tools.call.toolkit.elicit{,.prefixed}` scripts are ApplicationIT
   "shape" scripts and do NOT compose through the proxy — author a NEW proxy-specific scenario.

**GOTCHAS found this session (save the next session the rediscovery):**
- The existing `tools.call.toolkit.elicit{,.prefixed}` scripts are **ApplicationIT peer "shape"
  scripts and do NOT compose through the proxy** — the route-exit `/server.rpt` reads the *un-stripped*
  `bluesky__get_weather` in the request body (the proxy strips the name prefix), and the route-exit's
  challenge doesn't flow through the proxy as wired. **Author a NEW proxy-specific scenario; do not
  reuse these for a proxy IT.**
- **Blocking `tools/call` elicit through the proxy was never verified end-to-end** through the engine
  (only peer ApplicationIT). When my probe wired it as a proxy IT, the route-exit emitted `BEGIN/END/
  WINDOW` but **no CHALLENGE**. (Capability note: a target can only send CHALLENGE if the initiator
  granted CHALLENGE capability in its WINDOW — but capabilities are **ignored everywhere** in the
  binding today, so that is NOT the blocker; the cause is the peer scripts not composing, per above.)
  If blocking-through-proxy elicit coverage is wanted, it needs its own proxy-specific scenario.
- Proxy challenge relay already exists verbatim (`McpProxyItemFactory.onClientChallenge` →
  `doServerChallenge`; `McpProxyLifecycleFactory.onClientChallenge` → `doServerChallenge`, added in 7a).
  7b adds the `context` push there + the `elicitCallback` `context` echo/route/pop.

### Phase 7 — non-blocking `tools/list` + blocking `tools/call` (PLAN, design discussed 2026-06-04; NOT STARTED — see Phase 7b CONVERGED DESIGN above; this PLAN is partly superseded)

Issue #1810 §6. Two architecture decisions LOCKED this session (maintainer, john@aklivity.io):

**Decision A — defer the elicitation decision to the upstream (route-exit client binding).**
Each proxy route exits to its own south `mcp` `kind: client` binding representing a distinct
remote server; that binding already owns the auth decision for *its* upstream (guard
`NEEDS_PREAUTHORIZE → preauthorize → elicitCreate` challenge, or relaying the remote's own
`elicitation/create`). The proxy must NOT synthesize/originate elicitations — it is a
**relay/aggregator** of per-route decisions. Different routes legitimately make different
elicitation decisions. (Anti-phishing rendering — Zilla-minted `elicitationId`, callback
`redirect_uri`, `state` — still happens at the north `McpServerFactory` exactly as it does
for `tools/call` today; "defer to upstream" governs *who decides what to elicit*, not *who
renders it to the client*.)

**Decision B — encode the toolkit in the `elicitationId` so the OAuth callback self-routes.**
`elicitationId = <toolkit>__<nonce>` — `__` is already `McpRouteConfig.DELIMITER_NAME` (tool
names ship as `github__get_issue`), so it is the consistent boundary and won't collide with
the `.`-delimited callback `state`. Why this matters: `tools/list` is **non-blocking**, so the
list request stream is CLOSED by the time the OAuth callback lands — today's
`resolveElicitation` (McpServerFactory ~2952) resolves the callback by
`sessions.get(sessionId).elicitations.get(elicitationId)` → **a live held `McpRequestStream`**,
which no longer exists. Tagging the toolkit makes the callback self-routing without the held
stream: parse `__` → toolkit → route; hand `callbackUrl` to that route's guard; on completion
fire `list_changed`. The held-stream map (`session.elicitations`) drops to **optional** (only
the blocking `tools/call` resume still uses it). Invariant to assert: a toolkit prefix must not
contain `__` (`McpAggregateEventId.computePrefixes` derives unique prefixes — cheap to check).

**Division of responsibility (locked):**
- **binding** = MCP surface only: route by toolkit (strip the `<toolkit>__` prefix), emit/relay
  the per-route `elicitation/create`, fire `notifications/tools/list_changed`.
- **guard** = all security: validate the `nonce` (replay/forgery, internally or against the AS),
  bind `state` to **identity** end-to-end (embed at `preauthorize`, re-derive + validate at the
  async `reauthorize(callbackUrl)`), store the token per `(identity, route)`. Identity-binding
  (the spec MUST) lives **inside the guard**, NOT enforced earlier in the pipeline — the binding
  is never trusted with it. The guard surfaces the identity *back* to the binding on completion
  **only** so the binding knows which lifecycle SSE to address with `list_changed` (notification
  routing, not enforcement).
- **`list_changed` rides the lifecycle/events SSE** (existing `KIND_TOOLS_LIST_CHANGED` flush
  plumbing), NOT the (closed) list request stream — confirmed.

**Implementation shape (re-grep line numbers before editing):**
1. **Non-blocking list relay.** `McpProxyListFactory` already separates `hydration` from live
   (`onClientSkip` ~1371 keys off it). Hydration keeps skip+keep-stale. On the **live** path,
   instead of skip-to-next, relay the route-exit's `elicitCreate` CHALLENGE out as a per-toolkit
   `elicitation/create` while authorized toolkits' tools stream through; finalize the list
   without blocking; emit `list_changed` as each callback later lands. The route-exit list stream
   already produces the challenge — `McpToolsListStream` is a `McpRequestStream`, so
   `proceedWithRequest` runs `NEEDS_PREAUTHORIZE → preauthorize → elicitCreate` (Phase 5/6).
   Confirm the **live** `McpListClient` begin carries the **connecting client's authorization**
   (inbound long), not a cache credential, so the per-identity decision is the upstream's to make
   (hydration uses the cache credential; live must use the inbound identity).
2. **Multi-elicitation correlation.** Today correlation is single (`elicitationId` → one held
   stream). Make it per-route via the `<toolkit>__<nonce>` scheme: rework `manipulateElicitUrl`,
   the `elicitationId` supplier, and `resolveElicitation` (drop the 3-part `.`-split; parse
   toolkit from `elicitationId`, validate against configured routes, hand off to the route's
   guard). N `elicitation/create` events per `tools/list`, each its own `elicitationId`.
3. **Callback handler** (`McpAuthCallbackHandler`) collapses to: parse toolkit → route →
   `guard.reauthorize(callbackUrl, completion)`; guard validates nonce + identity internally; on
   completion fire `list_changed` to the returned identity's lifecycle SSE.
4. **Blocking `tools/call`** honors the per-request `timeout` Phase 6 added (`timeout==0` →
   `-32042`; `>0` → hold + `elicitation/create`, single-shot). Largely already in place from
   Phase 6; verify it composes with the per-route relay.
5. Test-first: spec scripts first (per-toolkit `elicitation/create` on a live `tools/list` to a
   multi-route binding with one authorized + one needs-preauth toolkit; `list_changed` after the
   callback; `tools/call` blocking honoring timeout). McpServerIT + McpProxy*IT + peer
   Network/ApplicationIT. Gate full spec + runtime `install` (jacoco/checkstyle/license).

Open questions to resolve at kickoff (none blocking the above):
- Emit all per-toolkit `elicitation/create`s up-front then one `list_changed` per authorization
  (read of §6), vs batch — go with up-front.
- Where the guard returns the identity on async completion (callback signature) — confirm the
  `GuardHandler` async `reauthorize(...,completion)` surfaces enough for `list_changed` targeting;
  if not, the binding keeps a lightweight `nonce → initiating session` note (notification routing
  only, not security).

### Phase 7 — CONFIRMED MECHANISM (maintainer, 2026-06-04 session — supersedes the IDL-field idea)

> ⚠️ **SUPERSEDED (2026-06-05) by "Phase 7b — CONVERGED DESIGN (context field)" above.** This
> `<toolkit>__`-in-the-OAuth-`state` injection + symmetric URL strip is NOT the chosen mechanism —
> routing now uses an in-band `context` field that never touches the `state`. Kept for history only.

Grounding corrected this session (verified against scripts + code):
- The **blocking** `tools/call` toolkit elicit ALREADY works via *state-preservation*: the Zilla
  `elicitationId` stays the plain nonce (`elicit-1`); the toolkit lives inside the upstream `state`
  the server preserves as the **3rd `.`-part** → manipulated state =
  `<sessionId>.<elicitationId>.<toolkit>__<upstreamNonce>` (e.g. `session-1.elicit-1.bluesky__7f3a9b1c`).
  The callback resolves through the **held** `McpRequestStream` (`session.elicitations.get(elicitationId)`),
  so the toolkit tag is NOT needed to route the blocking case.
- BUT `tools.call.toolkit.elicit{,.prefixed}` are **peer-to-peer `ApplicationIT`-only** scripts that
  **hardcode** `bluesky__` on BOTH sides (client.rpt writes it, server.rpt reads it). They validate
  the script/`McpFunctions` *shape* only — they do NOT prove the proxy injects `bluesky__`. Verified:
  the proxy does **not** inject today — `McpProxyItemFactory.McpClient.onClientChallenge` relays
  `challenge.extension()` **verbatim** to `server.doServerChallenge` (~line 800). No runtime IT exercises
  the prefixed toolkit elicit. **So the proxy-side injection IS unbuilt Phase 7 work.**

Confirmed mechanism (NOT the earlier IDL-field-on-`McpElicitCreateChallengeEx` framing — DROP that;
no IDL change):
1. **UP (elicit relay):** as the route-exit's `elicitCreate` (carrying `url?...state=<upstreamNonce>`)
   flows client→proxy→server, the **proxy** injects the toolkit into the `state` — prepend the route's
   capability prefix (`server.prefix`, already `bluesky__` for tools / `bluesky+` for resources — but use
   the `__` form for the elicit tag) to the existing `state` value → `state=bluesky__<upstreamNonce>`.
   The server then does its normal `manipulateElicitUrl` (prepends `<sessionId>.<elicitationId>.`) and
   renders `elicitation/create` to the client. Injection point: `McpProxyItemFactory` challenge relay
   (and the NEW `McpProxyListFactory` relay for the non-blocking list).
2. **BACK (callback) = SYMMETRIC PER-HOP STRIP.** Each hop *consumes and strips* exactly the `state`
   segment it prepended on UP, uses it to route/correlate, and forwards the remainder down:
   - the north `kind: server` (`McpAuthCallbackHandler`) strips its own `<sessionId>.<elicitationId>.`
     (uses it to identify the session/elicitation), then forwards the callback — now bearing
     `state=<toolkit>__<upstreamNonce>` — down its **single exit** (the proxy);
   - the **proxy** strips its `<toolkit>__` (the 3rd-part-prefix up to the first `__`), uses it to **pick
     the route**, and forwards the callback — now bearing the original `state=<upstreamNonce>` — down to
     that route-exit;
   - the **route-exit `mcp` client binding** receives its original `<upstreamNonce>` and runs the Phase-5
     `reauthorize(callbackUrl)` itself.
   **There is NO guard at the proxy route** (the proxy only *routes*; the per-route guard at the proxy is
   the *cache* guard only). The OAuth `reauthorize` is owned by the route-exit `mcp` **client** binding
   (`McpClientFactory`, where the Phase-5 `NEEDS_PREAUTHORIZE → preauthorize → elicit → reauthorize`
   machinery already lives). On completion the **proxy** fires `notifications/tools/list_changed` up the
   lifecycle SSE for that `sessionId` (the proxy owns the `McpLifecycleServer`, so no separate
   binding-side note is needed for routing OR notify targeting).
3. **Zilla `elicitationId` (2nd part) stays the plain nonce.** The toolkit is purely in the proxy-owned
   3rd-part prefix. Blocking `tools/call` is unchanged (held stream routes it; toolkit tag rides along).
   NOTE: under symmetric strip the anticipatory `tools.call.toolkit.elicit.prefixed/server.rpt` (~line 72,
   route-exit reading the UN-stripped `...bluesky__7f3a9b1c`) is WRONG and must be updated to the
   fully-stripped `state=...7f3a9b1c` when implementing.
4. **Non-blocking list (the headline):** `McpProxyListFactory` returns authorized toolkits' tools and
   finalizes WITHOUT blocking; for each unauthorized toolkit it relays an `elicitation/create` (UP per §1);
   on each callback (BACK per §2) the proxy reauthorizes that route and fires `list_changed`. The
   live `McpListClient` begin must carry the connecting client's inbound authorization (not a cache
   credential) so the per-identity decision is the upstream's.

### Phase 7 — SETTLED DESIGN CONCLUSIONS (consolidated, maintainer Q&A 2026-06-04)

> ⚠️ **SUPERSEDED (2026-06-05) for the callback-routing mechanism by "Phase 7b — CONVERGED DESIGN
> (context field)" above.** Conclusions B/C/E (route-exit owns `reauthorize`; OSS-vs-plus token homes;
> URL-mode-only timeout) still hold; the `<toolkit>__` state-injection/strip specifics do not.

These refine/augment the mechanism above and are all DECIDED (not open):

A. **No IDL change** — for the toolkit tag (use `state` injection, §1) AND for the elicit origin
   (see D). Drop both the `<toolkit>__<nonce>`-elicitationId idea and the `McpElicitOrigin` discriminator
   idea.

B. **`reauthorize` + `list_changed` ORIGINATE at the route-exit `mcp` client; the proxy only ROUTES +
   RELAYS.** The transition unauthorized→authorized happens inside the route-exit client's `reauthorize`,
   so it is the only party that knows "my toolkit's listing changed" — it emits `KIND_TOOLS_LIST_CHANGED`
   up its lifecycle reply; the proxy aggregates/relays it up the session lifecycle SSE (same path it
   already relays an upstream-native `notifications/tools/list_changed`). Proxy never synthesizes the
   notification (consistent with Decision A: proxy defers to upstream). `tools/list_changed` carries no
   params, so one from any route just tells the client "re-list" — double-firing (route + native) is a
   harmless idempotent re-list; no dedup needed.

C. **OSS-vs-plus split for per-user auth.** OSS ships only `guard-identity` + `guard-jwt`; NEITHER
   originates the OAuth redirect (`preauthorize` → authorize URL) or exchanges/caches a per-identity token
   (`reauthorize(callbackUrl)`). The interactive OAuth-code-flow guard is a **zilla-plus** component
   (`guard-azure-ad` / `guard-aws-lambda` / `guard-api-keys`). Credential homes: shared/baseline list
   credential → `McpProxyCache` store (hydrated with `with.cache.credentials` → fallback
   `options.cache.authorization`); per-identity OAuth token → INSIDE THE GUARD keyed by the session
   (`reauthorize` stores, `credentials(authorization)` retrieves) — NEVER the shared store (cross-user
   leakage). So the **guard-driven** per-user flow needs zilla-plus.
   Token-home by model (do not conflate):
   - **guard-driven (zilla-plus):** callback relayed to route-exit client → `guard.reauthorize(...,
     callbackUrl, completion)` (`McpClientFactory:2784`); the OAuth guard (e.g. `guard-azure-ad`, NOT
     `guard-identity`) does the async code→token exchange off-reactor and stashes the token keyed by
     `authorization`/sessionId; subsequent same-session requests inject it via `guard.credentials(...)`
     (`:2630`,`:2817`). Binding never blocks — guard does `sendAsync`→`signalAt`. Token lives in Zilla's guard.
   - **OSS relay (example):** no Zilla OAuth guard runs; Zilla rewrites `redirect_uri`→its callback and
     relays the callback UP to the remote (`elicitCallback` flush); the REMOTE is the OAuth client,
     exchanges code→token, and holds it keyed by its MCP session; subsequent requests ride the same
     upstream session (already authorized) — token NEVER lives in Zilla. (Or client carries a bearer and
     Zilla relays the `Authorization` header.)

C2. **"Zilla remembers a per-remote token" ⟺ "Zilla IS the OAuth client for that remote" — so it requires
   a per-route OAuth guard (zilla-plus); the binding cannot capture the token in the pure-relay case.**
   Only the OAuth client receives the token from the code→token exchange. In pure relay the REMOTE is the
   OAuth client (its own `client_id`), so the binding structurally cannot "process the callback directly"
   to capture credentials — doing the raw exchange in the binding would reinvent a guard on the hot path
   (blocking I/O). Two distinct MCP mechanisms (do not merge):
   - **(a) Upstream auth (RFC 9728 / MCP authorization):** remote returns `401`+`WWW-Authenticate`
     w/ `resource_metadata`; the MCP *client* (Zilla+guard) discovers the AS, (dynamically) registers,
     runs auth-code, holds the token, attaches bearer on later calls. Zilla SURFACES it to the connecting
     client as a URL `elicitation/create`. **Token held by Zilla's guard.** Phase-1 `resource_metadata`
     capture is the discovery hook. ← this is the mechanism that meets the "remember per-remote" goal.
   - **(b) Elicitation (SEP-1036 URL elicitation):** remote asks the user for an out-of-band URL action
     the REMOTE processes. **Token held by the remote.** ← relay model; Zilla cannot remember.
   Behavioral fork (matches the maintainer's instinct): the discriminator is **"did Zilla MINT the
   elicitation (`guard.preauthorize`, a) or RELAY it from the remote SSE (`onDecodeElicitCreate`, b)?"** —
   tracked per-elicitation in binding state (no IDL discriminator). Minted ⇒ `guard.reauthorize` (Zilla
   remembers); relayed ⇒ forward callback up to remote (remote remembers).
   Scaling design (meets goal): model the memory as a **per-route OAuth guard** — per-route = per-remote
   partitioning automatically; cache keyed by `authorization` (connecting-client identity, established at
   north, flowed down); `credentials(authorization)` re-presents on every subsequent request so the
   connecting client presents ONLY its own identity, never the per-remote tokens; back the per-worker map
   with a referenced `Store` (e.g. `store-redis`) for cross-worker/replica + durability. Effective vault
   key = (route≈remote, identity) → token, owned by the guard. Inherently zilla-plus; OSS pure-relay
   cannot remember (Zilla isn't the OAuth client there).

C3. **OSS Path B — relay/passthrough concrete mechanism (chosen OSS story; remote holds token).**
   Three parts make it work; the §1–2 state-injection/symmetric-strip machinery is NOT needed here (that's
   guard-model only — the callback never returns through Zilla):
   1. **Origin-conditional passthrough of the elicitation URL.** For a **remote-originated** `elicitCreate`
      (binding knows origin per-elicitation: `onDecodeElicitCreate` `:1357` vs guard mint `:2655`),
      SUPPRESS `manipulateElicitUrl` entirely — pass the URL **verbatim** (do NOT rewrite `redirect_uri`
      AND do NOT rewrite `state`). Rewriting `state` alone would break the remote's own callback
      correlation. The AS then redirects the browser straight to the REMOTE's callback; the remote
      exchanges the code and binds auth to ITS `Mcp-Session-Id`. Anti-phishing *rendering* may still occur
      (rendering ≠ rewriting). `manipulateElicitUrl` (`McpServerFactory:5154`/called `:4476`) must become
      origin-conditional (today it rewrites unconditionally).
   2. **Persistent upstream session + header replay = the actual "memory."** Route-exit client keeps ONE
      persistent upstream session per (route≈toolkit, connecting `sessionId`), captures the remote's
      `Mcp-Session-Id` from `initialize`, and replays `Mcp-Session-Id` + `MCP-Protocol-Version`
      (`HTTP_HEADER_SESSION="mcp-session-id"` `:118`, `HTTP_HEADER_MCP_VERSION="mcp-protocol-version"`
      `:122`) on EVERY subsequent request; resume on reconnect via `Last-Event-ID`. Map shape:
      `connecting sessionId → { toolkit → remote Mcp-Session-Id }`. Store-back for cross-worker/replica +
      durability. This is the part to verify/build (binding already has a session map + the header consts).
      The connecting client presents ONLY its own identity — never a per-remote token.
      **Lifecycle-persistence invariant (maintainer 2026-06-04):** once a per-route `McpLifecycleClient`
      (upstream session to a remote) is active for a connecting client it MUST remain active — retaining
      its `Mcp-Session-Id` — for the entire UNIFIED north session (`McpLifecycleServer`), NOT opened/closed
      per request. Establish it lazily on first use of the toolkit; per-request streams (`tools/list`,
      `tools/call`) reuse it; release the route sessions only when the unified session ends. Tearing a
      route's upstream session down between requests loses the remote's session-bound auth ⇒ re-elicitation
      every call. The persistent lifecycle is also what lets a later native `list_changed` ride up.
   3. **No Zilla hold/timeout; recovery is client-driven REPLAY.** Surface the elicitation and return
      promptly; the client completes OAuth out-of-band, then RE-ISSUES the original request. Since the
      session is now authorized, the replay is just another subsequent request and succeeds. Safe to
      replay because the original returned the elicitation *instead of executing* (no side effect); the
      client (not Zilla) drives the retry, so Zilla buffers nothing. (Per E: timeout/`-32042` are
      guard-model only.)
   `list_changed` after auth is emitted natively by the remote and relayed up (conclusion B). Limits:
   token lifetime tied to the remote session; depends on the remote supporting session-bound auth +
   resumption. Full parity (Zilla holds tokens) is Path A (a generic community-licensed OAuth guard).
   **Scales to N remotes each requiring elicitation (maintainer 2026-06-04):** per-route isolation (own
   lifecycle, own `Mcp-Session-Id`, own elicitation, own native `list_changed`) + callbacks going DIRECT
   to each remote (so each self-correlates via its OWN `state` — no shared Zilla correlation namespace, no
   cross-talk between concurrent elicitations) means N remotes coexist with no collision. Client authorizes
   each in any order and replays each toolkit's request on its own authorized session; a non-blocking
   `tools/list` surfaces one elicitation/create per unauthorized remote, returns public tools immediately,
   and fills in progressively as each remote's native `list_changed` is relayed. Only cost is resource:
   one persistent upstream session per actively-used remote per connecting client for the unified session.

D. **Cached-proxy per-user `*/list` needs the Phase-8 hybrid serve.** `McpProxyListFactory.newStream`
   today is cache-XOR-live: with a cache it serves the shared baseline ONLY (`McpCacheListServer`); the
   live path (`McpListServer`, no cache) fans out per-identity using the inbound authorization. So a
   newly-authorized per-identity toolkit appears on re-list ONLY if (i) the route is cache-less (live
   per-identity fan-out works in OSS today) OR (ii) Phase 8 adds the hybrid "cached baseline ∪ live
   per-identity merge". Phase 7 delivers the SIGNALING (elicit + list_changed); it is NOT sufficient for
   cached-proxy per-user listing on its own.

E. **Elicitation timeout — URL-mode ONLY for now (form deferred).** Because both the guard-triggered and
   the remote-server-triggered elicitations are URL mode, they have IDENTICAL hold + expiry semantics
   (hold the in-progress request up to `timeout`; on expiry emit `-32042 URLElicitationRequiredError`).
   So the binding does NOT need to distinguish origin, and NO discriminator/IDL change is needed. (Proven:
   both origins collapse to the same `McpChallengeEx.elicitCreate{id,url}` — guard mint at
   `McpClientFactory:2655`, remote SSE decode at `:1357`→`:3070`; server `:4470` sees no origin field.)
   SCOPE (maintainer pushback 2026-06-04): the hold/timer + `-32042` are **GUARD-MODEL ONLY** (Phase 6,
   already shipped on the `preauthorize` branch `:2655`). The **relay/passthrough model needs NO Zilla
   timer** — see C3: in passthrough Zilla is a transparent relay holding no buffered per-elicitation state,
   the REMOTE owns its request's lifetime, and recovery is client-driven retry on the authorized session
   (not a Zilla-side hold). Do NOT arm a timer on the remote-relayed `:1357` path. Revisit
   origin-discrimination only when form-mode lands.

### Phase 7a — SHIPPED (2026-06-04, this branch — non-blocking partial-auth proxy list + relay elicit + relay native list_changed)

Track A core landed. Runtime change is small and confined to `McpProxyLifecycleFactory`:
- **Partial-auth lifecycle open.** A route-exit that needs authorization now advises an
  `elicitCreate` CHALLENGE (reply not opened, no sessionId) instead of bearer-resetting. New
  `McpLifecycleClient.onClientChallenge` (wired into `onClientMessage` via `ChallengeFW.TYPE_ID`)
  (1) marks the route settled via the new `settleLifecycle` guard (one-shot `settled` flag) so
  `pendingClients` counts it down and the unified north lifecycle OPENS on the authorized subset,
  and (2) relays the elicitation up the north lifecycle SSE via the new
  `McpLifecycleServer.doServerChallenge` (CHALLENGE on the server `initialId`, mirrors
  `McpProxyItemFactory.doServerChallenge`). `onClientBegin` now also settles via `settleLifecycle`.
  The all-or-nothing `onClientBearerReset` path is UNCHANGED (genuine bearer reset still
  resets the whole session — `reject.bearer.toolkit.multi` still green).
- **List skips the challenged route deterministically.** `McpLifecycleClient.register` now keys
  off `settled` (was `replyOpened && sessionId != null`), so a challenged-but-unauthorized route
  (settled, sessionId null) settles immediately as a SKIP in the list fan-out instead of blocking
  forever on a reply that never comes. The already-non-blocking list serves the authorized subset.
- **Native `list_changed` relay** rides up unchanged via the existing aggregate-event-id path
  (`onClientFlush` → `doServerFlush`); when the unauthorized route later authorizes (sends its
  reply BEGIN + native `toolsListChanged`), the proxy relays it up (aggregate id `2=200` when only
  that route has fired) and a re-list returns the full set.

Spec contract finalized (the anticipatory proxy scripts needed three corrections, all consistent
with the persistence invariant + the relay design; peer ApplicationIT stays green):
- `tools.list.partial.toolkit.multi.prefixed/{client,server}`: added the relayed `elicitCreate`
  challenge assert/emit (the proxy-view north MUST observe the surfaced elicitation; was missing).
- `tools.list.partial.toolkit.multi/{client,server}`: removed the premature app2 lifecycle close
  (`write close`/`read closed`) — the unauthorized route's lifecycle STAYS OPEN per the N2
  persistence invariant; it is not torn down between requests.
- `lifecycle.notify.tools.list.changed.after.authorize.toolkit.multi.prefixed/client` +
  `.../after.authorize.toolkit.multi/server`: added barriers so app2 authorizes only AFTER the
  first list completes (`write await FIRST_LIST_DONE` on the server; second list gated on a new
  `LIST_CHANGED` barrier set when the north observes the relayed flush) — makes "first list =
  authorized subset, second list = full set after authorize" deterministic; fixed the expected
  aggregate id to `2=200` (app1 fires no list_changed here, so no `;S=100` component).

ITs: `McpProxyIT#shouldInitializeLifecyclePartialToolkitMulti`,
`shouldListToolsWithPartialToolkitMulti`, `shouldNotifyToolsListChangedAfterAuthorizeToolkitMulti`
(McpProxyIT 44→47). Full no-skip green: runtime binding-mcp 212 ITs + 26 UTs + jacoco/checkstyle/
license; spec binding-mcp.spec 199 ITs (ApplicationIT 105) + UTs + gates.

**Remaining Phase 7 work = 7b** (Track B guard hooks: `<toolkit>__` state inject-on-UP + symmetric
strip-on-BACK + Zilla-in-callback routing + guard-driven `list_changed` origination). Not started.

### Phase 7a — GROUNDED FINDINGS (2026-06-04, re-verified against code; CORRECTS the implementation shape above)

Phase 7a (Track A non-blocking live `tools/list` + relay elicit + relay native `list_changed`) is BIGGER than
"add a challenge relay to `McpProxyListFactory`". Verified current behavior:
- **The live list already non-blocks.** `McpProxyListFactory.McpListServer.onClientSkip` (`:1371`): on `hydration`
  → abort; on LIVE → `onNextClient` (continue aggregating). So partial aggregation across routes already works.
- **An unauthorized route surfaces at the per-route LIFECYCLE, not the list stream.** `McpListClient.onLifecycleSettled`
  (`McpProxyListFactory:291`) skips a route precisely when its per-route `McpLifecycleClient.sessionId == null`
  (`:299` → `:317 server.onClientSkip`). The list stream itself never sees a challenge.
- **THE BLOCKER: lifecycle establishment is all-or-nothing.** When a route-exit replies with a bearer RESET
  (upstream needs auth) before the north reply opens, `McpLifecycleServer.onClientBearerReset`
  (`McpProxyLifecycleFactory:505`) `doServerReset`s the ENTIRE unified north lifecycle with that bearer extension
  and aborts every other route client (`:512-521`, guarded by `!McpState.replyOpened(state)`). So ONE unauthorized
  route currently fails the whole proxy `initialize`. Bearer reset reaches there via `McpLifecycleClient.onClientReset`
  `:1108` (`bearer = extension.sizeof() > 0` `:1129` → `:1134 server.onClientBearerReset`).
- **Relay primitives already exist:** north→client challenge `McpLifecycleServer.doClientChallenge`
  (`McpProxyLifecycleFactory:904`, today used for RESUME); native `list_changed` relay up the lifecycle SSE via
  `extractEventId :1150` + `injectFlushEx :1178` (`KIND_TOOLS_LIST_CHANGED`); N1 verbatim elicit-URL passthrough
  in `McpServerFactory.manipulateElicitUrl` (placeholder `replace.me` gate, `:5155`/called `:4477`).

**Core 7a work = restructure the lifecycle aggregation to allow PARTIAL auth** (let the unified lifecycle OPEN
when ≥1 route settles / a zero-auth baseline exists; for each unauthorized route relay its elicitation/create UP
the north lifecycle SSE instead of resetting the whole session), then the already-non-blocking list serves the
authorized subset and native `list_changed` rides up as routes authorize. This changes `onClientBearerReset` from
"reset-all" to "mark-route-unauthorized + relay-elicit + keep-going", and gates north-reply-open on partial rather
than total settlement. Touches `McpProxyLifecycleFactory` (aggregation + elicit relay) primarily, NOT mainly
`McpProxyListFactory`.

**Test-first plan (first PR-able increment):**
1. Spec scenario(s), application + network: live `initialize` to a 2-route proxy, route A authorized (settles,
   `session-1a`) + route B bearer/elicit (relay `elicitation/create` up the north lifecycle SSE); unified lifecycle
   OPENS (`session-1`); `tools/list` returns A's tools only; then route B authorizes → native `list_changed`
   relayed up → re-list returns A∪B. Closest templates: `tools.list.toolkit.multi` (live 2-route list),
   `cache.hydrate.toolkit.multi.skip.unauthorized` (skip shape), `tools.call.elicit.passthrough` (N1 verbatim URL),
   `lifecycle.notify.tools.list.changed.toolkit.multi` (list_changed). NEW scenario dir needed — none covers
   live partial-auth lifecycle + elicit relay today.
2. Confirm RED against current code (all-or-nothing reset fails the partial-open assertion).
3. Implement the aggregation restructure; McpProxyIT + McpProxyLifecycleIT + peer Network/ApplicationIT; gate full
   spec + runtime `install`.

**DECIDED (maintainer 2026-06-04):** (1) **Open partial; empty baseline if all routes fail** — the unified
lifecycle always opens, serving whatever authorized subset exists (possibly empty), never bearer-resetting the
whole session. (2) **The elicitation is SOURCED at the route-exit `mcp client` binding, NOT minted by the proxy.**
The proxy only RELAYS the route-exit's `elicitCreate` CHALLENGE up the north lifecycle SSE (via `doClientChallenge`
`McpProxyLifecycleFactory:904`). Consistent with Decision A (proxy = relay/aggregator; it never originates
elicitations). So in a proxy IT the route-exit (app-side script standing in for the `mcp client` binding's app
face) emits the `elicitCreate` CHALLENGE on its lifecycle reply instead of settling with a sessionId; the proxy
relays it up and marks that route unauthorized (skips it in the list) while keeping the session open.

**SPEC CONTRACT AUTHORED + peer-green (2026-06-04, in tree → committed).** New scenarios under
`specs/.../streams/application/`: `lifecycle.initialize.partial.toolkit.multi` (proxy-only: app1 settles
`session-1a` NOT aborted; app2 relays `elicitCreate`; north opens `session-1` + reads relayed elicit),
`tools.list.partial.toolkit.multi{,.prefixed}` (peer-tested: authorized route A returns tools, route B skipped),
`lifecycle.notify.tools.list.changed.after.authorize.toolkit.multi{,.prefixed}` (proxy-only: route B authorizes
out-of-band → native `list_changed` relayed up → re-list returns A∪B). No `McpFunctions` changes needed
(`challengeEx.elicitCreate`/`matchChallengeEx`, lifecycle/toolsList begin, `toolsListChanged` flush all exist).
Challenge verb direction: accept-side `read advise zilla:challenge` (builder) ↔ initiator-side
`write advised zilla:challenge` (matcher) — mirror `tools.call.toolkit.elicit`. Peer ApplicationIT methods added:
`shouldListToolsWithPartialToolkitMulti{,Prefixed}` (ApplicationIT 103→105, full spec module green). The
lifecycle/notify scenarios are proxy-only (no self-consistent peer pair — matches `reject.bearer.toolkit.multi`
precedent). **Minor TODO at impl: the relay elicit url reuses the `replace.me` placeholder; a pure relay carries
the remote's real `redirect_uri` verbatim — tidy when wiring (harmless, proxy relays bytes unchanged).**

**Proxy-IT RED targets to wire ALONGSIDE the runtime change** (do NOT add before — they fail today and break CI).
Add to `McpProxyIT` (already has `.external("app1").external("app2")` + `@Configuration("proxy.toolkit.multi.yaml")`):
1. `shouldInitializeLifecyclePartialToolkitMulti` → `${app}/lifecycle.initialize.partial.toolkit.multi/{client,server}`
2. `shouldListToolsWithPartialToolkitMulti` → `${app}/tools.list.partial.toolkit.multi.prefixed/client` + `${app}/tools.list.partial.toolkit.multi/server`
3. `shouldNotifyToolsListChangedAfterAuthorizeToolkitMulti` → `${app}/lifecycle.notify.tools.list.changed.after.authorize.toolkit.multi.prefixed/client` + `.../after.authorize.toolkit.multi/server`
(Mirror the existing `shouldRejectLifecycleInitializeWithBearerChallengeToolkitMulti` / `shouldNotifyToolsListChangedWithAggregateEventId` cross-pattern.)

### #1810 BROADENED SCOPE + DONE-vs-NEEDED AUDIT (maintainer 2026-06-04)

Broaden #1810 to cover BOTH tracks (A = OSS relay; B = Zilla-managed per-toolkit OAuth). Audit below
classifies every phase as still-required / no-longer-required / new, given the evolved design.

**DONE & STILL REQUIRED (no change):**
- **P1** resource_metadata capture/re-render — both tracks (AS discovery for B; surfacing the remote's
  auth requirement for A). Keep.
- **P2 / 2e** hydrater split + keep-stale + jacoco — cache infra for the shared/public baseline list;
  orthogonal to auth track. Keep.
- **P4** protocol `2025-11-25` + `elicitation.url` negotiation — fundamental to surfacing URL elicitation
  in both tracks. Keep.
- **P5** guard preauthorize→reauthorize **+ Gap A/B** — Track B core. NOTE Gap A (inbound MASK_AUTHORIZED
  reuse) is the LINCHPIN of client-driven replay in Track B (a re-issued request after auth succeeds
  via `credentials(authorization)`), so it is MORE important now, not less. Keep.
- **P9** preauthorize→elicit→callback→reauthorize ITs — Track B coverage. Keep.

**DONE & KEPT BY DESIGN (design evolved but retained deliberately):**
- **P6 hold-and-resume (`timeout>0`: body buffer + signal resume).** The client-driven REPLAY pattern
  (return promptly → client authorizes → client re-issues → Gap A makes the re-issue succeed) works for
  BOTH tracks, so the hold is **additive UX** (request resumes automatically, no client re-issue round-trip)
  rather than strictly required. **Maintainer decision (2026-06-04): KEEP it** as a deliberate UX
  differentiator. `timeout==0`/`-32042` remains the portable default; the hold is the opt-in enhancement.
  So nothing already-done is being removed.
- **P3 per-route `with.cache.credentials`** — not obsolete; may retire later if a single
  `options.cache.authorization` + token-exchange-capable guard supersedes per-route cache creds.

**NOT STARTED & STILL REQUIRED (re-scoped):**
- **P7a (Track A, OSS)** — slim: origin-conditional passthrough relay of the remote's elicitation +
  non-blocking `tools/list` that returns public tools immediately + relay the remote's NATIVE
  `list_changed`. NO state tag/strip, NO Zilla-in-callback. (Depends on N1+N2 below.)
- **P7b (Track B, guard)** — the heavy part: §1–2 `<toolkit>__` state inject on UP + symmetric strip on
  BACK + Zilla-in-callback routing to the right route's guard + `list_changed` origination on reauthorize.
  §1–2 IS required for B's NON-BLOCKING-list callback routing (closed stream ⇒ no held-stream correlation ⇒
  must route by toolkit-in-state). Blocking `tools/call` already works (held stream).
- **P8 per-client listing filter (SEP-1488)** — Track B / future optimization. NOT required for the OSS
  example (Track A gets per-identity listing free from the remote on cache-less routes). Keep as future.
- **2d live-path baseline test** — still relevant (folds into P7a/P8 testing).

**NEW — Track A OSS critical path (ADD to broadened #1810):**
- **N1 — origin-conditional passthrough: DONE (2026-06-04, branch claude/kind-wright-P3p6I).**
  Mechanism (maintainer-chosen): the `redirect_uri` **placeholder** (`replace.me`) is the origin/OAuth-client
  signal — no IDL field, no per-stream origin tracking. `manipulateElicitUrl` (`McpServerFactory`) now gates
  BOTH the `state` and `redirect_uri` rewrites on the original `redirect_uri` value containing the placeholder:
  present → Zilla is the OAuth client, rewrite (gateway/existing behavior, unchanged); absent → remote is the
  OAuth client, **verbatim passthrough** (state + redirect_uri untouched). Security rationale: redirect_uri
  registration + `state` (CSRF) + PKCE are all bound to the OAuth client, so always-via-Zilla breaks the
  remote-client case — placeholder gate = correct AND secure. Tests: `tools.call.elicit.passthrough`
  (network+application .rpt) + IT methods in McpServerIT/NetworkIT/ApplicationIT. All elicit ITs green
  (Server 68 / Client 61 / Proxy 44); checkstyle clean. Open follow-up: placeholder-trust (a malicious
  upstream sending the `replace.me` host to coax Zilla into the callback path) — belongs to the gateway/Track-B
  callback path, not this additive passthrough.
- **N2 — persistent per-route lifecycle + `Mcp-Session-Id`/`MCP-Protocol-Version` replay + resume. DONE+pushed
  (2026-06-04, branch claude/kind-wright-P3p6I).**
  **FINDING: N2a/N2b were already implemented in `McpClientFactory`** — explore summary was wrong; verified in
  code: upstream `Mcp-Session-Id` captured at `onNetBegin :2371` → `remoteSessionId` on the persistent
  `McpLifecycleStream`; `MCP-Protocol-Version` captured `:4135` → `negotiatedVersion`; both replayed via
  `transportSessionId() :2150` / `protocolVersion() :2156` on every subsequent request encoder
  (`HttpNotifyInitialized :4190`, `HttpKeepalive :4230`, request `:4207`); session kept warm by keepalive ping
  (`scheduleKeepalive :2501`), torn down only at `doAppTerminate :2526`. Existing tests MASKED this by reusing
  `session-1` on BOTH the app and transport sides.
  **Locked in by a face-based session-id naming convention swept across the ENTIRE binding-mcp test corpus**
  (so every boundary remap is asserted, not hidden):
  - **net face** (HTTP/transport) = `transport-N`; **app face** (application/unified) = `session-N`.
  - **Server**: generates the transport id (`supplySessionId`, affinity-aligned), takes the app/unified id from
    downstream. **Client**: captures the upstream transport id, generates its own app session id. OAuth callback
    `state` prefix carries the transport/`sessions`-key id.
  - **Proxy** (`McpProxyLifecycleFactory`, verified): MINTS its own north app id (`supplySessionId`/`newSessionId`
    `:186`, called `:171`, replied north `:544`) = `session-1`, and CAPTURES each route-exit's app id per route
    (`McpLifecycleClient.sessionId :1024`) replaying it on that route's fan-out. The captured per-toolkit exits
    are named **`session-1a` / `session-1b`** to convey they are the per-route children of the aggregate north
    `session-1`; the `.prefixed` (north/aggregated) view stays `session-1`.
  - The earlier dedicated `lifecycle.initialize.session.affinity` lock-in scenario was REMOVED as redundant once
    `lifecycle.initialize` itself carried `transport-1`/`session-1`.
  **N2c (resume / `Last-Event-Id`) — COVERED**, not just "to assess": the resume paths (`lifecycle.events.resume`,
  `tools.call.with.progress.resume`, and the multi-toolkit `lifecycle.events.resume.aggregate`/`.partial`) now
  carry distinct transport/session ids end-to-end, so resume affinity is asserted. **N2d (Store-backing)
  deferred** — session state is per-worker and same-session streams are worker-affine by design (session id
  drives stream affinity), so a Store is redundant for correctness; only needed for cross-restart/multi-replica
  durability (filed as follow-up). Full real-upstream affinity is exercised next by N3.
  **Commits** (this branch): `46568507` server+client face-based convention (163 files); `924bdc6c` remove
  redundant affinity scenario; `e848c3ce` proxy `tools.list.toolkit.multi` per-toolkit; `e26b0bde` remaining
  proxy multi scenarios + ApplicationIT peer fix; `975356e2` rename per-toolkit exits → `session-1a`/`session-1b`.
  **Full no-skip green: spec 197 ITs + 76 UT; runtime 209 ITs + 26 UT (jacoco/checkstyle/license/notice pass).**
- **N3 — the OSS `mcp-proxy` example itself** (zilla.yaml + demo + ITs) demonstrating elicitation +
  auth-guarded list/call via relay, scaling to N remotes. **SPLIT OUT of #1810 (maintainer 2026-06-04)** —
  the e2e example can ship as a separate effort against real upstreams; it does NOT gate the binding PR.

**PR DELIVERABLE = a DUAL-READY mcp BINDING (maintainer 2026-06-04).** When this PR merges, the binding
must support BOTH the OSS-relay example and the zilla-plus guard example — even though only the **OSS
example ships first**. The mcp binding lives in zilla OSS and zilla-plus builds on it, so ALL binding-level
hooks for both tracks ship in OSS in this PR; only the **OAuth-client guard implementation is zilla-plus**
(separate repo, later). Concretely:
- **In this PR (binding, zilla OSS):** Track A — **N1 + N2 + P7a**; Track B binding hooks — **P7b** (§1–2
  state inject/strip + Zilla-in-callback routing + `list_changed` origination) on top of the already-done
  **P5** (preauthorize/reauthorize orchestration) and **P6** (timeout/-32042). I.e. P7b is IN scope now
  (binding readiness), NOT deferred — the guard must be able to drive it later with no binding changes.
- **NOT in this PR:** the zilla-plus OAuth-client guard (`guard-*`), and the zilla-plus example (built
  later against the dual-ready binding). P8 (per-client filter / cached+per-identity hybrid) stays future
  unless the plus example needs cached listing (cache-less plus routes avoid it).
- **First example shipped: OSS relay (N3)** — zilla.yaml + demo + ITs, Track A only. **NOTE: N3 is now
  split OUT of #1810 (maintainer 2026-06-04)** — it ships as a separate effort and does not gate the binding PR.
- **#1810 issue tracking (decided 2026-06-04): do NOT edit the issue body or the existing 2026-06-04
  broadening comment.** The body (Model B / gateway-managed design) + that comment (Model A relay, 7a/7b
  split, N1/N2/N3) already capture the scope. Fold the STATUS DELTAS into the **PR description** when the PR
  opens (NOT a new issue comment): N1+N2 DONE; N3 split out (separate effort); remaining #1810 binding work =
  Phase 7 (7a non-blocking `tools/list` + relay native `list_changed`; 7b gateway-managed `state`
  inject/strip + callback routing + `list_changed` origination); P8 + zilla-plus OAuth guard stay future.
- **Orthogonal simplification (still confirm):** P6-hold removal vs client-replay is independent of
  dual-readiness — it does not block either example; decide separately. If confirmed, it is the one piece
  of ALREADY-DONE work the evolved design makes redundant.

### #1810 PHASING IMPLICATIONS of the OSS Path B understanding (maintainer 2026-06-04)

The relay understanding BIFURCATES #1810 into two tracks; most of the current plan is Track B.
- **Track A — OSS relay:** remote is the auth authority; token session-bound at the remote; callback goes
  DIRECT to the remote (Zilla out of the callback loop); works in pure OSS, no OAuth guard.
- **Track B — guard-driven:** Zilla is the OAuth client; token in Zilla's guard per (route, identity);
  callback returns THROUGH Zilla; needs an OAuth-client guard (zilla-plus, or the Path-A new OSS guard).

Mapping of existing phases:
- **Phase 5** (guard preauthorize→reauthorize) → **Track B only**; OSS relay never calls it.
- **Phase 6** (`timeout` hold + `-32042`) → **Track B only**; Path B is client-driven replay, no Zilla hold
  (conclusion E correction) — NOT on the OSS critical path.
- **Phase 7 splits:** the **§1–2 state-injection / symmetric-strip / Zilla-in-callback routing → Track B
  only** (only needed when the callback returns through Zilla). The **non-blocking `tools/list` +
  `list_changed`** is needed by both, but Track A's version is much smaller: passthrough the remote's
  elicitation, relay the remote's NATIVE `list_changed` — no tag/strip. Call it **Phase 7a (OSS relay)**
  vs **Phase 7b (guard)**.
- **Phase 8** (per-client filter / cached-baseline∪per-identity hybrid) → **Track B / optimization**;
  Track A gets per-identity listing free from the remote on **cache-less routes**. Not required for the
  OSS example.

Two genuinely-NEW items the OSS example needs (NOT in any current phase) — the OSS critical path:
1. **Origin-conditional passthrough** — suppress `manipulateElicitUrl` for remote-originated elicitations
   (verbatim URL; see C3.1). Small, essential.
2. **Persistent per-route lifecycle + `Mcp-Session-Id`/`MCP-Protocol-Version` replay + resume, Store-backed**
   (see C3.2 + the lifecycle-persistence invariant). Partly present (session map); durable affinity to build.

NET: the OSS example deliverable can ship WITHOUT Phases 5, 6, the §1–2 part of 7, and Phase 8 (all Track
B / zilla-plus). OSS critical path = **(new) passthrough + (new) persistent session affinity + slim Phase
7a non-blocking-list relay + native `list_changed` relay**. SCOPING QUESTION for #1810: its title is
"per-toolkit **oauth** for mcp proxy" = literally Track B (Zilla-managed OAuth). Decide whether the OSS
relay example is in-scope for #1810 (add the two new Track-A items) or split to a separate
"OSS mcp-proxy elicitation example" issue, with #1810 remaining the Zilla-managed-OAuth (Track B) work.

### Phase 7 — how the OSS mcp-proxy EXAMPLE demonstrates elicitation + auth-guarded list & call

Goal: show URL elicitation AND an auth-guarded `tools/list` + `tools/call` working in **pure OSS** (no
zilla-plus OAuth guard). Key: the **remote MCP server is the auth authority** and **originates** the URL
elicitation; Zilla proxy RELAYS the elicitation down and the client's bearer up — Zilla is a transparent
credential relay, not the OAuth boundary (contrast: zilla-plus where the OAuth guard makes Zilla itself
the boundary and does token-exchange + cached/per-identity hybrid listing).

Config shape: mcp `kind: server` (north) → mcp `kind: proxy` → one or more mcp `kind: client` routes to
remote MCP servers; at least one remote requires OAuth. Use a **cache-less** (or baseline-cache-only)
route so the live per-identity path runs (per D); optional `guard-jwt` at the north only to validate the
presented bearer — it does NOT originate the flow.

Demo flow:
1. Client initializes (lifecycle); advertises URL-elicitation capability.
2. Client `tools/list` with NO token → proxy fans out; public toolkits return tools immediately
   (non-blocking finalize); the auth-guarded remote returns a **URL `elicitation/create`** (its own
   authorize URL), which Zilla relays to the client. **The north server `manipulateElicitUrl`
   (`McpServerFactory:5154`) rewrites the authorize URL before rendering it: `state` ←
   `<sessionId>.<elicitationId>.<toolkit>__<nonce>` AND `redirect_uri` ← `server.redirectURI` (Zilla's
   own `/mcp/auth/callback`) — so the OAuth callback ALWAYS routes back through Zilla, never direct.**
   The in-progress request is held up to `timeout` (per E).
3. Client opens the (Zilla-rewritten) authorize URL → completes OAuth **directly with the remote's AS** →
   the AS redirects the browser to **Zilla's** `/mcp/auth/callback?code=...&state=<sessionId>.<elicitationId>.<toolkit>__<nonce>`.
   Zilla correlates via `state` (resume held request / route toolkit) and relays the callback UP to the
   remote as the `elicitCallback` flush; the remote exchanges the code and emits `elicitComplete`. (No
   Zilla guard involved — Zilla rewrote the URL + relayed; the remote is the OAuth client.)
4. Remote now treats the identity as authorized and emits native `notifications/tools/list_changed`,
   which Zilla relays up the lifecycle SSE (or the client simply re-lists after the elicitation completes).
5. Client re-sends `tools/list` WITH the bearer → Zilla forwards the bearer to the remote (live
   per-identity path) → remote returns the full auth-guarded toolset → merged into the aggregate list.
6. Client `tools/call` an auth-guarded tool WITH the bearer → Zilla relays it → remote executes → result
   streams back.

Result: elicitation (URL, remote-originated, relayed) + auth-guarded list + auth-guarded call, all in OSS,
with the per-identity token carried by the client and relayed by Zilla. The zilla-plus OAuth guard is only
needed when Zilla must BE the auth boundary (originate the redirect, exchange/cache tokens, and serve a
cached baseline merged with per-identity toolkits — Phase 8).

RESOLVED at kickoff discussion (maintainer, 2026-06-04): symmetric per-hop strip (see §2) — each hop
strips exactly what it injected; the route-exit `mcp` client owns the `reauthorize`; the proxy has NO
route guard (cache guard only).

Edit points (re-grep; numbers drift):
- `McpProxyItemFactory.java` `McpClient.onClientChallenge` (~797-801) + a url/state rewrite helper:
  parse the `elicitCreate.url()`, prepend `<toolkit>__` to the `state=` value, rebuild the `McpChallengeEx`,
  relay up. `McpServer.prefix` holds the capability prefix; derive the `<toolkit>` (strip the capability
  delimiter, or thread the route's toolkit name).
- `McpServerFactory.resolveElicitation` (~2952) + `McpAuthCallbackHandler` (~2793): the server already
  strips `<sessionId>.<elicitationId>.` to correlate; when no **held** stream exists (closed list),
  instead of 410-GONE forward the callback (bearing the residual `state=<toolkit>__<upstreamNonce>`) down
  the single exit to the proxy.
- `McpProxyLifecycleFactory` / `McpProxyListFactory`: receive the forwarded callback, strip+parse
  `<toolkit>__` from `state` to pick the route, forward the residual `state=<upstreamNonce>` down to that
  route-exit `mcp` client (which runs its own Phase-5 `reauthorize`), then `KIND_TOOLS_LIST_CHANGED` flush
  up the lifecycle SSE on completion. (Proxy routes + notifies; it does NOT call `reauthorize`.)
- Test-first: a runtime multi-route proxy IT (one authorized + one needs-preauth toolkit) — currently
  ZERO runtime ITs cover the prefixed toolkit elicit; add net+app scripts + `McpProxy*IT` + peer
  `Network/ApplicationIT`; FIX the un-stripped `tools.call.toolkit.elicit.prefixed/server.rpt`. Gate full
  spec + runtime `install`.


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

## Phase 2 — DESIGN REVISION (unified blob + per-route fragments, supersedes everything below)

**Maintainer decisions (this session, john@aklivity.io):**
- Keep **single unified cache storage per kind** (the current envelope-blob
  format) in the store — NOT per-`(kind,prefix)` store keys. This reverses the
  per-slice STORAGE of decision #2.
- BUT the hydrater holds, per kind, an **in-memory per-route fragment map**
  (prefix → that route's prefix-injected items, no envelope) and **assembles the
  unified blob** from it. This keeps decision #1's per-route hydration *drive* and
  enables per-`(route,kind)` refresh / failure isolation.
- **Failure handling = keep-stale per route.** On a route's hydrate:
  - success with items → `fragment[prefix] = items`
  - success but empty → `fragment[prefix] = ""` (**replace with empty** — a toolkit
    that legitimately emptied its list IS reflected)
  - failure (reset/abort/bearer-challenge/timeout) → **leave `fragment[prefix]`
    unchanged** (retain last-known-good); a transient route failure must NOT wipe
    its tools from the aggregate.
- The store always holds the **unified blob per kind**, reassembled as
  `prelude(kind)` + join(configured routes' non-empty fragments in sorted prefix
  order, `,`) + `close`. Per-`(route,kind)` refresh just updates that route's
  fragment and rewrites the unified blob.

Why this satisfies "single storage" AND resilience: the per-route breakdown is
**hydrater-internal (per-worker) state**, not stored; only the assembled unified
blob is persisted. On lock-handover/failover the new lock-holder rebuilds
fragments via a full populate, then refreshes incrementally.

What this keeps UNCHANGED (small blast radius, safer):
- `McpProxyCache.McpListCache` STORE layout (one value per kind) — **unchanged**.
- `McpCacheListServer` serve path — **unchanged**.
- Seeded YAMLs (×3) and **all** `.rpt` scripts — **unchanged** (both wire
  boundaries preserved). The assembled unified blob must be **byte-identical** to
  today's single all-routes merge for the existing hydrate ITs (verify per-route
  fragment concat reproduces the merge bytes: same prelude/`,`/close, same
  per-route prefix injection, skip empty fragments to avoid stray separators).

What changes (the actual Phase 2 work):
- Relocate the hydrater into the `stream` package so it can drive
  `McpLifecycleServer`/`McpListServer`/`McpListClient`/`McpLifecycleClient` and the
  `decode*` states **in place** (no new abstractions, no `*Sink` interface, no
  extracted decoder). Sink `MessageConsumer` reply target + pre-granted fixed reply
  window; route-exit streams stay async via the engine bus (the only re-entrancy-
  safe decoupling — the synchronous direct-call shortcut is still proven fatal,
  see "Verified finding" below).
- Drive `McpListServer` **per route** (one prefix in `remaining`) into a per-route
  fragment; keep-stale policy above; assemble + `put` the unified blob per kind.
- Replace the `originId == routedId` proxy with an explicit `hydration` boolean on
  `McpLifecycleServer`. Remove the loopback discriminators (`hydrating()`, the
  `originId != routedId` term in `aggregating()`, the onServerBegin loopback
  branch, the `deferring`/`server.hydrating()` guards → keyed on `hydration`, and
  the `McpProxyListFactory.newStream` `originId != routedId` term).

Keep-stale is a NEW behavior on the refresh path → needs its own spec scenario +
IT (test-first). It does NOT break existing ITs (they don't fail a route during a
refresh that had a prior value; initial-populate failure still contributes nothing,
matching `cache.hydrate.toolkit.multi.skip.unauthorized`).

Sequencing (no PR yet, so split is fine and lower-risk):
- **Commit 1:** relocation + loopback removal + per-route fragment assembly +
  keep-stale fragment policy — all EXISTING ITs green (behavior-preserving for the
  cases they cover). Green checkpoint.
- **Commit 2:** new `cache.refresh.*.keep.stale.on.failure`-style spec scenario +
  IT proving a failing route retains its prior tools while others refresh.

Defer 2d (live-path baseline) to Phase 7/8 as before.

### Commit 2 spec (true keep-stale — IN PROGRESS)

Commit 1 landed the relocation but its `fragments` map is **per hydrate cycle**
(fresh per `McpListKindHydrater`), so it is still effectively **drop-on-failure**
(matches today; existing ITs pass). Commit 2 delivers real keep-stale:

1. **Persist fragments per `(handler, kind)`** across hydrate cycles (hoist the
   map from `McpListKindHydrater` onto `HandlerImpl`, keyed by kind). Assemble the
   unified blob from the persisted map each cycle.
2. **Fragment-update policy (confirmed rule):**
   - successful list with items → update fragment
   - successful list, empty (`{"...":[]}`, lifecycle established) → update to `""`
     (replace-with-empty)
   - ANY failure — abort / reset / **bearer challenge** / timeout / lifecycle not
     established → **do NOT update** (retain last-known-good)
   So a route's per-cycle drive must surface skip/failure to its sink as **ABORT**
   (failed), distinct from a genuine empty END. In Commit 1 a bearer-skip currently
   reaches the sink as an empty END (→ fragment `""`); Commit 2 must classify it as
   failure so the prior fragment is kept.
3. **Test-first new scenario** (multi-route refresh): populate two toolkits (A, B)
   both returning tools; refresh where A returns updated tools and B fails (abort
   or bearer challenge); assert the served list = A-updated + **B-original** (B kept
   stale). Confirm it FAILS on Commit 1 (B dropped) before implementing. Add the
   `.rpt` (network+application as needed), the `McpProxyCacheIT` method, and the
   peer-to-peer `ProxyCacheIT` method. Needs a multi-route refresh `zilla.yaml`
   (model on `proxy.cache.toolkit.multi.yaml` + `proxy.cache.refresh.yaml`).
4. Existing ITs must stay green (initial-populate skip still contributes nothing —
   `cache.hydrate.toolkit.multi.skip.unauthorized` unaffected since there is no
   prior fragment on first populate).

---

## Phase 2 — superseded CONFIRMED DESIGN (per-route + per-slice) — DO NOT IMPLEMENT

> Kept for history only. The single-blob revision above supersedes this. The
> structural findings (loopback flow, discriminator locations, the rejected
> synchronous-shortcut finding) remain accurate and useful; the per-slice
> storage/serve/seeded-yaml changes do **not** apply.

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

- **3**: **DONE** (see status table). Shipped `with.cache.credentials` per-route
  static credential with precedence over `options.cache.authorization`. The
  per-route credential is reauthorized once in `McpLifecycleClient` and used
  **consistently for that route's lifecycle and list hydration streams** (each
  `McpLifecycleClient` now carries its own `authorization`; `McpListClient`'s
  upstream frames read `lifecycle.authorization`). The live path is unchanged
  (the client's `authorization` defaults to `server.authorization`).
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
