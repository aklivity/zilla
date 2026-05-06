# Zilla — Agent Guide

Zilla is a multi-protocol event-driven gateway that bridges HTTP, WebSocket, SSE,
gRPC, MQTT, and MCP to Apache Kafka. It is configured entirely via `zilla.yaml`,
with no code changes required for protocol mapping. All runtime behavior is
defined by a pipeline of named **bindings**.

---

## Repository layout

```
runtime/                     # Core engine and all bindings
  engine/                    # EngineWorker, config loader, stream model
  binding-tcp/               # TCP server/client binding
  binding-tls/               # TLS binding
  binding-http/              # HTTP/1.1 + HTTP/2 binding
  binding-kafka/             # Kafka cache + client binding
  binding-mqtt/              # MQTT binding
  binding-grpc/              # gRPC binding
  binding-sse/               # Server-Sent Events binding
  binding-http-kafka/        # HTTP↔Kafka proxy binding
  binding-mqtt-kafka/        # MQTT↔Kafka proxy binding
  binding-grpc-kafka/        # gRPC↔Kafka proxy binding
  ...
specs/                       # Integration test specifications (IT)
incubator/                   # Bindings under active development
maven-plugin/                # Code generator for flyweight types
```

---

## Where deeper guidance lives

This file holds repo-wide guidance only. When you start working in a subtree,
its `AGENTS.md` is loaded automatically with more detail:

| Path | Scope |
| --- | --- |
| [runtime/AGENTS.md](runtime/AGENTS.md) | Java module system, stream model, binding kinds, EngineWorker threading, factory + flyweight pattern, server/client/proxy patterns, per-stream field naming, buffer slots, decode strategy, unit tests |
| [runtime/engine/AGENTS.md](runtime/engine/AGENTS.md) | Engine SPI conventions (Javadoc neutrality, null defaults), `EngineContext` implementer fan-out, test implementations for engine concepts |
| [runtime/binding-kafka/AGENTS.md](runtime/binding-kafka/AGENTS.md) | Kafka local cache (mmap segments, `IoUtil.unmap()`, retention timestamps) |
| [specs/AGENTS.md](specs/AGENTS.md) | `.rpt` script structure, folder layout, IT method naming, `XxxFunctions` builders/matchers, JUEL typed variants, k3po + JUnit 4 migration, JSON schema patches, required spec coverage |

---

## Build

Zilla uses Maven with Java 25.

Every new Maven project directory must include `mvnw` and `mvnw.cmd` copied
from an existing module — this applies to all new projects regardless of type
(`runtime/`, `specs/`, `incubator/`, etc.).

```bash
# Add license headers to new files — run this first after creating new source
# files, otherwise the build will fail on the license check before compilation
./mvnw license:format

# Full build with tests
./mvnw install

# Skip integration tests (faster)
./mvnw install -DskipITs

# Skip all tests
./mvnw install -DskipTests

# Build a specific module
./mvnw install -pl runtime/binding-http -am

# Run a single test class
./mvnw test -pl runtime/binding-http -Dtest=HttpServerIT   # class names are type-prefixed: Http*
```

The Maven plugin in `maven-plugin/` generates flyweight Java classes from `.idl`
files. Always run a full build after modifying any `.idl` file.

---

## Test-first discipline

Zilla follows a strict test-first discipline. For every new feature or bug
fix:

1. Write the spec script(s) and/or unit tests first, before any implementation
2. Confirm the tests fail for the right reason against the current code
3. Implement until the tests pass
4. Do not open a PR with implementation code that has no corresponding tests

This is especially important for new bindings. The spec scripts define the
correct protocol behavior; the Java implementation exists to satisfy them.
Never write implementation code and retrofit tests to match it — that
defeats the purpose.

Spec script conventions and unit-test conventions are detailed in
[specs/AGENTS.md](specs/AGENTS.md) and [runtime/AGENTS.md](runtime/AGENTS.md).

---

## Adding a new binding

Repo-level scaffolding (everything before module-specific implementation):

1. Open a GitHub Issue to discuss the design before writing any code
2. Create `specs/binding-<n>.spec/` and write `.rpt` scripts for the happy
   path and key error scenarios, derived from the relevant protocol
   specification — see [specs/AGENTS.md](specs/AGENTS.md)
3. Create `runtime/binding-<n>/` and `specs/binding-<n>.spec/` following the
   existing module layout. Every new project directory (both `runtime/` and
   `specs/`) must include these top-level files copied from an existing
   module: `COPYRIGHT`, `LICENSE`, `NOTICE`, `NOTICE.template`, `mvnw`,
   `mvnw.cmd`. All new components use the **Aklivity Community License** —
   copy `LICENSE-AklivityCommunity`, `COPYRIGHT-AklivityCommunity`, and
   `NOTICE-AklivityCommunity` from the top-level repository directory,
   renaming them to `LICENSE`, `COPYRIGHT`, and `NOTICE.template` respectively
   in the new module. Then generate `NOTICE` by running
   `./mvnw notice:generate --projects <path/to/project>` from the repository
   root; do not copy `NOTICE` from another module as it must reflect the new
   module's actual dependencies. Never edit `NOTICE` files directly — always
   regenerate via `./mvnw notice:generate --projects <path/to/project>`;
   manual edits will be overwritten. Source file headers must carry the
   Aklivity Community License copyright notice
   (`Copyright 2021-2024 Aklivity Inc`); run `./mvnw license:format` to apply
   the correct header automatically
