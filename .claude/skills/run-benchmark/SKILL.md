---
name: run-benchmark
description: Build and run a JMH benchmark for a runtime module and capture allocation/throughput numbers. Use when adding, modifying, or verifying a JMH benchmark under runtime/<module>/src/test/java/.../bench/.
---

# Running JMH benchmarks

JMH benchmarks live alongside unit tests under `src/test/java/.../bench/` (e.g.
`JsonPipelineBM`). They are compiled in the `test` phase but not run by
Surefire.

**Always run the affected benchmark before checking in any change to it** — a
clean `test-compile` is not enough; a real JMH pass must succeed and produce
sensible numbers. Build the module, then run the JMH harness over the test
classpath:

```bash
# 1) compile (generates the JMH BenchmarkList) and resolve the test classpath
./mvnw test-compile -pl runtime/<module>
./mvnw dependency:build-classpath -pl runtime/<module> -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt -q

# 2) run JMH (agrona needs the jdk.internal.misc open that Surefire adds for us)
CP="runtime/<module>/target/classes:runtime/<module>/target/test-classes:$(cat /tmp/cp.txt)"
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -cp "$CP" \
    org.openjdk.jmh.Main "<BenchmarkClass>" -prof gc          # full run
# quick smoke pass while iterating:
#   ... org.openjdk.jmh.Main "<BenchmarkClass>" -f 1 -wi 3 -i 4 -w 1 -r 1 -prof gc
```

Include `-prof gc` so allocation-per-op (`gc.alloc.rate.norm`, in B/op) is
reported — it is the stable signal; throughput at smoke settings has wide error
bars. Capture representative before/after numbers in the PR when a change is
meant to affect performance.
