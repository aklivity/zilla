---
name: add-engine-concept
description: Checklist for adding a new pluggable engine concept (binding, guard, vault, catalog, store, model, resolver, router, exporter, metric group) and its test implementation to runtime/engine. Use when introducing a new SPI concept type to the Zilla engine.
---

# Adding a new engine concept

Test implementations belong in the engine module's test sources
(`runtime/engine/src/test/java/.../engine/test/internal/<concept>/`), not in a
separate project. Do not create a `runtime/<concept>-test/` module.

1. Add `TestXxxFactorySpi` (and supporting classes) under
   `src/test/java/.../engine/test/internal/<concept>/`.
2. Register it via a `META-INF/services/<SpiInterfaceName>` file under
   `src/test/resources/` — test code does not use the Java module system, so
   use `ServiceLoader` service files, not `module-info.java`.
3. Add an `<include>` entry for the new concept's classes in the
   `maven-jar-plugin` `test-jar` execution in `pom.xml`, e.g.
   `io/aklivity/zilla/runtime/engine/test/internal/<concept>/**/*.class` —
   without this the classes will not be published in the test JAR and other
   spec modules will not be able to load the test implementation.
4. Update `specs/engine.spec/src/main/scripts/.../config/server.yaml` to
   include a `type: test` instance of the new concept.
5. Update the `test` binding (`TestBindingFactorySpi`) to interact with the
   new concept so its handler code paths are exercised.
6. Add or extend a test method in `EngineIT` (in
   `runtime/engine/src/test/java/.../engine/`) that exercises the new
   concept's behavior — `EngineIT` is the primary mechanism for achieving
   code coverage of the engine project, so every new concept type must be
   reachable from at least one test method. The corresponding test config
   (`server.yaml`) and `.rpt` scripts live in `specs/engine.spec`.

Each new concept also needs a `test.schema.patch.json` — see "Test schema
patches" in [runtime/engine/AGENTS.md](../../../runtime/engine/AGENTS.md) for
where that lives and how it's wired into the test classpath.

## Adding a method to `EngineContext`

If the new concept requires adding a method to the `EngineContext` interface,
search for all classes that implement it beyond `EngineWorker` — component
modules such as `binding-tls` and `binding-echo` have their own `*Worker`
classes that implement `EngineContext` for benchmarking or testing (e.g.,
`TlsWorker`, `EchoWorker`). Each of these must be updated with a no-op
default implementation of the new method or the build will fail.
