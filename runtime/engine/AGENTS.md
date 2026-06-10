# Zilla engine — Agent Guide

Guidance scoped to `runtime/engine/` — the Zilla engine module, its SPI
contracts, and the test implementations of every engine concept. General
runtime conventions (modular dependencies, threading, factory/flyweight
pattern, stream model) live in [../AGENTS.md](../AGENTS.md).

---

## Engine SPI conventions

The engine SPI interfaces (`GuardHandler`, `BindingHandler`, `VaultHandler`,
`StoreHandler`, etc.) are consumed by many independently-developed modules.
Two conventions to follow when adding to or modifying them:

- **Javadoc stays protocol-neutral.** Describe the contract — what the method
  receives, what it returns, what callers can rely on — without referencing
  specific upstream protocols or specific bindings. Protocol-specific
  terminology (e.g. authorization-server `state` parameters, `redirect_uri`,
  JWT `sub` claims) belongs in the implementing module's source, not the
  engine SPI. When the parameter format depends on the implementation, say
  "the format is guard-specific" (or binding-/vault-/store-specific) and let
  the implementing module document the specifics.
- **Default methods return `null` for absent values, not empty collections.**
  Matches the nullable convention already used by `identity`, `attribute`, and
  `credentials` on `GuardHandler`. Even though the JDK collection factories
  (`Set.of()`, `List.of()`) return cached singletons, the project convention
  is `null` so callers learn one rule across the SPI rather than mixing styles.

PR descriptions for engine SPI changes follow the same neutrality — describe
the engine-level addition and the in-tree consumer that justifies it (e.g.
"required by `binding-mcp` elicitation flow"); do not name downstream products
or proprietary modules as motivation.

### Adding a method to `EngineContext`

If a new engine concept requires adding a method to the `EngineContext`
interface, search for all classes that implement it beyond `EngineWorker` —
component modules such as `binding-tls` and `binding-echo` have their own
`*Worker` classes that implement `EngineContext` for benchmarking or testing
(e.g., `TlsWorker`, `EchoWorker`). Each of these must be updated with a no-op
default implementation of the new method or the build will fail.

---

## Test implementations for engine concepts

Every engine concept (binding, guard, vault, catalog, exporter, metric group,
model, resolver, router) has a minimal **test implementation** that lives in this
module's test sources under
`src/test/java/.../engine/test/internal/<concept>/`. For example:

| Concept | Test implementation class |
| --- | --- |
| binding | `TestBindingFactorySpi` |
| guard | `TestGuardFactorySpi`, `TestGuardContext` |
| vault | `TestVaultFactorySpi`, `TestVault`, `TestVaultContext` |
| catalog | `TestCatalogFactorySpi`, `TestCatalog`, `TestCatalogContext` |
| exporter | `TestExporterFactorySpi`, `TestExporter`, `TestExporterHandler` |
| metric group | `TestMetricGroupFactorySpi`, `TestMetricGroup` |
| model | `TestModelFactorySpi`, `TestModel`, `TestModelContext` |
| resolver | `TestResolverFactorySpi`, `TestResolverSpi` |
| router | `TestRouterFactorySpi`, `TestRouter`, `TestRouterContext` |

The engine module is built with Maven's `test-jar` packaging so these classes
are published as `engine:<version>:test-jar`. Every `specs/*.spec` module
declares this as a dependency (alongside the regular `engine` jar) so the test
implementations are on the classpath when spec ITs run:

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>engine</artifactId>
    <version>${project.version}</version>
    <type>test-jar</type>
</dependency>
```

The `specs/engine.spec` IT uses all test implementations together in a single
`server.yaml` that wires a `test` binding against a `test` guard, `test` vault,
`test` catalog, `test` exporter, and `test` metric group. This canonical config
is the integration smoke-test for the engine itself and provides code coverage
for the engine's wiring of all concept types.

### Adding a new engine concept

Test implementations belong in **this module's test sources**, not in a
separate project. Do not create a `runtime/<concept>-test/` module.

1. Add `TestXxxFactorySpi` (and supporting classes) under
   `src/test/java/.../engine/test/internal/<concept>/`
2. Register it via a `META-INF/services/<SpiInterfaceName>` file under
   `src/test/resources/` — test code does not use the Java module system, so
   use `ServiceLoader` service files, not `module-info.java`
3. Add an `<include>` entry for the new concept's classes in the
   `maven-jar-plugin` `test-jar` execution in `pom.xml`, e.g.
   `io/aklivity/zilla/runtime/engine/test/internal/<concept>/**/*.class` —
   without this the classes will not be published in the test JAR and other
   spec modules will not be able to load the test implementation
4. Update `specs/engine.spec/src/main/scripts/.../config/server.yaml` to
   include a `type: test` instance of the new concept
5. Update the `test` binding (`TestBindingFactorySpi`) to interact with the
   new concept so its handler code paths are exercised
6. Add or extend a test method in `EngineIT` (in this module's
   `src/test/java/.../engine/`) that exercises the new concept's behavior —
   `EngineIT` is the primary mechanism for achieving code coverage of the
   engine project, so every new concept type must be reachable from at least
   one test method. The corresponding test config (`server.yaml`) and `.rpt`
   scripts live in `specs/engine.spec`

### Test schema patches — single source of truth

Each test concept type has a `test.schema.patch.json` in `specs/engine.spec`
under `src/main/resources/io/aklivity/zilla/specs/engine/schema/<concept>/`.
That spec file is the single source of truth. The engine module's `pom.xml`
uses the `maven-dependency-plugin` `unpack-test-resources` execution to copy
those patches into `target/test-classes` at `process-test-resources` time,
remapping the path from `io/aklivity/zilla/specs/engine/schema/` to
`io/aklivity/zilla/runtime/engine/test/internal/`. Do not create a second copy
of these JSON files anywhere in the engine module.

The principle is that no production implementation of any concept type should
be required to test the engine's wiring — only the test implementations are
needed.
