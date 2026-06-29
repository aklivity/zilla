# Handoff — MCP tools/list scope filtering & securitySchemes (PR #1972 / #888)

> Living handoff for resuming work after a session `/clear`. This file lives on
> branch `claude/auth-scopes-filtering-excluder-wip` (DO NOT MERGE).

## Scope
- **aklivity/zilla PR #1972** — MCP tools/list scope-based filtering / securitySchemes.
- **aklivity/zilla-plus PR #888** — companion: guard `verify()` public + `@Override`.
- Local checkouts: `/home/user/zilla`, `/home/user/zilla-plus`.
- Feature branch (both repos): `claude/auth-scopes-filtering-pt61xu`.
- WIP branch (zilla): `claude/auth-scopes-filtering-excluder-wip` (resume point).
- Use GitHub MCP tools (no `gh` CLI).

## Design
- `securitySchemes` flows **end-to-end** to enforce authorized scopes: sources
  (mcp client / mcp-http / mcp-openapi) inject it → the mcp **proxy** consumes it
  to drop unauthorized tools and passes it through verbatim on surviving tools →
  the mcp **server** is the egress.
- **SEP-1488** (the MCP securitySchemes spec) is **not finalized**, so the mcp
  **server** kind must **strip** `securitySchemes` from its tools/list reply.
  Keep it everywhere upstream of the server.

## Done + pushed on `claude/auth-scopes-filtering-pt61xu` (HEAD `84b241e0`)
All 13 of jfallows' review comments addressed, plus:
- `a712c2a6` getStringView for scheme decode (review #8)
- `f88fe4c5` widen decodeItemSchemes parser to JsonParserEx (compile fix)
- `0b75c4f4` **B** — cache mcp-http tools/resources/prompts list replies (review #6)
- `0e976efa` **C** — reuse scope-filter pipeline across list responses
- `fddbeebb` **A** — reuse per-stream scopes list during list decode
- `f77c4923` style **#1–#5** (getStringView/contentEquals; McpScopeFilter↔
  McpSchemeInjector `Control` parity; `init`→void; lifecycle comments)
- `84b241e0` **#6** — incremental `JsonParserEx` in
  `McpBindingConfig.rebuildToolSchemaIndex`/`extractArguments`
All verified green via `binding-mcp` clean verify.

## REMAINING TASK (only open item): finish the SEP-1488 server gate
WIP on this branch (`claude/auth-scopes-filtering-excluder-wip`) adds:
- `runtime/binding-mcp/.../internal/transform/McpSchemeExcluder.java`
- `McpServerFactory` wiring: `schemeStaging`/`schemeStagingView` fields, pipeline
  built in `onDecodeToolsList`, `excludeSchemes()` helper in
  `McpRequestStream.onAppData`
- test-first `runtime/binding-mcp` `McpServerIT.shouldListToolsExcludingSecuritySchemes`
  (pairs `${net}/tools.list.security.schemes/client` with
  `${app}/tools.list.security.schemes/server`: app writes securitySchemes, net
  client reads them stripped). **That test passes.**

**PROBLEM:** the excluder reconstructs the reply via parser→generator, which
**compacts** the JSON and breaks `shouldListTools` — that test asserts the server
relays the backend's **pretty-printed** bytes verbatim.

**FIX:** make `McpSchemeExcluder` **byte-preserving** — model it on `model-json`
`JsonExtractor` (an `identity()`/verbatim transform that re-asserts verbatim
downstream and copies original source bytes), dropping **only** the
`securitySchemes` member and its separating comma, not re-serializing. Validate
that **both** `shouldListTools` (verbatim preserved) and
`shouldListToolsExcludingSecuritySchemes` (member dropped) pass. Then fold this
branch into `claude/auth-scopes-filtering-pt61xu` once green.

First step: read `McpSchemeExcluder.java` and `model-json` `JsonExtractor.java`,
then rework the excluder and run the two `McpServerIT` tests.

## Build / test gotchas
- `git config user.email noreply@anthropic.com; git config user.name Claude`
  (else commits show Unverified). Commit/push only the feature branches.
- Always use `clean` (moditect fails "already modular" on incremental rebuild).
- Single-test IT run:
  ```
  ./mvnw clean verify -pl runtime/binding-mcp -Dtest=void \
    -Dit.test='McpServerIT#shouldListTools+shouldListToolsExcludingSecuritySchemes' \
    -DfailIfNoTests=false -Djacoco.skip=true
  ```
  (`jacoco.skip` avoids the coverage gate on partial runs; a full module verify
  must pass coverage before committing.)
- Do **not** use `-am` for binding-mcp tests: it rebuilds `engine` and runs its
  unit tests, one of which (`TrustedTest.shouldConfigureTrustViaCacerts`) fails in
  this sandbox due to the `JAVA_TOOL_OPTIONS` truststore override — unrelated env
  flake. Deps are already installed in `.m2`.
- **Never** end a maven command with `; echo EXIT=$?` or pipe to `tail` — it masks
  maven's real exit. Redirect to a log and grep for `BUILD SUCCESS`/`BUILD FAILURE`
  and `Tests run:`.

## zilla-plus #888
Makes guard `verify()` public + `@Override` (ApiKeysGuardHandler,
AzureAdGuardHandler); bumps `zilla.version` to `develop-SNAPSHOT`. CI is RED only
because `develop-SNAPSHOT` isn't published — it builds locally after building
zilla first (`./mvnw clean install -DskipTests` in `/home/user/zilla`, then
`/home/user/zilla-plus`). Resolves once #1972 merges and a zilla artifact is
published. No code action needed.

## Watching
PR-activity subscriptions (#1972, #888) and the hourly cron self-check were
session-scoped and are likely gone after `/clear` — re-subscribe via
`mcp__github__subscribe_pr_activity` if desired. CI flakes on unrelated examples
(`tls.echo`) and `engine` `TrustedTest` are environmental, not from this PR.
