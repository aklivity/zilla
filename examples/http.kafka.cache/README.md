# http.kafka.cache

Listens on http port `8080` or https port `9090` and will serve cached responses from the `items-snapshots` topic in Kafka.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- kcat

### Install kcat client
Requires Kafka client, such as `kcat`.
```bash
$ brew install kcat
```

### Setup

The `setup.sh` script:
- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `items-snapshots` topic in Kafka with the `cleanup.policy=compact` topic configuration
- starts port forwarding

```bash
$ ./setup.sh
+ helm install zilla-http-kafka-cache chart --namespace zilla-http-kafka-cache --create-namespace --wait
NAME: zilla-http-kafka-cache
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-cache
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-http-kafka-cache --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/1234567890-abcde
+ kubectl exec --namespace zilla-http-kafka-cache pod/1234567890-abcde -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic items-snapshots --config cleanup.policy=compact --if-not-exists
Created topic items-snapshots.
+ kubectl port-forward --namespace zilla-http-kafka-cache service/zilla 8080 9090
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-http-kafka-cache service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

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
< HTTP/1.1 304 Not Modified
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

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Kafka and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
99998
+ killall kubectl
+ helm uninstall zilla-http-kafka-cache --namespace zilla-http-kafka-cache
release "zilla-http-kafka-cache" uninstalled
+ kubectl delete namespace zilla-http-kafka-cache
namespace "zilla-http-kafka-cache" deleted
```
