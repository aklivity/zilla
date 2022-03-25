# sse.kafka.fanout
Listens on http port `8080` or https port `9090` and will stream back whatever is published to the `zilla` topic in Kafka.

### Requirements
 - Docker 20.10+

### Start kafka broker and zilla engine
```bash
$ docker stack deploy -c stack.yml example
Creating network example_net0
Creating service example_zilla
Creating service example_kafka
Creating service example_zookeeper
```

### Install sse-cat client
Requires Server-Sent Events client, such as `sse-cat` version `2.0.5` or higher on `node` version `14` or higher.
```bash
$ npm install -g sse-cat
```

### Install kcat client
Requires Kafka client, such as `kcat`.
```bash
$ brew install kcat
```

### Verify behavior
Connect `sse-cat` client first, then send `Hello, world` from `kcat` producer client.
```bash
$ sse-cat http://localhost:8080/zilla
Hello, world
```
```bash
$ echo "Hello, world `date`" | kcat -P -b localhost:9092 -t zilla
```

## Browser
Browse to `https://localhost:8080/index.html` and make sure to visit the `localhost` site and trust the `localhost` certificate.

Click the `Go` button to attach the browser SSE event source to Kafka via Zilla.

All existing messages in the `zilla` Kafka topic are replayed to the browser.

Open the browser developer tools console to see additional logging, such as the `open` event.

Additional messages produced to the `zilla` Kafka topic then arrive at the browser live.


## Reliability

Simulate connection loss by stopping the `zilla` service in the `docker` stack.

```
$ docker service scale example_zilla=0
```

This causes errors to be logged in the browser console during repeated attempts to automatically reconnect.

Simulate connection recovery by starting the `zilla` service again.

```
$ docker service scale example_zilla=1
```

Any messages produced to the `zilla` Kafka topic while the browser was attempting to reconnect are now delivered immediately.

Additional messages produced to the `zilla` Kafka topic then arrive at the browser live.
