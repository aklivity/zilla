# common-avro

Format-native streaming Avro for the hot path: streaming decode/encode and
Avro-schema validation over Agrona `DirectBuffer`, with no full-document
buffering and no per-message allocation. Peer to `common-json`; the two
compose only in the `model-avro` converter, neither depends on the other.

## Run performance benchmarks

Build the benchmark jar from this directory:

```sh
../../mvnw clean -DskipTests package
```

Run the Avro decode and encode benchmarks with GC allocation profiling:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  -jar target/common-avro-develop-SNAPSHOT-shaded-tests.jar \
  '.*Avro.*BM.*' -prof gc
```

For a quick smoke run while iterating, reduce the warmup and measurement time:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  -jar target/common-avro-develop-SNAPSHOT-shaded-tests.jar \
  '.*Avro.*BM.*' -prof gc -wi 1 -i 1 -r 200ms -w 200ms -f 0
```

The `--add-opens` option is required on recent JDKs when Agrona accesses
`jdk.internal.misc.Unsafe` from the shaded benchmark jar.
