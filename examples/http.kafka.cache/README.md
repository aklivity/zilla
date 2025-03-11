# http.kafka.cache

Listens on http port `7114` or https port `7114` and will serve cached responses from the `items-snapshots` topic in Kafka.

## Requirements

- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Retrieve all the items, initially returns empty array.

```bash
curl -v http://localhost:7114/items
```

output:

```text
...
> GET /items HTTP/1.1
...
< HTTP/1.1 200 OK
< Content-Type: application/json
< Etag: AQIAAQ==
...
[]
```

Retrieve all the items again, if not matching the previous `etag`, returns not modified.

```bash
curl -v http://localhost:7114/items \
       -H "If-None-Match: AQIAAQ=="
```

output:

```text
...
> GET /items HTTP/1.1
> If-None-Match: AQIAAQ==
...
< HTTP/1.1 304 Not Modified
< Content-Type: application/json
< Etag: AQIAAQ==
...
```

Retrieve a specific item, initially not found after `5 seconds`.

```bash
curl -v http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "Prefer: wait=5"
```

output:

```text
...
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> Prefer: wait=5
...
< HTTP/1.1 404 Not Found
...
```

Produce an item snapshot to the kafka topic.

```bash
echo "{\"greeting\":\"Hello, world `date`\"}" | docker compose -p zilla-http-kafka-cache exec -T kafkacat \
  kafkacat -P \
    -b kafka.examples.dev:29092 \
    -t items-snapshots \
    -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
    -H "content-type=application/json"
```

Retrieve a specific item again, now returned.

```bash
curl -v http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
  -H "Prefer: wait=5"
```

output:

```text
...
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> Prefer: wait=5
...
< HTTP/1.1 200 OK
< Content-Type: "application/json"
< Etag: AQIAAg==
...
{"greeting":"Hello, world ..."}
```

Retrieve a specific item again, if not matching the previous `etag`, returns not modified.

```bash
curl -v http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "If-None-Match: AQIAAg=="
```

output:

```text
...
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> If-None-Match: AQIAAg==
...
< HTTP/1.1 304 Not Modified
< Content-Type: "application/json"
< Etag: AQIAAg==
...
```

Retrieve a specific item again, if not matching the previous `etag`, preferring to wait. After 5 seconds, returns not modified.

```bash
curl -v http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "If-None-Match: AQIAAg==" \
       -H "Prefer: wait=5"
```

output:

```text
...
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> If-None-Match: AQIAAg==
> Prefer: wait=60
...
< HTTP/1.1 304 Not Modified
< Content-Type: "application/json"
< Etag: AQIAAg==
...
```

Retrieve a specific item again, if not matching the previous `etag`, preferring to wait for 60 seconds.

```bash
curl -v http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "If-None-Match: AQIAAg==" \
       -H "Prefer: wait=60"
```

output:

```text
...
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> If-None-Match: AQIAAg==
> Prefer: wait=60
...
< HTTP/1.1 200 OK
< Content-Type: "application/json"
< Etag: AQIABA==
...
{"greeting":"Hello, world ..."}
```

Before the 60 seconds elapses, produce an updated item snapshot to the kafka topic with the same `key`.
The prefer wait http request returns the new item snapshot with updated `etag` as shown above.

```bash
echo "{\"greeting\":\"Hello, world `date`\"}" | docker compose -p zilla-http-kafka-cache exec -T kafkacat \
  kafkacat -P \
    -b kafka.examples.dev:29092 \
    -t items-snapshots \
    -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
    -H "content-type=application/json"
```

Retrieve all the items, returns array with one item.

```bash
curl -v http://localhost:7114/items
```

output:

```text
...
> GET /items HTTP/1.1
...
< HTTP/1.1 200 OK
< Content-Type: "application/json"
< Etag: AQIAAg==
...
[{"greeting":"Hello, world ..."}]
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
