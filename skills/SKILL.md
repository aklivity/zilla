---
name: zilla
description: Spin up a runnable Zilla gateway instance based on the official aklivity/zilla examples (https://github.com/aklivity/zilla/tree/develop/examples). Use this whenever the user wants to try, demo, test, or explore Zilla — e.g. "spin up a Zilla instance", "show me a Zilla example", "set up Zilla with Kafka/gRPC/MQTT/SSE/JWT/TLS/WebSocket", "run the Zilla quickstart", "give me a working Zilla config for X", or any request to stand up a local Zilla + Kafka (or other backend) stack via Docker Compose. Also use this to pick the right pre-built example for a described use case (REST-to-Kafka, gRPC-to-Kafka, MQTT bridging, JWT-secured streaming, etc.) even if the user doesn't say "example" explicitly.
---

# Zilla examples runner

Zilla ships a large set of self-contained, runnable examples in
`aklivity/zilla` (path `examples/`, branch `develop`), each a Docker Compose
stack with a working `zilla.yaml`. This skill picks the right example for
what the user wants and gets it running, instead of hand-writing a Zilla
config from scratch.

Prefer this over improvising a `zilla.yaml` from memory — the examples are
tested in CI and are more likely to actually work.

## Workflow

1. **Pick the example.** Read `references/examples-catalog.md` for the full
   table of examples (protocol, what it demonstrates, ports, extra
   requirements) and the "Picking an example" heuristics at the bottom.
   State which example you picked and why, in one sentence. If the request
   is genuinely ambiguous (e.g. could match two very different examples),
   ask which protocol/use case they care about instead of guessing.
2. **Fetch just that example** (don't clone the whole repo — it's a large
   monorepo). Run:
   ```bash
   bash scripts/fetch_example.sh <example.name> <target-dir>
   ```

   This sparse-checks-out only `examples/<example.name>` from
   `github.com/aklivity/zilla` at `develop` HEAD and copies it to
   `<target-dir>`. Requires network access to github.com — if that's
   unavailable in the current environment, tell the user and offer to walk
   them through doing it locally instead (`git clone` + `git
   sparse-checkout set examples/<name>`).
3. **Always share the full files, not just a summary — as actual downloadable
   files.** Before running anything, write every file needed to run the
   example to disk and share them as downloadable files (not just code
   blocks pasted into chat) — at minimum `compose.yaml` and
   `etc/zilla.yaml`, plus any other file the example depends on
   (`etc/protos/*.proto`, `etc/specs/*.yaml`, JWT keys, TLS keystores,
   client scripts under `url-elicit/`, etc.), preserving the original
   relative paths (e.g. `etc/zilla.yaml`, not a flattened `zilla.yaml`) so
   the user's docker-compose volume mounts still work. Also give a one-line
   summary of what the stack does and what ports it uses (from the catalog
   table), and note any extra requirement listed in the catalog (e.g.
   `grpcurl`, `ghz`, Node.js client) so the user isn't surprised.
   Keep the explanation of *why* you picked or merged configs brief — one
   or two sentences, stated once. Don't narrate the merge/synthesis process
   step by step or repeat the same explanation twice.
4. **Start the stack.** If Claude has shell/Docker access in this
   environment, offer to run it directly:
   ```bash
   cd <target-dir>
   docker compose up -d
   ```

   If Claude does NOT have Docker access in the current environment (e.g.
   this is a hosted chat session with no local shell for the user's
   machine), give the user the exact commands to run themselves rather than
   pretending to run them — clone/fetch, `docker compose up -d`, plus the
   `ZILLA_VERSION` and `NAMESPACE` env vars if relevant.
5. **Verify it's working — share the exact commands.** Pull the "Verify
   behavior" section straight out of the fetched `README.md` (curl commands,
   grpcurl commands, websocket client snippets, etc. — these are the same
   commands used in the repo's own CI test at `.github/test.sh`, so they're
   known to work) and paste them verbatim, in order, so the user can copy
   and run them without going back to the fetched directory. Show expected
   output alongside each command where the README provides it.
6. **Teardown reminder.** Mention `docker compose down` to clean up when
   they're done, and that named volumes/topics are removed with `docker
   compose down -v` if they want a truly clean slate for a re-run.
## Adapting an example

If the user wants something *close to* an example but not identical (e.g.
"like http.kafka.crud but with a different topic name" or "same as
tcp.echo but on port 9000"), fetch the closest matching example first, then
edit `etc/zilla.yaml` and/or `compose.yaml` in the copied directory rather
than writing a config from scratch. Keep edits minimal, state what you
changed in a sentence or two, and share the edited files as downloadable
files (see step 3 above) — don't narrate the edit process.

If the user wants to combine ideas from two examples (e.g. JWT auth from
`http.proxy.jwt` plus the Kafka CRUD pattern from `http.kafka.crud`), fetch
both, merge bindings into a single `zilla.yaml`, and briefly state which
two examples you drew from and why in one or two sentences — not a
step-by-step account of the merge. Share the resulting files as downloadable
files, preserving the `etc/` layout.

### Validate any config you generate or edit

Any time you produce a `zilla.yaml` that isn't a verbatim, unmodified copy
from an example (edited config, merged config, config written to satisfy a
custom request), validate it against `references/config-schema.md` before
presenting it — that file summarizes the official schema at
https://docs.aklivity.io/zilla/next/reference/config/overview.html. Check:

- every binding's `type`/`kind` pair is a real, valid combination
- the binding chain's `routes...exit` references actually resolve to
  bindings that exist in the file, and roughly follows the typical
  pipeline order (protocol server → bridge/proxy → Kafka bindings →
  protocol client)
- any `guard`, `vault`, or `catalog` referenced by name from a binding is
  actually declared in the corresponding top-level section
- no `[Plus]`-only type is used unless the user is targeting Zilla Plus
- no `[Incubator]` type is used without `ZILLA_INCUBATOR_ENABLED=true` set
  in the compose file
  If a binding's specific option fields (not just type/kind) are in
  question, fetch that binding's own reference page — e.g.
  `https://docs.aklivity.io/zilla/next/reference/config/bindings/<type>/` —
  rather than guessing from memory. State explicitly that you validated the
  config against the docs, and call out anything you weren't able to
  confirm.

## Multi-example / "show me everything" requests

If the user wants to browse rather than run ("what Zilla examples exist for
Kafka?", "list gRPC examples"), just answer from
`references/examples-catalog.md` — no need to fetch anything from GitHub.

## Notes

- Default Zilla image is `ghcr.io/aklivity/zilla:latest`; pin with
  `ZILLA_VERSION=<tag> docker compose up -d` if the user wants a specific
  version.
- Most examples default to Kafka (Apache Kafka via `apache/kafka` image)
  plus a Kafka UI on port 8080 — mention the port collision if the user
  wants to run two Kafka-backed examples side by side (use `NAMESPACE` env
  var and remap ports, or run one at a time).
- Examples are also mirrored (older/archived copies) at
  `github.com/aklivity/zilla-examples`, but `aklivity/zilla`'s own
  `examples/` directory on `develop` is the current, actively maintained
  source — prefer it.
