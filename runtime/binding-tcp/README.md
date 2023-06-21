# binding-tcp

## Run performance benchmark

```
./mvnw clean install
```

```
cd /target
java -jar ./binding-tcp-develop-SNAPSHOT-shaded-tests.jar TcpServerBM
```

<b>Note:</b> with Java 16 or higher add ` --add-opens=java.base/java.io=ALL-UNNAMED  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED` just after `java` to avoid getting errors related to reflective access across Java module boundaries when running the benchmark.
