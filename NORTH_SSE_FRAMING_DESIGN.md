# Design note — north MCP server: SSE response framing (StreamableHTTP conformance)

> Status: design for a future change. Uncommitted scratch note — move/delete as you like.
> Context lives in memory `mcp-url-elicit-nonconformant`.

## Problem

The MCP TypeScript SDK client (`StreamableHTTPClientTransport`, v1.29.0) opens a
**standalone GET `/mcp` SSE stream** — the channel for *server‑initiated* requests
such as `elicitation/create` — only when it is talking to a server that **streams
SSE responses**. Empirically:

| client → server | requests observed | `initialize` response |
| --- | --- | --- |
| → SDK upstream (`server.mjs`) | POST, POST, **GET**, … works | `text/event-stream` (`event: message\ndata:…`) |
| → Zilla north | POST, POST, **timeout — no GET** | `application/json` |

Because Zilla north answers with plain JSON, the SDK client never opens its
standalone GET → north never sends `RESUME` → the proxy never opens south's GET to
the eliciting upstream → the upstream's GET‑only `elicitation/create` is never
delivered. This blocks url‑mode elicitation end‑to‑end (and, generally, **any
server‑initiated MCP request** flowing through Zilla).

**Why JSON breaks it (the capability signal).** A modern Streamable‑HTTP client is
written to interoperate with *both* transports: the legacy 2024 HTTP+SSE server and
the new Streamable‑HTTP server. The way it distinguishes them is the **response
content‑type**: an SSE (`text/event-stream`) response means "modern Streamable‑HTTP
server — you may open a standalone GET and I may push server‑initiated messages,"
whereas a plain `application/json` response signals a legacy/non‑streaming server, so
the client does *not* open the standalone GET. By answering JSON, Zilla north makes
every modern client downgrade us to "can't push" — which is precisely the bug.
Therefore north must answer SSE to *announce* it is a Streamable‑HTTP server, even
when the immediate response body is just a single JSON‑RPC result.

This is not specific to elicitation — it is a StreamableHTTP **conformance gap**:
Zilla north does not present as an SSE‑streaming server.

## Decision

Stop trying to detect "is SSE necessary" per response (impossible — server‑initiated
elicitation is unpredictable and originates upstream mid‑tool‑call). Instead, **frame
responses as SSE whenever the request's `Accept` includes `text/event-stream`**,
matching the SDK server. Fall back to plain JSON only when the client does not accept
event‑stream. Both forms are spec‑valid; the SDK server always picks SSE when offered.

## Where it lives today (`runtime/binding-mcp/.../stream/McpServerFactory.java`)

- `acceptIncludesEventStream(accept)` helper — `:5665` (already used for GET routing at `:456`).
- POST `Accept` header is parsed at server BEGIN — `:373`.
- `sseUpgrade` flag drives the content type:
  - `doEncodeResponseBegin` content‑type decision — `:2353`
    (`contentType = sseUpgrade ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON`).
  - response postamble suffix — `:2596` (`sseUpgrade ? "}\n\n" : "}"`).
  - `doEncodeResponsePreamble` (`~:2254`) emits `data: {"jsonrpc":"2.0","id":<decodedId>,…`
    when `sseUpgrade` — **but no event `id:` line** today.
- `sseUpgrade` is set in only two places today:
  - `onAppChallenge` `:2336` (elicit / challenge path), and
  - `onAppBegin` `:5020` (`decodedProgressToken != null`).
- Several response builders hardcode `CONTENT_TYPE_JSON` (`:2051, :2084, :2101, :2297, :2546`) —
  these are the initialize / 202 / error / notify responses that must also become SSE‑capable.

## Proposed change

1. **Default `sseUpgrade` from the request `Accept`.** At request decode (POST BEGIN),
   set `sseUpgrade = acceptIncludesEventStream(accept)`. Keep the existing forced‑on cases
   (progressToken, elicit). Result: for normal SDK clients (`Accept: application/json,
   text/event-stream`) every response is SSE; for `application/json`‑only clients it stays JSON.

