# sse.kafka.fanout.filter

Streams Kafka records to clients using **Server-Sent Events (SSE)** with **key and header-based filtering**.

Zilla listens on HTTP port `7114` and fans out records from the Kafka `events` topic to SSE clients. Each SSE connection only receives records whose **Kafka key** and **Kafka headers** match the values encoded in the request path.

## Requirements

- docker compose

## Setup

To start the Docker Compose stack defined in the [compose.yaml](compose.yaml) file:

```bash
docker compose up -d
```

## How filtering works

The SSE endpoint uses the following path structure:

```
/{topic}/{key}/{tag}
```

- `topic` maps to the Kafka topic name
- `key` filters records by **Kafka record key**
- `tag` filters records by the Kafka **header `tag`**

Only records that match **both** the key and the header filter are delivered to the SSE client.

## Verify behavior (terminal)

### Open an SSE stream with key and header filter

```bash
curl -N --http2 -H "Accept:text/event-stream" "http://localhost:7114/events/1/abc"
```

This subscribes to:
- topic: `events`
- key: `1`
- header: `tag=abc`

### Produce a Kafka record with matching key and header

```bash
echo '{ "id": 1, "name": "Hello World!" }' | \
docker compose -p zilla-sse-kafka-fanout exec -T kafkacat \
  kafkacat -P \
    -b kafka.examples.dev:29092 \
    -t events \
    -k "1" \
    -H "tag=abc"
```

The payload appears as the `data:` field in the SSE stream.

### Non-matching records are filtered out

Records with a different key or a different (or missing) `tag` header are not delivered to the SSE client.

### Compacted topic behavior

The `events` topic is configured as **compacted**. Kafka retains only the **latest value per key**.

## Browser UI

Browse to [http://localhost:7114/index.html](http://localhost:7114/index.html) and make sure to visit the `localhost` site and trust the `localhost` certificate.

Click **Go** to attach the browser’s EventSource to Kafka via Zilla.

## Reliability

Stop and start the `zilla` service to observe automatic reconnection behavior.

## Teardown

```bash
docker compose down
```
