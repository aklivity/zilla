# http.kafka.oneway
Listens on http port `8080` or https port `9090` and will produce messages to the `events` topic in Kafka, synchronously.

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
```

### Create events Kafka topics
When `example_kafka` service has finished starting up, execute the following commands to create the topic if it does not already exist:
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic events \
        --if-not-exists
Created topic events.
```

### Verify behavior
Send a `POST` request with an event body.
```bash
$ curl -v \
       -X "POST" http://localhost:8080/events \
       -H "Content-Type: application/json" \
       -d "{\"greeting\":\"Hello, world\"}"
...
> POST /events HTTP/1.1
> Content-Type: application/json
...
< HTTP/1.1 204 No Content
```
Verify that the event has been produced to the `events` Kafka topic.
```bash
$ kcat -C -b localhost:9092 -t events -J -u | jq .
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

### Stop Kafka broker and Zilla engine
```bash
$ docker stack rm example
Removing service example_kafka
Removing service example_zilla
Removing network example_net0
```