2. **Primary signal is the SSE content‑type.** Answering `text/event-stream` is what tells the
   client "Streamable‑HTTP server → open the standalone GET." That is the necessary change.
   **Event `id:` lines are secondary (resumability):** include them on the SSE events so the
   client can resume via `Last-Event-ID`, reusing the existing id scheme (`<requestId>:<seq>`,
   see `encodeSseElicitIdLine` / `LIFECYCLE_STREAM_ID_PREFIX`); add to `doEncodeResponsePreamble`
   / the result event framing. The SDK transport also has a "reconnect on priming event (POST
   stream with event id)" path (`streamableHttp.js` `_handleSseStream`, ~line 223) — so event
   ids may also matter for *when* the GET (re)opens.
   ⚠️ **Verify empirically**: make north SSE first (content‑type only), watch whether the client
   opens the GET; add event ids if needed. Don't over‑engineer ahead of the observed behavior.

3. **Route the hardcoded‑JSON responses through the `sseUpgrade` decision** (initialize result,
   202 accepts, errors) so the whole response surface is consistent, not just tool results.

4. **Close semantics unchanged** for request/response: write the result event, then end the
   POST response (SDK server closes the per‑request SSE after the response). The standalone
   GET stays open for the session.

Once north speaks SSE, the SDK client opens its standalone GET → north `McpEventStream` →
`session.doAppResume()` `RESUME` → proxy → south GET. The session‑stream elicit relay
(already implemented and IT‑green) then flows.

## Test impact (the main cost — wide but mechanical)

Most binding‑mcp **network** IT scripts assert `Content-Type: application/json` and a single
JSON body for `initialize` / `tools/list` / `tools/call` / etc. These must migrate to SSE
framing on the **net** scripts only:

- `${net}/…/server.rpt` (north output): `write` `event: message\nid: <…>\ndata: {…}\n\n`
  instead of a bare JSON body + `application/json` header.
- `${net}/…/client.rpt` (downstream peer): matching `read`s.
- **App‑layer scripts are unaffected** — the mcp extension layer carries no HTTP framing.
- Keep at least one `Accept: application/json`‑only scenario to cover the JSON fallback path.
- `McpServerIT` / `NetworkIT` are the affected IT classes; `ApplicationIT` / proxy app‑layer
  ITs are not.

Given the breadth, do this as a dedicated change (its own PR), separate from the elicitation
feature, with the IT‑script migration as the bulk of the diff.

## Sequencing to get the example working end‑to‑end

1. **North SSE framing** (this note) → client opens the standalone GET.
2. **Proxy GET‑listener establishment** — fix `clients == 0` post‑hydration so the fresh‑session
   GET `RESUME` re‑establishes a GET listener per aggregate route, **without** re‑entering the
   request‑routing path (the first attempt produced `-32600 Invalid request`; reverted).
   Decouple the per‑route GET listener from hydration teardown and from request routing.
3. Session‑stream elicit relay — **already implemented & IT‑green** (north emit on GET, proxy
   relay, south POST‑back, complete on GET). No further work expected.
4. Validate: `ZILLA_VERSION=develop-SNAPSHOT docker compose up -d` then
   `examples/mcp.proxy/.github/test.sh` → expect "OK url-mode elicitation relayed end-to-end".

## Risk / notes

- Core server‑binding response‑path change → land behind the full IT suite.
- Spec‑compliant either way; JSON‑only clients keep working via the `Accept` fallback.
- SSE framing overhead is negligible.
- Already in place & green and unaffected by this note: request‑stream passthrough
  (north+south+proxy), `McpFunctions` coverage, the `notifications/cancelled` NPE crash‑guard,
  the `doAppEnd`/`cleanupRequest` null‑guard, and the `lifecycle.elicit.completed` session ITs.
