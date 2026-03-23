# Contributing to Zilla

[![Build Status][build-status-image]][build-status]
[![Slack Community][community-image]][community-join]

:+1::tada: First of all, thank you for taking the time to contribute to Zilla! :tada::+1:

## Table of Contents

- [Reporting Bugs](#reporting-bugs)
- [Submitting a Fix](#submitting-a-fix)
- [Proposing a Feature](#proposing-a-feature)
- [Building Locally](#building-locally)
- [Architecture Overview](#architecture-overview)
  - [Module Structure](#module-structure)
  - [Threading Model and Factory Classes](#threading-model-and-factory-classes)
- [Testing Philosophy](#testing-philosophy)
  - [Why Protocol-Level Integration Tests?](#why-protocol-level-integration-tests)
  - [k3po Script Tests](#k3po-script-tests)
  - [When Unit Tests Are Appropriate](#when-unit-tests-are-appropriate)
  - [Writing New Tests](#writing-new-tests)
- [Questions](#questions)

---

## Reporting Bugs

- **Ensure the bug was not already reported** by searching on GitHub under [Issues](https://github.com/aklivity/zilla/issues).
- If you can't find an open issue addressing the problem, [open a new one](https://github.com/aklivity/zilla/issues/new). Include a **title and clear description**, as much relevant information as possible, and ideally an **executable test case** demonstrating that the expected behavior is not occurring.

## Submitting a Fix

- Open a new GitHub pull request with the patch.
- Ensure the PR description clearly describes the problem and solution. Include the relevant issue number if applicable.
- Run `./mvnw clean install` locally to verify a successful build and confirm that the GitHub Actions automated PR build completes successfully.

## Proposing a Feature

- Suggest your change in the [Slack Community #zilla channel][community-join] and start writing code.
- Do not open a GitHub issue until you have collected positive feedback about the change. GitHub issues are primarily intended for bug reports and fixes.

## Building Locally

```bash
./mvnw clean install
```

The build runs Checkstyle, compiles all modules, and executes the full test suite including k3po integration tests. A full build takes several minutes. To build without running tests:

```bash
./mvnw clean install -DskipTests
```

---

## Architecture Overview

### Module Structure

The repository is split into two parallel module trees under `runtime/` and `specs/`:

- **`runtime/<module>`** — implementation code for each protocol binding, catalog, guard, vault, exporter, and the core engine.
- **`specs/<module>.spec`** — k3po network script tests and helper functions for the corresponding runtime module. Spec modules are published as test-scoped artifacts so integration tests in `runtime/` can depend on them.

Every runtime module that handles protocol interactions has a matching spec module. This is a hard convention: new protocol functionality needs corresponding spec coverage.

Other top-level directories:

- **`build/`** — the `flyweight-maven-plugin`, which generates Agrona flyweight boilerplate from descriptor files.
- **`incubator/`** — experimental features not yet part of the stable API surface.
- **`cloud/`** — Helm chart and Docker image packaging, isolated from runtime concerns.

### Threading Model and Factory Classes

Understanding the threading model is essential to reading the protocol binding code without misinterpreting its structure.

**Zilla runs one I/O thread per CPU core.** Each thread owns its resources exclusively — there is no shared mutable state between threads during normal operation. This eliminates the need for locks, atomic operations, or thread-safe data structures on the hot path, which is central to Zilla's performance goals.

**Factories are instantiated once per thread.** A class like `HttpServerFactory` is not a singleton — each I/O thread creates its own instance. Because each factory instance is confined to a single thread, all instance fields are safe to read and write without synchronization. The factory instance acts as an implicit thread-local context.

**Inner classes decompose functionality within a factory.** Each inner class inside a factory — a stream handler, a connection state machine, a codec — holds a reference to its enclosing factory instance. This gives every inner class free, unsynchronized access to the shared per-thread state (buffer pools, configuration, counters, codec instances) through the outer class reference, without passing that context explicitly through every method call.

The result is that a factory source file may be large, but it is not a monolith with poor separation of concerns. The inner classes are the decomposition. They are co-located in the same file because co-location is what grants them access to the shared outer-instance scope — separating them into distinct top-level classes would require either making that state static (wrong) or passing it explicitly everywhere (verbose and error-prone).

**In practice, when reading a factory class:**

- Treat the outer class fields as the thread-local shared context.
- Treat each inner class as a self-contained component that borrows that context.
- Follow the inner class hierarchy rather than the file length when assessing complexity.

**In practice, when modifying a factory class:**

- Additions of new protocol behaviour typically mean a new inner class, or new methods on an existing inner class.
- Shared per-thread resources (buffer slots, counters) belong on the outer factory instance.
- Do not introduce `static` state into a factory to work around the per-instance model — this breaks thread isolation.

---

## Testing Philosophy

### Why Protocol-Level Integration Tests?

Zilla's primary quality instrument is **protocol conformance testing at the integration level**, not unit testing of individual classes. This is a deliberate design choice, not an oversight. Understanding the reasoning will help you write tests that provide lasting value.

The core property we want from a test suite is **stability across implementation changes**. A test that must be rewritten whenever the internal implementation changes provides a weak safety guarantee — it tells you the current implementation matches the current tests, but not whether the implementation is actually correct. When tests and implementation change together, the original test's ability to detect bugs is lost.

Protocol integration tests avoid this problem because their fixed point of stability is external and durable: **published protocol standards**. HTTP/1.1, MQTT 3.1 and 5.0, Kafka wire protocol, gRPC over HTTP/2 — these standards are stable requirements for compatibility with any conforming client or broker. A test that verifies correct RFC 7230 message framing does not need to change when the internal HTTP parser is refactored, optimized, or replaced entirely. If the refactored implementation accidentally breaks framing, the unchanged test will catch it.

This means:

- **A large internal refactoring with no spec test changes is the goal**, not a warning sign. If you restructure an inner class hierarchy or change how buffer slots are managed, the spec tests should pass unchanged. That unchanged passing is the proof that no regression was introduced.
- **A PR that adds new integration test scripts alongside new functionality is a complete contribution.** The spec tests are the deliverable, not supporting material.
- **A PR that adds new unit tests for internal implementation details is not necessarily an improvement.** If those unit tests would need to be updated whenever the implementation changes, they are adding maintenance cost without adding stability.

### k3po Script Tests

Integration tests use [k3po](https://github.com/aklivity/k3po), a network protocol scripting tool that drives real network interactions against a running Zilla engine instance.

Each test scenario consists of a pair of script files:

- **`client.rpt`** — the client side of the conversation: what to send, what to expect back.
- **`server.rpt`** — the server (or upstream) side: what Zilla forwards, what the backend responds.

Scripts are plain-text and intentionally readable. A simple HTTP example:

```
# client.rpt
connect "http://localhost:8080/"
  option http:transport "zilla://streams/net0"
  option zilla:window 8192
  option zilla:transmission "duplex"
connected

write http:method "GET"
write http:header "Accept" "application/json"
write close

read http:status "200" /.*/
read http:header "Content-Type" "application/json"
read http:payload /.+/
read close
```

Scripts live in the matching `specs/<module>.spec` module under `src/main/scripts/`. The directory path encodes the scenario identity, e.g.:

```
specs/binding-http.spec/src/main/scripts/
  io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/
    message.format/
      request.with.headers/
        client.rpt
        server.rpt
```

Integration test runners in `runtime/<module>/src/test/` wire scripts to a live engine via `@Specification` annotations:

```java
@Test
@Configuration("server.yaml")
@Specification({
    "${net}/request.with.headers/client",
    "${app}/request.with.headers/server" })
public void requestWithHeaders() throws Exception
{
    k3po.finish();
}
```

The `EngineRule` starts a Zilla engine from the named YAML configuration. The `K3poRule` drives both scripts simultaneously and asserts that both complete without deviation.

**Adding a new scenario** means:

1. Writing `client.rpt` and `server.rpt` in the appropriate `specs/` directory.
2. Adding a `@Test` method in the matching `*IT.java` class in `runtime/`.
3. Providing a `zilla.yaml` configuration fixture in the spec module's `src/test/resources/` if a new topology is needed.

Script names should reflect the normative language of the relevant specification or RFC — `request.with.headers`, `server.sent.close.notify`, `connect.with.authorization` — so the test suite reads as a structured coverage map of the standard.

### When Unit Tests Are Appropriate

Unit tests using JUnit, JMock, and Mockito are present and appropriate for components whose correctness is determined by implementation logic independent of protocol interactions:

- **Configuration parsing** — verifying that `EngineConfiguration` correctly reads and validates YAML properties.
- **Metrics and counters** — verifying histogram recording, scalar accumulation, and reader arithmetic.
- **Utility classes** — header value parsers, codec helpers, data structure implementations.
- **Engine internals** — namespace resolution, affinity calculation, binding lifecycle.

These components have stable, implementation-anchored contracts that don't change when protocol handling is refactored, making unit tests appropriate and low-maintenance.

Unit tests are **not** the right tool for:

- Verifying that an HTTP stream is correctly proxied to Kafka. Use a k3po spec test.
- Verifying that MQTT CONNECT and CONNACK frames are handled correctly. Use a k3po spec test.
- Verifying that TLS handshake errors produce the right downstream behaviour. Use a k3po spec test.

### Writing New Tests

When adding a new protocol feature or fixing a bug:

1. **First, write or extend the k3po scripts** in the appropriate `specs/` module. The scenario should be derivable from the relevant RFC or protocol specification section.
2. **Add the `@Test` method** in the integration test runner in `runtime/`.
3. **Run the build** to confirm the new test fails before the implementation change (confirming the test is actually exercising the new path), then passes after.
4. If you are adding new configuration logic, metric calculations, or utility functions, unit tests for those components are welcome and appropriate.

When fixing a bug, the k3po scripts that reproduce the bug are the most valuable part of the PR — they document the failure mode, prevent regression, and confirm the fix is complete.

---

## Questions

Ask any question about the Zilla source code in the [Slack Community #zilla channel][community-join].

Thank you for helping to make the Zilla open source project successful! 🙂

[build-status-image]: https://github.com/aklivity/zilla/workflows/build/badge.svg
[build-status]: https://github.com/aklivity/zilla/actions
[community-image]: https://img.shields.io/badge/slack-@aklivitycommunity-blue.svg?logo=slack
[community-join]: https://join.slack.com/t/aklivitycommunity/shared_invite/zt-sy06wvr9-u6cPmBNQplX5wVfd9l2oIQ
