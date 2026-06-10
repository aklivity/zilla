# common-json

## Run performance benchmarks

Build the benchmark jar from this directory:

```sh
../../mvnw clean -DskipTests package
```

Run the JSON validation and projection benchmarks with GC allocation profiling:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  -jar target/common-json-develop-SNAPSHOT-shaded-tests.jar \
  '.*Json.*BM.*' -prof gc
```

For a quick smoke run while iterating, reduce the warmup and measurement time:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  -jar target/common-json-develop-SNAPSHOT-shaded-tests.jar \
  '.*Json.*BM.*' -prof gc -wi 1 -i 1 -r 200ms -w 200ms -f 0
```

The `--add-opens` option is required on recent JDKs when Agrona accesses
`jdk.internal.misc.Unsafe` from the shaded benchmark jar.
