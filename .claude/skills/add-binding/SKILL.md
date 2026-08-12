---
name: add-binding
description: Step-by-step workflow for scaffolding and implementing a new Zilla binding — GitHub issue, spec scripts, license files, module-info/factory SPI/configuration checklist, and JSON schema patch wiring. Use when adding a new binding under runtime/ and specs/.
---

# Adding a new binding

Full end-to-end checklist for introducing a new `binding-<n>` module, spanning
repo scaffolding, the runtime implementation, and the spec/schema wiring.
Cross-cutting patterns referenced below (factory + flyweight pattern,
server/client/proxy shapes, stream field naming, decode strategy) are
documented in [runtime/AGENTS.md](../../../runtime/AGENTS.md) — read that
before implementing the stream handler.

## 1. Repo-level scaffolding

1. Open a GitHub Issue to discuss the design before writing any code.
2. Create `specs/binding-<n>.spec/` and write `.rpt` scripts for the happy
   path and key error scenarios, derived from the relevant protocol
   specification — see [specs/AGENTS.md](../../../specs/AGENTS.md).
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
   the correct header automatically.
4. Add the module to `runtime/pom.xml` and the root `pom.xml`.

## 2. Runtime-side module-level checklist

1. Declare `module-info.java` — exports SPI packages only, keeps `internal.*`
   unexported, registers the factory SPI with `provides`.
2. Define flyweight types in `src/main/resources/META-INF/zilla/<n>.idl`.
3. Add the module to `runtime/pom.xml` and the root `pom.xml`.
4. Verify all new dependencies are fully modular (see "Java module system" in
   [runtime/AGENTS.md](../../../runtime/AGENTS.md)).
5. Implement the type-prefixed factory SPI (e.g., `HttpBindingFactorySpi`,
   `MqttBindingFactorySpi`) and register it in
   `META-INF/services/io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi`.
   The factory SPI receives a general `Configuration`; construct the
   component-specific subclass from it (e.g., `new HttpKafkaConfiguration(config)`)
   and pass that subclass — not the raw `Configuration` — into the stream handler
   and any other collaborators that need config access.
6. Implement the type-prefixed stream handler (e.g., `HttpServerFactory`,
   `MqttServerFactory`) extending `BindingHandler`, driven by the failing spec
   scripts.
7. Add `XxxConfiguration extends Configuration` in `src/main/java/.../internal/`
   — even as a placeholder with an empty `ConfigurationDef` — so that runtime
   configuration properties can be added later without structural changes. See
   `HttpKafkaConfiguration` for a minimal example. Include two constructors:
   a no-args constructor that calls `super(XXX_CONFIG, new Configuration())`
   for use in tests and tooling, and a `Configuration`-parameter constructor
   that calls `super(XXX_CONFIG, config)` for production use. Prefer the
   no-args constructor in unit tests and any context where no external
   configuration is needed. Add a corresponding `XxxConfigurationTest` that
   calls `shouldVerifyConstants()` (verifying property name strings match the
   `PropertyDef` names) to satisfy class coverage requirements.
8. Write k3po IT scripts covering the stream state machine — see the
   `write-k3po-it` skill for wiring up the IT class, and
   [specs/AGENTS.md](../../../specs/AGENTS.md) for script conventions.
   Required spec coverage for every binding:
   - Happy path for each `kind` and each `capability` the binding supports
   - Flow control: sender blocked by zero WINDOW, WINDOW credit restores flow
   - Orderly close: client-initiated END, server-initiated END
   - Abortive close: ABORT mid-stream, RESET on rejected stream
   - Protocol error: malformed input rejected with correct error response
   - Config validation: invalid `zilla.yaml` produces a clear startup error

   Write the spec script first by consulting the relevant protocol RFC or
   specification. Do not derive expected behavior from existing
   implementation code.

The Maven plugin generates flyweight classes during `generate-sources`. Run
`./mvnw generate-sources -pl runtime/binding-<n>` to regenerate after `.idl`
changes without a full build.

## 3. JSON schema wiring

1. Create `binding-<type>.schema.json` in the spec project under
   `src/main/resources/META-INF/zilla/schema/`.
2. Copy it to the runtime project under the same path.
3. The engine picks it up from the classpath at startup automatically —
   no engine registration step required.
4. Validate the schema by running the spec IT with an intentionally invalid
   `zilla.yaml` and confirming a clear validation error is produced.

See [specs/AGENTS.md](../../../specs/AGENTS.md) for how the aggregated schema
is assembled and the nested `-ext` extension-point convention, if the new
binding needs one.

## 4. Before opening the PR

Confirm `./mvnw install` passes, including all ITs.
