# http.kafka.async

Listens on http port `7114` and will correlate requests and responses over the `items-requests`
and `items-responses` topics in Kafka, asynchronously.

## Requirements

- jq
- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Send a `PUT` request for a specific item.

```bash
curl -v \
    -X "PUT" "http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
    -H "Idempotency-Key: 1" \
    -H "Content-Type: application/json" \
    -H "Prefer: respond-async" \
    -d "{\"greeting\":\"Hello, world\"}"
```

output:

```text
...
> PUT /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> Idempotency-Key: 1
> Content-Type: application/json
...
< HTTP/1.1 202 Accepted
< Location: /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;cid=<location_cid>
```

Use the returned location with correlation id specified by the `cid` param to attempt completion of the asynchronous request within `10 seconds`.
Note that no correlated response has been produced to the kafka `items-responses` topic, so this will timeout after `10 seconds`.

```bash
curl -v \
       "http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;cid=<location_cid>" \
       -H "Prefer: wait=10"
```

output:

```text
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;cid=<location_cid> HTTP/1.1
> Prefer: wait=10
...
< HTTP/1.1 202 Accepted
< Location: /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;cid=<location_cid>
...
```

Use the returned location with correlation id specified by the `cid` param to attempt completion of the asynchronous request within `60 seconds`.
Note that the response will not return until you complete the following step to produce the response with `kafkacat`.

```bash
curl -v \
  "http://localhost:7114/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;cid=<location_cid>" \
  -H "Prefer: wait=60"
```

output:

```text
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;cid=<location_cid> HTTP/1.1
> Prefer: wait=60
...
< HTTP/1.1 OK
...
{"greeting":"Hello, world ..."}
```

Verify the request, then send the correlated response via the kafka `items-responses` topic.

```bash
docker compose -p zilla-http-kafka-async exec kafkacat \
  kafkacat -C -b kafka.examples.dev:29092 -t items-requests -J -u | jq .
```

output:

```json
{
  "topic": "items-requests",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1652465273281,
  "broker": 1001,
  "headers": [
    ":scheme",
    "http",
    ":method",
    "PUT",
    ":path",
    "/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07",
    ":authority",
    "localhost:7114",
    "user-agent",
    "curl/7.79.1",
    "accept",
    "*/*",
    "idempotency-key",
    "1",
    "content-type",
    "application/json",
    "zilla:reply-to",
    "items-responses",
    "zilla:correlation-id",
    "<location_cid>"
  ],
  "key": "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07",
  "payload": "{\"greeting\":\"Hello, world\"}"
}
% Reached end of topic items-requests [0] at offset 1
```

Make sure to propagate the request message `zilla:correlation-id` header verbatim as a response message `zilla:correlation-id` header.

```bash
echo "{\"greeting\":\"Hello, world `date`\"}" | docker compose -p zilla-http-kafka-async exec -T kafkacat \
  kafkacat -P \
    -b kafka.examples.dev:29092 \
    -t items-responses \
    -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
    -H ":status=200" \
    -H "zilla:correlation-id=<location_cid>"
```

The previous asynchronous request will complete with `200 OK` if done within `60 seconds` window, otherwise `202 Accepted` is returned again.

```text
< HTTP/1.1 202 Accepted
< Content-Length: 0
< Location: /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;cid=<location_cid>
<
* Connection #0 to host localhost left intact
```

Verify the response via the kafka `items-responses` topic.

```bash
docker compose -p zilla-http-kafka-async exec kafkacat \
  kafkacat -C -b kafka.examples.dev:29092 -t items-responses -J -u | jq .
```

output:

```json
{
  "topic": "items-responses",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1698334635176,
  "broker": 1,
  "headers": [
    ":status",
    "200",
    "zilla:correlation-id",
    "<location_cid>"
  ],
  "key": "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07",
  "payload": "{\"greeting\":\"Hello, world Thu Oct 26 11:37:15 EDT 2023\"}"
}
% Reached end of topic items-responses [0] at offset 1
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
