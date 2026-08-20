# binding-kafka

Binds a Kafka topic partition to the Zilla local cache: fetches each partition once from the broker,
stores it as memory-mapped segment files, and serves it to any number of downstream clients without
additional round-trips to Kafka.

## Run performance benchmarks

`engine` is a `provided`-scope dependency here (it is supplied by the runtime image, not bundled per
binding), so the benchmarks run from the test classpath rather than a shaded jar. Compile the module
and resolve its test classpath:

```sh
../../mvnw test-compile -pl runtime/binding-kafka
../../mvnw dependency:build-classpath -pl runtime/binding-kafka -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt -q
```

Run the `KafkaPipeline` benchmarks with GC allocation profiling:

```sh
CP="target/classes:target/test-classes:$(cat /tmp/cp.txt)"
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED -cp "$CP" \
  org.openjdk.jmh.Main '.*KafkaPipelineBM.*' -prof gc
```

For a quick smoke run while iterating, reduce the warmup and measurement time:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED -cp "$CP" \
  org.openjdk.jmh.Main '.*KafkaPipelineBM.*' -prof gc -wi 1 -i 1 -r 200ms -w 200ms -f 1
```

The `--add-opens` option is required on recent JDKs when Agrona accesses
`jdk.internal.misc.Unsafe` from the benchmark classpath.
