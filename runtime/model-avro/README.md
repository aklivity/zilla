# model-avro

Converts Avro-encoded Kafka message values to and from a configured view (e.g. JSON), validating
against a schema resolved from a catalog. Built on top of `common-avro`'s streaming Avro pipeline.

## Run performance benchmarks

Build the benchmark jar from this directory:

```sh
../../mvnw clean -DskipTests package
```

Run the Avro model decode and encode benchmarks with GC allocation profiling:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  -jar target/model-avro-develop-SNAPSHOT-shaded-tests.jar \
  '.*AvroModel.*BM.*' -prof gc
```

For a quick smoke run while iterating, reduce the warmup and measurement time:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  -jar target/model-avro-develop-SNAPSHOT-shaded-tests.jar \
  '.*AvroModel.*BM.*' -prof gc -wi 1 -i 1 -r 200ms -w 200ms -f 0
```

The `--add-opens` option is required on recent JDKs when Agrona accesses
`jdk.internal.misc.Unsafe` from the shaded benchmark jar.
