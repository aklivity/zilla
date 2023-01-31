# http.kafka.crud

This simple http.kafka.crud example illustrates how to configure zilla to expose a REST API that just creates, updates,
deletes and reads messages in `items-snapshots` log-compacted Kafka topic acting as a table.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Setup

The `setup.sh` script:
- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `items-snapshots` topic in Kafka with the `cleanup.policy=compact` topic configuration
- starts port forwarding

```bash
$ ./setup.sh
+ helm install zilla-http-kafka-crud chart --namespace zilla-http-kafka-crud --create-namespace --wait
NAME: zilla-http-kafka-crud
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-crud
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-http-kafka-crud --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-1234567890-abcde
+ kubectl exec --namespace zilla-http-kafka-crud pod/kafka-1234567890-abcde -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic items-snapshots --config cleanup.policy=compact --if-not-exists
Created topic items-snapshots.
+ kubectl port-forward --namespace zilla-http-kafka-crud service/zilla 8080 9090
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-http-kafka-crud service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Endpoints

| Protocol | Method | Endpoint    | Topic           | Description             |
|----------|--------|-------------|-----------------|-------------------------|
| HTTP     | POST   | /items      | items-snapshots | Create an item.         |
| HTTP     | PUT    | /items/{id} | items-snapshots | Update item by the key. |
| HTTP     | DELETE | /items/{id} | items-snapshots | Delete item by the key. |
| HTTP     | GET    | /items      | items-snapshots | Fetch all items.        |
| HTTP     | GET    | /items/{id} | items-snapshots | Fetch item by the key.  |


### Verify behavior

`POST` request.

Note: You can remove `-H 'Idempotency-Key: 1'` to generate random key.

```bash
$ curl -k -v -X POST https://localhost:9090/items -H 'Idempotency-Key: 1'  -H 'Content-Type: application/json' -d '{"greeting":"Hello, world1"}'
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
```

`GET` request to fetch specific item. 

```bash
$ curl -k -v https://localhost:9090/items/1
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
$ curl -k -v -X DELETE https://localhost:9090/items/1
...
> DELETE /items/1 HTTP/2
> Host: localhost:9090
> user-agent: curl/7.85.0
> accept: */*
>
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
< HTTP/2 204
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Kafka and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
99998
+ killall kubectl
+ helm uninstall zilla-http-kafka-crud --namespace zilla-http-kafka-crud
release "zilla-http-kafka-crud" uninstalled
+ kubectl delete namespace zilla-http-kafka-crud
namespace "zilla-http-kafka-crud" deleted
```
