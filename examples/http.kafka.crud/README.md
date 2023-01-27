# http.kafka.crud
This simple http.kafka.crud example illustrates how to configure zilla to expose a REST API that just creates, updates, deletes and reads messages in `items-snapshots` log-compacted Kafka topic acting as a table.

### Requirements
 - Docker 20.10+
 - curl

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

### Endpoints

| Protocol | Method | Endpoint    | Topic           | Description                     |
|----------|--------|-------------|-----------------|---------------------------------|
| HTTP     | POST   | /items      | items-snapshots | Create an item.                 |
| HTTP     | PUT    | /items/{id} | items-snapshots | Update item by message the key. |
| HTTP     | DELETE | /items/{id} | items-snapshots | Delete item by message the key. |
| HTTP     | GET    | /items      | items-snapshots | Fetch all items.                |
| HTTP     | GET    | /items/{id} | items-snapshots | Fetch message by the key.       |


### Verify behavior
`POST` request.
:Note: You can remove `-H 'Idempotency-Key: 1'` to generate random key.
```bash
$ curl -k -X POST https://localhost:9090/items -H 'Idempotency-Key: 1'  -H 'Content-Type: application/json' -d '{"greeting":"Hello, world1"}'
...
POST /items HTTP/2
Host: localhost:9090
user-agent: curl/7.85.0
accept: */*
idempotency-key: 2
content-type: application/json
content-length: 28
* We are completely uploaded and fine
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
HTTP/2 204
[]
```

`GET` request to fetch specific item. 
```bash
$ curl -k -v  https://localhost:9090/items/1
...
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
< HTTP/2 200
< content-length: 27
< content-type: application/json
< etag: AQIACA==
<
* Connection #0 to host localhost left intact
{"greeting":"Hello, world1"}%
```

`PUT` request to update specific item.
```bash
$ curl -k -v -X PUT https://localhost:9090/items/1 -H 'Content-Type: application/json' -d '{"greeting":"Hello, world2"}'
...
PUT /items/1 HTTP/2
Host: localhost:9090
user-agent: curl/7.85.0
accept: */*
idempotency-key: 2
content-type: application/json
content-length: 28
* We are completely uploaded and fine
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
HTTP/2 204
```

`DELETE` request to delete specific item.
```bash
$ curl -k -v -X PUT https://localhost:9090/items/1
...
> DELETE /items/1 HTTP/2
> Host: localhost:9090
> user-agent: curl/7.85.0
> accept: */*
>
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
< HTTP/2 204
```


### Delete compacted Kafka topic
Optionally delete the topics to clean up, otherwise it will still be present when the stack is deployed again next time.
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

