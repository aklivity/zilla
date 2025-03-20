# sse.kafka.fanout

Listens on http port `7114` or https port `7114` and will stream back whatever is published to the `events` topic in Kafka.

## Requirements

- docker compose
- sse-cat

### Install sse-cat client

Requires Server-Sent Events client, such as `sse-cat` version `2.0.5` or higher on `node` version `14` or higher.

```bash
npm install -g sse-cat
```

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Open a `text/event-stream` from the sse endpoint in a terminal.

```bash
curl -N --http2 -H "Accept:text/event-stream" "http://localhost:7114/events"
```

In a new terminal send a text payload from the `kafkacat` producer client.

```bash
echo '{ "id": 1, "name": "Hello World!"}' | docker compose -p zilla-sse-kafka-fanout exec -T kafkacat \
  kafkacat -P -b kafka.examples.dev:29092 -t events -k "1"
```

The text payload will be the `data:` of the sse message seen in the `text/event-stream` terminal session.

Note that only the latest messages with distinct keys are guaranteed to be retained by a compacted Kafka topic, so use different values for `-k` above to retain more than one message in the `events` topic.

### Browser

Browse to [http://localhost:7114/index.html]() and make sure to visit the `localhost` site and trust the `localhost` certificate.

Click the `Go` button to attach the browser SSE event source to Kafka via Zilla.

All non-compacted messages with distinct keys in the `events` Kafka topic are replayed to the browser.

Open the browser developer tools console to see additional logging, such as the `open` event.

Additional messages produced to the `events` Kafka topic then arrive at the browser live.

### Reliability

Simulate connection loss by stopping the `zilla` service in the `docker` stack.

```bash
docker compose -p zilla-sse-kafka-fanout stop zilla
```

This causes errors to be logged in the browser console during repeated attempts to automatically reconnect.

Simulate connection recovery by starting the `zilla` service again.

```bash
docker compose -p zilla-sse-kafka-fanout start zilla
```

Any messages produced to the `events` Kafka topic while the browser was attempting to reconnect are now delivered immediately.

Additional messages produced to the `events` Kafka topic then arrive at the browser live.

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
