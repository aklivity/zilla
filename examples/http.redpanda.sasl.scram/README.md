# http.redpanda.sasl.scram
Listens on http port `8080` or https port `9090` and will produce messages to the `events` topic in `SASL/SCRAM` enabled Redpanda cluster, synchronously.

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

### Start Redpanda one-node cluster and zilla engine
```bash
$ docker stack deploy -c stack.yml example --resolve-image never
Creating network example_net0
Creating service example_redpanda
Creating service example_zilla
```

### Create SCRAM credentials
When `example_redpanda` service has finished starting up, execute the following commands to create user(`user`):
```bash
$ docker exec -it $(docker ps -q -f name=example_redpanda) \
        rpk acl user \
        create user \
        -p redpanda
Created user "user".        
```

### Create events topic
Execute the following commands to create the topic if it does not already exist:
```bash
$ docker exec -it $(docker ps -q -f name=example_redpanda) \
    rpk topic create events \
                --user user \
                --password redpanda \
                --sasl-mechanism SCRAM-SHA-256
TOPIC   STATUS
events  OK
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
Verify that the event has been produced to the `events` topic in Redpanda cluster.
```bash
$ kcat -b localhost:9092 -X security.protocol=SASL_PLAINTEXT \
		-X sasl.mechanisms=SCRAM-SHA-256 \
		-X sasl.username=user \
		-X sasl.password=redpanda \
		-t events -J -u | jq .
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

### Stop Redpanda and Zilla engine
```bash
$ docker stack rm example
Removing service example_redpanda
Removing service example_zilla
Removing network example_net0
```
