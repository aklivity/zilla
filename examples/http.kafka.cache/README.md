# http.kafka.cache
Listens on http port `8080` or https port `9090` and will serve cached responses from the `items-snapshots` topic in Kafka.

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
$ docker stack deploy -c stack.yml example --resolve-image never
Creating network example_net0
Creating service example_zilla
Creating service example_kafka
```

### Create compacted Kafka topic
When `example_kafka` service has finished starting up, execute the following commands to create the topic if it does not already exist:
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic items-snapshots \
        --config cleanup.policy=compact \
        --if-not-exists
Created topic items-snapshots.
```
Note the `cleanup.policy=compact` topic configuration.

### Verify behavior
Retrieve all the items, initially returns empty array.
```bash
$ curl -v http://localhost:8080/items
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
$ curl -v http://localhost:8080/items \
       -H "If-None-Match: AQIAAQ=="
...
> GET /items HTTP/1.1
> If-None-Match: AQIAAQ==
...
< HTTP/1.1 304 OK
< Content-Type: application/json
< Etag: AQIAAQ==
...
```
Retrieve a specific item, initially not found after `5 seconds`.
```bash
$ curl -v http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "Prefer: wait=5"
...
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> Prefer: wait=5
...
< HTTP/1.1 404 Not Found
...
```
Produce an item snapshot to the kafka topic.
```bash
$ echo "{\"greeting\":\"Hello, world `date`\"}" | \
    kcat -P \
         -b localhost:9092 \
         -t items-snapshots \
         -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
         -H "content-type=application/json"
```
Retrieve a specific item again, now returned.
```bash
$ curl -v http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "Prefer: wait=5"
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
$ curl -v http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "If-None-Match: AQIAAg=="
...
> GET /items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 HTTP/1.1
> If-None-Match: AQIAAg==
...
< HTTP/1.1 304 Not Modified
< Content-Type: "application/json"
< Etag: AQIAAg==
...
```
Retrieve a specific item again, if not matching the previous `etag`, prefering to wait. After 5 seconds, returns not modified.
```bash
$ curl -v http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "If-None-Match: AQIAAg==" \
       -H "Prefer: wait=5"
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
Retrieve a specific item again, if not matching the previous `etag`, prefering to wait for 60 seconds.
```bash
$ curl -v http://localhost:8080/items/5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07 \
       -H "If-None-Match: AQIAAg==" \
       -H "Prefer: wait=60"
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
$ echo "{\"greeting\":\"Hello, world `date`\"}" | \
    kcat -P \
         -b localhost:9092 \
         -t items-snapshots \
         -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
         -H "content-type=application/json"
```
Retrieve all the items, returns array with one item.
```bash
$ curl -v http://localhost:8080/items
...
> GET /items HTTP/1.1
...
< HTTP/1.1 200 OK
< Content-Type: "application/json"
< Etag: AQIAAg==
...
[{"greeting":"Hello, world ..."}]
```

### Delete compacted Kafka topic
Optionally delete the topicsto clean up, otherwise it will still be present when the stack is deployed again next time.
```
$ docker exec -it $(docker ps -q -f name=example_kafka) \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --delete \
        --topic items-snapshots \
        --if-exists
```

### Stop Kafka broker and Zilla engine
```bash
$ docker stack rm example
Removing service example_kafka
Removing service example_zilla
Removing network example_net0
```

