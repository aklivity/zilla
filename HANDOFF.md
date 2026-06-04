# Handoff — Issue #1810: per-toolkit `oauth` for `mcp` proxy

Branch: `claude/kind-wright-P3p6I` (zilla repo). Develop here; push here only.
Issue: https://github.com/aklivity/zilla/issues/1810 — the design/phasing is the
source of truth. This file carries the cross-session context (the remote
environment is ephemeral: each session is a fresh clone with no prior chat).

> **Next session — start here:** Phases 1, 2, 2e, 3, 6 are DONE+pushed on this
> branch. Phases 4, 5, 9 already landed in `develop` (Phase 4 #1820; Phases 5 + 9
> core via #1739/#1752 — see the corrected Status table, audited 2026-06-03).
> **The first genuinely-unbuilt phase is now Phase 7** (non-blocking `tools/list`
> emitting per-unauthorized-toolkit `elicitation/create` + `notifications/tools/list_changed`;
> blocking `tools/call` honoring the per-request timeout — the timeout plumbing
> Phase 6 added). Phase 8 follows. Phase 5 has two residual gaps (binding-level
> not per-route guard/creds; `reauthorize(null)` not inbound bearer) — flagged in
> the table, not yet scoped. Branch is green. Re-grep line numbers before editing.

---

## Status

| Phase | State |
| --- | --- |
| 1 — `resource_metadata` capture + carry + re-render | **DONE, pushed, full module green** |
| 2 — split hydrater from live entry; remove `originId == routedId` loopback | **DONE+pushed. Commit 1: relocation + loopback removal + per-route fragment assembly (behavior-preserving). Commit 2: keep-stale — per-route fragments live on `McpProxyCache.McpListCache`; failed route (abort/reset/timeout/bearer challenge) keeps last-known-good, success replaces, empty→empty; new `cache.refresh.toolkit.keep.stale.on.failure` scenario + `shouldRefreshToolkitKeepingStaleOnFailure`. Runtime no-skip green: McpProxyCacheIT 28, McpProxyLifecycleIT 7, UT 20, checkstyle/license/jacoco pass.** |
| 2e — spec-module jacoco 0.95<0.96 (pre-existing Phase-1 `McpFunctions` gap) | **DONE+pushed. Extended `McpFunctionsTest` (50→69) → 0.98; full spec verify green (184 ITs + UT + jacoco/checkstyle/license).** |
| 3 — `with.cache` static credential over `options.cache.authorization` | **DONE+pushed.** Per-route `with.cache.credentials` added to `McpWithConfig` (+`McpWithCacheConfig`/builder), `McpWithConfigAdapter`, and the route `with` schema block. **Per-route credential is resolved once in `McpLifecycleClient.doClientBegin` (hydration only): if the route has `with.cache.credentials` it is reauthorized through the cache guard into the lifecycle client's `authorization`, else it inherits `server.authorization` (= the binding cache authorization from `options.cache.authorization`) → precedence `with` > `options`.** The route's **lifecycle AND list streams share that single per-route authorization**: `McpListClient`'s upstream (route-exit) frames now use `lifecycle.authorization` instead of `server.authorization`. `McpBindingConfig.routeCacheCredentials(routedId)` looks up the override. Reauthorize is once-per-route (lifecycle client is `supplyClient`-memoized, `doClientBegin` idempotent), so all list kinds for a route reuse the same session. New `McpWithConfigAdapterTest` (4) + scenario `cache.hydrate.credentials.toolkit` (single accept @ `authorization`=1L covers lifecycle+toolsList since they now match) wired into `McpProxyCacheIT#shouldHydrateToolkitWithRouteCredentials` (server-only, hydrate filter=tools) and peer-to-peer `ProxyCacheIT#shouldHydrateToolkitWithRouteCredentials`. Negative-checked (reverting the reauthorize → IT times out). Full no-skip green: runtime 200 ITs/UT + jacoco/checkstyle/license/notice; spec 185 + gates. |
| 4 — protocol `2025-11-25` + `elicitation.url` negotiation | already landed before this branch (#1820) |
| 5 — guard `NEEDS_PREAUTHORIZE → preauthorize → callback → reauthorize` | **CORE DONE in `develop` (#1739/#1752 "MCP elicitation across server, proxy, client"), NOT this branch.** `McpClientFactory`: `reauthorize(null)`→`MASK_AUTHORIZED`→`credentials()`→stamp; `NEEDS_PREAUTHORIZE`→`guard.preauthorize`→`elicitCreate` challenge→async `reauthorize` on callback (`onElicitCompleted`). `McpServerFactory`: `McpAuthCallbackHandler`, `isAuthCallbackPath`, `resolveRedirectURI`, state correlation. **Residual gaps vs spec wording (audited 2026-06-03, unaddressed):** (a) live guard+credentials are **binding-level** (`binding.guard`/`binding.credentials`), `McpRouteConfig` has no guard/creds — NOT per-route; (b) `reauthorize` is called with **`null`**, not the connecting client's inbound bearer (no inbound `Authorization` capture on the live path). Decide whether these are wanted (overlaps Phase 8 per-identity). |
| 6 — `timeout` option + per-request `McpXxxBeginEx` carriage; hold-and-resume | **DONE+pushed (6a plumbing + 6a server stamping + 6b behavior).** Per-binding `timeout` option (config/builder/adapter/schema); `int64 timeout` on the six request BeginEx variants + `McpFunctions`; server stamps effective timeout (gated by `CLIENT_ELICITATION_URL`) on each request BeginEx; client honors it as the elicit hold budget; server holds (`elicitation/create` SSE) when `timeout>0` and emits `-32042 URLElicitationRequiredError` (url in `data`) on expiry/`timeout==0`; explicit DECLINE stays `-32000`. See the "Phase 6 — 6b" section for the 3 commits + k3po teardown notes. |
| 7 — non-blocking `tools/list` / blocking `tools/call` | **NOT STARTED (audited 2026-06-03).** `tools/list` is BLOCKING — `McpProxyListFactory.onNextClient` finalizes only when all routes polled; unauthorized toolkit is silently SKIPPED (`onClientSkip`→`onNextClient`), no `elicitation/create` emitted from `tools/list` (only from `tools/call`). `KIND_TOOLS_LIST_CHANGED` flush plumbing exists but only fires after a full cache-refresh cycle, NOT per-toolkit when a toolkit authorizes during a live list. Depends on Phase 6 timeout. |
| 8 — per-client listing filter (SEP-1488 / operator map / annotations) | **NOT STARTED — zero implementation (audited 2026-06-03).** List serve path (`McpCacheListServer`) emits the SAME shared aggregate bytes to every client; `authorization` is captured but never used to filter. No `securitySchemes`, no tool→scope map config, no `readOnlyHint`/`destructiveHint`, no acquirable/non-acquirable distinction, no per-identity live listing. |
| 2d — live-path baseline test (baseline + per-identity toolkits) | **deferred to Phase 7/8** (maintainer decision) |
| 9 — IT coverage of the preauthorize→elicit→callback→reauthorize flow | **DONE in `develop` (#1739/#1752), NOT this branch.** Scenarios: `tools.call.elicit.{completed,declined,timeout}` × {plain, `.guarded`, `.proxied`}, `reject.auth.callback.unknown.elicitation`, `lifecycle.initialize.elicitation.{url,form}`. (Residual: no IT for the Phase-5 per-route/inbound-bearer gaps above, since those aren't implemented.) |

### Phase 5 residual (Gap A + Gap B) — CONFIRMED DESIGN (current task)

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
