# http.kafka.oneway

Listens on http port `7114` or https port `7114` and will produce messages to the `events` topic in Kafka, synchronously. Zilla connects to Kafka using SASL-SCRAM over an SSL encrypted connection.

## Requirements

- jq
- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Send a `POST` request with an event body.

```bash
curl -v http://localhost:7114/events -H "Content-Type: application/json" -d '{"greeting":"Hello, world"}'
```

output:

```text
...
> POST /events HTTP/1.1
> Content-Type: application/json
...
< HTTP/1.1 204 No Content
```

Verify that the event has been produced to the `events` Kafka topic.

```bash
docker compose -p zilla-http-kafka-oneway exec kafkacat \
  kafkacat -C -b kafka:29092 -t events -J -u | jq .
```

output:

```json
{
  "topic": "events",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1652465273281,
  "broker": 1001,
  "headers": [
    "content-type",
    "application/json"
  ],
  "payload": "{\"greeting\":\"Hello, world\"}"
}
% Reached end of topic events [0] at offset 1
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
