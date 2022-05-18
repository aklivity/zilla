# http.kafka.sync
Listens on http port `8080` or https port `9090` and will correlate requests and responses over the `items-requests` and `items-responses` topics in Kafka, synchronously.

### Requirements
 - Docker 20.10+
 - curl
 - kcat
 - jq

### Install kcat client
Requires Kafka client, such as `kcat`.
```bash
$ brew install kcat
```

### Start kafka broker and zilla engine
```bash
$ docker stack deploy -c stack.yml example --resolve-image never
Creating network example_net0
Creating service example_zilla
Creating service example_kafka
Creating service example_zookeeper
```

### Create request and response Kafka topics
When `example_kafka` service has finished starting up, execute the following commands to create the topics if they do not already exist:
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic items-requests \
        --if-not-exists
Created topic items-requests.
```
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic items-responses \
        --if-not-exists
Created topic items-responses.
```

### Verify behavior
Send a `PUT` request for a specific item.
Note that the response will not return until you complete the following step to produce the response with `kcat`.
```bash
$ curl -v \
       -X "PUT" http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "Idempotency-Key: 1" \
       -H "Content-Type: application/json" \
       -d "{\"greeting\":\"Hello, world\"}"
...
> PUT /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> Idempotency-Key: 1
> Content-Type: application/json
...
< HTTP/1.1 200 OK
...
{"greeting":"Hello, world ..."}
```
Verify the request, then send the correlated response via the kafka `items-responses` topic.
```bash
$ kcat -C -b localhost:9092 -t items-requests -J -u | jq .
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
    "localhost:8080",
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
    "1-e75a4e507cc0dc66a28f5a9617392fe8"
  ],
  "key": "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07",
  "payload": "{\"greeting\":\"Hello, world\"}"
}
% Reached end of topic items-requests [0] at offset 1
```
Make sure to propagate the request message `zilla:correlation-id` header verbatim as a response message `zilla:correlation-id` header.
```bash
$ echo "{\"greeting\":\"Hello, world `date`\"}" | \
    kcat -P \
         -b localhost:9092 \
         -t items-responses \
         -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
         -H ":status=200" \
         -H "zilla:correlation-id=1-e75a4e507cc0dc66a28f5a9617392fe8"
```

### Delete request and response Kafka topics
Optionally delete the topics to clean up, otherwise they will still be present when the stack is deployed again next time.
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --delete \
        --topic items-requests \
        --if-exists
```
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --delete \
        --topic items-responses \
        --if-exists
```

### Stop Kafka broker and Zilla engine
```bash
$ docker stack rm example
Removing service example_kafka
Removing service example_zilla
Removing service example_zookeeper
Removing network example_net0
```
