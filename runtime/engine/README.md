# Engine benchmarks

`EngineBench`, in the engine test jar, runs the real engine with one worker and plays both ends of a binding
from the JMH thread: a `Client` initiates streams into the binding under test and a `Responder` (replays a response) or a
`Reflector` (reflects data back) stands in for the external binding it routes to. Because the benchmark thread is allocation-free in steady state, the `gc`
profiler measures the engine worker's own allocation.

| Benchmark | Binding | Notes |
| --- | --- | --- |
| `EchoServerBM` | `binding-echo` | Floor for engine, ring buffer and stream cost; no application hop |
| `ProxyServerBM` | `binding-proxy` | PROXY v2 header decode and an application hop |
| `LlmOpenaiServerBM` | `binding-llm` | HTTP request in, mock provider response out |

`echo` and `proxy` are raw full-duplex relays, so they are measured per stream and per message. `llm` is
HTTP-shaped, so it is measured per whole request.

| Method | Classes | One operation is |
| --- | --- | --- |
| `open` | `EchoServerBM`, `ProxyServerBM` | A stream opened and closed with no data. Does not depend on `size`; use `-p size=small` |
| `stream` | `EchoServerBM`, `ProxyServerBM` | One message of `size` sent on a single long-lived stream while the reflected bytes flow back, paced only by the credit the binding grants |
| `exchange` | `LlmOpenaiServerBM` | A request sent and its response received to the end |
| `leave` | all | A stream opened with a body and left in flight (the mock application withholds the reply; `echo` cannot, so it leaves the stream open) |

Read `gc.alloc.rate.norm` (B/op) from the `gc` profiler for `open`, `stream` and `exchange`, and
`retained.heap.norm` (B/op) from `RetainedHeapProfiler` for `leave`. The retained-heap profiler reports roughly
zero for the other methods, and the `gc` numbers for `leave` are not meaningful. For `stream`, `ops/s` times the
message size is the relay throughput, but it is noisy; allocation per message is the stable figure.

`RetainedObjectsProfiler` names the classes behind `retained.heap.norm`: it takes a live-object class histogram
before and after every iteration and lists the classes that grew, largest first, as B/op and objects/op. Add
`-prof io.aklivity.zilla.runtime.engine.test.RetainedObjectsProfiler` next to `RetainedHeapProfiler` and read the
iteration that retains most. The benchmark's own `Client` and its flyweights are live for every in-flight stream, so
they are part of `leave`'s figure (about 0.85 KB per stream); the rest is the engine and the binding. A class that
grows in only some iterations is usually a table doubling in size, which is how unbounded growth shows up.

`RetainedHeapProfiler` forces two full collections before and after every iteration, so it needs at least one
warmup iteration, releases state only at trial teardown, and aggregates with the maximum to ignore the last
iteration, which JMH profiles after trial teardown.

## Run

Benchmarks run over the module's resolved test classpath. Build and resolve it from the repository root
(`clean` is required: omitting it fails in moditect), using the module under test, for example `binding-llm`:

```sh
mvn clean install -pl runtime/binding-llm -am -DskipTests -DskipITs -Dskip.checks=true
mvn dependency:build-classpath -pl runtime/binding-llm -DincludeScope=test \
  -Dmdep.outputFile=/tmp/cp.txt -q
CP="runtime/binding-llm/target/classes:runtime/binding-llm/target/test-classes:$(cat /tmp/cp.txt)"
JVM="--add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Xms2g -Xmx2g"
```

Allocation per request (`llm`; use `open` or `stream` for `echo` and `proxy`):

```sh
java $JVM -cp "$CP" org.openjdk.jmh.Main LlmOpenaiServerBM.exchange \
  -jvmArgsAppend "-Xms2g -Xmx2g" -prof gc
```

Retained heap per in-flight request (`leave` does not depend on `streaming`, so fix it):

```sh
java $JVM -cp "$CP" org.openjdk.jmh.Main LlmOpenaiServerBM.leave -p streaming=false \
  -jvmArgsAppend "-Xms2g -Xmx2g" -prof io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler
```

Without escape analysis, which shows allocation the JIT would otherwise remove (pass the flag to both the
harness JVM and the forks):

```sh
java $JVM -XX:-DoEscapeAnalysis -cp "$CP" org.openjdk.jmh.Main LlmOpenaiServerBM.exchange \
  -jvmArgsAppend "-Xms2g -Xmx2g -XX:-DoEscapeAnalysis" -prof gc
```

For echo and proxy, use `runtime/binding-echo` or `runtime/binding-proxy` for the module and `EchoServerBM` or
`ProxyServerBM` for the class. A quick smoke pass while iterating:

```sh
java $JVM -cp "$CP" org.openjdk.jmh.Main LlmOpenaiServerBM.exchange -p size=small -p streaming=false \
  -f 1 -wi 2 -i 3 -w 1 -r 1 -prof gc
```

The `--add-opens` option is required on recent JDKs when Agrona accesses `jdk.internal.misc.Unsafe`. Parameters
(`size` is `small` or `10k`, `streaming` is `false` or `true` for llm) apply to every method in the class, so a
method that does not depend on one will repeat identical results across its values.