4. Add the module to `runtime/pom.xml` and the root `pom.xml`

Then proceed with the runtime-side implementation steps in
[runtime/AGENTS.md](runtime/AGENTS.md) and the spec/IT steps in
[specs/AGENTS.md](specs/AGENTS.md). Confirm `./mvnw install` passes including
all ITs before opening the PR.

---

## Code style

All Java code must pass the project checkstyle rules defined in
`conf/src/main/resources/io/aklivity/zilla/conf/checkstyle/configuration.xml`.
Run `./mvnw checkstyle:check` to verify before committing. Key rules to be
aware of: 4-space indentation (no tabs), 130-character line limit, opening
braces on a new line (`LeftCurly` option `nl`), closing braces alone on their
own line (`RightCurly` option `alone`), no trailing whitespace, imports ordered
by group (`java`, `javax`, `jakarta`, `org`, `com`) with a blank line between
groups and no star imports.

- YAML files use 2-space indentation, no tabs; JSON files use 4-space indentation, no tabs
- Prefer non-block lambdas (expression lambdas) over block lambdas (`{ return
  ...; }`) — even when the expression spans multiple lines via a builder chain,
  keep it as a single expression without braces or an explicit `return`
- Method parameters are each on their own line, indented 4 spaces relative to
  the method declaration, with the closing `)` on the same line as the last
  parameter:

```java
private void onNetworkData(
    long traceId,
    long authorization,
    int reserved,
    OctetsFW payload)
{
```

- Methods should have a single `return` statement at the end where possible;
  avoid early returns except for guard clauses at the very top of a method
- Avoid the `...IfNecessary` method naming suffix (e.g., `doEndIfNecessary`,
  `cleanupDecodeSlotIfNecessary`) — name methods for what they do (`doEnd`,
  `cleanupDecodeSlot`); internal conditionality based on stream state or slot
  value is an implementation detail that does not belong in the name
- Java 21; no preview features
- No Lombok
- Use `jakarta.json` APIs (e.g., `JsonObject`, `JsonReader`, `JsonParser`)
  for JSON processing — do not introduce Jackson (`com.fasterxml.jackson`)
- Prefer interface types over implementation classes for field, parameter, and
  return types where a suitable interface exists (e.g., `List` over `ArrayList`,
  `Map` over `HashMap`, `ConcurrentMap` over `ConcurrentHashMap`)
- Never use fully qualified class names as field, parameter, or variable types —
  add an `import` and use the simple type name. The only exception is a naming
  collision where two different packages define the same class name; in that
  case qualify the less-frequently-used type
- Package-private classes preferred over public where there is no SPI contract
- `final` on all fields; immutable config objects
- Flyweight field names use the `*RO` / `*RW` suffix convention consistently
- Error paths must call `cleanup()` and release any acquired resources before
  returning
- Log via the Zilla event system (`BindingEvent`), not `java.util.logging` or
  SLF4J, on the hot path

### Import ordering

Checkstyle enforces a strict import order. Violations cause build failures, so
**always sort imports alphabetically by fully-qualified package name** within
each group, and separate groups with a blank line in this order:

1. `java.*`
2. `javax.*`
3. `jakarta.*`
4. `org.*`
5. `com.*`
6. `io.*` (covers all `io.aklivity.zilla.*` imports)

Within the `io.aklivity.zilla.runtime.engine.*` sub-packages the alphabetical
rule means, for example:

```
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;   // c
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;        // g
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;    // m
import io.aklivity.zilla.runtime.engine.poller.PollerKey;          // p  ← before store
import io.aklivity.zilla.runtime.engine.store.StoreHandler;        // s  ← before vault
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;        // v
```

When adding a new import, insert it at the correct alphabetical position —
do not append it at the end of the group.

---

## Key dependencies

| Dependency | Purpose |
| --- | --- |
| `agrona` | Lock-free ring buffers, flyweight buffer access, `IoUtil` for mmap |
| `zilla:maven-plugin` | Generates flyweight Java from `.idl` type definitions |
| `junit5` | Unit and integration tests |
| `mockito` | Mocking in unit tests |

---

## Contribution workflow

1. Fork the repo and create a branch using gitflow naming conventions:
   `feature/<short-description>` for new features, or
   `fix/<issue-number>-<short-description>` for bug fixes
2. Make changes; ensure `./mvnw install` passes with no failures
3. Open a PR against the `develop` branch (not `main`)
4. PRs require at least one approving review from a maintainer
5. Commit messages follow Conventional Commits:
   `feat(binding-http): add trailers support`
   `fix(engine): release mmap'd segment on log rotation`
6. Do not include generated sources (`target/`) or IDE files in commits

### Diagnosing PR build failures

When a PR build fails, always fetch the actual CI logs before attempting to
diagnose or fix the failure. Do not guess at the cause based on the change
diff alone — retrieve the logs first using GitHub MCP tools, or ask the user
to provide them. Build failures often have non-obvious root causes (e.g., a
checkstyle violation, a transitive module-info issue, or a flaky unrelated
test) that are impossible to diagnose without the log output.

For significant new bindings or behavior changes, open a GitHub Issue first
to discuss the design before writing code.

---

## Useful references

- Docs: https://docs.aklivity.io/zilla/latest/
- Binding reference: https://docs.aklivity.io/zilla/latest/reference/config/bindings/
- How Zilla Works (architecture deep-dive): https://www.aklivity.io/post/how-zilla-works
- Examples: https://github.com/aklivity/zilla-examples
