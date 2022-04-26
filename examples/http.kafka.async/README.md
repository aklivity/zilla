# http.kafka.async
Listens on http port `8080` or https port `9090` and will correlate requests and responses over the `items-requests` and `items-responses` topics in Kafka, asynchronously.

### Requirements
 - Docker 20.10+
 - curl
 - kcat

### Install kcat client
Requires Kafka client, such as `kcat`.
```bash
$ brew install kcat
```

### Start kafka broker and zilla engine
```bash
$ docker stack deploy -c stack.yml example
Creating network example_net0
Creating service example_zilla
Creating service example_kafka
Creating service example_zookeeper
```

### Create request and response Kafka topics
When `example_kafka` service has finished starting up, execute the following commands:
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server kafka.internal.net:9092 \
        --create \
        --topic items-requests
Created topic items-requests.
```
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server kafka.internal.net:9092 \
        --create \
        --topic items-responses
Created topic items-responses.
```

### Verify behavior
Send a `PUT` request for a specific item.
```bash
$ curl -v \
    -X "PUT" "http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
    -H "Idempotency-Key: 1" \
    -H "Content-Type: application/json" \
    -H "Prefer: respond-async" \
    -d "{\"greeting\":\"Hello, world `date`\"}"
...
> PUT /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> Idempotency-Key: 1
> Content-Type: application/json
...
< HTTP/1.1 202 Accepted
< Location: /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;1
```

Use the returned location to attempt to verify completion of the asynchronous request within `10 seconds`.
Note that no correlated response has been produced to the kafka `items-responses` topic, so this will timeout after `10 seconds`.
```bash
$ curl -v \
       "http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;1" \
       -H "Prefer: wait=10"
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;1 HTTP/1.1
> Prefer: wait=10
...
< HTTP/1.1 202 Accepted
< Location: /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;1
...
```
Use the returned location to attempt to verify completion of the asynchronous request within `60 seconds`.
Note that the response will not return until you complete the following step.
```bash
$ curl -v \
       "http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;1" \
       -H "Prefer: wait=60"
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07;1 HTTP/1.1
> Prefer: wait=60
...
< HTTP/1.1 OK
...
{"greeting":"Hello, world ..."}
```
Verify the request, then send the correlated response via the kafka `items-responses` topic.
```bash
$ kcat -C -b localhost:9092 -t items-requests -f "[%k]\n{%h}\n%s\n"
[5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07]
{:scheme=http,:method=PUT,:path=/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07,:authority=localhost:8080,user-agent=curl/7.79.1,accept=*/*,content-type=application/json,idempotency-key=1,zilla:reply-to=items-responses,zilla:correlation-id=1}
{"greeting":"Hello, world ..."}
% Reached end of topic items-requests [0] at offset 1
```
```bash
$ echo "{\"greeting\":\"Hello, world `date`\"}" | \
    kcat -P \
         -b localhost:9092 \
         -t items-responses \
         -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
         -H ":status=200" \
         -H "zilla:correlation-id=1"
```

### Stop Kafka broker and Zilla engine
```bash
$ docker stack rm
Removing service example_kafka
Removing service example_zilla
Removing service example_zookeeper
Removing network example_net0
```
