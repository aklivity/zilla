### Running locally

```bash
cat zpm.json.template | env VERSION=develop-SNAPSHOT envsubst > zpm.json
```

```bash
./zpmw clean
```

```bash
./zpmw install --debug --exclude-remote-repositories  
```

```
./zilla start --config <path/to/zilla.yaml>
```

### Startup benchmark

`ZillaStartupBM` is a single-shot JMH benchmark that starts the image in Docker and reports the time from the
container start recorded by Docker to the last modified time of `/var/run/zilla/ready` in the container,
as the `startupMs` secondary result (read its `min, avg, max` line). It needs a running Docker daemon.

```bash
./mvnw test-compile -pl cloud/docker-image
./mvnw dependency:build-classpath -pl cloud/docker-image -DincludeScope=test -Dmdep.outputFile=/tmp/cp.txt -q
java -cp "cloud/docker-image/target/test-classes:$(cat /tmp/cp.txt)" \
    org.openjdk.jmh.Main ZillaStartupBM -p image=ghcr.io/aklivity/zilla:develop-SNAPSHOT
```
