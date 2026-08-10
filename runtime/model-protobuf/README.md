# model-protobuf

The `protobuf` model: validates and transcodes Kafka message values against a Protobuf schema
resolved from a configured catalog, composing `common-protobuf` for the wire side and
`common-json` for the protobuf &lt;-&gt; JSON view.

## Run performance benchmarks

`model-protobuf` depends on `engine` as a `provided`-scope dependency (the engine is supplied by
the runtime host, not bundled), so the decode/encode pipeline benchmarks run over the module's
resolved **test** classpath rather than a single self-contained jar.

Compile the module and resolve its test classpath from this directory:

```sh
../../mvnw test-compile -pl runtime/model-protobuf
../../mvnw dependency:build-classpath -pl runtime/model-protobuf -DincludeScope=test \
  -Dmdep.outputFile=/tmp/model-protobuf-cp.txt -q
```

Run the decode and encode pipeline benchmarks with GC allocation profiling:

```sh
CP="target/classes:target/test-classes:$(cat /tmp/model-protobuf-cp.txt)"
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED -cp "$CP" \
  org.openjdk.jmh.Main '.*Protobuf.*BM.*' -prof gc
```

For a quick smoke run while iterating, reduce the warmup and measurement time:

```sh
java --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED -cp "$CP" \
  org.openjdk.jmh.Main '.*Protobuf.*BM.*' -prof gc -wi 1 -i 1 -r 200ms -w 200ms -f 1
```

The `--add-opens` option is required on recent JDKs when Agrona accesses
`jdk.internal.misc.Unsafe`.
