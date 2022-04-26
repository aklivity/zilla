# sse.kafka.fanout
Listens on http port `8080` or https port `9090` and will stream back whatever is published to the `zilla` topic in Kafka.

### Requirements
 - Docker 20.10+
 - sse-cat (via `node` version `14` or higher)
 - kcat

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

### Start Kafka broker and Zilla engine
```bash
$ docker stack deploy -c stack.yml example
Creating network example_net0
Creating service example_zilla
Creating service example_kafka
Creating service example_zookeeper
```

### Create compacted Kafka topic
When example_kafka service has finished starting up, execute the following command:
```bash
docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server kafka.internal.net:9092 \
        --create \
        --topic events \
        --config cleanup.policy=compact
```
Note the `cleanup.policy=compact` topic configuration.

### Verify behavior
Connect `sse-cat` client first, then send `Hello, world ...` from `kcat` producer client.
Note that the `Hello, world ...` message will not arrive until after using `kcat` to produce the `Hello, world ...` message in the next step.
```bash
$ sse-cat http://localhost:8080/events
Hello, world ...
```
```bash
$ echo "Hello, world `date`" | kcat -P -b localhost:9092 -t events -k 1
```
Note that only the latest messages with distinct keys are guaranteed to be retained by a compacted Kafka topic, so use different values for `-k` above to retain more than one message in the `events` topic.

## Browser

Browse to `https://localhost:9090/index.html` and make sure to visit the `localhost` site and trust the `localhost` certificate.

Click the `Go` button to attach the browser SSE event source to Kafka via Zilla.

All non-compacted messages with distinct keys in the `events` Kafka topic are replayed to the browser.

Open the browser developer tools console to see additional logging, such as the `open` event.

Additional messages produced to the `events` Kafka topic then arrive at the browser live.


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

Any messages produced to the `events` Kafka topic while the browser was attempting to reconnect are now delivered immediately.

Additional messages produced to the `events` Kafka topic then arrive at the browser live.

## Stop Kafka broker and Zilla engine
```bash
$ docker stack rm
Removing service example_kafka
Removing service example_zilla
Removing service example_zookeeper
Removing network example_net0
```
